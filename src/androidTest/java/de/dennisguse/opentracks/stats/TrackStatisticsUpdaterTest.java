package de.dennisguse.opentracks.stats;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import de.dennisguse.opentracks.content.data.TestDataUtil;
import de.dennisguse.opentracks.data.models.Altitude;
import de.dennisguse.opentracks.data.models.AltitudeExtremities;
import de.dennisguse.opentracks.data.models.AltitudeGainLoss;
import de.dennisguse.opentracks.data.models.Distance;
import de.dennisguse.opentracks.data.models.HeartRate;
import de.dennisguse.opentracks.data.models.Position;
import de.dennisguse.opentracks.data.models.Power;
import de.dennisguse.opentracks.data.models.Speed;
import de.dennisguse.opentracks.data.models.Statistics;
import de.dennisguse.opentracks.data.models.Track;
import de.dennisguse.opentracks.data.models.TrackPoint;
import de.dennisguse.opentracks.data.statistics.TrackStatisticsUpdater;

@RunWith(AndroidJUnit4.class)
public class TrackStatisticsUpdaterTest {

    @Test
    public void startTime() {
        // given
        Instant startTime = Instant.parse("2021-10-24T23:00:00.000Z");
        TrackPoint tp = new TrackPoint(TrackPoint.Type.SEGMENT_START_MANUAL, startTime);

        // when
        TrackStatisticsUpdater subject = new TrackStatisticsUpdater(tp);

        // then
        assertEquals(
                new Statistics(
                        startTime,
                        startTime,
                        Duration.ZERO,
                        Duration.ZERO,
                        Distance.ZERO,
                        Speed.ZERO,
                        null,
                        null,
                        null,
                        null
                ),
                subject.getTrackStatistics()
        );
    }

    @Test
    public void addTrackPoint_TestingTrack() {
        // given
        TestDataUtil.TrackData data = TestDataUtil.createTestingTrack(new Track.Id(1));

        // when
        TrackStatisticsUpdater subject = new TrackStatisticsUpdater(data.trackPoints());

        // then
        Statistics result = subject.getTrackStatistics();
        assertEquals(
                new Statistics(
                        Instant.EPOCH,
                        Instant.EPOCH.plusSeconds(13),
                        Duration.ofSeconds(12),
                        Duration.ofSeconds(12),
                        Distance.of(142.2637701034546),
                        Speed.of(12.448093056678772),
                        new AltitudeExtremities(2.5, 32.5),
                        new AltitudeGainLoss(36, 36),
                        HeartRate.of(106.833336f),
                        Power.of(405.2778f)
                ),
                result
        );
    }

    @Test
    public void addTrackPoint_distance_from_GPS_moving() {
        // given
        TrackPoint tp1 = new TrackPoint(TrackPoint.Type.SEGMENT_START_MANUAL, Instant.ofEpochMilli(1000));
        TrackPoint tp2 = createTrackPoint(0, 0, Altitude.WGS84.of(5.0), Instant.ofEpochMilli(2000));
        TrackPoint tp3 = createTrackPoint(0.00001, 0, Altitude.WGS84.of(5.0), Instant.ofEpochMilli(3000), Speed.of(5f), null);

        // when
        TrackStatisticsUpdater subject = new TrackStatisticsUpdater(List.of(tp1, tp2, tp3));

        // then
        assertEquals(Distance.of(1.1057428121566772), subject.getTrackStatistics().totalDistance());
    }

    @Test
    public void addTrackPoint_distance_from_GPS_moving_and_sensor_moving() {
        // given
        TrackPoint tp1 = new TrackPoint(TrackPoint.Type.SEGMENT_START_MANUAL, Instant.ofEpochMilli(1000));
        TrackPoint tp2 = createTrackPoint(0, 0, Altitude.WGS84.of(5.0), Instant.ofEpochMilli(2000), Speed.of(5f), null);
        TrackPoint tp3 = createTrackPoint(0.001, 0, Altitude.WGS84.of(5.0), Instant.ofEpochMilli(3000), Speed.of(5f), null);
        TrackPoint tp4 = createTrackPoint(0.001, 0, Altitude.WGS84.of(5.0), Instant.ofEpochMilli(4000), Speed.of(5f), Distance.of(5));
        TrackPoint tp5 = new TrackPoint(null,
                TrackPoint.Type.SEGMENT_END_MANUAL,
                Position.of(Instant.ofEpochMilli(5000)),
                Distance.of(10f),
                null,
                null,
                null,
                null,
                null);

        // when
        TrackStatisticsUpdater subject = new TrackStatisticsUpdater(List.of(tp1, tp2, tp3));

        // then
        assertEquals(Distance.of(110.57427215576172), subject.getTrackStatistics().totalDistance());

        // when
        subject.addTrackPoint(tp4);
        subject.addTrackPoint(tp5);

        // then
        assertEquals(Distance.of(125.57427215576172), subject.getTrackStatistics().totalDistance());
    }

