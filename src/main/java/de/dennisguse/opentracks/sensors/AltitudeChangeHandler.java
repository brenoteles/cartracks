package de.dennisguse.opentracks.sensors;

import androidx.annotation.NonNull;

import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.data.models.AltitudeGainLoss;
import de.dennisguse.opentracks.data.models.AtmosphericPressure;
import de.dennisguse.opentracks.sensors.driver.BarometerBluetooth;
import de.dennisguse.opentracks.sensors.driver.BarometerInternal;
import de.dennisguse.opentracks.sensors.driver.BluetoothDriver;
import de.dennisguse.opentracks.sensors.driver.Driver;
import de.dennisguse.opentracks.sensors.sensorData.AggregatorBarometer;

/**
 * Estimates the altitude gain and altitude loss using the device's pressure sensor (i.e., barometer).
 */
//TODO Rename class
public class AltitudeChangeHandler extends SensorHandler<AtmosphericPressure, AltitudeGainLoss> {

    public AltitudeChangeHandler(SensorManager sensorManager) {
        super(sensorManager);
    }

    @NonNull
    @Override
    protected Driver createDriverInternal() {
        return new BarometerInternal(this);
    }

    @NonNull
    @Override
    protected Driver createDriverBluetooth(String address) {
        return new BluetoothDriver<>(
                BluetoothUtils.getAdapter(getContext()),
                this,
                new BarometerBluetooth()
        );
    }

    @NonNull
    @Override
    public AggregatorBarometer createAggregator(String sensorAddress, String sensorName) {
        return new AggregatorBarometer(sensorAddress, sensorName);
    }

    @Override
    protected int getSensorPreferenceKey() {
        return R.string.settings_sensor_bluetooth_pressure_key;
    }
}
