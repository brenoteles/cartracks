package de.dennisguse.opentracks.sensors;

import androidx.annotation.NonNull;

import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.data.models.Power;
import de.dennisguse.opentracks.sensors.driver.BluetoothDriver;
import de.dennisguse.opentracks.sensors.driver.CyclingPowerBluetooth;
import de.dennisguse.opentracks.sensors.driver.Driver;
import de.dennisguse.opentracks.sensors.sensorData.AggregatorCyclingPower;

public class CyclingPowerHandler extends SensorHandler<CyclingPowerBluetooth.Data, Power> {

    protected CyclingPowerHandler(SensorManager sensorManager) {
        super(sensorManager);
    }

    @NonNull
    @Override
    protected Driver createDriverBluetooth(String address) {
        return new BluetoothDriver<>(
                BluetoothUtils.getAdapter(getContext()),
                this,
                new CyclingPowerBluetooth()
        );
    }

    @NonNull
    @Override
    protected AggregatorCyclingPower createAggregator(String sensorAddress, String sensorName) {
        return new AggregatorCyclingPower(sensorAddress, sensorName);
    }

    @Override
    protected int getSensorPreferenceKey() {
        return R.string.settings_sensor_bluetooth_cycling_power_key;
    }
}
