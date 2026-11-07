package de.dennisguse.opentracks.sensors;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.time.Instant;

import de.dennisguse.opentracks.data.models.Position;
import de.dennisguse.opentracks.sensors.sensorData.SensorData;
import de.dennisguse.opentracks.sensors.sensorData.SensorDataSet;
import de.dennisguse.opentracks.services.handlers.TrackPointCreator;
import de.dennisguse.opentracks.settings.PreferencesUtils;
import de.dennisguse.opentracks.util.SystemUtils;

public class SensorManager implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = SensorManager.class.getSimpleName();

    private Context context;

    private Handler handler;

    private PowerManager.WakeLock wakeLock;

    private final TrackPointCreator observer;

    private AltitudeChangeHandler altitudeChangeHandler;

    private GpsHandler gpsHandler;

    public SensorManager(TrackPointCreator observer) {
        this.observer = observer;
    }

    public void start(Context context, Handler handler) {
        if (isStarted()) {
            throw new RuntimeException("SensorManager cannot be started twice; stop first.");
        }

        wakeLock = SystemUtils.acquireWakeLock(context, wakeLock);
        this.context = context;
        this.handler = handler;

        gpsHandler = new GpsHandler(this, observer);
        altitudeChangeHandler = new AltitudeChangeHandler(this);

        onSharedPreferenceChanged(null, null);
    }

    public void stop() {
        wakeLock = SystemUtils.releaseWakeLock(wakeLock);

        altitudeChangeHandler.disconnect();
        altitudeChangeHandler = null;

        gpsHandler.disconnect();
        gpsHandler = null;

        context = null;
        handler = null;
    }

    public void reset() {
        if (gpsHandler == null || altitudeChangeHandler == null) {
            Log.d(TAG, "No recording running and no reset necessary.");
            return;
        }

        Log.i(TAG, "Resetting data");

        altitudeChangeHandler.resetAggregated();
        gpsHandler.resetAggregated();
    }

    @VisibleForTesting
    public GpsHandler getGpsHandler() {
        return gpsHandler;
    }

    @Deprecated
    @VisibleForTesting
    public AltitudeChangeHandler getAltitudeChangeHandler() {
        return altitudeChangeHandler;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, @Nullable String key) {
        if (!isStarted()) return;

        if (PreferencesUtils.isKey(gpsHandler.getSensorPreferenceKey(), key)) {
            gpsHandler.connect();
        }

        if (PreferencesUtils.isKey(altitudeChangeHandler.getSensorPreferenceKey(), key)) {
            altitudeChangeHandler.connect();
        }
    }

    public Instant getNow() {
        return observer.getNow();
    }

    public Context getContext() {
        return context;
    }

    public Handler getHandler() {
        return handler;
    }

    public void onChange() {
        observer.onChange();
    }

    public SensorDataSet getSensorDataSet(Instant now) {
        //We always need a Position with now
        SensorData<Position> gpsSensorData = gpsHandler.getSensorData(now);
        if (gpsSensorData != null && gpsSensorData.data() != null) {
            gpsSensorData = new SensorData<>(gpsSensorData.data().with(now), gpsSensorData.sensorNameOrAddress());
        } else {
            gpsSensorData = new SensorData<>(Position.of(now), "");
        }

        return new SensorDataSet(
                gpsSensorData,
                altitudeChangeHandler.getSensorData(now)
        );
    }

    private boolean isStarted() {
        return wakeLock != null;
    }
}
