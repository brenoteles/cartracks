package de.dennisguse.opentracks.sensors;

import androidx.annotation.NonNull;

import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.sensors.driver.BluetoothDriver;
import de.dennisguse.opentracks.sensors.driver.CyclingDistanceSpeedBluetooth;
import de.dennisguse.opentracks.sensors.driver.Driver;
import de.dennisguse.opentracks.sensors.sensorData.AggregatorCyclingDistanceSpeed;
import de.dennisguse.opentracks.settings.PreferencesUtils;

public class CyclingDistanceSpeedHandler extends SensorHandler<CyclingDistanceSpeedBluetooth.WheelData, AggregatorCyclingDistanceSpeed.Data> {

    protected CyclingDistanceSpeedHandler(SensorManager sensorManager) {
        super(sensorManager);
    }

    @NonNull
    @Override
    protected Driver createDriverBluetooth(String address) {
        return new BluetoothDriver<>(
                BluetoothUtils.getAdapter(getContext()),
                this,
                new CyclingDistanceSpeedBluetooth()
        );
    }

    @NonNull
    @Override
    protected AggregatorCyclingDistanceSpeed createAggregator(String sensorAddress, String sensorName) {
        return new AggregatorCyclingDistanceSpeed(sensorAddress, sensorName);
    }

    @Override
    protected int getSensorPreferenceKey() {
        return R.string.settings_sensor_bluetooth_cycling_speed_key;
    }

    @Override
    public void onDataReceived(CyclingDistanceSpeedBluetooth.WheelData value) {
        //TODO Fetch once and then listen for changes.
        ((AggregatorCyclingDistanceSpeed) aggregator).setWheelCircumference(PreferencesUtils.getWheelCircumference());

        super.onDataReceived(value);
    }
}
