package de.dennisguse.opentracks.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.core.location.LocationManagerCompat;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.rule.GrantPermissionRule;
import androidx.test.rule.ServiceTestRule;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.TestUtil;
import de.dennisguse.opentracks.content.data.TestDataUtil;
import de.dennisguse.opentracks.data.ContentProviderUtils;
import de.dennisguse.opentracks.data.models.AltitudeGainLoss;
import de.dennisguse.opentracks.data.models.Cadence;
import de.dennisguse.opentracks.data.models.Distance;
import de.dennisguse.opentracks.data.models.HeartRate;
import de.dennisguse.opentracks.data.models.Position;
import de.dennisguse.opentracks.data.models.Speed;
import de.dennisguse.opentracks.data.models.Statistics;
import de.dennisguse.opentracks.data.models.Track;
import de.dennisguse.opentracks.data.models.TrackPoint;
import de.dennisguse.opentracks.io.file.importer.TrackPointAssert;
import de.dennisguse.opentracks.sensors.SensorManager;
import de.dennisguse.opentracks.sensors.driver.GpsInternal;
import de.dennisguse.opentracks.sensors.driver.RunningSpeedAndCadenceBluetooth;
import de.dennisguse.opentracks.sensors.sensorData.AggregatorBarometer;
import de.dennisguse.opentracks.sensors.sensorData.AggregatorHeartRate;
import de.dennisguse.opentracks.sensors.sensorData.AggregatorRunning;
import de.dennisguse.opentracks.services.handlers.TrackPointCreator;
import de.dennisguse.opentracks.settings.PreferencesUtils;

@RunWith(AndroidJUnit4.class)
public class TrackRecordingServiceRecordingTest {

    @Rule
    public final ServiceTestRule mServiceRule = ServiceTestRule.withTimeout(5, TimeUnit.SECONDS);

    @Rule
    public GrantPermissionRule mGrantPermissionRule = TestUtil.createGrantPermissionRule();

    private final Context context = ApplicationProvider.getApplicationContext();
    private ContentProviderUtils contentProviderUtils;

    private TrackRecordingService service;

    @BeforeClass
    public static void preSetUp() {
        // Prepare looper for Android's message queue
        if (Looper.myLooper() == null) Looper.prepare();
    }

    @AfterClass
    public static void finalTearDown() {
        if (Looper.myLooper() != null) Looper.myLooper().quit();
    }

    private TrackRecordingService startService() throws TimeoutException {
        Intent startIntent = new Intent(context, TrackRecordingService.class);
        return ((TrackRecordingService.Binder) mServiceRule.bindService(startIntent))
                .getService();
    }

    @Before
    public void setUp() throws TimeoutException {
        contentProviderUtils = new ContentProviderUtils(context);

        PreferencesUtils.setString(R.string.recording_distance_interval_key, R.string.recording_distance_interval_default);
        PreferencesUtils.setString(R.string.idle_duration_key, R.string.idle_duration_default);

        service = startService();
    }

    @MediumTest
    @Test
    public void reCreate_not_recording() {
        // given
        TrackIdStorage.unset(context);

        assertFalse(service.isRecording());

        // when
        service.onStartCommand(null, 0, 1);

        // then
        assertFalse(service.isRecording());
    }

    @MediumTest
    @Test
    public void reCreate_recording() {
        // given
        assertFalse(service.isRecording());
        Track.Id trackId = service.startNewTrack();
        assertTrue(service.isRecording());

        // when (crash and restart)
        service.onDestroy();
        service.onCreate();
        service.onStartCommand(null, 0, 1);

        // then
        assertTrue(service.isRecording());
        assertEquals(trackId, service.getRecordingStatusObservable().getValue().trackId());

        service.endCurrentTrack();
    }