    @Test
    public void addTrackPoint_distance_from_GPS_moving_and_sensor_disconnecting() {
        // given
        TrackPoint tp1 = new TrackPoint(TrackPoint.Type.SEGMENT_START_MANUAL, Instant.ofEpochMilli(1000));
        TrackPoint tp2 = createTrackPoint(0, 0, Altitude.WGS84.of(5.0), Instant.ofEpochMilli(2000), Speed.of(5f), null);

        TrackPoint tp3 = createTrackPoint(0.00001, 0, Altitude.WGS84.of(5.0), Instant.ofEpochMilli(3000), Speed.of(5f), Distance.of(5));
        TrackPoint tp4 = createTrackPoint(0.0005, 0, Altitude.WGS84.of(5.0), Instant.ofEpochMilli(4000), Speed.of(5f), null);
        TrackPoint tp5 = new TrackPoint(TrackPoint.Type.SEGMENT_END_MANUAL, Instant.ofEpochMilli(5000));

        // when
        TrackStatisticsUpdater subject = new TrackStatisticsUpdater(List.of(tp1, tp2, tp3));

        // then
        assertEquals(Distance.of(5), subject.getTrackStatistics().totalDistance());

        // when
        subject.addTrackPoint(tp4);
        subject.addTrackPoint(tp5);

        // then
        assertEquals(Distance.of(59.181396484375), subject.getTrackStatistics().totalDistance());
    }

    @Test
    public void addTrackPoint_maxSpeed_multiple_segments() {
        TrackStatisticsUpdater subject = new TrackStatisticsUpdater(List.of(
                new TrackPoint(TrackPoint.Type.SEGMENT_START_MANUAL, Instant.ofEpochSecond(0)),
                createTrackPoint(0, 0, Altitude.WGS84.of(0), Instant.ofEpochSecond(1), Speed.of(2), null),
                createTrackPoint(0, 0, Altitude.WGS84.of(0), Instant.ofEpochSecond(2), Speed.of(2), null),
                new TrackPoint(TrackPoint.Type.SEGMENT_END_MANUAL, Instant.ofEpochSecond(4))
        ));
        assertEquals(Speed.of(2), subject.getTrackStatistics().maxSpeed());

        // when
        List.of(
                new TrackPoint(TrackPoint.Type.SEGMENT_START_MANUAL, Instant.ofEpochSecond(5)),
                createTrackPoint(0, 0, Altitude.WGS84.of(0), Instant.ofEpochSecond(6), Speed.of(1), null),
                createTrackPoint(0, 0, Altitude.WGS84.of(0), Instant.ofEpochSecond(7), Speed.of(1), null),
                new TrackPoint(TrackPoint.Type.SEGMENT_END_MANUAL, Instant.ofEpochSecond(8))
        ).forEach(subject::addTrackPoint);

        // then
        assertEquals(Speed.of(2f), subject.getTrackStatistics().maxSpeed());
    }

