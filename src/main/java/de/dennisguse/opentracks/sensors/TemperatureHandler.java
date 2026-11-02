package de.dennisguse.opentracks.sensors;

import androidx.annotation.NonNull;

import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.data.models.Temperature;
import de.dennisguse.opentracks.sensors.driver.BluetoothDriver;
import de.dennisguse.opentracks.sensors.driver.Driver;
import de.dennisguse.opentracks.sensors.driver.TemperatureBluetooth;
import de.dennisguse.opentracks.sensors.sensorData.AggregatorTemperature;

public class TemperatureHandler extends SensorHandler<Temperature, Temperature> {

    protected TemperatureHandler(SensorManager sensorManager) {
        super(sensorManager);
    }

    @NonNull
    @Override
    protected Driver createDriverBluetooth(String address) {
        return new BluetoothDriver<>(
                BluetoothUtils.getAdapter(getContext()),
                this,
                new TemperatureBluetooth()
        );
    }

    @NonNull
    @Override
    protected AggregatorTemperature createAggregator(String sensorAddress, String sensorName) {
        return new AggregatorTemperature(sensorAddress, sensorName);
    }

    @Override
    protected int getSensorPreferenceKey() {
        return R.string.settings_sensor_bluetooth_temperature_key;
    }
}