    @MediumTest
    @Test
    public void recording_startStop() {
        // given
        TrackPointCreator trackPointCreator = service.getTrackPointCreator();

        // when
        String startTime = "2020-02-02T02:02:02Z";
        trackPointCreator.setClock(startTime);
        Track.Id trackId = service.startNewTrack();
        mockAltitudeChange(trackPointCreator, 0);


        // then
        assertEquals(new Statistics(Instant.parse(startTime), Instant.parse(startTime), Duration.ZERO, Duration.ZERO, Distance.ZERO, Speed.ZERO, null, null, null, null)
                , contentProviderUtils.getTrack(trackId).statistics());

        new TrackPointAssert().assertEquals(List.of(
                new TrackPoint(TrackPoint.Type.SEGMENT_START_MANUAL, Instant.parse(startTime))
        ), TestDataUtil.getTrackPoints(contentProviderUtils, trackId));


        // when
        String stopTime = "2020-02-02T02:02:03Z";
        trackPointCreator.setClock(stopTime);
        service.endCurrentTrack();

        // then
        assertEquals(new Statistics(Instant.parse(startTime), Instant.parse(stopTime), Duration.ofSeconds(1), Duration.ofSeconds(1), Distance.ZERO, Speed.ZERO, null, new AltitudeGainLoss(0, 0), null, null)
                , contentProviderUtils.getTrack(trackId).statistics());

        new TrackPointAssert().assertEquals(List.of(
                new TrackPoint(TrackPoint.Type.SEGMENT_START_MANUAL, Instant.parse(startTime)),
                new TrackPoint(
                        null,
                        TrackPoint.Type.SEGMENT_END_MANUAL,
                        Position.of(Instant.parse(stopTime)),
                        null,
                        null,
                        null,
                        null,
                        null,
                        new AltitudeGainLoss(0, 0))
        ), TestDataUtil.getTrackPoints(contentProviderUtils, trackId));
    }


    /**
     * Test that an IDLE event, doesn't store invalid GPS-provided data.
     */
    @MediumTest
    @Test
    public void recording_startIdle() throws InterruptedException {
        // given
        TrackPointCreator trackPointCreator = service.getTrackPointCreator();
        String startTime = "2020-02-02T02:02:02Z";
        trackPointCreator.setClock(startTime);
        Track.Id trackId = service.startNewTrack();

        //We do not want the real GPS to interfere.
        GpsInternal gpsInternal = (GpsInternal)trackPointCreator.getSensorManager().getGpsHandler().driver;
        LocationManagerCompat.removeUpdates(gpsInternal.locationManager, gpsInternal.locationListenerCompat);

        String gps1 = "2020-02-02T02:02:03Z";
        sendGPSLocation(trackPointCreator, gps1, 45.0, 35.0, 1, 15);
        String gps2 = "2020-02-02T02:02:04Z";
        sendGPSLocation(trackPointCreator, gps2, 45.0, 35.0, 1, 15);

        // when
        String idleTime = "2020-02-02T02:02:17Z";
        trackPointCreator.setClock(idleTime);
        Thread.sleep(Duration.ofSeconds(15).toMillis());

        // then
        new TrackPointAssert().assertEquals(List.of(
                new TrackPoint(TrackPoint.Type.SEGMENT_START_MANUAL, Instant.parse(startTime)),
                new TrackPoint(TrackPoint.Type.TRACKPOINT,
                        new Position(
                                Instant.parse(gps1),
                                45d, 35d, Distance.of(1),
                                null, null,
                                null,
                                Speed.of(15))),
                new TrackPoint(TrackPoint.Type.TRACKPOINT,
                        new Position(
                                Instant.parse(gps2),
                                45d, 35d, Distance.of(1),
                                null, null,
                                null,
                                Speed.of(15))),
                new TrackPoint(TrackPoint.Type.IDLE, Instant.parse(idleTime))
        ), TestDataUtil.getTrackPoints(contentProviderUtils, trackId));
    }

    @MediumTest
    @Test
    public void testRecording_startPauseResume() {
        // given
        String startTime = "2020-02-02T02:02:02Z";
        TrackPointCreator trackPointCreator = service.getTrackPointCreator();

        trackPointCreator.setClock(startTime);
        Track.Id trackId = service.startNewTrack();
        mockAltitudeChange(trackPointCreator, 0);


        // when
        String pauseTime = "2020-02-02T02:02:03Z";
        trackPointCreator.setClock(pauseTime);
        service.endCurrentTrack();

        // then
        assertEquals(new Statistics(Instant.parse(startTime), Instant.parse(pauseTime), Duration.ofSeconds(1), Duration.ofSeconds(1), Distance.ZERO, Speed.ZERO, null, new AltitudeGainLoss(0, 0), null, null)
                , contentProviderUtils.getTrack(trackId).statistics());

        new TrackPointAssert().assertEquals(List.of(
                new TrackPoint(TrackPoint.Type.SEGMENT_START_MANUAL, Instant.parse(startTime)),
                new TrackPoint(
                        null,
                        TrackPoint.Type.SEGMENT_END_MANUAL,
                        Position.of(Instant.parse(pauseTime)),
                        null,
                        null,
                        null,
                        null,
                        null,
                        new AltitudeGainLoss(0, 0))
        ), TestDataUtil.getTrackPoints(contentProviderUtils, trackId));

        //when
        String resumeTime = "2020-02-02T02:02:04Z";
        trackPointCreator.setClock(resumeTime);
        service.resumeTrack(trackId);

        // then
        assertEquals(new Statistics(Instant.parse(startTime), Instant.parse(resumeTime), Duration.ofSeconds(1), Duration.ofSeconds(1), Distance.ZERO, Speed.ZERO, null, new AltitudeGainLoss(0, 0), null, null)
                , contentProviderUtils.getTrack(trackId).statistics());

        new TrackPointAssert().assertEquals(List.of(
                new TrackPoint(TrackPoint.Type.SEGMENT_START_MANUAL, Instant.parse(startTime)),
                new TrackPoint(
                        null,
                        TrackPoint.Type.SEGMENT_END_MANUAL,
                        Position.of(Instant.parse(pauseTime)),
                        null,
                        null,
                        null,
                        null,
                        null,
                        new AltitudeGainLoss(0, 0)
                ),
                new TrackPoint(TrackPoint.Type.SEGMENT_START_MANUAL, Instant.parse(resumeTime))
        ), TestDataUtil.getTrackPoints(contentProviderUtils, trackId));
    }


