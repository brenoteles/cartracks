package de.dennisguse.opentracks.data.statistics;

import de.dennisguse.opentracks.data.models.Cadence;
import de.dennisguse.opentracks.data.models.HeartRate;
import de.dennisguse.opentracks.data.models.Power;

public record SensorStatistics(
         HeartRate maxHeartRate,
         HeartRate avgHeartRate,
         Cadence maxCadence,
         Cadence avgCadence,
         Power maxPower,
         Power avgPower
) {

    public boolean hasHeartRate() {
        return avgHeartRate != null && maxHeartRate != null;
    }

    public boolean hasCadence() {
        return avgCadence != null && maxCadence != null;
    }

    public boolean hasPower() {
        return avgPower != null;
    }
}
