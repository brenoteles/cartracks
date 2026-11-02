package de.dennisguse.opentracks.data.models;

import static org.junit.Assert.assertEquals;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.time.Instant;

@RunWith(AndroidJUnit4.class)
public class StatisticsTest {

    @Test
    public void testMerge() {
        // given
        Statistics subject = new Statistics(
                Instant.ofEpochMilli(1000),
                Instant.ofEpochMilli(2500),
                Duration.ofMillis(1500),
                Duration.ofMillis(700),
                Distance.of(750.0),
                Speed.of(60.0),
                new AltitudeExtremities(1200, 1250),
                new AltitudeGainLoss(50, 0),
                HeartRate.of(100),
                null
        );

        Statistics append = new Statistics(
                        Instant.ofEpochMilli(3000),
                        Instant.ofEpochMilli(4000),
                        Duration.ofMillis(1000),
                        Duration.ofMillis(600),
                        Distance.of(350.0),
                        Speed.of(30.0),
                        new AltitudeExtremities(2800.0, 3575.0),
                        new AltitudeGainLoss(850, 0),
                        HeartRate.of(200),
                        null
        );

        // when
        Statistics result = subject.merge(append);

        // then
        assertEquals(
                new Statistics(
                        Instant.ofEpochMilli(1000),
                        Instant.ofEpochMilli(4000),
                        Duration.ofMillis(2500),
                        Duration.ofMillis(1300),
                        Distance.of(1100),
                        Speed.of(60),
                        new AltitudeExtremities(1200, 3575),
                        new AltitudeGainLoss(900, 0),
                        HeartRate.of(150),
                        null
                )
                , result
        );
    }
}