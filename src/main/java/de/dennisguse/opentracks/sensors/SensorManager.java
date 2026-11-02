package de.dennisguse.opentracks.sensors;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.time.Instant;

import de.dennisguse.opentracks.data.models.Cadence;
import de.dennisguse.opentracks.data.models.Distance;
import de.dennisguse.opentracks.data.models.Position;
import de.dennisguse.opentracks.data.models.Speed;
import de.dennisguse.opentracks.sensors.sensorData.AggregatorCyclingCadence;
import de.dennisguse.opentracks.sensors.sensorData.AggregatorCyclingDistanceSpeed;
import de.dennisguse.opentracks.sensors.sensorData.AggregatorCyclingPower;
import de.dennisguse.opentracks.sensors.sensorData.AggregatorHeartRate;
import de.dennisguse.opentracks.sensors.sensorData.AggregatorRunning;
import de.dennisguse.opentracks.sensors.sensorData.AggregatorTemperature;
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

    private HeartRateHandler heartRateHandler;
    private TemperatureHandler temperatureHandler;
    private CyclingCadenceHandler cyclingCadenceHandler;
    private CyclingDistanceSpeedHandler cyclingDistanceSpeedHandler;
    private CyclingPowerHandler cyclingPowerHandler;
    private RunningSpeedCadenceDistanceHandler runningSpeedCadenceDistanceHandler;

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
        heartRateHandler = new HeartRateHandler(this);
        temperatureHandler = new TemperatureHandler(this);
        cyclingCadenceHandler = new CyclingCadenceHandler(this);
        cyclingDistanceSpeedHandler = new CyclingDistanceSpeedHandler(this);
        cyclingPowerHandler = new CyclingPowerHandler(this);
        runningSpeedCadenceDistanceHandler = new RunningSpeedCadenceDistanceHandler(this);

        onSharedPreferenceChanged(null, null);
    }

    public void stop() {
        wakeLock = SystemUtils.releaseWakeLock(wakeLock);

        heartRateHandler.disconnect();
        heartRateHandler = null;

        temperatureHandler.disconnect();
        temperatureHandler = null;

        cyclingCadenceHandler.disconnect();
        cyclingCadenceHandler = null;

        cyclingDistanceSpeedHandler.disconnect();
        cyclingDistanceSpeedHandler = null;

        cyclingPowerHandler.disconnect();
        cyclingPowerHandler = null;

        runningSpeedCadenceDistanceHandler.disconnect();
        runningSpeedCadenceDistanceHandler = null;

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

        heartRateHandler.resetAggregated();
        temperatureHandler.resetAggregated();
        cyclingCadenceHandler.resetAggregated();
        cyclingDistanceSpeedHandler.resetAggregated();
        cyclingPowerHandler.resetAggregated();
        runningSpeedCadenceDistanceHandler.resetAggregated();
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

        if (PreferencesUtils.isKey(heartRateHandler.getSensorPreferenceKey(), key)) {
            heartRateHandler.connect();
        }

        if (PreferencesUtils.isKey(temperatureHandler.getSensorPreferenceKey(), key)) {
            temperatureHandler.connect();
        }

        if (PreferencesUtils.isKey(cyclingCadenceHandler.getSensorPreferenceKey(), key)) {
            cyclingCadenceHandler.connect();
        }

        if (PreferencesUtils.isKey(cyclingDistanceSpeedHandler.getSensorPreferenceKey(), key)) {
            cyclingDistanceSpeedHandler.connect();
        }

        if (PreferencesUtils.isKey(cyclingPowerHandler.getSensorPreferenceKey(), key)) {
            cyclingPowerHandler.connect();
        }

        if (PreferencesUtils.isKey(runningSpeedCadenceDistanceHandler.getSensorPreferenceKey(), key)) {
            runningSpeedCadenceDistanceHandler.connect();
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

    private SensorData<Cadence> getCadence(Instant now) {
        {
            SensorData<Cadence> value = cyclingCadenceHandler.getSensorData(now);
            if (value != null) {
                return cyclingCadenceHandler.getSensorData(now);
            }
        }

        SensorData<AggregatorRunning.Data> value = runningSpeedCadenceDistanceHandler.getSensorData(now);
        if (value != null && value.data().cadence() != null) {
            return new SensorData<>(value.data().cadence(), value.sensorNameOrAddress());
        }

        return null;
    }

    private SensorData<Distance> getDistance(Instant now) {
        {
            SensorData<AggregatorCyclingDistanceSpeed.Data> value = cyclingDistanceSpeedHandler.getSensorData(now);
            if (value != null) {
                return new SensorData<>(value.data().distanceOverall(), value.sensorNameOrAddress());
            }
        }

        SensorData<AggregatorRunning.Data> value = runningSpeedCadenceDistanceHandler.getSensorData(now);
        if (value != null) {
            return new SensorData<>(value.data().distance(), value.sensorNameOrAddress());
        }

        return null;
    }

    //TOOD simplify?
    private SensorData<Speed> getSpeed(Instant now) {
        {
            SensorData<AggregatorCyclingDistanceSpeed.Data> value = cyclingDistanceSpeedHandler.getSensorData(now);
            if (value != null && value.data() != null && value.data().speed() != null) {
                return new SensorData<>(value.data().speed(), value.sensorNameOrAddress());
            }
        }

        SensorData<AggregatorRunning.Data> value = runningSpeedCadenceDistanceHandler.getSensorData(now);
        if (value != null && value.data() != null && value.data().speed() != null) {
            return new SensorData<>(value.data().speed(), value.sensorNameOrAddress());
        }

        return null;
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
                getSpeed(now),
                getDistance(now),
                heartRateHandler.getSensorData(now),
                temperatureHandler.getSensorData(now),
                getCadence(now),
                cyclingPowerHandler.getSensorData(now),
                altitudeChangeHandler.getSensorData(now)
        );
    }

    private boolean isStarted() {
        return wakeLock != null;
    }

    @VisibleForTesting
    public void setAggregator(AggregatorHeartRate data) {
        heartRateHandler.setAggregator(data);
    }

    @VisibleForTesting
    public void setAggregator(@NonNull AggregatorTemperature data) {
        temperatureHandler.setAggregator(data);
    }

    @VisibleForTesting
    public void setAggregator(@NonNull AggregatorCyclingCadence data) {
        cyclingCadenceHandler.setAggregator(data);
    }

    @VisibleForTesting
    public void setAggregator(@NonNull AggregatorCyclingDistanceSpeed data) {
        cyclingDistanceSpeedHandler.setAggregator(data);
    }

    @VisibleForTesting
    public void setAggregator(@NonNull AggregatorCyclingPower data) {
        cyclingPowerHandler.setAggregator(data);
    }

    @VisibleForTesting
    public void setAggregator(@NonNull AggregatorRunning data) {
        runningSpeedCadenceDistanceHandler.setAggregator(data);
    }
}