    @MediumTest
    @Test
    public void testRecording_startPauseStop() {
        // given
        TrackPointCreator trackPointCreator = service.getTrackPointCreator();

        String starTime = "2020-02-02T02:02:02Z";
        trackPointCreator.setClock(starTime);
        Track.Id trackId = service.startNewTrack();
        mockAltitudeChange(trackPointCreator, 0);


        String pauseTime = "2020-02-02T02:02:03Z";
        trackPointCreator.setClock(pauseTime);
        service.endCurrentTrack();

        // when
        trackPointCreator.setClock("2020-02-02T02:02:04Z");
        service.endCurrentTrack();

        // then
        assertFalse(service.isRecording());

        List<TrackPoint> trackPoints = TestDataUtil.getTrackPoints(contentProviderUtils, trackId);
        new TrackPointAssert().assertEquals(List.of(
                new TrackPoint(TrackPoint.Type.SEGMENT_START_MANUAL, Instant.parse(starTime)),
                new TrackPoint(
                        null,
                        TrackPoint.Type.SEGMENT_END_MANUAL,
                        Position.of(Instant.parse(pauseTime)),
                        null,
                        null,
                        null,
                        null,
                        null,
                        new AltitudeGainLoss(0, 0))
        ), trackPoints);
    }

    @MediumTest
    @Test
    public void testRecording_startStopResumeStop() {
        // given
        String startTime = "2020-02-02T02:02:02Z";
        TrackPointCreator trackPointCreator = service.getTrackPointCreator();

        trackPointCreator.setClock(startTime);
        Track.Id trackId = service.startNewTrack();
        mockAltitudeChange(trackPointCreator, 0);

        String stopTime = "2020-02-02T02:02:03Z";
        trackPointCreator.setClock(stopTime);
        service.endCurrentTrack();

        // when
        String resumeTime = "2020-02-02T02:02:04Z";
        trackPointCreator.setClock(resumeTime);
        service.resumeTrack(trackId);
        mockAltitudeChange(trackPointCreator, 0);

        // then
        new TrackPointAssert().assertEquals(List.of(
                new TrackPoint(TrackPoint.Type.SEGMENT_START_MANUAL, Instant.parse(startTime)),
                new TrackPoint(
                        null,
                        TrackPoint.Type.SEGMENT_END_MANUAL,
                        Position.of(Instant.parse(stopTime)),
                        null,
                        null,
                        null,
                        null,
                        null,
                        new AltitudeGainLoss(0, 0)
                ),
                new TrackPoint(TrackPoint.Type.SEGMENT_START_MANUAL, Instant.parse(resumeTime))
        ), TestDataUtil.getTrackPoints(contentProviderUtils, trackId));
    }

