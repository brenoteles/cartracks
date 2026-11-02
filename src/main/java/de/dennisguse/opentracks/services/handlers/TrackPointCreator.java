package de.dennisguse.opentracks.services.handlers;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import de.dennisguse.opentracks.data.models.Distance;
import de.dennisguse.opentracks.data.models.Position;
import de.dennisguse.opentracks.data.models.TrackPoint;
import de.dennisguse.opentracks.sensors.GpsStatusValue;
import de.dennisguse.opentracks.sensors.SensorManager;
import de.dennisguse.opentracks.sensors.sensorData.SensorDataSet;
import de.dennisguse.opentracks.settings.PreferencesUtils;

/**
 * Creates TrackPoints while recording by fusing data from different sensors (e.g., GNSS, barometer, BLE sensors).
 */
public class TrackPointCreator implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = TrackPointCreator.class.getSimpleName();

    private Context context;

    private final Callback service;

    @NonNull
    private Clock clock = new MonotonicClock();
    private final SensorManager sensorManager;

    public TrackPointCreator(Callback service) {
        this.service = service;
        this.sensorManager = new SensorManager(this);
    }

    public synchronized void start(@NonNull Context context, @NonNull Handler handler) {
        this.context = context;

        sensorManager.start(context, handler);
    }

    public boolean isStarted() {
        return context != null;
    }

    private synchronized void reset() {
        sensorManager.reset();
    }

    public void stop() {
        sensorManager.stop();
        this.context = null;
    }

    /**
     * Got a new TrackPoint from Bluetooth only; contains no GPS location.
     */
    public synchronized void onChange() {
        TrackPoint trackPoint = createTrackPoint(TrackPoint.Type.TRACKPOINT);

        boolean stored = service.newTrackPoint(trackPoint, PreferencesUtils.getThresholdHorizontalAccuracy());  //TODO Cache preference for performance
        if (stored) {
            reset();
        }
    }

    public synchronized TrackPoint createSegmentStartManual() {
        return TrackPoint.createSegmentStartManualWithTime(getNow());
    }

    public synchronized TrackPoint createSegmentEnd() {
        TrackPoint segmentEnd = createTrackPoint(TrackPoint.Type.SEGMENT_END_MANUAL);
        reset();
        return segmentEnd;
    }

    public synchronized TrackPoint createIdle() {
        TrackPoint idle = createTrackPoint(TrackPoint.Type.IDLE);
        reset();
        return idle;
    }

    public Pair<TrackPoint, SensorDataSet> createCurrentTrackPoint(@Nullable TrackPoint lastTrackPointUISpeed, @Nullable TrackPoint lastTrackPointUIAltitude, @Nullable TrackPoint lastStoredTrackPointWithLocation) {
        Instant now = getNow();
        SensorDataSet sensorDataSet = sensorManager.getSensorDataSet(now);

        Position position = sensorDataSet.position().data();

        if (!position.hasLocation()
                && lastStoredTrackPointWithLocation != null
                && lastStoredTrackPointWithLocation.position().hasLocation()) {
            //We are taking the coordinates from the last stored TrackPoint, so the distance is monotonously increasing.
            position = position.withCoordinates(lastStoredTrackPointWithLocation.position());
        }

        if (lastTrackPointUISpeed != null)
            position = position.with(lastTrackPointUISpeed.position().speed());

        if (lastTrackPointUIAltitude != null)
            position = position.with(lastTrackPointUIAltitude.position().altitude());

        TrackPoint currentTrackPoint = new TrackPoint(
                null,
                TrackPoint.Type.TRACKPOINT,
                position,
                sensorDataSet.distance() != null ? sensorDataSet.distance().data() : null,
                sensorDataSet.heartRate() != null ? sensorDataSet.heartRate().data() : null,
                sensorDataSet.temperature() != null ? sensorDataSet.temperature().data() : null,
                sensorDataSet.cadence() != null ? sensorDataSet.cadence().data() : null,
                sensorDataSet.power() != null ? sensorDataSet.power().data() : null,
                sensorDataSet.altitudeGainLoss() != null ? sensorDataSet.altitudeGainLoss().data() : null
        );

        return new Pair<>(currentTrackPoint, sensorDataSet);
    }

    private TrackPoint createTrackPoint(TrackPoint.Type type) {
        Instant now = getNow();

        SensorDataSet sensorDataSet = sensorManager.getSensorDataSet(now);

        Position position = sensorDataSet.position().data();
        if (sensorDataSet.speed() != null) {
            position = position.with(sensorDataSet.speed().data());
        }

        return
                new TrackPoint(
                        null,
                        type,
                        position,
                        sensorDataSet.distance() != null ? sensorDataSet.distance().data() : null,
                        sensorDataSet.heartRate() != null ? sensorDataSet.heartRate().data() : null,
                        sensorDataSet.temperature() != null ? sensorDataSet.temperature().data() : null,
                        sensorDataSet.cadence() != null ? sensorDataSet.cadence().data() : null,
                        sensorDataSet.power() != null ? sensorDataSet.power().data() : null,
                        sensorDataSet.altitudeGainLoss() != null ? sensorDataSet.altitudeGainLoss().data() : null
                );
    }

    public Instant getNow() {
        return Instant.now(clock);
    }

    @VisibleForTesting
    public SensorManager getSensorManager() {
        return sensorManager;
    }

    @VisibleForTesting
    public void setClock(@NonNull String time) {
        this.clock = Clock.fixed(Instant.parse(time), ZoneId.of("CET"));
    }

    @Deprecated //TODO This should be refactored. Can we use a SensorDataSet for this?
    public void sendGpsStatus(GpsStatusValue gpsStatusValue) {
        service.newGpsStatus(gpsStatusValue);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, @Nullable String key) {
        sensorManager.onSharedPreferenceChanged(sharedPreferences, key);
    }

    public interface Callback {
        /**
         * @return Was TrackPoint stored (not discarded)?
         */
        boolean newTrackPoint(TrackPoint trackPoint, Distance thresholdHorizontalAccuracy);

        void newGpsStatus(GpsStatusValue gpsStatusValue);
    }
}
