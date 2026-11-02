package de.dennisguse.opentracks.sensors.sensorData;

import android.location.Location;

import androidx.annotation.NonNull;

import de.dennisguse.opentracks.data.models.Position;

public class AggregatorGPS extends Aggregator<Location, Position> {

    public AggregatorGPS(String sensorAddress, String sensorName) {
        super(sensorAddress, sensorName);
    }

    @Override
    protected void computeValue(@NonNull Raw<Location> current) {
        output = Position.of(current.value(), current.time());
    }

    @Override
    public void resetAggregated() {
        /*
         * GPS data is not an aggregated value, but for now we want to ensure to only save the data once.
         * The data is too large to save it more often than needed (i.e., duplicated values).
         */
        resetOutdated();
    }
}
