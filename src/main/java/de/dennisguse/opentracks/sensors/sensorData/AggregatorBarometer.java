package de.dennisguse.opentracks.sensors.sensorData;

import androidx.annotation.NonNull;

import de.dennisguse.opentracks.data.models.AltitudeGainLoss;
import de.dennisguse.opentracks.data.models.AtmosphericPressure;
import de.dennisguse.opentracks.sensors.PressureSensorUtils;

public class AggregatorBarometer extends Aggregator<AtmosphericPressure, AltitudeGainLoss> {

    private AtmosphericPressure lastAcceptedSensorValue;

    public AggregatorBarometer(String sensorAddress, String sensorName) {
        super(sensorAddress, sensorName);
    }

    @Override
    protected void computeValue(@NonNull Raw<AtmosphericPressure> current) {
        if (previous == null) {
            lastAcceptedSensorValue = current.value();
            output = new AltitudeGainLoss(0f, 0f);
            return;
        }

        PressureSensorUtils.AltitudeChange altitudeChange = PressureSensorUtils.computeChangesWithSmoothing_m(lastAcceptedSensorValue, previous.value(), current.value());
        if (altitudeChange != null) {
            if (output == null) {
                output = new AltitudeGainLoss(0f, 0f);
            }
            output = new AltitudeGainLoss(output.gain_m() + altitudeChange.getAltitudeGain_m(), output.loss_m() + altitudeChange.getAltitudeLoss_m());

            lastAcceptedSensorValue = altitudeChange.currentSensorValue();
        }
    }

    @Override
    protected void resetOutdated() {
    }

    @Override
    public void resetAggregated() {
        output = null;
    }
}
