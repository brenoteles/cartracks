package de.dennisguse.opentracks.sensors.sensorData;

import android.util.Log;

import androidx.annotation.NonNull;

import java.time.Duration;

import de.dennisguse.opentracks.data.models.Cadence;
import de.dennisguse.opentracks.sensors.UintUtils;
import de.dennisguse.opentracks.sensors.driver.CyclingCadenceBluetooth;

public class AggregatorCyclingCadence extends Aggregator<CyclingCadenceBluetooth.CyclingCadenceMeasurement, Cadence> {

    private final String TAG = AggregatorCyclingCadence.class.getSimpleName();

    public AggregatorCyclingCadence(String sensorAddress, String sensorName) {
        super(sensorAddress, sensorName);
    }

    @Override
    protected void computeValue(@NonNull Raw<CyclingCadenceBluetooth.CyclingCadenceMeasurement> current) {
        if (current.value() instanceof CyclingCadenceBluetooth.DirectCadenceData directCadenceData) {
            output = directCadenceData.cadence();
            return;
        }

        computeRawCadence((CyclingCadenceBluetooth.CrankData) current.value());
    }

    private void computeRawCadence(@NonNull CyclingCadenceBluetooth.CrankData currentRaw) {
        CyclingCadenceBluetooth.CrankData previousRaw = getPreviousRaw();
        if (previousRaw == null) return;

        float timeDiff_ms = UintUtils.diff(currentRaw.crankRevolutionsTime(), previousRaw.crankRevolutionsTime(), UintUtils.UINT16_MAX) / 1024f * 1000;
        Duration timeDiff = Duration.ofMillis((long) timeDiff_ms);

        if (timeDiff.isZero()) {
            return;
        }
        if (timeDiff.isNegative()) {
            Log.e(TAG, "Timestamps difference is invalid: cannot compute cadence.");
            output = null;
            return;
        }

        // TODO We have to treat with overflow according to the documentation: read https://codeberg.org/OpenTracksApp/OpenTracks/pulls/953#issuecomment-6466930
        if (currentRaw.crankRevolutionsCount() < previousRaw.crankRevolutionsCount()) {
            Log.e(TAG, "Crank revolutions count difference is invalid: cannot compute cadence.");
            output = null;
            return;
        }

        long crankDiff = UintUtils.diff(currentRaw.crankRevolutionsCount(), previousRaw.crankRevolutionsCount(), UintUtils.UINT32_MAX);
        output = Cadence.of(crankDiff, timeDiff);
    }

    private CyclingCadenceBluetooth.CrankData getPreviousRaw() {
        if (previous == null) {
            return null;
        }

        return previous.value() instanceof CyclingCadenceBluetooth.CrankData raw ? raw : null;
    }
}
