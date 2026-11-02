package de.dennisguse.opentracks.sensors;

import android.content.Context;
import android.location.LocationManager;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.time.Duration;
import java.time.Instant;

import de.dennisguse.opentracks.data.models.Distance;
import de.dennisguse.opentracks.data.models.Position;
import de.dennisguse.opentracks.settings.PreferencesUtils;

/**
 * This class handle GPS status according to received locations and some thresholds.
 */
class GpsStatusManager {

    // The duration that GpsStatus waits from minimal interval to consider GPS lost.
    @VisibleForTesting
    public static final Duration SIGNAL_LOST_THRESHOLD = Duration.ofSeconds(30);

    private Distance horizontalAccuracyThreshold;
    // Threshold for time without points.
    private Duration signalLostThreshold;

    private GpsStatusValue gpsStatus = GpsStatusValue.GPS_NONE;
    private GpsStatusListener observer;
    private final Context context;

    @Nullable
    private Position lastPosition = null;

    private Handler handler;

    public final Runnable gpsStatusTimer = () -> {
        determineGpsStatusByTime(Instant.now()); //TODO Get now via TrackPointCreator?
    };


    public GpsStatusManager(Context context, GpsStatusListener observer, Handler handler) {
        this.observer = observer;
        this.context = context;
        this.handler = handler;

        onRecordingDistanceChanged(PreferencesUtils.getRecordingDistanceInterval());
        onMinSamplingIntervalChanged(PreferencesUtils.getMinSamplingInterval());
    }

    public void start() {
        observer.onGpsStatusChanged(GpsStatusValue.GPS_ENABLED);
    }

    /**
     * The client that uses GpsStatus has to call this method to stop the Runnable if needed.
     */
    public void stop() {
        stopTimer();
        observer.onGpsStatusChanged(GpsStatusValue.GPS_NONE);
    }

    /**
     * Method to change the bad threshold from outside.
     *
     * @param value New preference value to signalBadThreshold.
     */
    public void onRecordingDistanceChanged(@NonNull Distance value) {
        horizontalAccuracyThreshold = value;
    }

    public void onMinSamplingIntervalChanged(Duration value) {
        signalLostThreshold = SIGNAL_LOST_THRESHOLD.plus(value); //TODO Reschedule gpsStatusTimer?
    }

    /**
     * This method must be called every time a new {@link Position} is received.
     * Receive new position and calculate the new status if needed.
     * It look for GPS changes in lastLocation if it's not null. If it's null then look for in lastValidLocation if any.
     */
    public void onNewTrackPoint(@NonNull Position position) {
        lastPosition = position;

        determineGpsStatusOnTrackpoint(position);
    }

    /**
     * Checks if {@link Position} has new GPS status looking up time and accuracy.
     * It depends of signalLostThreshold and signalBadThreshold.
     * If there is any change then it does the change.
     * Also, it'll run the runnable if signal is bad or stop it if the signal is lost.
     */
    private void determineGpsStatusOnTrackpoint(@NonNull Position position) {
        if (position.fulfillsAccuracy(horizontalAccuracyThreshold)) {
            if (gpsStatus != GpsStatusValue.GPS_SIGNAL_FIX) {
                setGpsStatus(GpsStatusValue.GPS_SIGNAL_FIX);
                scheduleTimer(); //TODO
            }
        } else {
            // GPS signal is to weak; TODO we might need a time-based threshold here as well (i.e., warn after Duration)
            if (gpsStatus != GpsStatusValue.GPS_SIGNAL_BAD) {
                setGpsStatus(GpsStatusValue.GPS_SIGNAL_BAD);
                scheduleTimer();
            }
        }
    }

    void determineGpsStatusByTime(Instant now) {
        if (lastPosition == null) {
            return;
        }
        if (signalLostThreshold.minus(Duration.between(lastPosition.time(), now)).isNegative()) {
            // Too much time without receiving signal -> signal lost.
            if (gpsStatus != GpsStatusValue.GPS_SIGNAL_LOST) {
                setGpsStatus(GpsStatusValue.GPS_SIGNAL_LOST);
            }
            return;
        }
        scheduleTimer();
    }

    /**
     * This method must be called from the client every time the GPS sensor is enabled.
     * Anyway, it checks that GPS is enabled because the client assumes that if it's on then GPS is enabled but user can disable GPS by hand.
     */
    public void onGpsEnabled() {
        if (gpsStatus == GpsStatusValue.GPS_ENABLED) {
            return;
        }
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            setGpsStatus(GpsStatusValue.GPS_ENABLED);
            scheduleTimer();
        } else {
            onGpsDisabled();
        }
    }


    /**
     * This method must be called from service every time the GPS sensor is disabled.
     */
    public void onGpsDisabled() {
        if (gpsStatus == GpsStatusValue.GPS_DISABLED) {
            return;
        }

        setGpsStatus(GpsStatusValue.GPS_DISABLED);
        lastPosition = null;
        stopTimer();
    }

    private void setGpsStatus(GpsStatusValue current) {
        gpsStatus = current;
        if (observer != null) {
            observer.onGpsStatusChanged(current);
        }
    }

    private void scheduleTimer() {
        handler.removeCallbacks(gpsStatusTimer);
        handler.postDelayed(gpsStatusTimer, signalLostThreshold.toMillis());
    }

    private void stopTimer() {
        handler.removeCallbacks(gpsStatusTimer);
    }

    public interface GpsStatusListener {
        void onGpsStatusChanged(GpsStatusValue currentStatus);
    }
}
