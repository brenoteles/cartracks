package de.dennisguse.opentracks.sensors.sensorData;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Instant;

import de.dennisguse.opentracks.data.models.Cadence;
import de.dennisguse.opentracks.data.models.Distance;
import de.dennisguse.opentracks.sensors.UintUtils;
import de.dennisguse.opentracks.sensors.driver.CyclingCadenceBluetooth;
import de.dennisguse.opentracks.sensors.driver.CyclingDistanceSpeedBluetooth;

@RunWith(AndroidJUnit4.class)
public class SensorDataCyclingTest {

    @Test
    public void compute_cadence_1() {
        AggregatorCyclingCadence current = new AggregatorCyclingCadence("", "");

        // when
        current.add(Instant.MIN, new CyclingCadenceBluetooth.CrankData(1, 1024));
        current.add(Instant.MIN, new CyclingCadenceBluetooth.CrankData(2, 2048));

        // then
        assertEquals(60, current.getAggregatedValue(Instant.MIN).getRPM(), 0.01);
    }

    @Test
    public void compute_cadence_2() {
        AggregatorCyclingCadence current = new AggregatorCyclingCadence("", "");

        // when
        current.add(Instant.MIN, new CyclingCadenceBluetooth.CrankData(1, 6184));
        current.add(Instant.MIN, new CyclingCadenceBluetooth.CrankData(2, 8016));

        // then
        assertEquals(33.53, current.getAggregatedValue(Instant.MIN).getRPM(), 0.01);
    }

    @Test
    public void compute_cadence_sameCount() {
        AggregatorCyclingCadence current = new AggregatorCyclingCadence("", "");

        // when
        current.add(Instant.MIN, new CyclingCadenceBluetooth.CrankData(1, 1024));
        current.add(Instant.MIN, new CyclingCadenceBluetooth.CrankData(1, 2048));

        // then
        assertEquals(Cadence.of(0), current.getAggregatedValue(Instant.MIN));
    }


    @Test
    public void compute_cadence_sameTime() {
        AggregatorCyclingCadence current = new AggregatorCyclingCadence("", "");

        // when
        current.add(Instant.MIN, new CyclingCadenceBluetooth.CrankData(1, 1024));
        current.add(Instant.MIN, new CyclingCadenceBluetooth.CrankData(2, 1024));

        // then
        assertFalse(current.hasReceivedData()); //TODO Cadence should be 0?
    }

    @Test
    public void compute_cadence_rollOverTime() {
        AggregatorCyclingCadence current = new AggregatorCyclingCadence("", "");

        // when
        current.add(Instant.MIN, new CyclingCadenceBluetooth.CrankData(1, UintUtils.UINT16_MAX - 1024));
        current.add(Instant.MIN, new CyclingCadenceBluetooth.CrankData(2, 0));

        // then
        assertEquals(60, current.getAggregatedValue(Instant.MIN).getRPM(), 0.01);
    }

    @Test
    @Deprecated
    public void compute_cadence_rollOverCount() {
        AggregatorCyclingCadence current = new AggregatorCyclingCadence("", "");

        // when
        current.add(Instant.MIN, new CyclingCadenceBluetooth.CrankData(UintUtils.UINT32_MAX - 1, 1024));
        current.add(Instant.MIN, new CyclingCadenceBluetooth.CrankData(0, 2048));

        // then
        // TODO See #953
//        assertEquals(60, current.getValue().getRPM(), 0.01);
        assertNull(current.getAggregatedValue(Instant.MIN));
    }

    @Test
    public void compute_cadence_directCadence_withoutPrevious() {
        AggregatorCyclingCadence current = new AggregatorCyclingCadence("", "");

        current.add(Instant.MIN, new CyclingCadenceBluetooth.DirectCadenceData(Cadence.of(92)));

        assertEquals(92, current.getAggregatedValue(Instant.MIN).getRPM(), 0.01);
    }

    @Test
    public void compute_cadence_directCadence_winsOverCrankData() {
        AggregatorCyclingCadence current = new AggregatorCyclingCadence("", "");

        current.add(Instant.MIN, new CyclingCadenceBluetooth.CrankData(1L, 1024));
        current.add(Instant.MIN, new CyclingCadenceBluetooth.DirectCadenceData(Cadence.of(92)));

        assertEquals(92, current.getAggregatedValue(Instant.MIN).getRPM(), 0.01);
    }

    @Test
    public void compute_speed() {
        AggregatorCyclingDistanceSpeed current = new AggregatorCyclingDistanceSpeed("", "");
        current.setWheelCircumference(Distance.ofMM(2150));

        // when
        current.add(Instant.MIN, new CyclingDistanceSpeedBluetooth.WheelData(1, 6184));
        current.add(Instant.MIN, new CyclingDistanceSpeedBluetooth.WheelData(2, 8016));

        // then
        assertEquals(2.15, current.getAggregatedValue(Instant.MIN).distance().toM(), 0.01);
        assertEquals(1.20, current.getAggregatedValue(Instant.MIN).speed().toMPS(), 0.01);
    }

    @Test
    @Deprecated
    public void compute_speed_rollOverCount() {
        AggregatorCyclingDistanceSpeed current = new AggregatorCyclingDistanceSpeed("", "");
        current.setWheelCircumference(Distance.ofMM(2000));

        // when
        current.add(Instant.MIN, new CyclingDistanceSpeedBluetooth.WheelData(UintUtils.UINT32_MAX - 1, 1024));
        current.add(Instant.MIN, new CyclingDistanceSpeedBluetooth.WheelData(0, 2048));

        // then
        // TODO See #953
//        assertEquals(2, current.getValue().getDistance().toM(), 0.01);
//        assertEquals(2, current.getValue().getSpeed().toMPS(), 0.01);
        assertNull(current.getAggregatedValue(Instant.MIN));
    }
}