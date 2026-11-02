package de.dennisguse.opentracks.sensors.sensorData;

import android.util.Log;

import androidx.annotation.NonNull;

import java.time.Duration;

import de.dennisguse.opentracks.data.models.Distance;
import de.dennisguse.opentracks.data.models.Speed;
import de.dennisguse.opentracks.sensors.UintUtils;
import de.dennisguse.opentracks.sensors.driver.CyclingDistanceSpeedBluetooth;

public class AggregatorCyclingDistanceSpeed extends Aggregator<CyclingDistanceSpeedBluetooth.WheelData, AggregatorCyclingDistanceSpeed.Data> {

    private final String TAG = AggregatorCyclingDistanceSpeed.class.getSimpleName();

    private Distance wheelCircumference;

    public AggregatorCyclingDistanceSpeed(String sensorAddress, String sensorName) {
        super(sensorAddress, sensorName);
    }

    @Override
    protected void computeValue(@NonNull Raw<CyclingDistanceSpeedBluetooth.WheelData> current) {
        if (previous == null) {
            return;
        }

        float timeDiff_ms = UintUtils.diff(current.value().wheelRevolutionsTime(), previous.value().wheelRevolutionsTime(), UintUtils.UINT16_MAX) / 1024f * 1000;
        Duration timeDiff = Duration.ofMillis((long) timeDiff_ms);

        if (timeDiff.isZero()) {
            return;
        }
        if (timeDiff.isNegative()) {
            Log.e(TAG, "Timestamps difference is invalid: cannot compute cadence.");
            output = null;
            return;
        }

        if (current.value().wheelRevolutionsCount() < previous.value().wheelRevolutionsCount()) {
            Log.e(TAG, "Wheel revolutions count difference is invalid: cannot compute speed.");
            output = null;
            return;
        }
        long wheelDiff = UintUtils.diff(current.value().wheelRevolutionsCount(), previous.value().wheelRevolutionsCount(), UintUtils.UINT32_MAX);

        Distance distance = wheelCircumference.multipliedBy(wheelDiff);
        Distance distanceOverall = distance;
        if (output != null) {
            distanceOverall = distance.plus(output.distanceOverall);
        }
        Speed speed_mps = Speed.of(distance, timeDiff);
        output = new Data(distance, distanceOverall, speed_mps);
    }

    @Override
    protected void resetOutdated() {
        Distance overallDistance = output != null ? output.distanceOverall : Distance.ZERO;
        output = new Data(Distance.ZERO, overallDistance, Speed.ZERO);
    }

    @Override
    public void resetAggregated() {
        if (output != null) {
            output = new Data(output.distance, Distance.ZERO, output.speed);
        }
    }

    public void setWheelCircumference(Distance wheelCircumference) {
        this.wheelCircumference = wheelCircumference;
    }

    public record Data(
            Distance distance, //Only used for debugging
            Distance distanceOverall,
            Speed speed) {
    }
}
