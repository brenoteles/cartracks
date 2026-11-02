package de.dennisguse.opentracks.services.announcement;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.icu.text.MessageFormat;
import android.os.Build;
import android.util.Pair;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;

import de.dennisguse.opentracks.LocaleRule;
import de.dennisguse.opentracks.content.data.TestDataUtil;
import de.dennisguse.opentracks.data.models.ActivityType;
import de.dennisguse.opentracks.data.models.AltitudeGainLoss;
import de.dennisguse.opentracks.data.models.Distance;
import de.dennisguse.opentracks.data.models.HeartRate;
import de.dennisguse.opentracks.data.models.Speed;
import de.dennisguse.opentracks.data.models.Statistics;
import de.dennisguse.opentracks.data.models.Track;
import de.dennisguse.opentracks.data.models.TrackPoint;
import de.dennisguse.opentracks.data.statistics.SensorStatistics;
import de.dennisguse.opentracks.sensors.sensorData.SensorData;
import de.dennisguse.opentracks.sensors.sensorData.SensorDataSet;
import de.dennisguse.opentracks.settings.PreferencesUtils;
import de.dennisguse.opentracks.settings.UnitSystem;
import de.dennisguse.opentracks.ui.intervals.IntervalStatisticsUpdater;

//Due to DateTimeFormatter using NBSP, these tests only work in SDK34+
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@RunWith(AndroidJUnit4.class)
public class VoiceAnnouncementUtilsTest {

    @Rule
    public final LocaleRule mLocaleRule = new LocaleRule(Locale.ENGLISH);

    private final Context context = ApplicationProvider.getApplicationContext();

    @Before
    public void setUp() {
        PreferencesUtils.setVoiceAnnounceUnit(true);
        PreferencesUtils.setVoiceAnnounceHeartRateCurrent(false);
        PreferencesUtils.setVoiceAnnounceLapHeartRate(false);
        PreferencesUtils.setVoiceAnnounceAverageHeartRate(false);
        PreferencesUtils.setVoiceAnnounceTotalDistance(true);
        PreferencesUtils.setVoiceAnnounceMovingTime(true);
        PreferencesUtils.setVoiceAnnounceAverageSpeedPace(true);
        PreferencesUtils.setVoiceAnnounceLapSpeedPace(true);
    }

    @Test
    public void getAnnouncement_metric_speed() {
        Track track = new Track(
                null,
                null,
                "",
                "",
                "",
                ActivityType.AIRPLANE,
                ZoneOffset.UTC,
                new Statistics(
                        Instant.EPOCH,
                        Instant.EPOCH,
                        Duration.ofHours(2).plusMinutes(5).plusSeconds(10),
                        Duration.ofHours(1).plusMinutes(5).plusSeconds(10),
                        Distance.of(20000),
                        Speed.of(100),
                        null,
                        null,
                        null,
                        null
                ));

        SensorDataSet dataSet = new SensorDataSet(
                null,
                null,
                null,
                new SensorData<>(HeartRate.of(60), "unused"),
                null, null,
                null,
                null
        );

        // when
        String announcement = VoiceAnnouncementUtils.createStatistics(context, track, dataSet, UnitSystem.METRIC, true, null, null).toString();

        // then
        assertEquals("12:00 AM. Total distance 20.0 kilometers. 1 hour 5 minutes 10 seconds. Average moving speed 18.4 kilometers per hour.", announcement);
    }

    @Test
    public void getAnnouncement_metric_speed_rounding_check() {
        Track track = new Track(
                null,
                null,
                "",
                "",
                "",
                ActivityType.AIRPLANE,
                ZoneOffset.UTC,
                new Statistics(
                        Instant.EPOCH,
                        Instant.EPOCH,
                        Duration.ofHours(2).plusMinutes(5).plusSeconds(10),
                        Duration.ofHours(1).plusSeconds(1),
                        Distance.of(20000),
                        Speed.of(100),
                        null,
                        new AltitudeGainLoss(6000, 6000),
                        null,
                        null
                ));

        SensorDataSet dataSet = new SensorDataSet(
                null,
                null,
                null,
                new SensorData<>(HeartRate.of(60), "unused"),
                null, null,
                null,
                null
        );

        // when
        String announcement = VoiceAnnouncementUtils.createStatistics(context, track, dataSet, UnitSystem.METRIC, true, null, null).toString();

        // then
        assertEquals("12:00 AM. Total distance 20.0 kilometers. 1 hour 1 second. Average moving speed 20.0 kilometers per hour.", announcement);
    }