    @MediumTest
    @Test
    public void testRecording_blesensor_only_no_distance() {
        // given
        String startTime = "2020-02-02T02:02:02Z";
        TrackPointCreator trackPointCreator = service.getTrackPointCreator();

        trackPointCreator.setClock(startTime);
        Track.Id trackId = service.startNewTrack();
        mockAltitudeChange(trackPointCreator, 0);

        SensorManager sensorManager = trackPointCreator.getSensorManager();
        // when
        String sensor1 = "2020-02-02T02:02:03Z";
        trackPointCreator.setClock(sensor1);

        AggregatorHeartRate avgHeartRate = new AggregatorHeartRate("", "");
        sensorManager.setAggregator(avgHeartRate);

        //Should be ignored
        avgHeartRate.add(trackPointCreator.getNow(), HeartRate.of(5));
        sensorManager.onChange();

        String sensor3 = "2020-02-02T02:02:13Z";
        trackPointCreator.setClock(sensor3);
        avgHeartRate.add(trackPointCreator.getNow(), HeartRate.of(7));
        sensorManager.onChange();

        String stopTime = "2020-02-02T02:02:15Z";
        trackPointCreator.setClock(stopTime);
        service.endCurrentTrack();

        // then
        new TrackPointAssert().assertEquals(List.of(
                new TrackPoint(TrackPoint.Type.SEGMENT_START_MANUAL, Instant.parse(startTime)),
                new TrackPoint(
                        null,
                        TrackPoint.Type.TRACKPOINT,
                        Position.of(Instant.parse(sensor3)),
                        null,
                        HeartRate.of(7),
                        null,
                        null,
                        null,
                        new AltitudeGainLoss(0, 0)
                ),
                new TrackPoint(
                        null,
                        TrackPoint.Type.SEGMENT_END_MANUAL,
                        Position.of(Instant.parse(stopTime)),
                        null,
                        HeartRate.of(7),
                        null,
                        null,
                        null,
                        new AltitudeGainLoss(0, 0)
                )
        ), TestDataUtil.getTrackPoints(contentProviderUtils, trackId));
    }

    @MediumTest
    @Test
    public void testRecording_gpsOnly_recordingDistance_above() {
        // given
        String startTime = "2020-02-02T02:02:02Z";
        TrackPointCreator trackPointCreator = service.getTrackPointCreator();
        trackPointCreator.setClock(startTime);
        Track.Id trackId = service.startNewTrack();
        mockAltitudeChange(trackPointCreator, 0);


        // when
        String gps1 = "2020-02-02T02:02:03Z";
        sendGPSLocation(trackPointCreator, gps1, 45.0, 35.0, 1, 15);

        // then
        assertEquals(new Statistics(Instant.parse(startTime), Instant.parse(gps1), Duration.ofSeconds(1), Duration.ofSeconds(1), Distance.ZERO, Speed.of(15), null, new AltitudeGainLoss(0, 0), null, null)
                , contentProviderUtils.getTrack(trackId).statistics());

        // when
        String gps2 = "2020-02-02T02:02:06Z";
        sendGPSLocation(trackPointCreator, gps2, 45.0001, 35.0, 1, 15);

        // then
        assertEquals(new Statistics(Instant.parse(startTime), Instant.parse(gps2), Duration.ofSeconds(4), Duration.ofSeconds(4), Distance.of(11.113178253173828), Speed.of(15), null, new AltitudeGainLoss(0, 0), null, null)
                , contentProviderUtils.getTrack(trackId).statistics());

        // when
        String gps3 = "2020-02-02T02:02:08Z";
        sendGPSLocation(trackPointCreator, gps3, 45.0002, 35.0, 1, 15);

        // then
        assertEquals(new Statistics(Instant.parse(startTime), Instant.parse(gps3), Duration.ofSeconds(6), Duration.ofSeconds(6), Distance.of(22.226356506347656), Speed.of(15), null, new AltitudeGainLoss(0, 0), null, null)
                , contentProviderUtils.getTrack(trackId).statistics());

        // when
        String stopTime = "2020-02-02T02:02:12Z";
        trackPointCreator.setClock(stopTime);
        service.endCurrentTrack();

        // then
        assertEquals(new Statistics(Instant.parse(startTime), Instant.parse(stopTime), Duration.ofSeconds(10), Duration.ofSeconds(10), Distance.of(22.226356506347656), Speed.of(15), null, new AltitudeGainLoss(0, 0), null, null)
                , contentProviderUtils.getTrack(trackId).statistics());

        new TrackPointAssert().assertEquals(List.of(
                new TrackPoint(TrackPoint.Type.SEGMENT_START_MANUAL, Instant.parse(startTime)),
                new TrackPoint(
                        null,
                        TrackPoint.Type.TRACKPOINT,
                        new Position(
                                Instant.parse(gps1),
                                45d, 35d, Distance.of(1),
                                null, null,
                                null,
                                Speed.of(15)),
                        null,
                        null,
                        null,
                        null,
                        null,
                        new AltitudeGainLoss(0, 0)
                ),
                new TrackPoint(
                        null,
                        TrackPoint.Type.TRACKPOINT,
                        new Position(
                                Instant.parse(gps2),
                                45.001, 35d, Distance.of(1),
                                null, null,
                                null,
                                Speed.of(15)),
                        null,
                        null,
                        null,
                        null,
                        null,
                        new AltitudeGainLoss(0, 0)
                ),
                new TrackPoint(
                        null,
                        TrackPoint.Type.TRACKPOINT,
                        new Position(
                                Instant.parse(gps3),
                                45.001, 35d, Distance.of(1),
                                null, null,
                                null,
                                Speed.of(15)),
                        null,
                        null,
                        null,
                        null,
                        null,
                        new AltitudeGainLoss(0, 0)
                ),
                new TrackPoint(
                        null,
                        TrackPoint.Type.SEGMENT_END_MANUAL,
                        Position.of(Instant.parse(stopTime)),
                        null,
                        null,
                        null,
                        null,
                        null,
                        new AltitudeGainLoss(0, 0)
                )
        ), TestDataUtil.getTrackPoints(contentProviderUtils, trackId));
    }