    @Test
    public void addTrackPoint_idle_withoutDistance() {
        // when
        TrackStatisticsUpdater subject = new TrackStatisticsUpdater(List.of(
                new TrackPoint(TrackPoint.Type.SEGMENT_START_MANUAL, Instant.ofEpochSecond(0)),
                createTrackPoint(0, 0, Altitude.WGS84.of(0), Instant.ofEpochSecond(1), Speed.of(2), null),
                createTrackPoint(0, 0, Altitude.WGS84.of(0), Instant.ofEpochSecond(2), Speed.of(2), null),

                new TrackPoint(TrackPoint.Type.IDLE, Instant.ofEpochSecond(30)),
                new TrackPoint(
                        null,
                        TrackPoint.Type.TRACKPOINT,
                        Position.of(Instant.ofEpochSecond(40)),
                        null,
                        HeartRate.of(50),
                        null,
                        null,
                        null,
                        null
                ),
                new TrackPoint(
                        null,
                        TrackPoint.Type.TRACKPOINT,
                        Position.of(Instant.ofEpochSecond(45)),
                        null,
                        HeartRate.of(50),
                        null,
                        null,
                        null,
                        null
                ),
                createTrackPoint(0, 1, Altitude.WGS84.of(0), Instant.ofEpochSecond(50)),
                createTrackPoint(0, 2, Altitude.WGS84.of(0), Instant.ofEpochSecond(55)),

                new TrackPoint(TrackPoint.Type.SEGMENT_END_MANUAL, Instant.ofEpochSecond(60))
        ));

        // then
        assertEquals(Duration.ofSeconds(35), subject.getTrackStatistics().movingDuration());
    }

    @Test
    public void addTrackPoint_idle_withDistance() {
        // when
        TrackStatisticsUpdater subject = new TrackStatisticsUpdater(List.of(
                new TrackPoint(TrackPoint.Type.SEGMENT_START_MANUAL, Instant.ofEpochSecond(0)),
                createTrackPoint(0, 0, Altitude.WGS84.of(0), Instant.ofEpochSecond(1), null, Distance.of(10)),
                createTrackPoint(0, 0, Altitude.WGS84.of(0), Instant.ofEpochSecond(2), null, Distance.of(10)),
                new TrackPoint(
                        null,
                        TrackPoint.Type.IDLE,
                        Position.of(Instant.ofEpochSecond(30)),
                        Distance.ofKilometer(1),
                        null,
                        null,
                        null,
                        null,
                        null
                ),
                new TrackPoint(
                        null,
                        TrackPoint.Type.TRACKPOINT,
                        Position.of(Instant.ofEpochSecond(40)),
                        null,
                        HeartRate.of(50),
                        null,
                        null,
                        null,
                        null
                ),
                new TrackPoint(
                        null,
                        TrackPoint.Type.TRACKPOINT,
                        Position.of(Instant.ofEpochSecond(45)),
                        null,
                        HeartRate.of(50),
                        null,
                        null,
                        null,
                        null
                ),
                createTrackPoint(0, 0, Altitude.WGS84.of(0), Instant.ofEpochSecond(50), null, Distance.of(10)),
                createTrackPoint(0, 0, Altitude.WGS84.of(0), Instant.ofEpochSecond(55), null, Distance.of(10)),

                new TrackPoint(TrackPoint.Type.SEGMENT_END_MANUAL, Instant.ofEpochSecond(60))
        ));

        // then
        assertEquals(Duration.ofSeconds(40), subject.getTrackStatistics().movingDuration());

        assertEquals(Distance.of(1040), subject.getTrackStatistics().totalDistance());
    }