    @Test
    public void getAnnouncement_metric_distance_rounding_check() {
        Track track = new Track(
                null,
                null,
                "",
                "",
                "",
                ActivityType.AIRPLANE,
                ZoneOffset.UTC,
                new Statistics(
                        Instant.EPOCH,
                        Instant.EPOCH,
                        Duration.ofHours(2).plusMinutes(5).plusSeconds(10),
                        Duration.ofHours(1),
                        Distance.of(19999),
                        Speed.of(100),
                        null,
                        new AltitudeGainLoss(6000, 6000),
                        null,
                        null
                ));

        SensorDataSet dataSet = new SensorDataSet(
                null,
                null,
                null,
                new SensorData<>(HeartRate.of(60), "unused"),
                null, null,
                null,
                null
        );

        // when
        String announcement = VoiceAnnouncementUtils.createStatistics(context, track, dataSet, UnitSystem.METRIC, true, null, null).toString();

        // then
        assertEquals("12:00 AM. Total distance 20.0 kilometers. 1 hour. Average moving speed 20.0 kilometers per hour.", announcement);
    }

    @Test
    public void getAnnouncement_metric_distance_rounding_check_two() {
        Track track = new Track(
                null,
                null,
                "",
                "",
                "",
                ActivityType.AIRPLANE,
                ZoneOffset.UTC,
                new Statistics(
                        Instant.EPOCH,
                        Instant.EPOCH,
                        Duration.ofHours(2).plusMinutes(5).plusSeconds(10),
                        Duration.ofHours(1),
                        Distance.of(19900),
                        Speed.of(100),
                        null,
                        new AltitudeGainLoss(6000, 6000),
                        null,
                        null
                ));

        SensorDataSet dataSet = new SensorDataSet(
                null,
                null,
                null,
                new SensorData<>(HeartRate.of(60), "unused"),
                null, null,
                null,
                null
        );

        // when
        String announcement = VoiceAnnouncementUtils.createStatistics(context, track, dataSet, UnitSystem.METRIC, true, null, null).toString();

        // then
        assertEquals("12:00 AM. Total distance 19.9 kilometers. 1 hour. Average moving speed 19.9 kilometers per hour.", announcement);
    }

    @Test
    public void getAnnouncement_metric_distance_without_unit() {
        PreferencesUtils.setVoiceAnnounceUnit(false);

        Track track = new Track(
                null,
                null,
                "",
                "",
                "",
                ActivityType.AIRPLANE,
                ZoneOffset.UTC,
                new Statistics(
                        Instant.EPOCH,
                        Instant.EPOCH,
                        Duration.ofHours(2).plusMinutes(5).plusSeconds(10),
                        Duration.ofHours(1),
                        Distance.of(19900),
                        Speed.of(100),
                        null,
                        new AltitudeGainLoss(6000, 6000),
                        null,
                        null
                ));

        SensorDataSet dataSet = new SensorDataSet(
                null,
                null,
                null,
                new SensorData<>(HeartRate.of(60), "unused"),
                null, null,
                null,
                null
        );

        // when
        String announcement = VoiceAnnouncementUtils.createStatistics(context, track, dataSet, UnitSystem.METRIC, true, null, null).toString();

        // then
        assertEquals("12:00 AM. Total distance 19.9. 1 hour. Average moving speed 19.9.", announcement);
    }

    @Test
    public void getAnnouncement_withInterval_metric_speed() {
        // given
        Pair<Track, Statistics> data = buildTrackWithTrackPoints();

        SensorDataSet dataSet = new SensorDataSet(
                null,
                null,
                null,
                new SensorData<>(HeartRate.of(60), "unused"),
                null, null,
                null,
                null
        );

        // when
        String announcement = VoiceAnnouncementUtils.createStatistics(context, data.first, dataSet, UnitSystem.METRIC, true, data.second, null).toString();

        // then
        assertEquals("12:16 AM. Total distance 14.2 kilometers. 16 minutes 39 seconds. Average moving speed 51.2 kilometers per hour. Lap speed 51.2 kilometers per hour.", announcement);
    }

