package de.dennisguse.opentracks.io.healthconnect.exporter;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.health.connect.client.HealthConnectClient;
import androidx.health.connect.client.records.DistanceRecord;
import androidx.health.connect.client.records.ElevationGainedRecord;
import androidx.health.connect.client.records.ExerciseRoute;
import androidx.health.connect.client.records.ExerciseSessionRecord;
import androidx.health.connect.client.records.Record;
import androidx.health.connect.client.records.SpeedRecord;
import androidx.health.connect.client.records.metadata.Device;
import androidx.health.connect.client.records.metadata.Metadata;
import androidx.health.connect.client.units.Length;
import androidx.health.connect.client.units.Velocity;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import de.dennisguse.opentracks.data.ContentProviderUtils;
import de.dennisguse.opentracks.data.TrackPointIterator;
import de.dennisguse.opentracks.data.models.ActivityType;
import de.dennisguse.opentracks.data.models.Position;
import de.dennisguse.opentracks.data.models.Track;
import de.dennisguse.opentracks.data.models.TrackPoint;
import kotlin.coroutines.Continuation;
import kotlin.jvm.functions.Function2;
import kotlinx.coroutines.BuildersKt;

/**
 * Exports a single track as an ExerciseSession to Health Connect.
 * Uses the Track.uuid as clientRecordId to prevent duplicates on re-export.
 */
//TODO add reasons for failures (if shown in the UI: must be localized)
//TODO Export sensor data (permissions needed)
//TODO Show progress: like ExportActivity? Or use a notification (aka handle it as a background process)
public class HealthConnectWorker extends Worker {

    private static final String TAG = HealthConnectWorker.class.getSimpleName();
    static final String WORK_TAG = "HealthConnectSync";

    static final String TRACK_ID_KEY = "trackId";

    public HealthConnectWorker(Context context, WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        if (!HealthConnectUtils.isAvailable(getApplicationContext())) {
            Log.w(TAG, "Health Connect SDK not available");
            return Result.failure();
        }

        //TODO If not all permissions are granted, export the data that was granted.
        if (!HealthConnectUtils.hasRequiredPermissions(getApplicationContext())) {
            Log.w(TAG, "Health Connect required permissions not granted");
            return Result.failure();
        }

        Track.Id trackId = new Track.Id(getInputData().getLong(TRACK_ID_KEY, -1L));
        if (trackId.id() != -1L) {
            return exportTrack(trackId);
        }
        return exportAll();
    }

    private Result exportTrack(Track.Id trackId) {
        try {
            export(getApplicationContext(), trackId);
            return Result.success();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.retry();
        } catch (Exception e) {
            Log.e(TAG, "Health Connect export failed for track " + trackId, e);
            return Result.failure();
        }
    }

    private Result exportAll() {
        List<Track> allTracks = new ContentProviderUtils(getApplicationContext())
                .getTracks();

        for (Track track : allTracks) {
            if (isStopped()) {
                Log.i(TAG, "Initial Health Connect sync stopped by WorkManager");
                return Result.success();
            }

            try {
                export(getApplicationContext(), track.id());
            } catch (InterruptedException e) {
                return Result.retry();
            } catch (Exception e) {
                Log.e(TAG, "Initial Health Connect sync failed for track " + track.id().id(), e);
                return Result.failure();
            }
        }

        return Result.success();
    }


