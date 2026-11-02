package de.dennisguse.opentracks.data.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.time.Duration;
import java.time.Instant;

public record Statistics(
        @NonNull
        Instant startTime,
        @NonNull
        Instant stopTime,
        @NonNull
        Duration totalDuration,
        @NonNull
        Duration movingDuration, // Based on when we believe the user is traveling

        @NonNull
        Distance totalDistance,

        @NonNull
        Speed maxSpeed,

        @Nullable
        AltitudeExtremities altitudeExtremities,

        @Nullable
        AltitudeGainLoss altitudeGainLoss,

        //NOTE: The following values are not persisted.
        @Nullable
        HeartRate avgHeartRate,
        @Nullable
        Power avgPower
) {

    //TODO: Should not be necessary; refactor and remove.
    @Deprecated
    public static final Statistics DEFAULT =
        new Statistics(
                Instant.EPOCH,
                Instant.EPOCH,
                Duration.ZERO,
                Duration.ZERO,
                Distance.ZERO,
                Speed.ZERO,
                null,
                null,
                null,
                null
        );

    public Duration getStoppedTime() {
        return totalDuration.minus(movingDuration);
    }

    public Speed getAverageSpeed() {
        return Speed.of(totalDistance, totalDuration);
    }

    public Speed getAverageMovingSpeed() {
        return Speed.of(totalDistance, movingDuration);
    }

    /**
     * Combines these statistics with those from another object.
     * This assumes that the time periods covered by each do not intersect.
     */
    //TODO Should be refactored to append only
    @NonNull
    public Statistics merge(Statistics other) {
        if (other == null) return this;

        HeartRate newAvgHeartRate = avgHeartRate;
        if (avgHeartRate == null) {
            newAvgHeartRate = other.avgHeartRate;
        } else {
            if (other.avgHeartRate != null) {
                // Using total time as weights for the averaging.
                // Important to do this before total time is updated
                newAvgHeartRate = HeartRate.of(
                        (totalDuration.getSeconds() * avgHeartRate.getBPM() + other.totalDuration.getSeconds() * other.avgHeartRate.getBPM())
                                / (totalDuration.getSeconds() + other.totalDuration.getSeconds())
                );
            }
        }

        Power newAvgPower = avgPower;
        if (avgPower == null) {
            newAvgPower = other.avgPower;
        } else {
            if (other.avgPower != null) {
                // Using total time as weights for the averaging.
                // Important to do this before total time is updated
                newAvgPower = Power.of(
                        (totalDuration.getSeconds() * avgPower.getW() + other.totalDuration.getSeconds() * other.avgPower.getW())
                                / (totalDuration.getSeconds() + other.totalDuration.getSeconds())
                );
            }
        }

        AltitudeExtremities newAltitudeExtremities;
        if (altitudeExtremities != null && other.altitudeExtremities != null) {
            newAltitudeExtremities = new AltitudeExtremities(
                    Math.min(altitudeExtremities.min_m(), other.altitudeExtremities.min_m()),
                    Math.max(altitudeExtremities.max_m(), other.altitudeExtremities.max_m())
            );
        } else {
            newAltitudeExtremities = altitudeExtremities != null ? altitudeExtremities : other.altitudeExtremities;
        }

        AltitudeGainLoss newAltitudeGainLoss;
        if (altitudeGainLoss != null && other.altitudeGainLoss != null) {
            newAltitudeGainLoss = new AltitudeGainLoss(
                    altitudeGainLoss.gain_m() + other.altitudeGainLoss.gain_m(),
                    altitudeGainLoss.loss_m() + other.altitudeGainLoss.loss_m()
            );
        } else {
            newAltitudeGainLoss = altitudeGainLoss != null ? altitudeGainLoss : other.altitudeGainLoss;
        }

        return new Statistics(
                startTime.isBefore(other.startTime) ? startTime : other.startTime,
                stopTime.isAfter(other.stopTime) ? stopTime : other.stopTime,
                totalDuration.plus(other.totalDuration),
                movingDuration.plus(other.movingDuration),
                totalDistance.plus(other.totalDistance),
                Speed.max(maxSpeed, other.maxSpeed),
                newAltitudeExtremities,
                newAltitudeGainLoss,
                newAvgHeartRate,
                newAvgPower
                );
    }
}
