package de.dennisguse.opentracks.content.data;

import android.content.Context;
import android.net.Uri;
import android.util.Pair;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import de.dennisguse.opentracks.data.ContentProviderUtils;
import de.dennisguse.opentracks.data.MarkerIterator;
import de.dennisguse.opentracks.data.TrackPointIterator;
import de.dennisguse.opentracks.data.models.ActivityType;
import de.dennisguse.opentracks.data.models.Altitude;
import de.dennisguse.opentracks.data.models.AltitudeGainLoss;
import de.dennisguse.opentracks.data.models.Cadence;
import de.dennisguse.opentracks.data.models.Distance;
import de.dennisguse.opentracks.data.models.HeartRate;
import de.dennisguse.opentracks.data.models.Marker;
import de.dennisguse.opentracks.data.models.Position;
import de.dennisguse.opentracks.data.models.Power;
import de.dennisguse.opentracks.data.models.Speed;
import de.dennisguse.opentracks.data.models.Statistics;
import de.dennisguse.opentracks.data.models.Track;
import de.dennisguse.opentracks.data.models.TrackPoint;
import de.dennisguse.opentracks.ui.markers.MarkerUtils;
import de.dennisguse.opentracks.util.FileUtils;

public class TestDataUtil {

    public static final double INITIAL_LATITUDE = 37.0;
    public static final double INITIAL_LONGITUDE = -57.0;
    public static final double ALTITUDE_INTERVAL = 2.5;
    public static final float ALTITUDE_GAIN = 3;
    public static final float ALTITUDE_LOSS = 3;

    /**
     * Create a track without any trackPoints.
     */
    public static Track createTrack(Track.Id trackId) {
        return new Track(
                trackId,
                UUID.randomUUID(),
                "Test: " + trackId.id(),
                "",
                "",
                ActivityType.UNKNOWN,
                ZoneOffset.UTC,
                Statistics.DEFAULT);
    }

    /**
     * Simulates a track which is used for testing.
     *
     * @param trackId   the trackId of the track
     * @param numPoints the trackPoints number in the track
     */
    @Deprecated //TODO Should start with SEGMENT_START_MANUAL and end with SEGMENT_END_MANUAL.
    public static Pair<Track, List<TrackPoint>> createTrack(Track.Id trackId, int numPoints) {
        Track track = createTrack(trackId);

        List<TrackPoint> trackPoints = new ArrayList<>(numPoints);
        for (int i = 0; i < numPoints; i++) {
            trackPoints.add(createTrackPoint(i));
        }

        return new Pair<>(track, trackPoints);
    }


    public static TrackData createTestingTrack(Track.Id trackId) {
        Track track = createTrack(trackId);

        int i = 0;
        List<TrackPoint> trackPoints = List.of(
                TrackPoint.createSegmentStartManualWithTime(Instant.ofEpochSecond(i++)),
                createTrackPoint(i++),
                createTrackPoint(i++),
                createTrackPoint(i++),
                createTrackPoint(i++, TrackPoint.Type.SEGMENT_START_AUTOMATIC),
                createTrackPoint(i++),
                createTrackPoint(i++),
                createTrackPoint(i++),
                createTrackPoint(i++, TrackPoint.Type.SEGMENT_END_MANUAL),

                TrackPoint.createSegmentStartManualWithTime(Instant.ofEpochSecond(i++)),
                createTrackPoint(i++),
                createTrackPoint(i++),
                createTrackPoint(i++),
                createTrackPoint(i, TrackPoint.Type.SEGMENT_END_MANUAL)
        );

        List<Marker> markers = List.of(
                new Marker(null, trackId, "Marker 1", "Marker description 1", "Marker typeLocalized 3", trackPoints.get(1).position(), null),
                new Marker(null, trackId, "Marker 2", "Marker description 2", "Marker typeLocalized 3", trackPoints.get(4).position(), null),
                new Marker(null, trackId, "Marker 3", "Marker description 3", "Marker typeLocalized 3", trackPoints.get(5).position(), null)
        );

        return new TrackData(track, trackPoints, markers);
    }

    public record TrackData(Track track, List<TrackPoint> trackPoints, List<Marker> markers) {
    }


    public static Track createTrackAndInsert(ContentProviderUtils contentProviderUtils, Track.Id trackId, int numPoints) {
        Pair<Track, List<TrackPoint>> pair = createTrack(trackId, numPoints);

        insertTrackWithLocations(contentProviderUtils, pair.first, pair.second);

        return pair.first;
    }

    public static TrackPoint createTrackPoint(int i, TrackPoint.Type type, AltitudeGainLoss altitudeGainLoss) {
        return new TrackPoint(
                null,
                type,
                new Position(
                        Instant.ofEpochSecond(i),
                        INITIAL_LATITUDE + (double) i / 10000.0,
                        INITIAL_LONGITUDE - (double) i / 10000.0,
                        Distance.of(i / 100.0f),
                        Altitude.WGS84.of(i * ALTITUDE_INTERVAL),
                        null,
                        null,
                        Speed.of(5f + (i / 10f))
                ),
                null,
                HeartRate.of(100f + i % 80),
                null,
                Cadence.of(300f + i),
                Power.of(400f + i),
                altitudeGainLoss
        );
    }

    public static TrackPoint createTrackPoint(int i, TrackPoint.Type type) {
        return createTrackPoint(i, TrackPoint.Type.TRACKPOINT, new AltitudeGainLoss(ALTITUDE_GAIN, ALTITUDE_LOSS));
    }

    public static TrackPoint createTrackPoint(int i) {
        return createTrackPoint(i, TrackPoint.Type.TRACKPOINT);
    }


    /**
     * Inserts a track with locations into the database.
     *
     * @param track       track to be inserted
     * @param trackPoints trackPoints to be inserted
     */
    public static void insertTrackWithLocations(ContentProviderUtils contentProviderUtils, Track track, List<TrackPoint> trackPoints) {
        contentProviderUtils.insertTrack(track);
        contentProviderUtils.bulkInsertTrackPoint(trackPoints, track.id());
    }

    public static Marker createMarkerWithPhoto(Context context, Track.Id trackId, TrackPoint trackPoint) throws IOException {
        return createMarkerWithPhoto(context, trackId, trackPoint, "Marker description");
    }

    public static Marker createMarkerWithPhoto(Context context, Track.Id trackId, TrackPoint trackPoint, String description) throws IOException {
        File dstFile = new File(MarkerUtils.getImageUrl(context, trackId));
        dstFile.createNewFile();
        Uri photoUri = FileUtils.getUriForFile(context, dstFile);

        return new Marker(
                null,
                trackId,
                "Marker name",
                description,
                "Marker typeLocalized",
                trackPoint.position(),
                photoUri);
    }

    public static List<TrackPoint> getTrackPoints(ContentProviderUtils contentProviderUtils, Track.Id trackId) {
        try (TrackPointIterator trackPointIterator = contentProviderUtils.getTrackPointIterator(trackId, null)) {
            ArrayList<TrackPoint> trackPoints = new ArrayList<>();
            trackPointIterator.forEachRemaining(trackPoints::add);
            return trackPoints;
        }
    }

    public static List<Marker> getMarkers(ContentProviderUtils contentProviderUtils, Track.Id trackId) {
        try (MarkerIterator trackPointIterator = contentProviderUtils.getMarkerIterator(trackId)) {
            ArrayList<Marker> markers = new ArrayList<>();
            trackPointIterator.forEachRemaining(markers::add);
            return markers;
        }
    }
}