    @MediumTest
    @Test
    public void testRecording_gpsOnly_recordingDistance_below() {
        // given
        String startTime = "2020-02-02T02:02:02Z";
        TrackPointCreator trackPointCreator = service.getTrackPointCreator();
        trackPointCreator.setClock(startTime);
        Track.Id trackId = service.startNewTrack();
        mockAltitudeChange(trackPointCreator, 0);

        // when
        String gps1 = "2020-02-02T02:02:03Z";
        sendGPSLocation(trackPointCreator, gps1, 45.0, 35.0, 1, 15);

        // then
        Statistics gps1Statistics = new Statistics(Instant.parse(startTime), Instant.parse(gps1), Duration.ofSeconds(1), Duration.ofSeconds(1), Distance.ZERO, Speed.of(15), null, new AltitudeGainLoss(0, 0), null, null);
        assertEquals(gps1Statistics, contentProviderUtils.getTrack(trackId).statistics());

        // when
        String gps2 = "2020-02-02T02:02:06Z";
        sendGPSLocation(trackPointCreator, gps2, 45.00001, 35.0, 1, 15);

        // then
        assertEquals(gps1Statistics, contentProviderUtils.getTrack(trackId).statistics());

        // when
        String gps3 = "2020-02-02T02:02:08Z";
        sendGPSLocation(trackPointCreator, gps3, 45.00002, 35.0, 1, 15);

        // then
        assertEquals(gps1Statistics, contentProviderUtils.getTrack(trackId).statistics());

        // when
        String stopTime = "2020-02-02T02:02:12Z";
        trackPointCreator.setClock(stopTime);
        service.endCurrentTrack();

        // then
        assertEquals(new Statistics(Instant.parse(startTime), Instant.parse(stopTime), Duration.ofSeconds(10), Duration.ofSeconds(10), Distance.of(2.222635507583618), Speed.of(15), null, new AltitudeGainLoss(0, 0), null, null)
                , contentProviderUtils.getTrack(trackId).statistics());

        new TrackPointAssert().assertEquals(List.of(
                new TrackPoint(TrackPoint.Type.SEGMENT_START_MANUAL, Instant.parse(startTime)),
                new TrackPoint(
                        null,
                        TrackPoint.Type.TRACKPOINT,
                        new Position(
                                Instant.parse(gps1),
                                45d, 35d, Distance.of(1),
                                null, null,
                                null,
                                Speed.of(15)),
                        null,
                        null,
                        null,
                        null,
                        null,
                        new AltitudeGainLoss(0, 0)
                ),
                new TrackPoint(
                        null,
                        TrackPoint.Type.TRACKPOINT,
                        new Position(
                                Instant.parse(gps3),
                                45.00002, 35d, Distance.of(1),
                                null, null,
                                null,
                                Speed.of(15)),
                        null,
                        null,
                        null,
                        null,
                        null,
                        new AltitudeGainLoss(0, 0)
                ),
                new TrackPoint(
                        null,
                        TrackPoint.Type.SEGMENT_END_MANUAL,
                        new Position(
                                Instant.parse(stopTime),
                                45.00002, 35d, Distance.of(1),
                                null, null,
                                null,
                                Speed.of(15)),
                        null,
                        null,
                        null,
                        null,
                        null,
                        new AltitudeGainLoss(0, 0)
                )
        ), TestDataUtil.getTrackPoints(contentProviderUtils, trackId));
    }

