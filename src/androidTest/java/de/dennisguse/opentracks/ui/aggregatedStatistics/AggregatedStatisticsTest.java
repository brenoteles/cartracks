package de.dennisguse.opentracks.ui.aggregatedStatistics;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.data.AggregatedStatistic;
import de.dennisguse.opentracks.data.ContentProviderUtils;
import de.dennisguse.opentracks.data.TrackSelection;
import de.dennisguse.opentracks.data.models.ActivityType;
import de.dennisguse.opentracks.data.models.AltitudeExtremities;
import de.dennisguse.opentracks.data.models.AltitudeGainLoss;
import de.dennisguse.opentracks.data.models.Distance;
import de.dennisguse.opentracks.data.models.Speed;
import de.dennisguse.opentracks.data.models.Statistics;
import de.dennisguse.opentracks.data.models.Track;

@RunWith(JUnit4.class)
public class AggregatedStatisticsTest {

    private final Context context = ApplicationProvider.getApplicationContext();

    private static void createTrack(Context context, Distance totalDistance, Duration totalTime, String activityTypeLocalized) {
        Track track = new Track(
                null,
                UUID.randomUUID(), //TODO Use constant UUID
                "",
                "",
                activityTypeLocalized,
                ActivityType.findByLocalizedString(context, activityTypeLocalized),
                ZoneOffset.UTC,
                new Statistics(
                Instant.ofEpochMilli(1000),
                Instant.ofEpochMilli(1000).plus(totalTime),
                totalTime,
                totalTime,
                totalDistance,
                Speed.of(50),
                new AltitudeExtremities(1250, 1250),
                new AltitudeGainLoss(50, 50),
                null,
                null
        ));

        new ContentProviderUtils(context).insertTrack(track);
    }

    @Before
    public void setUp() {
        new ContentProviderUtils(context).deleteAllTracks(context);
    }

    @Test
    public void testAggregate_empty() {
        // when
        List<AggregatedStatistic> result = new ContentProviderUtils(context).getAggregatedStatisticsForTracks(new TrackSelection());

        // then
        assertEquals(List.of(), result);
    }

    @Test
    public void testAggregate_oneBikingTrack() {
        // given
        Distance totalDistance = Distance.ofKilometer(10);
        Duration totalTime = Duration.ofMinutes(40);
        String biking = context.getString(R.string.activity_type_biking);

        createTrack(context, totalDistance, totalTime, biking);

        // when
        List<AggregatedStatistic> result = new ContentProviderUtils(context).getAggregatedStatisticsForTracks(new TrackSelection());

        // then
        assertEquals(List.of(
                new AggregatedStatistic("biking", 1, Duration.ofMinutes(40), Distance.ofKilometer(10), Speed.of(50))
        ), result);
    }

    @Test
    public void testAggregate_twoBikingTracks() {
        // given
        Distance totalDistance = Distance.ofKilometer(10);
        Duration totalTime = Duration.ofMinutes(40);
        String biking = context.getString(R.string.activity_type_biking);

        createTrack(context, totalDistance, totalTime, biking);
        createTrack(context, totalDistance, totalTime, biking);

        // when
        List<AggregatedStatistic> result = new ContentProviderUtils(context).getAggregatedStatisticsForTracks(new TrackSelection());

        // then
        assertEquals(List.of(
                new AggregatedStatistic("biking", 2, Duration.ofMinutes(80), Distance.ofKilometer(20), Speed.of(50))
        ), result);
    }

    @Test
    public void testAggregate_severalTracksWithSeveralActivities() {
        // given
        Distance totalDistance = Distance.ofKilometer(10);
        Duration totalTime = Duration.ofMinutes(40);

        String biking = context.getString(R.string.activity_type_biking);
        String running = context.getString(R.string.activity_type_running);
        String walking = context.getString(R.string.activity_type_walking);
        String driving = context.getString(R.string.activity_type_driving);

        createTrack(context, totalDistance, totalTime, biking);
        createTrack(context, totalDistance, totalTime, running);
        createTrack(context, totalDistance, totalTime, walking);
        createTrack(context, totalDistance, totalTime, biking);
        createTrack(context, totalDistance, totalTime, running);
        createTrack(context, totalDistance, totalTime, walking);
        createTrack(context, totalDistance, totalTime, biking);
        createTrack(context, totalDistance, totalTime, biking);
        createTrack(context, totalDistance, totalTime, biking);
        createTrack(context, totalDistance, totalTime, driving);

        // when
        List<AggregatedStatistic> result = new ContentProviderUtils(context).getAggregatedStatisticsForTracks(new TrackSelection());

        // then
        assertEquals(List.of(
                new AggregatedStatistic("biking", 5, Duration.ofMinutes(200), Distance.ofKilometer(50), Speed.of(50)),
                new AggregatedStatistic("walking", 2, Duration.ofMinutes(80), Distance.ofKilometer(20), Speed.of(50)),
                new AggregatedStatistic("running", 2, Duration.ofMinutes(80), Distance.ofKilometer(20), Speed.of(50)),
                new AggregatedStatistic("driving", 1, Duration.ofMinutes(40), Distance.ofKilometer(10), Speed.of(50))
        ), result);
    }  
    
    @Test
    public void testAggregate_severalTracksWithSeveralActivities_TrackSelection() {
        // given
        Distance totalDistance = Distance.ofKilometer(10);
        Duration totalTime = Duration.ofMinutes(40);

        String biking = context.getString(R.string.activity_type_biking);
        String running = context.getString(R.string.activity_type_running);
        String walking = context.getString(R.string.activity_type_walking);
        String driving = context.getString(R.string.activity_type_driving);

        createTrack(context, totalDistance, totalTime, biking);
        createTrack(context, totalDistance, totalTime, running);
        createTrack(context, totalDistance, totalTime, walking);
        createTrack(context, totalDistance, totalTime, biking);
        createTrack(context, totalDistance, totalTime, running);
        createTrack(context, totalDistance, totalTime, walking);
        createTrack(context, totalDistance, totalTime, biking);
        createTrack(context, totalDistance, totalTime, biking);
        createTrack(context, totalDistance, totalTime, biking);
        createTrack(context, totalDistance, totalTime, driving);

        TrackSelection trackSelection = new TrackSelection();
        trackSelection.addActivityType("biking");
        trackSelection.addActivityType("driving");

        // when
        List<AggregatedStatistic> result = new ContentProviderUtils(context).getAggregatedStatisticsForTracks(trackSelection);

        // then
        assertEquals(List.of(
                new AggregatedStatistic("biking", 5, Duration.ofMinutes(200), Distance.ofKilometer(50), Speed.of(50)),
                new AggregatedStatistic("driving", 1, Duration.ofMinutes(40), Distance.ofKilometer(10), Speed.of(50))
        ), result);
    }
}
