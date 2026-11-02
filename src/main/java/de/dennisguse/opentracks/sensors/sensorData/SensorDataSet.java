package de.dennisguse.opentracks.sensors.sensorData;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.dennisguse.opentracks.data.models.AltitudeGainLoss;
import de.dennisguse.opentracks.data.models.Cadence;
import de.dennisguse.opentracks.data.models.Distance;
import de.dennisguse.opentracks.data.models.HeartRate;
import de.dennisguse.opentracks.data.models.Position;
import de.dennisguse.opentracks.data.models.Power;
import de.dennisguse.opentracks.data.models.Speed;
import de.dennisguse.opentracks.data.models.Temperature;

public record SensorDataSet(
        @NonNull
        SensorData<Position> position,
        @Nullable
        SensorData<Speed> speed,
        @Nullable
        SensorData<Distance> distance,
        @Nullable
        SensorData<HeartRate> heartRate,
        @Nullable
        SensorData<Temperature> temperature,
        SensorData<Cadence> cadence,
        @Nullable
        SensorData<Power> power,
        @Nullable
        SensorData<AltitudeGainLoss> altitudeGainLoss
) {
}
