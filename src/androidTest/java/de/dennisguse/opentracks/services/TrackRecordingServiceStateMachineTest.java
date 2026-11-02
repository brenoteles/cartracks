/*
 * Copyright 2010 Google Inc.
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
package de.dennisguse.opentracks.services;

import static de.dennisguse.opentracks.util.LiveDataTestUtils.waitForValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import android.os.Looper;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.rule.GrantPermissionRule;
import androidx.test.rule.ServiceTestRule;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import de.dennisguse.opentracks.TestUtil;
import de.dennisguse.opentracks.data.ContentProviderUtils;
import de.dennisguse.opentracks.data.models.Track;
import de.dennisguse.opentracks.sensors.GpsStatusValue;

/**
 * Testing the states of TrackRecordingService.
 */
@RunWith(AndroidJUnit4.class)
public class TrackRecordingServiceStateMachineTest {

    private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(2);

    @Rule
    public final ServiceTestRule mServiceRule = ServiceTestRule.withTimeout(5, TimeUnit.SECONDS);

    @Rule
    public GrantPermissionRule mRuntimePermissionRule = TestUtil.createGrantPermissionRule();

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
        service = startService();
        tearDown();
    }

    @After
    public void tearDown() {
        // Ensure that the database is empty after every test
        contentProviderUtils.deleteAllTracks(context);
    }

    @MediumTest
    @Test
    public void initialState() {
        // given
        List<Track> tracks = contentProviderUtils.getTracks();
        assertTrue(tracks.isEmpty());

        // when
        // noop

        // then
        assertFalse(service.isRecording());
        assertEquals(TrackRecordingService.STATUS_DEFAULT, service.getRecordingStatusObservable().getValue());
        assertEquals(RecordingData.NOT_RECORDING, service.getRecordingDataObservable().getValue());
        assertEquals(GpsStatusValue.GPS_NONE, service.getGpsStatusObservable().getValue());
    }

    @MediumTest
    @Test
    public void gps_startStop() {
        // given
        assertEquals(GpsStatusValue.GPS_NONE, service.getGpsStatusObservable().getValue());

        // when
        service.tryStartSensors();
        waitUntilGpsStatusIs(GpsStatusValue.GPS_ENABLED);

        // then
        assertEquals(TrackRecordingService.STATUS_DEFAULT, service.getRecordingStatusObservable().getValue());
        assertEquals(RecordingData.NOT_RECORDING, service.getRecordingDataObservable().getValue());
        assertEquals(GpsStatusValue.GPS_ENABLED, service.getGpsStatusObservable().getValue());

        // when
        service.stopSensors();
        waitUntilGpsStatusIs(GpsStatusValue.GPS_NONE);

        // then
        assertEquals(TrackRecordingService.STATUS_DEFAULT, service.getRecordingStatusObservable().getValue());
        assertEquals(RecordingData.NOT_RECORDING, service.getRecordingDataObservable().getValue());
        assertEquals(GpsStatusValue.GPS_NONE, service.getGpsStatusObservable().getValue());
    }

    @MediumTest
    @Test
    public void recording_startStopResume_no_data() {
        // given
        assertFalse(service.isRecording());

        // when
        Track.Id trackId = service.startNewTrack();
        waitUntilRecordingStatusIs(new RecordingStatus(trackId));
        waitUntilRecordingDataIsNotDefault();
        waitUntilGpsStatusIs(GpsStatusValue.GPS_ENABLED);

        // then
        assertTrue(service.isRecording());
        assertEquals(new RecordingStatus(trackId), service.getRecordingStatusObservable().getValue());
        assertNotEquals(RecordingData.NOT_RECORDING, service.getRecordingDataObservable().getValue());
        assertEquals(GpsStatusValue.GPS_ENABLED, service.getGpsStatusObservable().getValue());

        service.endCurrentTrack();
        waitUntilRecordingStatusIs(TrackRecordingService.STATUS_DEFAULT);
        waitUntilGpsStatusIs(GpsStatusValue.GPS_NONE);

        // when
        service.resumeTrack(trackId);
        waitUntilRecordingStatusIs(new RecordingStatus(trackId));
        waitUntilGpsStatusIs(GpsStatusValue.GPS_ENABLED);

        // then
        assertTrue(service.isRecording());
        assertEquals(new RecordingStatus(trackId), service.getRecordingStatusObservable().getValue());
        assertNotEquals(RecordingData.NOT_RECORDING, service.getRecordingDataObservable().getValue());
        assertEquals(GpsStatusValue.GPS_ENABLED, service.getGpsStatusObservable().getValue());


        // when
        service.endCurrentTrack();
        waitUntilRecordingStatusIs(TrackRecordingService.STATUS_DEFAULT);
        waitUntilGpsStatusIs(GpsStatusValue.GPS_NONE);

        // then
        assertFalse(service.isRecording());
        assertEquals(TrackRecordingService.STATUS_DEFAULT, service.getRecordingStatusObservable().getValue());
        assertNotEquals(RecordingData.NOT_RECORDING, service.getRecordingDataObservable().getValue());
        assertEquals(GpsStatusValue.GPS_NONE, service.getGpsStatusObservable().getValue());


        // when
        service.resumeTrack(trackId);
        waitUntilRecordingStatusIs(new RecordingStatus(trackId));
        waitUntilGpsStatusIs(GpsStatusValue.GPS_ENABLED);

        // then
        assertTrue(service.isRecording());
        assertEquals(new RecordingStatus(trackId), service.getRecordingStatusObservable().getValue());
        assertNotEquals(RecordingData.NOT_RECORDING, service.getRecordingDataObservable().getValue());
        assertEquals(GpsStatusValue.GPS_ENABLED, service.getGpsStatusObservable().getValue());
    }

    @MediumTest
    @Test
    public void cannotResume_non_existing_track() {
        // given
        assertFalse(service.isRecording());
        assertEquals(TrackRecordingService.STATUS_DEFAULT, service.getRecordingStatusObservable().getValue());
        assertEquals(RecordingData.NOT_RECORDING, service.getRecordingDataObservable().getValue());
        assertEquals(GpsStatusValue.GPS_NONE, service.getGpsStatusObservable().getValue());

        // when
        service.resumeTrack(new Track.Id(-1));

        // then
        assertFalse(service.isRecording());
        assertEquals(TrackRecordingService.STATUS_DEFAULT, service.getRecordingStatusObservable().getValue());
        assertEquals(RecordingData.NOT_RECORDING, service.getRecordingDataObservable().getValue());
        assertEquals(GpsStatusValue.GPS_NONE, service.getGpsStatusObservable().getValue());
    }

    @MediumTest
    @Test
    public void cannotEnd_without_starting() {
        // given
        assertFalse(service.isRecording());
        assertEquals(TrackRecordingService.STATUS_DEFAULT, service.getRecordingStatusObservable().getValue());
        assertEquals(RecordingData.NOT_RECORDING, service.getRecordingDataObservable().getValue());
        assertEquals(GpsStatusValue.GPS_NONE, service.getGpsStatusObservable().getValue());

        // when
        service.endCurrentTrack();

        // then
        assertEquals(TrackRecordingService.STATUS_DEFAULT, service.getRecordingStatusObservable().getValue());
        assertEquals(RecordingData.NOT_RECORDING, service.getRecordingDataObservable().getValue());
        assertEquals(GpsStatusValue.GPS_NONE, service.getGpsStatusObservable().getValue());
    }

    @Ignore("TODO Bug: GPS can be stopped although the current track is recording")
    @MediumTest
    @Test
    public void recording_stopGPS_noop() {
        // given
        Track.Id trackId = service.startNewTrack();
        assertEquals(GpsStatusValue.GPS_ENABLED, service.getGpsStatusObservable().getValue());

        // when
        service.stopSensors(); //TODO Should be ignored as service is recording

        // then
        assertTrue(service.isRecording());
        assertEquals(new RecordingStatus(trackId), service.getRecordingStatusObservable().getValue());
        assertNotEquals(RecordingData.NOT_RECORDING, service.getRecordingDataObservable().getValue());
        assertEquals(GpsStatusValue.GPS_ENABLED, service.getGpsStatusObservable().getValue());
    }

    @MediumTest
    @Test
    public void recording_startRecording_alreadyRecording() {
        // given
        Track.Id trackId = service.startNewTrack();
        assertTrue(service.isRecording());

        // when
        Track.Id newTrackId = service.startNewTrack();

        // then
        assertNotNull(trackId);
        assertNull(newTrackId);
    }

    private void waitUntilRecordingDataIsNotDefault() {
        waitForValue(service.getRecordingDataObservable(),
                recordingData -> !RecordingData.NOT_RECORDING.equals(recordingData),
                WAIT_TIMEOUT,
                "recording data to leave NOT_RECORDING");
    }

    private void waitUntilGpsStatusIs(GpsStatusValue expected) {
        waitForValue(service.getGpsStatusObservable(),
                expected::equals,
                WAIT_TIMEOUT,
                "gps status to become " + expected);
    }

    private void waitUntilRecordingStatusIs(RecordingStatus expected) {
        waitForValue(service.getRecordingStatusObservable(),
                expected::equals,
                WAIT_TIMEOUT,
                "recording status to become " + expected);
    }
}