    private static ExportResult export(Context context, Track.Id trackId) throws InterruptedException {
        ContentProviderUtils contentProviderUtils = new ContentProviderUtils(context);
        Track track = contentProviderUtils.getTrack(trackId);
        if (track == null) {
            throw new RuntimeException("Track not found: " + trackId);
        }

        HealthConnectClient client = HealthConnectClient.getOrCreate(context);
        Metadata metadata = Metadata.activelyRecorded(
                new Device(Device.TYPE_PHONE, Build.MANUFACTURER, Build.MODEL),
                track.uuid().toString()
        );

        List<Record> records = new ArrayList<>();

        ExerciseData exerciseData = createExerciseData(contentProviderUtils, trackId);

        //Avoid bug in HealthConnect: data must be within ExerciseSessionRecord.startTime and ExerciseSessionRecord.stopTime
        List<ExerciseRoute.Location> locations = exerciseData.locations.stream()
                .filter(i -> track.startTime().toInstant().isBefore(i.getTime()) && track.stopTime().toInstant().isAfter(i.getTime()))
                .toList();

        records.add(new ExerciseSessionRecord(
                track.startTime().toInstant(),
                track.startTime().getOffset(),
                track.stopTime().toInstant(),
                track.stopTime().getOffset(),
                metadata,
                toExerciseType(track.activityType()),
                track.name(),
                track.description(),
                Collections.emptyList(),
                Collections.emptyList(),
                new ExerciseRoute(locations)));

        records.add(new DistanceRecord(
                track.startTime().toInstant(),
                track.startTime().getOffset(),
                track.stopTime().toInstant(),
                track.stopTime().getOffset(),
                Length.meters(track.statistics().totalDistance().toM()),
                metadata
        ));

        if (track.statistics().altitudeGainLoss() != null) {
            records.add(new ElevationGainedRecord(
                    track.startTime().toInstant(),
                    track.startTime().getOffset(),
                    track.stopTime().toInstant(),
                    track.stopTime().getOffset(),
                    Length.meters(track.statistics().altitudeGainLoss().gain_m()),
                    metadata
            ));
        }

        if (!exerciseData.speeds.isEmpty()) {
            records.add(new SpeedRecord(
                    track.startTime().toInstant(),
                    track.startTime().getOffset(),
                    track.stopTime().toInstant(),
                    track.stopTime().getOffset(),
                    exerciseData.speeds,
                    metadata
            ));
        }

        Object ignored = BuildersKt.runBlocking(
                kotlin.coroutines.EmptyCoroutineContext.INSTANCE,
                (Function2) (scope, cont) -> client.insertRecords(records, (Continuation) cont));

        Log.i(TAG, "Exported track to Health Connect: " + track.name());
        return new ExportResult(track.name());
    }

    private static ExerciseData createExerciseData(ContentProviderUtils contentProviderUtils, Track.Id trackId) {
        List<ExerciseRoute.Location> locations = new LinkedList<>();
        List<SpeedRecord.Sample> speeds = new LinkedList<>();

        try (TrackPointIterator iterator = contentProviderUtils.getTrackPointIterator(trackId, null)) {
            while (iterator.hasNext()) { //TODO iterator.iterator.forEachRemaining()
                TrackPoint trackPoint = iterator.next();
                if (trackPoint.type() != TrackPoint.Type.TRACKPOINT) continue; //TODO Include SEGMENT_START_AUTOMATIC?

                Position position = trackPoint.position();

                if (position.hasLocation()) {
                    //ExcerciseRoute does not allow duplicated timestamps.
                    if (locations.stream().noneMatch(location -> location.getTime().equals(position.time()))) {
                        locations.add(new ExerciseRoute.Location(
                                trackPoint.getTime(),
                                position.latitude(),
                                position.longitude(),
                                position.horizontalAccuracy() != null ? Length.meters(position.horizontalAccuracy().toM()) : null,
                                position.verticalAccuracy() != null ? Length.meters(position.verticalAccuracy().toM()) : null,
                                position.altitude() != null ? Length.meters(position.altitude().toM()) : null)
                        );
                    }
                }

                if (position.hasSpeed()) {
                    speeds.add(new SpeedRecord.Sample(
                            trackPoint.getTime(),
                            Velocity.metersPerSecond(position.speed().toMPS())
                    ));
                }

                //TODO Cadence
                //TODO Power
                //TODO Heartrate
            }
        }

        return new ExerciseData(locations, speeds);
    }

    private record ExerciseData(
            List<ExerciseRoute.Location> locations,
            List<SpeedRecord.Sample> speeds
    ) {}


    private static int toExerciseType(ActivityType activityType) {
        return switch (activityType) {
            case RUNNING, STREET_RUNNING, TRAIL_RUNNING, TRACK_RUNNING -> ExerciseSessionRecord.EXERCISE_TYPE_RUNNING;
            case BIKING, CYCLING, ROAD_BIKING, MOUNTAIN_BIKING, TRACK_CYCLING -> ExerciseSessionRecord.EXERCISE_TYPE_BIKING;
            case WALKING, SPEED_WALKING -> ExerciseSessionRecord.EXERCISE_TYPE_WALKING;
            case HIKING, OFF_TRAIL_HIKING, TRAIL_HIKING -> ExerciseSessionRecord.EXERCISE_TYPE_HIKING;
            case CROSS_COUNTRY_SKIING, SKIING -> ExerciseSessionRecord.EXERCISE_TYPE_SKIING;
            case SNOW_BOARDING -> ExerciseSessionRecord.EXERCISE_TYPE_SNOWBOARDING;
            case KAYAKING -> ExerciseSessionRecord.EXERCISE_TYPE_PADDLING;
            case ROWING -> ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE;
            case SWIMMING -> ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL;
            case SWIMMING_OPEN -> ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER;
            default -> ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT;
        };
    }

    public record ExportResult(String trackName) {
    }
}
