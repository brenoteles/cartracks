package de.dennisguse.opentracks.sensors;

import androidx.annotation.NonNull;

import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.data.models.HeartRate;
import de.dennisguse.opentracks.sensors.driver.BluetoothDriver;
import de.dennisguse.opentracks.sensors.driver.Driver;
import de.dennisguse.opentracks.sensors.driver.HeartRateBluetooth;
import de.dennisguse.opentracks.sensors.sensorData.AggregatorHeartRate;

public class HeartRateHandler extends SensorHandler<HeartRate, HeartRate> {

    protected HeartRateHandler(SensorManager sensorManager) {
        super(sensorManager);
    }

    @NonNull
    @Override
    protected Driver createDriverBluetooth(String address) {
        return new BluetoothDriver<>(
                BluetoothUtils.getAdapter(getContext()),
                this,
                new HeartRateBluetooth()
        );
    }

    @NonNull
    @Override
    protected AggregatorHeartRate createAggregator(String sensorAddress, String sensorName) {
        return new AggregatorHeartRate(sensorAddress, sensorName);
    }

    @Override
    protected int getSensorPreferenceKey() {
        return R.string.settings_sensor_bluetooth_heart_rate_key;
    }
}