    @MediumTest
    @Test
    public void testRecording_gpsOnly_recordingDistance_movement_non_idle() {
        // given
        String startTime = "2020-02-02T02:02:02Z";
        TrackPointCreator trackPointCreator = service.getTrackPointCreator();
        trackPointCreator.setClock(startTime);
        Track.Id trackId = service.startNewTrack();
        mockAltitudeChange(trackPointCreator, 0);


        // when
        String gps1 = "2020-02-02T02:02:03Z";
        sendGPSLocation(trackPointCreator, gps1, 45.0, 35.0, 1, 15);

        // when - will be ignored
        String gps2 = "2020-02-02T02:02:04Z";
        sendGPSLocation(trackPointCreator, gps2, 45.0, 35.0, 1, 15);

        // when
        String gps3 = "2020-02-02T02:02:05Z";
        sendGPSLocation(trackPointCreator, gps3, 45.0001, 35.0, 1, 15);

        // when
        String stopTime = "2020-02-02T02:02:12Z";
        trackPointCreator.setClock(stopTime);
        service.endCurrentTrack();

        // then
        new TrackPointAssert().assertEquals(List.of(
                new TrackPoint(TrackPoint.Type.SEGMENT_START_MANUAL, Instant.parse(startTime)),
                new TrackPoint(
                        null,
                        TrackPoint.Type.TRACKPOINT,
                        new Position(
                                Instant.parse(gps1),
                                45d, 35d, Distance.of(1),
                                null, null,
                                null,
                                Speed.of(15)),
                        null,
                        null,
                        null,
                        null,
                        null,
                        new AltitudeGainLoss(0, 0)
                ),
                new TrackPoint(
                        null,
                        TrackPoint.Type.TRACKPOINT,
                        new Position(
                                Instant.parse(gps3),
                                45d, 35d, Distance.of(1),
                                null, null,
                                null,
                                Speed.of(15)),
                        null,
                        null,
                        null,
                        null,
                        null,
                        new AltitudeGainLoss(0, 0)),
                new TrackPoint(
                        null,
                        TrackPoint.Type.SEGMENT_END_MANUAL,
                        Position.of(Instant.parse(stopTime)),
                        null,
                        null,
                        null,
                        null,
                        null,
                        new AltitudeGainLoss(0, 0))
        ), TestDataUtil.getTrackPoints(contentProviderUtils, trackId));
    }

    @MediumTest
    @Test
    public void testRecording_gpsOnly_ignore_inaccurate() {
        // given
        String startTime = "2020-02-02T02:02:02Z";
        TrackPointCreator trackPointCreator = service.getTrackPointCreator();
        trackPointCreator.setClock(startTime);
        Track.Id trackId = service.startNewTrack();
        mockAltitudeChange(trackPointCreator, 0);

        // when
        String gps1 = "2020-02-02T02:02:03Z";
        sendGPSLocation(trackPointCreator, gps1, 45.0, 35.0, 100, 15);

        // then
        Statistics startStatistics = new Statistics(Instant.parse(startTime), Instant.parse(startTime), Duration.ZERO, Duration.ZERO, Distance.ZERO, Speed.ZERO, null, null, null, null);
        assertEquals(startStatistics, contentProviderUtils.getTrack(trackId).statistics());


        // when
        String gps2 = "2020-02-02T02:02:06Z";
        sendGPSLocation(trackPointCreator, gps2, 45.1, 35.0, 100, 15);

        // then
        assertEquals(startStatistics, contentProviderUtils.getTrack(trackId).statistics());


        // when
        String stopTime = "2020-02-02T02:02:12Z";
        trackPointCreator.setClock(stopTime);
        service.endCurrentTrack();

        // then
        assertEquals(new Statistics(Instant.parse(startTime), Instant.parse(stopTime), Duration.ofSeconds(10), Duration.ofSeconds(10), Distance.ZERO, Speed.ZERO, null, new AltitudeGainLoss(0, 0), null, null)
                , contentProviderUtils.getTrack(trackId).statistics());

        // then
        new TrackPointAssert().assertEquals(List.of(
                new TrackPoint(TrackPoint.Type.SEGMENT_START_MANUAL, Instant.parse(startTime)),
                new TrackPoint(
                        null,
                        TrackPoint.Type.SEGMENT_END_MANUAL,
                        Position.of(Instant.parse(stopTime)),
                        null,
                        null,
                        null,
                        null,
                        null,
                        new AltitudeGainLoss(0, 0))
        ), TestDataUtil.getTrackPoints(contentProviderUtils, trackId));
    }