    @Test
    public void getAnnouncement_metric_pace() {
        Track track = new Track(
                null,
                null,
                "",
                "",
                "",
                ActivityType.AIRPLANE,
                ZoneOffset.UTC,
                new Statistics(
                        Instant.EPOCH,
                        Instant.EPOCH,
                        Duration.ofHours(2).plusMinutes(5).plusSeconds(10),
                        Duration.ofHours(1).plusMinutes(5).plusSeconds(10),
                        Distance.of(20000),
                        Speed.of(100),
                        null,
                        new AltitudeGainLoss(6000, 6000),
                        null,
                        null
                ));

        SensorDataSet dataSet = new SensorDataSet(
                null,
                null,
                null,
                new SensorData<>(HeartRate.of(60), "unused"),
                null, null,
                null,
                null
        );

        // when
        String announcement = VoiceAnnouncementUtils.createStatistics(context, track, dataSet, UnitSystem.METRIC, false, null, null).toString();

        // then
        assertEquals("12:00 AM. Total distance 20.0 kilometers. 1 hour 5 minutes 10 seconds. Pace 3 minutes 15 seconds per kilometer.", announcement);
    }

    @Test
    public void getAnnouncement_withInterval_metric_pace() {
        // given
        Pair<Track, Statistics> data = buildTrackWithTrackPoints();

        SensorDataSet dataSet = new SensorDataSet(
                null,
                null,
                null,
                new SensorData<>(HeartRate.of(60), "unused"),
                null, null,
                null,
                null
        );

        // when
        String announcement = VoiceAnnouncementUtils.createStatistics(context, data.first, dataSet, UnitSystem.METRIC, false, data.second, null).toString();

        // then
        assertEquals("12:16 AM. Total distance 14.2 kilometers. 16 minutes 39 seconds. Pace 1 minute 10 seconds per kilometer. Lap time 1 minute 10 seconds per kilometer.", announcement);
    }

    @Test
    public void getAnnouncement_imperial_speed() {
        Track track = new Track(
                null,
                null,
                "",
                "",
                "",
                ActivityType.AIRPLANE,
                ZoneOffset.UTC,
                new Statistics(
                        Instant.EPOCH,
                        Instant.EPOCH,
                        Duration.ofHours(2).plusMinutes(5).plusSeconds(10),
                        Duration.ofHours(1).plusMinutes(5).plusSeconds(10),
                        Distance.of(20000),
                        Speed.of(100),
                        null,
                        new AltitudeGainLoss(6000, 6000),
                        null,
                        null
                ));

        SensorDataSet dataSet = new SensorDataSet(
                null,
                null,
                null,
                new SensorData<>(HeartRate.of(60), "unused"),
                null, null,
                null,
                null
        );

        // when
        String announcement = VoiceAnnouncementUtils.createStatistics(context, track, dataSet, UnitSystem.IMPERIAL_FEET, true, null, null).toString();

        // then
        assertEquals("12:00 AM. Total distance 12.4 miles. 1 hour 5 minutes 10 seconds. Average moving speed 11.4 miles per hour.", announcement);
    }

    @Test
    public void getAnnouncement_imperial_speed_1() {
        Track track = new Track(
                null,
                null,
                "",
                "",
                "",
                ActivityType.AIRPLANE,
                ZoneOffset.UTC,
                new Statistics(
                        Instant.EPOCH,
                        Instant.EPOCH,
                        Duration.ofHours(2).plusMinutes(5).plusSeconds(10),
                        Duration.ofHours(1),
                        Distance.ofMile(1.1),
                        null,
                        null,
                        null,
                        null,
                        null
                ));

        SensorDataSet dataSet = new SensorDataSet(
                null,
                null,
                null,
                new SensorData<>(HeartRate.of(60), "unused"),
                null, null,
                null,
                null
        );

        // when
        String announcement = VoiceAnnouncementUtils.createStatistics(context, track, dataSet, UnitSystem.IMPERIAL_FEET, true, null, null).toString();

        // then
        assertEquals("12:00 AM. Total distance 1.1 miles. 1 hour. Average moving speed 1.1 miles per hour.", announcement);
    }