    @Test
    public void addTrackPoint_idle_remain_idle() {
        // when
        TrackStatisticsUpdater subject = new TrackStatisticsUpdater(new TrackPoint(TrackPoint.Type.SEGMENT_START_MANUAL, Instant.ofEpochSecond(0)));
        subject.addTrackPoint(
                createTrackPoint(0, 0, Altitude.WGS84.of(0), Instant.ofEpochSecond(10), null, Distance.of(10))
        );

        subject.addTrackPoint(new TrackPoint(TrackPoint.Type.IDLE, Instant.ofEpochSecond(30)));
        // then
        assertTrue(subject.isIdle());
        assertEquals(Duration.ofSeconds(30), subject.getTrackStatistics().movingDuration());
        assertEquals(Duration.ofSeconds(30), subject.getTrackStatistics().totalDuration());
        assertEquals(Distance.of(10), subject.getTrackStatistics().totalDistance());

        // when
        subject.addTrackPoint(
                new TrackPoint(
                        null,
                        TrackPoint.Type.TRACKPOINT,
                        Position.of(Instant.ofEpochSecond(40)),
                        Distance.ZERO,
                        null,
                        null,
                        null,
                        null,
                        null
                )
        );
        // then
        assertTrue(subject.isIdle());
        assertEquals(Duration.ofSeconds(30), subject.getTrackStatistics().movingDuration());
        assertEquals(Duration.ofSeconds(40), subject.getTrackStatistics().totalDuration());
        assertEquals(Distance.of(10), subject.getTrackStatistics().totalDistance());

        // when
        subject.addTrackPoint(
                new TrackPoint(
                        null,
                        TrackPoint.Type.TRACKPOINT,
                        Position.of(Instant.ofEpochSecond(45)),
                        Distance.of(1),
                        null,
                        null,
                        null,
                        null,
                        null
                )
        );
        // then
        assertTrue(subject.isIdle());
        assertEquals(Duration.ofSeconds(30), subject.getTrackStatistics().movingDuration());
        assertEquals(Duration.ofSeconds(45), subject.getTrackStatistics().totalDuration());
        assertEquals(Distance.of(11), subject.getTrackStatistics().totalDistance());

        // when
        subject.addTrackPoint(
                new TrackPoint(
                        null,
                        TrackPoint.Type.TRACKPOINT,
                        Position.of(Instant.ofEpochSecond(50)),
                        Distance.of(10),
                        null,
                        null,
                        null,
                        null,
                        null
                )
        );
        // then
        assertFalse(subject.isIdle());
        assertEquals(Duration.ofSeconds(30), subject.getTrackStatistics().movingDuration());
        assertEquals(Duration.ofSeconds(50), subject.getTrackStatistics().totalDuration());
        assertEquals(Distance.of(21), subject.getTrackStatistics().totalDistance());

        // when
        subject.addTrackPoint(new TrackPoint(TrackPoint.Type.SEGMENT_END_MANUAL, Instant.ofEpochSecond(60)));

        // then
        assertFalse(subject.isIdle());
        assertEquals(Duration.ofSeconds(40), subject.getTrackStatistics().movingDuration());
        assertEquals(Duration.ofSeconds(60), subject.getTrackStatistics().totalDuration());
        assertEquals(Distance.of(21), subject.getTrackStatistics().totalDistance());
    }

    @Test
    public void copy_constructor() {
        // given
        TrackPoint tp1 = new TrackPoint(TrackPoint.Type.SEGMENT_START_MANUAL, Instant.ofEpochMilli(1000));
        TrackPoint tp2 = createTrackPoint(0, 0, Altitude.WGS84.of(5.0), Instant.ofEpochMilli(2000), Speed.of(5f), null);
        TrackPoint tp3 = createTrackPoint(0.00001, 0, Altitude.WGS84.of(5.0), Instant.ofEpochMilli(3000), Speed.of(5f), null);
        TrackPoint tp4 = createTrackPoint(0.0005, 0, Altitude.WGS84.of(5.0), Instant.ofEpochMilli(4000), Speed.of(5f), null);
        TrackPoint tp5 = new TrackPoint(TrackPoint.Type.SEGMENT_END_MANUAL, Instant.ofEpochMilli(5000));

        TrackStatisticsUpdater initial = new TrackStatisticsUpdater(List.of(tp1, tp2, tp3, tp4));

        // when
        TrackStatisticsUpdater subject = new TrackStatisticsUpdater(initial, tp5);

        // then
        assertEquals(new Statistics(
                Instant.ofEpochMilli(1000),
                Instant.ofEpochMilli(4000),
                Duration.ofSeconds(3),
                Duration.ofSeconds(3),
                Distance.of(55.28713929653168),
                Speed.of(18.429046432177227),
                new AltitudeExtremities(5, 5),
                null,
                null,
                null
        ), initial.getTrackStatistics());
        assertEquals(new Statistics(
                Instant.ofEpochMilli(1000),
                Instant.ofEpochMilli(5000),
                Duration.ofSeconds(4),
                Duration.ofSeconds(4),
                Distance.of(55.28713929653168),
                Speed.of(18.429046432177227),
                new AltitudeExtremities(5, 5),
                null,
                null,
                null
        ), subject.getTrackStatistics());
    }

    public TrackPoint createTrackPoint(double latitude, double longitude, Altitude altitude, Instant time) {
        return createTrackPoint(latitude, longitude, altitude, time, null, null);
    }

    public TrackPoint createTrackPoint(double latitude, double longitude, Altitude altitude, Instant time, Speed speed, Distance sensorDistance) {
        return new TrackPoint(
                null,
                TrackPoint.Type.TRACKPOINT,
                new Position(
                        time,
                        latitude,
                        longitude,
                        null,
                        altitude,
                        null,
                        null,
                        speed
                ),
                sensorDistance,
                null,
                null,
                null,
                null,
                null
        );
    }
}