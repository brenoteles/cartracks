package de.dennisguse.opentracks.sensors.sensorData;

import androidx.annotation.NonNull;

import de.dennisguse.opentracks.data.models.Power;
import de.dennisguse.opentracks.sensors.driver.CyclingPowerBluetooth;

public class AggregatorCyclingPower extends Aggregator<CyclingPowerBluetooth.Data, Power> {

    public AggregatorCyclingPower(String name, String address) {
        super(name, address);
    }

    @Override
    public void computeValue(@NonNull Raw<CyclingPowerBluetooth.Data> current) {
        this.output = current.value().power();
    }
}
