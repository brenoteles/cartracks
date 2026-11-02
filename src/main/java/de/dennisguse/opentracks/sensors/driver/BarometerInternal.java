package de.dennisguse.opentracks.sensors.driver;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import de.dennisguse.opentracks.data.models.AtmosphericPressure;

public class BarometerInternal implements Driver {

    private static final String TAG = BarometerInternal.class.getSimpleName();

    private static final Duration SAMPLING_PERIOD = Duration.ofSeconds(5);

    private final DriverObserver<AtmosphericPressure> observer;

    private android.hardware.SensorManager sensorService;

    private Instant lastEventTime = Instant.EPOCH;

    private final SensorEventListener sensorEventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (!isConnected()) {
                Log.w(TAG, "Not connected to sensor, cannot process data.");
                return;
            }

            Instant currentTime = Instant.now();

            if (lastEventTime.plus(SAMPLING_PERIOD).isAfter(currentTime)) {
                return;
            }

            lastEventTime = currentTime;
            observer.onDataReceived(AtmosphericPressure.ofHPA(event.values[0]));
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            Log.w(TAG, "Sensor accuracy changes are (currently) ignored.");
        }
    };

    public BarometerInternal(@NonNull DriverObserver<AtmosphericPressure> observer) {
        this.observer = observer;
    }

    @Override
    public void connect(Context context, Handler handler, String addressIgnored) {
        sensorService = (android.hardware.SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        Sensor pressureSensor = sensorService.getDefaultSensor(Sensor.TYPE_PRESSURE);
        int samplingPeriodUs = (int) TimeUnit.MICROSECONDS.convert(SAMPLING_PERIOD);

        if (pressureSensor != null && sensorService.registerListener(sensorEventListener, pressureSensor, samplingPeriodUs, handler)) {
            observer.onConnected(null, null);
            return;
        }

        Log.w(TAG, "No pressure sensor available.");
        observer.onDisconnected();
    }

    @Override
    public boolean isConnected() {
        return sensorService != null;
    }

    @Override
    public void disconnect() {
        if (!isConnected()) return;

        sensorService.unregisterListener(sensorEventListener);
        sensorService = null;
    }
}
