package de.dennisguse.opentracks.sensors.sensorData;

import androidx.annotation.NonNull;

import de.dennisguse.opentracks.data.models.Cadence;
import de.dennisguse.opentracks.data.models.Distance;
import de.dennisguse.opentracks.data.models.Speed;
import de.dennisguse.opentracks.sensors.driver.RunningSpeedAndCadenceBluetooth;

/**
 * Provides cadence in rpm and speed in milliseconds from Bluetooth LE Running Speed and Cadence sensors.
 */
public final class AggregatorRunning extends Aggregator<RunningSpeedAndCadenceBluetooth.Data, AggregatorRunning.Data> {

    private static final String TAG = AggregatorRunning.class.getSimpleName();

    public AggregatorRunning(String sensorAddress, String sensorName) {
        super(sensorAddress, sensorName);
    }

    @Override
    public void computeValue(@NonNull Raw<RunningSpeedAndCadenceBluetooth.Data> current) {
        if (previous == null) {
            return;
        }

        Distance distance = null;
        if (previous.value().totalDistance() != null && current.value().totalDistance() != null) {
            distance = current.value().totalDistance().minus(previous.value().totalDistance());
            if (output != null) {
                distance = distance.plus(output.distance);
            }
        }

        output = new Data(current.value().speed(), current.value().cadence(), distance);
    }

    @Override
    protected void resetOutdated() {
        Distance distance = output != null ? output.distance : Distance.ZERO;
        output = new Data(Speed.ZERO, Cadence.of(0f), distance);
    }

    @Override
    public void resetAggregated() {
        if (output != null) {
            output = new Data(output.speed, output.cadence, Distance.ZERO);
        }
    }

    public record Data(
            Speed speed,
            Cadence cadence,
            @NonNull Distance distance) {
    }
}

