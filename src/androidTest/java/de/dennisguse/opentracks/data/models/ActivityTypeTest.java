package de.dennisguse.opentracks.data.models;

import static org.junit.Assert.assertEquals;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;

@RunWith(AndroidJUnit4.class)
public class ActivityTypeTest {

    @Test
    public void findBy_id() {
        assertEquals(ActivityType.TRAIL_RUNNING, ActivityType.findBy("trail running"));
    }

    @Test
    public void findBy_idOfEveryActivityType() {
        for (ActivityType activityType : ActivityType.values()) {
            assertEquals(activityType, ActivityType.findBy(activityType.getId()));
        }
    }

    @Test
    public void findBy_synonymsOfEveryActivityType() {
        for (ActivityType activityType : ActivityType.values()) {
            for (String synonym : activityType.synonyms) {
                assertEquals(activityType, ActivityType.findBy(synonym));
                assertEquals(activityType, ActivityType.findBy(synonym.toUpperCase(Locale.ROOT).replace(' ', '_')));
            }
        }
    }

    @Test
    public void findBy_ignoresCase() {
        assertEquals(ActivityType.RUNNING, ActivityType.findBy("RUNNING"));
        assertEquals(ActivityType.ATV, ActivityType.findBy("atv"));
    }

    @Test
    public void findBy_ignoresSeparators() {
        assertEquals(ActivityType.TRAIL_RUNNING, ActivityType.findBy("TRAIL_RUNNING"));
        assertEquals(ActivityType.MOUNTAIN_BIKING, ActivityType.findBy("mountain-biking"));
        assertEquals(ActivityType.CROSS_COUNTRY_SKIING, ActivityType.findBy("cross_country_skiing"));
    }

    @Test
    public void findBy_ignoresSurroundingAndRepeatedWhitespace() {
        assertEquals(ActivityType.MOUNTAIN_BIKING, ActivityType.findBy("  Mountain   Biking "));
    }

    @Test
    public void findBy_synonym() {
        assertEquals(ActivityType.RUNNING, ActivityType.findBy("treadmill"));
        assertEquals(ActivityType.TRAIL_RUNNING, ActivityType.findBy("TRAIL_RUN"));
        assertEquals(ActivityType.MOUNTAIN_BIKING, ActivityType.findBy("MOUNTAIN_BIKE"));
        assertEquals(ActivityType.SWIMMING_OPEN, ActivityType.findBy("SWIMMING_OPENWATER"));
        assertEquals(ActivityType.BIKING, ActivityType.findBy("bike"));
    }

    @Test
    public void findBy_unknown() {
        assertEquals(ActivityType.UNKNOWN, ActivityType.findBy("hovercraft"));
    }

    @Test
    public void findBy_null() {
        assertEquals(ActivityType.UNKNOWN, ActivityType.findBy(null));
    }

    @Test
    public void findBy_blank() {
        assertEquals(ActivityType.UNKNOWN, ActivityType.findBy("   "));
    }
}
