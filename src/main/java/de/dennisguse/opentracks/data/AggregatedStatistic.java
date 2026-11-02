package de.dennisguse.opentracks.data;

import androidx.annotation.NonNull;

import java.time.Duration;

import de.dennisguse.opentracks.data.models.Distance;
import de.dennisguse.opentracks.data.models.Speed;

public record AggregatedStatistic(
        @NonNull
        String activityTypeLocalized,
        int countTracks,

        @NonNull
        Duration totalMovingTime,
        @NonNull
        Distance totalDistance,
        @NonNull
        Speed maxSpeed
) {

    public Speed getAverageMovingSpeed() {
        return Speed.of(totalDistance, totalMovingTime);
    }
}
