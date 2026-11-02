package de.dennisguse.opentracks.sensors;

import androidx.annotation.NonNull;

import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.data.models.Cadence;
import de.dennisguse.opentracks.sensors.driver.BluetoothDriver;
import de.dennisguse.opentracks.sensors.driver.CyclingCadenceBluetooth;
import de.dennisguse.opentracks.sensors.driver.Driver;
import de.dennisguse.opentracks.sensors.sensorData.AggregatorCyclingCadence;

public class CyclingCadenceHandler extends SensorHandler<CyclingCadenceBluetooth.CyclingCadenceMeasurement, Cadence> {

    protected CyclingCadenceHandler(SensorManager sensorManager) {
        super(sensorManager);
    }

    @NonNull
    @Override
    protected Driver createDriverBluetooth(String address) {
        return new BluetoothDriver<>(
                BluetoothUtils.getAdapter(getContext()),
                this,
                new CyclingCadenceBluetooth()
        );
    }

    @NonNull
    @Override
    protected AggregatorCyclingCadence createAggregator(String sensorAddress, String sensorName) {
        return new AggregatorCyclingCadence(sensorAddress, sensorName);
    }

    @Override
    protected int getSensorPreferenceKey() {
        return R.string.settings_sensor_bluetooth_cycling_cadence_key;
    }
}
