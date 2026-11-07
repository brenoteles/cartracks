package de.dennisguse.opentracks.sensors.sensorData;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.dennisguse.opentracks.data.models.AltitudeGainLoss;
import de.dennisguse.opentracks.data.models.Position;

/**
 * Only GPS and the internal barometer remain; the sports Bluetooth sensors (heart rate, cadence, power, ...) were removed.
 */
public record SensorDataSet(
        @NonNull
        SensorData<Position> position,
        @Nullable
        SensorData<AltitudeGainLoss> altitudeGainLoss
) {
}
