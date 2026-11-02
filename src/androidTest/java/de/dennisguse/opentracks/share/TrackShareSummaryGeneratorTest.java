/*
 * Copyright 2012 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package de.dennisguse.opentracks.share;

import static org.junit.Assert.assertEquals;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Locale;

import de.dennisguse.opentracks.LocaleRule;
import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.data.models.AltitudeExtremities;
import de.dennisguse.opentracks.data.models.AltitudeGainLoss;
import de.dennisguse.opentracks.data.models.Distance;
import de.dennisguse.opentracks.data.models.Speed;
import de.dennisguse.opentracks.data.models.Statistics;
import de.dennisguse.opentracks.data.models.Track;
import de.dennisguse.opentracks.util.StringUtils;

/**
 * Tests for {@link TrackShareSummaryGenerator}.
 *
 * @author Jimmy Shih
 */
@RunWith(AndroidJUnit4.class)
public class TrackShareSummaryGeneratorTest {

    @Rule
    public final LocaleRule mLocaleRule = new LocaleRule(Locale.ENGLISH);

    private static final Instant START_TIME = Instant.ofEpochMilli(1288721514000L);
    private TrackShareSummaryGenerator descriptionGenerator;

    @Before
    public void setUp() {
        descriptionGenerator = new TrackShareSummaryGenerator(ApplicationProvider.getApplicationContext());
    }

    @Test
    public void testGenerateTrackDescription() {
        Track track = new Track(
                null,
                null,
                "",
                "",
                "hiking",
                null,
                ZoneOffset.UTC,
                new Statistics(
                        START_TIME,
                        START_TIME,
                        Duration.ofSeconds(600),
                        Duration.ofSeconds(300),
                        Distance.of(20000),
                        Speed.of(100),
                        new AltitudeExtremities(-500, 550),
                        new AltitudeGainLoss(6000, 6000),
                        null,
                        null
                ));

        String expected = //"Created by"
                "OpenTracks (Debug)\n\n"
                        + "Name: -\n"
                        + "Activity type: hiking\n"
                        + "Description: -\n"
                        + "Total distance: 20.00 km (12.4 mi)\n"
                        + "Total time: 10:00\n"
                        + "Moving time: 05:00\n"
                        + "Average speed: 120.00 km/h (74.6 mi/h)\n"
                        + "Average moving speed: 240.00 km/h (149.1 mi/h)\n"
                        + "Max speed: 360.00 km/h (223.7 mi/h)\n"
                        + "Average pace: 0:30 min/km (0:48 min/mi)\n"
                        + "Average moving pace: 0:15 min/km (0:24 min/mi)\n"
                        + "Fastest pace: 0:10 min/km (0:16 min/mi)\n"
                        + "Max elevation: 550 m (1804 ft)\n"
                        + "Min elevation: -500 m (-1640 ft)\n"
                        + "Elevation gain: 6000 m (19685 ft)\n"
                        + "Elevation loss: 6000 m (19685 ft)\n"
                        + "Recorded: " + StringUtils.formatDateTimeWithOffset(OffsetDateTime.ofInstant(START_TIME, ZoneId.systemDefault())) + "\n";

        assertEquals(expected, descriptionGenerator.generateTrackDescription(track));
    }

    @Test
    public void testWriteDistance() {
        StringBuilder builder = new StringBuilder();
        descriptionGenerator.writeDistance(Distance.of(1100), builder, R.string.description_total_distance);
        assertEquals("Total distance: 1.10 km (0.7 mi)\n", builder.toString());
    }

    @Test
    public void testWriteTime() {
        StringBuilder builder = new StringBuilder();
        descriptionGenerator.writeTime(Duration.ofMillis(1000), builder, R.string.description_total_time);
        assertEquals("Total time: 00:01\n", builder.toString());
    }

    @Test
    public void testWriteSpeed() {
        StringBuilder builder = new StringBuilder();
        descriptionGenerator.writeSpeed(Speed.of(1.1), builder, R.string.description_average_speed);
        assertEquals("Average speed: 3.96 km/h (2.5 mi/h)\n", builder.toString());
    }

    @Test
    public void testWriteAltitude() {
        StringBuilder builder = new StringBuilder();
        descriptionGenerator.writeAltitude(4.2, builder, R.string.description_min_altitude);
        assertEquals("Min elevation: 4 m (14 ft)\n", builder.toString());
    }

    @Test
    public void testWritePace() {
        StringBuilder builder = new StringBuilder();
        descriptionGenerator.writePace(Speed.of(1.1), builder, R.string.description_average_pace_in_minute);
        assertEquals("Average pace: 15:09 min/km (24:23 min/mi)\n", builder.toString());
    }
}