    @MediumTest
    @Test
    public void testRecording_gpsOnly_segment() {
        // given
        String startTime = "2020-02-02T02:02:02Z";
        TrackPointCreator trackPointCreator = service.getTrackPointCreator();
        trackPointCreator.setClock(startTime);
        Track.Id trackId = service.startNewTrack();
        mockAltitudeChange(trackPointCreator, 0);

        // when
        String gps1 = "2020-02-02T02:02:03Z";
        sendGPSLocation(trackPointCreator, gps1, 45.0, 35.0, 1, 15);

        // then
        assertEquals(new Statistics(Instant.parse(startTime), Instant.parse(gps1), Duration.ofSeconds(1), Duration.ofSeconds(1), Distance.ZERO, Speed.of(15), null, new AltitudeGainLoss(0, 0), null, null)
                , contentProviderUtils.getTrack(trackId).statistics());

        // when
        String gps2 = "2020-02-02T02:02:06Z";
        sendGPSLocation(trackPointCreator, gps2, 45.1, 35.0, 1, 15);

        // then
        assertEquals(new Statistics(Instant.parse(startTime), Instant.parse(gps2), Duration.ofSeconds(4), Duration.ofSeconds(4), Distance.of(11113.275390625), Speed.of(2778.31884765625f), null, new AltitudeGainLoss(0, 0), null, null)
                , contentProviderUtils.getTrack(trackId).statistics());

        // when
        String stopTime = "2020-02-02T02:02:12Z";
        trackPointCreator.setClock(stopTime);
        service.endCurrentTrack();

        // then
        assertEquals(new Statistics(Instant.parse(startTime), Instant.parse(stopTime), Duration.ofSeconds(10), Duration.ofSeconds(10), Distance.of(11113.275390625), Speed.of(1111.3275146484375), null, new AltitudeGainLoss(0, 0), null, null)
                , contentProviderUtils.getTrack(trackId).statistics());


        // then
        new TrackPointAssert().assertEquals(List.of(
                new TrackPoint(TrackPoint.Type.SEGMENT_START_MANUAL, Instant.parse(startTime)),
                new TrackPoint(
                        null,
                        TrackPoint.Type.TRACKPOINT,
                        new Position(
                                Instant.parse(gps1),
                                45d, 35d, Distance.of(1),
                                null, null,
                                null,
                                Speed.of(15)),
                        null,
                        null,
                        null,
                        null,
                        null,
                        new AltitudeGainLoss(0, 0)
                ),
                new TrackPoint(
                        null,
                        TrackPoint.Type.SEGMENT_START_AUTOMATIC,
                        new Position(
                                Instant.parse(gps2),
                                45.1, 35d, Distance.of(1),
                                null, null,
                                null,
                                Speed.of(15)),
                        null,
                        null,
                        null,
                        null,
                        null,
                        new AltitudeGainLoss(0, 0)
                ),
                new TrackPoint(
                        null,
                        TrackPoint.Type.SEGMENT_END_MANUAL,
                        Position.of(Instant.parse(stopTime)),
                        null,
                        null,
                        null,
                        null,
                        null,
                        new AltitudeGainLoss(0, 0))
        ), TestDataUtil.getTrackPoints(contentProviderUtils, trackId));
    }

