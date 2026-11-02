package de.dennisguse.opentracks.sensors.sensorData;

import androidx.annotation.Nullable;

public record SensorData<T>(


        @Nullable
        T data,

        String sensorNameOrAddress
) {
}