    @Test
    public void getAnnouncement_imperial_meter_speed_1() {
        Track track = new Track(
                null,
                null,
                "",
                "",
                "",
                ActivityType.AIRPLANE,
                ZoneOffset.UTC,
                new Statistics(
                        Instant.EPOCH,
                        Instant.EPOCH,
                        Duration.ofHours(2).plusMinutes(5).plusSeconds(10),
                        Duration.ofHours(1),
                        Distance.ofMile(1.1),
                        null,
                        null,
                        null,
                        null,
                        null
                ));

        SensorDataSet dataSet = new SensorDataSet(
                null,
                null,
                null,
                new SensorData<>(HeartRate.of(60), "unused"),
                null, null,
                null,
                null
        );

        // when
        String announcement = VoiceAnnouncementUtils.createStatistics(context, track, dataSet, UnitSystem.IMPERIAL_METER, true, null, null).toString();

        // then
        assertEquals("12:00 AM. Total distance 1.1 miles. 1 hour. Average moving speed 1.1 miles per hour.", announcement);
    }

    @Test
    public void getAnnouncement_metric_speed_1() {
        Track track = new Track(
                null,
                null,
                "",
                "",
                "",
                ActivityType.AIRPLANE,
                ZoneOffset.UTC,
                new Statistics(
                        Instant.EPOCH,
                        Instant.EPOCH,
                        Duration.ofHours(2).plusMinutes(5).plusSeconds(10),
                        Duration.ofHours(1),
                        Distance.ofKilometer(1.1),
                        null,
                        null,
                        null,
                        null,
                        null
                ));

        SensorDataSet dataSet = new SensorDataSet(
                null,
                null,
                null,
                new SensorData<>(HeartRate.of(60), "unused"),
                null, null,
                null,
                null
        );

        // when
        String announcement = VoiceAnnouncementUtils.createStatistics(context, track, dataSet, UnitSystem.METRIC, true, null, null).toString();

        // then
        assertEquals("12:00 AM. Total distance 1.1 kilometers. 1 hour. Average moving speed 1.1 kilometers per hour.", announcement);
    }

    @Test
    public void getAnnouncement_withInterval_imperial_speed() {
        // given
        Pair<Track, Statistics> data = buildTrackWithTrackPoints();

        SensorDataSet dataSet = new SensorDataSet(
                null,
                null,
                null,
                new SensorData<>(HeartRate.of(60), "unused"),
                null, null,
                null,
                null
        );

        // when
        String announcement = VoiceAnnouncementUtils.createStatistics(context, data.first, dataSet, UnitSystem.IMPERIAL_FEET, true, data.second, null).toString();

        // then
        assertEquals("12:16 AM. Total distance 8.8 miles. 16 minutes 39 seconds. Average moving speed 31.8 miles per hour. Lap speed 31.8 miles per hour.", announcement);
    }

    @Test
    public void getAnnouncement_imperial_pace() {
        Track track = new Track(
                null,
                null,
                "",
                "",
                "",
                ActivityType.AIRPLANE,
                ZoneOffset.UTC,
                new Statistics(
                        Instant.EPOCH,
                        Instant.EPOCH,
                        Duration.ofHours(2).plusMinutes(5).plusSeconds(10),
                        Duration.ofHours(1).plusMinutes(5).plusSeconds(10),
                        Distance.of(20000),
                        Speed.of(100),
                        null,
                        new AltitudeGainLoss(6000, 6000),
                        null,
                        null
                ));

        SensorDataSet dataSet = new SensorDataSet(
                null,
                null,
                null,
                new SensorData<>(HeartRate.of(60), "unused"),
                null, null,
                null,
                null
        );

        // when
        String announcement = VoiceAnnouncementUtils.createStatistics(context, track, dataSet, UnitSystem.IMPERIAL_FEET, false, null, null).toString();

        // then
        assertEquals("12:00 AM. Total distance 12.4 miles. 1 hour 5 minutes 10 seconds. Pace 5 minutes 15 seconds per mile.", announcement);
    }

    @Test
    public void getAnnouncement_withInterval_imperial_pace() {
        // given
        Pair<Track, Statistics> data = buildTrackWithTrackPoints();

        SensorDataSet dataSet = new SensorDataSet(
                null,
                null,
                null,
                new SensorData<>(HeartRate.of(60), "unused"),
                null, null,
                null,
                null
        );

        // when
        String announcement = VoiceAnnouncementUtils.createStatistics(context, data.first, dataSet, UnitSystem.IMPERIAL_FEET, false, data.second, null).toString();

        // then
        assertEquals("12:16 AM. Total distance 8.8 miles. 16 minutes 39 seconds. Pace 1 minute 53 seconds per mile. Lap time 1 minute 53 seconds per mile.", announcement);
    }