    /**
     * Make sure that GPS-based TrackPoints are stored, if the distance to the previous GPS-based TrackPoint is greater than recordingDistanceInterval.
     */
    @MediumTest
    @Test
    public void testRecording_gpsAndSensor_gpsIdleMoving_sensorMoving() {
        // TODO Check TrackStatistics
        // given
        String startTime = "2020-02-02T02:02:02Z";
        TrackPointCreator trackPointCreator = service.getTrackPointCreator();
        trackPointCreator.setClock(startTime);
        Track.Id trackId = service.startNewTrack();

        AggregatorRunning aggregatorRunning = new AggregatorRunning("", "");
        SensorManager sensorManager = trackPointCreator.getSensorManager();
        sensorManager.setAggregator(aggregatorRunning);

        // when
        String sensor1 = "2020-02-02T02:02:03Z";
        trackPointCreator.setClock(sensor1);
        //Should be ignored
        aggregatorRunning.add(trackPointCreator.getNow(), new RunningSpeedAndCadenceBluetooth.Data(Speed.of(5), Cadence.of(1), Distance.ZERO));
        sensorManager.onChange();

        // when
        String sensor2 = "2020-02-02T02:02:04Z";
        trackPointCreator.setClock(sensor2);
        aggregatorRunning.add(trackPointCreator.getNow(), new RunningSpeedAndCadenceBluetooth.Data(Speed.of(5), Cadence.of(2), Distance.of(2)));
        sensorManager.onChange();

        // when
        String gps1 = "2020-02-02T02:02:05Z";
        sendGPSLocation(trackPointCreator, gps1, 45.0, 35.0, 1, 15);

        // when
        String sensor3 = "2020-02-02T02:02:06Z";
        trackPointCreator.setClock(sensor3);
        aggregatorRunning.add(trackPointCreator.getNow(), new RunningSpeedAndCadenceBluetooth.Data(Speed.of(5), Cadence.of(3), Distance.of(12)));
        sensorManager.onChange();

        // when
        String sensor4 = "2020-02-02T02:02:07Z";
        trackPointCreator.setClock(sensor4);
        //Should be ignored
        aggregatorRunning.add(trackPointCreator.getNow(), new RunningSpeedAndCadenceBluetooth.Data(Speed.of(5), Cadence.of(4), Distance.of(14)));
        sensorManager.onChange();

        // when
        String gps2 = "2020-02-02T02:02:08Z";
        sendGPSLocation(trackPointCreator, gps2, 45.0, 35.0, 4, 15); //Should be ignored

        // when
        String sensor5 = "2020-02-02T02:02:10Z";
        trackPointCreator.setClock(sensor5);
        //Should be ignored
        aggregatorRunning.add(trackPointCreator.getNow(), new RunningSpeedAndCadenceBluetooth.Data(Speed.of(5), Cadence.of(5), Distance.of(16)));
        sensorManager.onChange();

        // when
        String gps3 = "2020-02-02T02:02:12Z";
        sendGPSLocation(trackPointCreator, gps3, 45.001, 35.0, 1, 15);

        // when
        String gps4 = "2020-02-02T02:02:14Z";
        sendGPSLocation(trackPointCreator, gps4, 45.001, 35.0, 1, 15);


        // when
        String stopTime = "2020-02-02T02:02:16Z";
        trackPointCreator.setClock(stopTime);
        service.endCurrentTrack();

        // then
        new TrackPointAssert().assertEquals(List.of(
                new TrackPoint(TrackPoint.Type.SEGMENT_START_MANUAL, Instant.parse(startTime)),
                new TrackPoint( //First moving TrackPoint: stored as the time might be interesting.
                        null,
                        TrackPoint.Type.TRACKPOINT,
                        new Position(
                                Instant.parse(sensor2),
                                null, null, null,
                                null, null,
                                null,
                                Speed.of(5)),
                        Distance.of(2),
                        null,
                        null,
                        Cadence.of(2),
                        null,
                        null
                ),
                new TrackPoint(
                        null,
                        TrackPoint.Type.TRACKPOINT,
                        new Position(
                                Instant.parse(gps1),
                                45d, 35d, Distance.of(1),
                                null, null,
                                null,
                                Speed.of(5)),
                        Distance.ZERO,
                        null,
                        null,
                        Cadence.of(2),
                        null,
                        null
                ),
                new TrackPoint(
                        null,
                        TrackPoint.Type.TRACKPOINT,
                        new Position(
                                Instant.parse(sensor3),
                                null, null, null,
                                null, null,
                                null,
                                Speed.of(5)),
                        Distance.of(10),
                        null,
                        null,
                        Cadence.of(3),
                        null,
                        null
                ),
                new TrackPoint(
                        null,
                        TrackPoint.Type.TRACKPOINT,
                        new Position(
                                Instant.parse(gps3),
                                45.001, 35d, Distance.of(1),
                                null, null,
                                null,
                                Speed.of(5)),
                        Distance.of(4.0),
                        null,
                        null,
                        Cadence.of(5),
                        null,
                        null
                ),
                new TrackPoint(
                        null,
                        TrackPoint.Type.TRACKPOINT,
                        new Position(
                                Instant.parse(gps4),
                                45.001, 35d, Distance.of(1),
                                null, null,
                                null,
                                Speed.of(5)),
                        Distance.ZERO,
                        null,
                        null,
                        Cadence.of(5),
                        null,
                        null
                ),
                new TrackPoint(
                        null,
                        TrackPoint.Type.SEGMENT_END_MANUAL,
                        new Position(
                                Instant.parse(stopTime),
                                45.001, 35d, Distance.of(1),
                                null, null,
                                null,
                                Speed.ZERO), //Sensor data is now outdated, but we do not fall back to GPS.
                        Distance.ZERO,
                        null,
                        null,
                        Cadence.of(0), //TODO This could be null, right?
                        null,
                        null
                )
        ), TestDataUtil.getTrackPoints(contentProviderUtils, trackId));
    }

    private void mockAltitudeChange(TrackPointCreator trackPointCreator, float altitudeGain) {
        trackPointCreator.getSensorManager().getAltitudeChangeHandler().setAggregator(new AggregatorBarometer("", "") {
            @Override
            public boolean hasReceivedData() {
                return true;
            }

            @NonNull
            @Override
            public AltitudeGainLoss getAggregatedValue(Instant now) {
                return new AltitudeGainLoss(altitudeGain, altitudeGain);
            }
        });
    }

    private static void sendGPSLocation(TrackPointCreator trackPointCreator, String time, double latitude, double longitude, float accuracy, long speed) {
        Location location = new Location("mock");
        location.setTime(1L); // Should be ignored anyhow.
        location.setLatitude(latitude);
        location.setLongitude(longitude);
        location.setAccuracy(accuracy);
        location.setSpeed(speed);

        trackPointCreator.setClock(time);
        trackPointCreator.getSensorManager().getGpsHandler().onDataReceived(location);
    }
}
