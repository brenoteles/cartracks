package de.dennisguse.opentracks.sensors.sensorData;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.Duration;
import java.time.Instant;

public abstract class Aggregator<InputType, OutputType> {

    private static final Duration MAX_SENSOR_DATE_SET_AGE = Duration.ofSeconds(5);

    @Nullable
    protected Raw<InputType> previous;

    //TODO Make private
    protected OutputType output;

    private final String sensorAddress;
    private final String sensorName;

    Aggregator(String sensorAddress, String sensorName) {
        this.sensorAddress = sensorAddress;
        this.sensorName = sensorName;
    }

    private String getSensorNameOrAddress() {
        return sensorName != null ? sensorName : sensorAddress;
    }

    public final void add(Instant now, InputType current) {
        Raw<InputType> next = new Raw<>(now, current);
        computeValue(next);
        previous = next;
    }

    protected abstract void computeValue(@NonNull Raw<InputType> current);

    /**
     * @return did we process data from a sensor.
     * NOTE: for some sensors this may require more than one measurement.
     */
    public boolean hasReceivedData() {
        return output != null;
    }

    @Nullable
    protected OutputType getAggregatedValue(Instant now) {
        if (isOutdated(now)) {
            resetOutdated();
        }

        return output;
    }

    @NonNull
    public SensorData<OutputType> getAggregatedValueWithSensorName(Instant now) {
        return new SensorData<>(getAggregatedValue(now), getSensorNameOrAddress());
    }

    /**
     * Reset short-term (i.e., non-aggregated) values that were directly derived from sensor data.
     */
    protected void resetOutdated() {
        output = null;
    }

    /**
     * Reset long-term (i.e., aggregated) values (more than derived from previous SensorData) like overall distance.
     */
    public void resetAggregated() {
    }

    /**
     * Is the data recent considering the current time.
     */
    private boolean isOutdated(Instant now) {
        if (previous == null) {
            return true;
        }

        return now
                .isAfter(
                        previous.time()
                                .plus(getMaxSensorAge())
                );
    }

    protected Duration getMaxSensorAge() {
        return MAX_SENSOR_DATE_SET_AGE;
    }

    @NonNull
    @Override
    public String toString() {
        return "sensorAddress=" + sensorAddress + " data=" + output;
    }

    protected record Raw<T>(
            @NonNull Instant time,
            @NonNull T value
    ) {
    }
}