    @Test
    public void getAnnouncement_heart_rate_and_sensor_statistics() {
        // given
        PreferencesUtils.setVoiceAnnounceAverageHeartRate(true);
        PreferencesUtils.setVoiceAnnounceLapHeartRate(true);

        Pair<Track, Statistics> data = buildTrackWithTrackPoints();
        int lapHeartRate = Math.round(data.second.avgHeartRate().getBPM());

        SensorStatistics sensorStatistics = new SensorStatistics(HeartRate.of(180f), HeartRate.of(180f), null, null, null, null);

        SensorDataSet dataSet = new SensorDataSet(
                null,
                null,
                null,
                new SensorData<>(HeartRate.of(60), "unused"),
                null, null,
                null,
                null
        );

        // when
        String announcement = VoiceAnnouncementUtils.createStatistics(context, data.first, dataSet, UnitSystem.METRIC, true, data.second, sensorStatistics).toString();

        // then
        assertEquals("12:16 AM. Total distance 14.2 kilometers. 16 minutes 39 seconds. Average moving speed 51.2 kilometers per hour. Lap speed 51.2 kilometers per hour. Average heart rate 180 bpm. Lap heart rate " + lapHeartRate + " bpm.", announcement);
    }

    @Test
    public void getAnnouncement_heart_rate() {
        // given
        PreferencesUtils.setVoiceAnnounceHeartRateCurrent(true);
        PreferencesUtils.setVoiceAnnounceLapHeartRate(true);
        PreferencesUtils.setVoiceAnnounceAverageHeartRate(true);

        PreferencesUtils.setVoiceAnnounceTotalDistance(false);
        PreferencesUtils.setVoiceAnnounceMovingTime(false);
        PreferencesUtils.setVoiceAnnounceAverageSpeedPace(false);
        PreferencesUtils.setVoiceAnnounceLapSpeedPace(false);

        Pair<Track, Statistics> data = buildTrackWithTrackPoints();
        int lapHeartRate = Math.round(data.second.avgHeartRate().getBPM());

        SensorStatistics sensorStatistics = new SensorStatistics(HeartRate.of(180f), HeartRate.of(180f), null, null, null, null);

        SensorDataSet dataSet = new SensorDataSet(
                null,
                null,
                null,
                new SensorData<>(HeartRate.of(60), "unused"),
                null, null,
                null,
                null
        );

        // when
        String announcement = VoiceAnnouncementUtils.createStatistics(context, data.first, dataSet, UnitSystem.METRIC, true, data.second, sensorStatistics).toString();

        // then
        assertEquals("12:16 AM.  Current heart rate 60 bpm. Average heart rate 180 bpm. Lap heart rate " + lapHeartRate + " bpm.", announcement);
    }

    @Test
    public void ICUMessageDemo() {
        // Android 7's ICU MessageFormat; working
        String template = """
                {n, plural,
                one {1 mile}
                other {{n,number,#.#} miles}
                }""";

        assertEquals("1.1 miles", MessageFormat.format(template, Map.of("n", 1.1)));
        assertEquals("1 mile", MessageFormat.format(template, Map.of("n", 1)));
        assertEquals("1.1 miles", MessageFormat.format(template, Map.of("n", 1.11)));
        assertEquals("1.2 miles", MessageFormat.format(template, Map.of("n", 1.18)));
    }

    private static Pair<Track, Statistics> buildTrackWithTrackPoints() {
        int numberOfPoints = 1000;

        ArrayList<TrackPoint> trackPoints = new ArrayList<>();
        for (int i = 0; i < numberOfPoints; i++) {
            trackPoints.add(TestDataUtil.createTrackPoint(i));
        }

        IntervalStatisticsUpdater intervalStatistics = new IntervalStatisticsUpdater(Distance.of(1000));
        intervalStatistics.addTrackPoints(trackPoints.iterator());
        Statistics lastInterval = intervalStatistics.getLastInterval();

        Track track = new Track(
                null,
                null,
                "",
                "",
                "",
                ActivityType.AIRPLANE,
                ZoneOffset.UTC,
                new Statistics(
                        Instant.EPOCH,
                        Instant.EPOCH.plusSeconds(999),
                        Duration.ofMinutes(16).plusSeconds(39),
                        Duration.ofMinutes(16).plusSeconds(39),
                        Distance.of(14208),
                        Speed.of(104),
                        null,
                        null,
                        null,
                        null
                ));

        return new Pair<>(track, lastInterval);
    }
}
