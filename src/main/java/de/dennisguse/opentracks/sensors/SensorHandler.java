package de.dennisguse.opentracks.sensors;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.time.Instant;

import de.dennisguse.opentracks.sensors.driver.Driver;
import de.dennisguse.opentracks.sensors.driver.DriverObserver;
import de.dennisguse.opentracks.sensors.driver.SensorType;
import de.dennisguse.opentracks.sensors.sensorData.Aggregator;
import de.dennisguse.opentracks.sensors.sensorData.SensorData;
import de.dennisguse.opentracks.settings.PreferencesUtils;


public abstract class SensorHandler<AggregatorInput, AggregatorOutput> implements DriverObserver<AggregatorInput> {

    private static final String TAG = SensorHandler.class.getSimpleName();

    @VisibleForTesting
    public Driver driver;

    protected final SensorManager sensorManager;

    protected Aggregator<AggregatorInput, AggregatorOutput> aggregator;

    protected SensorHandler(SensorManager sensorManager) {
        this.sensorManager = sensorManager;
    }

    void connect() {
        disconnect();

        String address = PreferencesUtils.getString(getSensorPreferenceKey(), getSensorPreferenceDefaultValue());

        switch (PreferencesUtils.getSensorType(address)) {
            case NONE -> {
                onDisconnected();
                return;
            }
            case INTERNAL -> {
                driver = createDriverInternal();
            }
            case REMOTE -> {
                driver = createDriverBluetooth(address);
            }
            default -> throw new UnsupportedOperationException();
        }

        driver.connect(sensorManager.getContext(), sensorManager.getHandler(), address);
    }

    void disconnect() {
        if (driver == null) return;

        driver.disconnect();
        driver = null;
    }

    @NonNull
    protected Driver createDriverInternal() {
        throw new RuntimeException("Not implemented");
    }

    @NonNull
    protected Driver createDriverBluetooth(String address) {
        throw new RuntimeException("Not implemented");
    }

    @NonNull
    protected abstract Aggregator<AggregatorInput, AggregatorOutput> createAggregator(String sensorAddress, String sensorName);

    protected abstract int getSensorPreferenceKey();

    protected String getSensorPreferenceDefaultValue() {
        return SensorType.NONE.getPreferenceValue();
    }

    protected Context getContext() {
        return sensorManager.getContext();
    }

    void resetAggregated() {
        if (aggregator != null) {
            aggregator.resetAggregated();
        }
    }

    @Nullable
    SensorData<AggregatorOutput> getSensorData(Instant now) {
        if (aggregator == null) return null;
        if (!aggregator.hasReceivedData()) return null;

        return aggregator.getAggregatedValueWithSensorName(now);
    }

    //TODO REMOVE
    @VisibleForTesting
    public void setAggregator(Aggregator<AggregatorInput, AggregatorOutput> aggregator) {
        this.aggregator = aggregator;
    }

    public void onConnected(String sensorAddress, String sensorName) {
        SensorHandler.this.aggregator = createAggregator(sensorAddress, sensorName);
    }

    @Override
    public void onConnectionLost() {
        if (SensorHandler.this.aggregator == null) {
            Log.d(TAG, "Connection lost, but where not connected.");
            return;
        }

        SensorHandler.this.aggregator.resetAggregated();
    }

    @Override
    public void onDataReceived(AggregatorInput value) {
        if (aggregator == null) {
            throw new RuntimeException("Received data while being disconnected.");
        }
        SensorHandler.this.aggregator.add(sensorManager.getNow(), value);
        sensorManager.onChange();
    }

    @Override
    public void onDisconnected() {
        SensorHandler.this.aggregator = null;
    }
}
