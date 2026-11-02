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
package de.dennisguse.opentracks.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.util.Pair;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import de.dennisguse.opentracks.content.data.TestDataUtil;
import de.dennisguse.opentracks.content.data.TestSensorDataUtil;
import de.dennisguse.opentracks.data.models.ActivityType;
import de.dennisguse.opentracks.data.models.Altitude;
import de.dennisguse.opentracks.data.models.AltitudeExtremities;
import de.dennisguse.opentracks.data.models.AltitudeGainLoss;
import de.dennisguse.opentracks.data.models.Cadence;
import de.dennisguse.opentracks.data.models.Distance;
import de.dennisguse.opentracks.data.models.HeartRate;
import de.dennisguse.opentracks.data.models.Marker;
import de.dennisguse.opentracks.data.models.MarkerBuilder;
import de.dennisguse.opentracks.data.models.Position;
import de.dennisguse.opentracks.data.models.Power;
import de.dennisguse.opentracks.data.models.Speed;
import de.dennisguse.opentracks.data.models.Statistics;
import de.dennisguse.opentracks.data.models.Track;
import de.dennisguse.opentracks.data.models.TrackBuilder;
import de.dennisguse.opentracks.data.models.TrackPoint;
import de.dennisguse.opentracks.data.statistics.SensorStatistics;
import de.dennisguse.opentracks.data.tables.MarkerColumns;
import de.dennisguse.opentracks.data.tables.TrackPointsColumns;
import de.dennisguse.opentracks.data.tables.TracksColumns;
import de.dennisguse.opentracks.util.FileUtils;

/**
 * A unit test for {@link ContentProviderUtils}.
 *
 * @author Bartlomiej Niechwiej
 * @author Youtao Liu
 */
public class CustomContentProviderUtilsTest {

    private static final String NAME_PREFIX = "test name";
    private static final String MOCK_DESC = "Mock Next Marker Desc!";
    private static final String TEST_DESC = "Test Desc!";
    private static final String TEST_DESC_NEW = "Test Desc new!";
    private static final String TEST_NAME_NEW = "Test Name new!";

    private final Context context = ApplicationProvider.getApplicationContext();
    private ContentProviderUtils contentProviderUtils;

    @Before
    public void setUp() {
        contentProviderUtils = new ContentProviderUtils(context);
        contentProviderUtils.deleteAllTracks(context);
    }

    @Test
    public void testLocationIterator_noPoints() {
        assertEquals(0, testIterator(new Track.Id(1), 0).size());
    }

    @Test
    public void testLocationIterator_noAscending() {
        assertEquals(50, testIterator(new Track.Id(1), 50).size());
        assertEquals(50, testIterator(new Track.Id(2), 50).size());
    }

    @Test
    public void testLocationIterator_largeTrack() {
        assertEquals(20000 / 2, testIterator(new Track.Id(1), 20000 / 2).size());
    }

    private List<TrackPoint> testIterator(Track.Id trackId, int numPoints) {
        TrackPoint.Id lastPointId = initializeTrack(trackId, numPoints);
        List<TrackPoint> locations = new ArrayList<>(numPoints);
        try (TrackPointIterator it = contentProviderUtils.getTrackPointIterator(trackId, null)) {
            while (it.hasNext()) {
                TrackPoint trackPoint = it.next();
                assertNotNull(trackPoint);
                locations.add(trackPoint);
                // Make sure the IDs are returned in the right order.
                assertEquals(lastPointId.id() - numPoints + locations.size(), trackPoint.id().id());
            }
            assertEquals(numPoints, locations.size());
        }
        return locations;
    }

    private TrackPoint.Id initializeTrack(Track.Id id, int numPoints) {
        Track createdTrack = new Track(
                id,
                UUID.randomUUID(),
                "Test: " + id,
                "",
                "",
                ActivityType.UNKNOWN,
                ZoneOffset.UTC,
                Statistics.DEFAULT);

        contentProviderUtils.insertTrack(createdTrack);

        Track track = contentProviderUtils.getTrack(id);
        assertNotNull(track);

        List<TrackPoint> trackPoints = new ArrayList<>(numPoints);
        for (int i = 0; i < numPoints; ++i) {
            trackPoints.add(new TrackPoint(TrackPoint.Type.TRACKPOINT,
                    new Position(
                            Instant.ofEpochMilli(i),
                            37.0 + (double) i / 10000.0,
                            57.0 - (double) i / 10000.0,
                            Distance.of(i / 100.0f),
                            Altitude.WGS84.of(i * 2.5),
                            null,
                            null,
                            null)));
        }
        contentProviderUtils.bulkInsertTrackPoint(trackPoints, id);

        // Load all inserted trackPoints.
        TrackPoint.Id lastPointId = null;
        int counter = 0;
        try (TrackPointIterator it = contentProviderUtils.getTrackPointIterator(id, null)) {
            while (it.hasNext()) {
                TrackPoint trackPoint = it.next();
                lastPointId = trackPoint.id();
                counter++;
            }
        }

        assertTrue(numPoints == 0 || lastPointId.id() > 0);
        assertEquals(numPoints, counter);

        return lastPointId;
    }

    /**
     * Tests the method {@link ContentProviderUtils#createTrack(Cursor)}.
     */
    @Test
    public void testCreateTrack() {
        Track.Id trackId = new Track.Id(System.currentTimeMillis());
        String name = NAME_PREFIX + trackId.id();

        MatrixCursor cursor = new MatrixCursor(new String[]{
                TracksColumns._ID,
                TracksColumns.UUID,
                TracksColumns.NAME,
                TracksColumns.DESCRIPTION,
                TracksColumns.ACTIVITY_TYPE,
                TracksColumns.ACTIVITY_TYPE_LOCALIZED,
                TracksColumns.STARTTIME,
                TracksColumns.STARTTIME_OFFSET,
                TracksColumns.STOPTIME,
                TracksColumns.TOTALDISTANCE,
                TracksColumns.TOTALTIME,
                TracksColumns.MOVINGTIME,
                TracksColumns.MAXSPEED,
                TracksColumns.MIN_ALTITUDE,
                TracksColumns.MAX_ALTITUDE,
                TracksColumns.ALTITUDE_GAIN,
                TracksColumns.ALTITUDE_LOSS,
        });
        cursor.addRow(new Object[]{
                trackId.id(),
                UUIDUtils.toBytes(UUID.randomUUID()),
                name,
                "",
                ActivityType.UNKNOWN.getId(),
                "",
                1_000L,
                ZoneOffset.UTC.getTotalSeconds(),
                2_000L,
                123.45f,
                1_000L,
                500L,
                12.34f,
                null,
                null,
                null,
                null,
        });
        assertTrue(cursor.moveToFirst());

        Track track = ContentProviderUtils.createTrack(cursor);
        assertEquals(trackId, track.id());
        assertEquals(name, track.name());
    }

    private void assertCount(int trackCount, int trackPointCount, int markerCount) {
        ContentResolver contentResolver = context.getContentResolver();
        try (Cursor tracksCursor = contentResolver.query(CustomContentProvider.UrlType.TRACKS.getUri(), null, null, null, TracksColumns._ID)) {
            assertEquals(trackCount, tracksCursor.getCount());
        }
        try (Cursor tracksPointsCursor = contentResolver.query(CustomContentProvider.UrlType.TRACKPOINTS_BY_ID.getUri(), null, null, null, TrackPointsColumns._ID)) {
            assertEquals(trackPointCount, tracksPointsCursor.getCount());
        }
        try (Cursor markerCursor = contentResolver.query(CustomContentProvider.UrlType.MARKERS.getUri(), null, null, null, MarkerColumns._ID)) {
            assertEquals(markerCount, markerCursor.getCount());
        }
    }

    /**
     * Tests the method {@link ContentProviderUtils#deleteAllTracks(Context)}
     */
    @Test
    public void testDeleteAllTracks() {
        // Insert track, points and marker at first.
        Track.Id trackId = new Track.Id(System.currentTimeMillis());
        TestDataUtil.createTrackAndInsert(contentProviderUtils, trackId, 10);

        contentProviderUtils.insertMarker(new Marker(
                null,
                trackId,
                "",
                "",
                "",
                getLastValidTrackPoint(trackId).position(),
                null
        ));

        assertCount(1, 10, 1);

        // when
        contentProviderUtils.deleteAllTracks(context);

        // then
        assertCount(0, 0, 0);
    }

    /**
     * Tests the method {@link ContentProviderUtils#deleteAllTracks(Context)}
     */
    @Test
    public void testDeleteAllTracks_withMarkerAndPhoto() throws IOException {
        // Insert track, points and marker with photo at first.
        Track.Id trackId = new Track.Id(System.currentTimeMillis());
        TestDataUtil.createTrackAndInsert(contentProviderUtils, trackId, 10);

        TrackPoint trackPoint = getLastValidTrackPoint(trackId);
        Marker marker = TestDataUtil.createMarkerWithPhoto(context, trackId, trackPoint);
        contentProviderUtils.insertMarker(marker);

        assertCount(1, 10, 1);
        assertNotNull(marker.photoUrl());
        File dir = FileUtils.getPhotoDir(context, trackId);
        assertTrue(dir.isDirectory());
        assertEquals(1, dir.list().length);
        assertTrue(dir.exists());

        // when
        contentProviderUtils.deleteAllTracks(context);

        // then
        assertCount(0, 0, 0);
        assertFalse(dir.exists());
    }

    /**
     * Tests the method {@link ContentProviderUtils#deleteTrack(Context, Track.Id)}.
     */
    @Test
    public void testDeleteTrack() {
        // Insert three tracks, points of two tracks and way point of one track.
        long random = System.currentTimeMillis();
        Track.Id trackId1 = new Track.Id(random);
        Track.Id trackId2 = new Track.Id(random + 1);
        Track.Id trackId3 = new Track.Id(random + 2);

        TestDataUtil.createTrackAndInsert(contentProviderUtils, trackId1, 10);

        TestDataUtil.createTrackAndInsert(contentProviderUtils, trackId2, 10);
        TestDataUtil.createTrackAndInsert(contentProviderUtils, trackId3, 10);

        contentProviderUtils.insertMarker(new Marker(
                null,
                trackId1,
                "",
                "",
                "",
                getLastValidTrackPoint(trackId1).position(),
                null
        ));

        assertCount(3, 30, 1);

        // when
        contentProviderUtils.deleteTrack(context, trackId1);

        // then
        assertCount(2, 20, 0);
    }

    /**
     * Tests the method {@link ContentProviderUtils#deleteTrack(Context, Track.Id)}.
     */
    @Test
    public void testDeleteTrack_withMarkerPhoto() throws IOException {
        // Insert three tracks.
        long random = System.currentTimeMillis();
        Track.Id trackId1 = new Track.Id(random);
        Track.Id trackId2 = new Track.Id(random + 1);
        Track.Id trackId3 = new Track.Id(random + 2);

        TestDataUtil.createTrackAndInsert(contentProviderUtils, trackId1, 10);
        TestDataUtil.createTrackAndInsert(contentProviderUtils, trackId2, 10);
        TestDataUtil.createTrackAndInsert(contentProviderUtils, trackId3, 10);

        // Insert a marker in tracks trackId and trackId + 1.
        TrackPoint trackPoint1 = getLastValidTrackPoint(trackId1);
        Marker marker1 = TestDataUtil.createMarkerWithPhoto(context, trackId1, trackPoint1);
        contentProviderUtils.insertMarker(marker1);
        File dir1 = FileUtils.getPhotoDir(context, trackId1);

        TrackPoint trackPoint2 = getLastValidTrackPoint(trackId2);
        Marker marker2 = TestDataUtil.createMarkerWithPhoto(context, trackId2, trackPoint2);
        contentProviderUtils.insertMarker(marker2);
        File dir2 = FileUtils.getPhotoDir(context, trackId2);

        // Check.
        assertCount(3, 30, 2);
        assertNotNull(marker1.photoUrl());
        assertTrue(dir1.isDirectory());
        assertEquals(1, dir1.list().length);
        assertTrue(dir1.exists());
        assertTrue(dir2.isDirectory());
        assertEquals(1, dir2.list().length);
        assertTrue(dir2.exists());

        // when
        contentProviderUtils.deleteTrack(context, trackId1);

        // then
        assertCount(2, 20, 1);
        assertFalse(dir1.exists());
        assertTrue(dir2.exists());
    }

    /**
     * Tests the method {@link ContentProviderUtils#getTracks()}
     */
    @Test
    public void testGetAllTracks() {
        // given
        int initialTrackNumber = contentProviderUtils.getTracks().size();
        Track.Id trackId = new Track.Id(System.currentTimeMillis());
        contentProviderUtils.insertTrack(TestDataUtil.createTrack(trackId));

        // when
        List<Track> allTracks = contentProviderUtils.getTracks();

        // then
        assertEquals(initialTrackNumber + 1, allTracks.size());
        assertEquals(trackId, allTracks.get(allTracks.size() - 1).id());
    }

    /**
     * Tests the method {@link ContentProviderUtils#getTrack(Track.Id)}
     */
    @Test
    public void testGetTrack_by_id() {
        // given
        Track.Id trackId = new Track.Id(System.currentTimeMillis());
        contentProviderUtils.insertTrack(TestDataUtil.createTrack(trackId));

        // when / then
        assertNotNull(contentProviderUtils.getTrack(trackId));
    }

    /**
     * Tests the method {@link ContentProviderUtils#getTrack(Track.Id)}
     */
    @Test
    public void testGetTrack_by_uuid() {
        // given
        Track.Id trackId = new Track.Id(System.currentTimeMillis());
        Track track = TestDataUtil.createTrack(trackId);
        contentProviderUtils.insertTrack(track);

        // when / then
        assertNotNull(contentProviderUtils.getTrack(track.uuid()));
    }

    @Test
    public void testUpdateTrack() {
        // given
        Track.Id trackId = new Track.Id(System.currentTimeMillis());
        TrackBuilder track = new TrackBuilder(TestDataUtil.createTrack(trackId));
        String nameOld = "name1";
        String nameNew = "name2";
        track.setName(nameOld);

        // when / then
        contentProviderUtils.insertTrack(track.getTrack());
        assertEquals(nameOld, contentProviderUtils.getTrack(trackId).name());
        track.setName(nameNew);
        contentProviderUtils.updateTrack(track.getTrack());
        assertEquals(nameNew, contentProviderUtils.getTrack(trackId).name());
    }

    @Test
    public void testCreateContentValues_marker() {
        Track.Id trackId = new Track.Id(System.currentTimeMillis());
        Pair<Track, List<TrackPoint>> track = TestDataUtil.createTrack(trackId, 10);

        TrackBuilder trackBuilder = new TrackBuilder(track.first);

        // AverageSpeed
        trackBuilder.setStatistics(new Statistics(
                Instant.ofEpochMilli(1000),
                Instant.ofEpochMilli(2500),
                Duration.ofMillis(1500),
                Duration.ofMillis(700),
                Distance.of(750),
                Speed.of(60),
                new AltitudeExtremities(1250, 1200),
                new AltitudeGainLoss(50, 50),
                null,
                null
        ));
        contentProviderUtils.insertTrack(trackBuilder.getTrack());

        MarkerBuilder marker = new MarkerBuilder(trackId, track.second.get(0));
        marker.setDescription(TEST_DESC);
        contentProviderUtils.insertMarker(marker.getMarker());

        Marker.Id markerId = new Marker.Id(System.currentTimeMillis());
        marker.setId(markerId);
        ContentValues contentValues = contentProviderUtils.createContentValues(marker.getMarker(), trackId);
        assertEquals(markerId.id(), contentValues.get(MarkerColumns._ID));
        assertEquals((int) (TestDataUtil.INITIAL_LONGITUDE * 1000000), contentValues.get(MarkerColumns.LONGITUDE));
        assertEquals(TEST_DESC, contentValues.get(MarkerColumns.DESCRIPTION));
    }

    /**
     * Tests the method
     * {@link ContentProviderUtils#deleteMarker(Context, Marker.Id)}
     * when there is only one marker in the track.
     */
    @Test
    public void testDeleteMarker_onlyOneMarker() {
        Track.Id trackId = new Track.Id(System.currentTimeMillis());
        TestDataUtil.createTrackAndInsert(contentProviderUtils, trackId, 10);

        // Insert at first.
        Marker.Id marker1Id = contentProviderUtils.insertMarker(new Marker(
                null,
                trackId,
                "",
                TEST_DESC,
                "",
                getLastValidTrackPoint(trackId).position(),
                null
        ));

        // Check insert was done.
        assertEquals(1, TestDataUtil.getMarkers(contentProviderUtils, trackId).size());

        // Delete
        contentProviderUtils.deleteMarker(context, marker1Id);

        assertNull(contentProviderUtils.getMarker(marker1Id));
    }

    /**
     * Tests the method
     * {@link ContentProviderUtils#deleteMarker(Context, Marker.Id)}
     * when there is only one marker in the track.
     */
    @Test
    public void testDeleteMarker_onlyOneMarkerWithPhotoUrl() throws IOException {
        Track.Id trackId = new Track.Id(System.currentTimeMillis());
        TestDataUtil.createTrackAndInsert(contentProviderUtils, trackId, 10);

        // Insert at first.
        TrackPoint trackPoint = getLastValidTrackPoint(trackId);
        Marker marker1 = TestDataUtil.createMarkerWithPhoto(context, trackId, trackPoint);
        contentProviderUtils.insertMarker(marker1);

        // Check insert was done.
        assertEquals(1, TestDataUtil.getMarkers(contentProviderUtils, trackId).size());

        // Get marker id that needs to delete.
        Marker.Id marker1Id = contentProviderUtils.insertMarker(marker1);

        // Check marker has photo and it's in the external storage.
        assertNotNull(marker1.photoUrl());
        File dir = FileUtils.getPhotoDir(context, trackId);
        assertTrue(dir.isDirectory());
        assertEquals(1, dir.list().length);
        assertTrue(dir.exists());

        // Delete
        contentProviderUtils.deleteMarker(context, marker1Id);

        // Check marker doesn't exists and photo folder was deleted.
        assertNull(contentProviderUtils.getMarker(marker1Id));
        assertFalse(dir.exists());
    }

    /**
     * Tests the method {@link ContentProviderUtils#deleteMarker(Context, Marker.Id)} when there is more than one marker in the track.
     */
    @Test
    public void testDeleteMarker_hasNextMarker() {
        Track.Id trackId = new Track.Id(System.currentTimeMillis());
        TestDataUtil.createTrackAndInsert(contentProviderUtils, trackId, 10);

//        Track track = TestDataUtil.createTrackAndInsert(trackId, 10);
//
//        TrackStatistics statistics = new TrackStatistics();
//        statistics.setStartTime_ms(1000L);
//        statistics.setStopTime_ms(2500L);
//        statistics.setTotalTime(1500L);
//        statistics.setMovingTime(700L);
//        statistics.setTotalDistance(750.0);
//        statistics.setTotalAltitudeGain(50.0);
//        statistics.setMaxSpeed(60.0);
//        statistics.setMaxAltitude(1250.0);
//        statistics.setMinAltitude(1200.0);
//
//        track.setTrackStatistics(statistics);
//        TestDataUtil.insertTrackWithLocations(contentProviderUtils, track);

        // Insert at first.
        Marker.Id marker1Id = contentProviderUtils.insertMarker(new Marker(
                null,
                trackId,
                "",
                MOCK_DESC,
                "",
                getLastValidTrackPoint(trackId).position(),
                null
        ));
        Marker.Id marker2Id = contentProviderUtils.insertMarker(new Marker(
                null,
                trackId,
                "",
                MOCK_DESC,
                "",
                getLastValidTrackPoint(trackId).position(),
                null
        ));

        // Delete
        assertNotNull(contentProviderUtils.getMarker(marker1Id));
        contentProviderUtils.deleteMarker(context, marker1Id);
        assertNull(contentProviderUtils.getMarker(marker1Id));

        assertEquals(MOCK_DESC, contentProviderUtils.getMarker(marker2Id).description());
    }

    /**
     * Tests the method {@link ContentProviderUtils#getNextMarkerNumber(Track.Id)}.
     */
    @Test
    public void testGetNextMarkerNumber() {
        Track.Id trackId = new Track.Id(System.currentTimeMillis());
        TestDataUtil.createTrackAndInsert(contentProviderUtils, trackId, 10);

        for (int i = 0; i < 4; i++) {
            contentProviderUtils.insertMarker(new Marker(
                    null,
                    trackId,
                    "",
                    MOCK_DESC,
                    "",
                    getLastValidTrackPoint(trackId).position(),
                    null
            ));
        }

        assertEquals(Integer.valueOf(4), contentProviderUtils.getNextMarkerNumber(trackId));
    }

    /**
     * Tests the method {@link ContentProviderUtils#insertMarker(Marker)} and
     * {@link ContentProviderUtils#getMarker(Marker.Id)}.
     */
    @Test
    public void testInsertAndGetMarker() {
        Track.Id trackId = new Track.Id(System.currentTimeMillis());
        TestDataUtil.createTrackAndInsert(contentProviderUtils, trackId, 10);

        Marker.Id markerId = contentProviderUtils.insertMarker(
                new Marker(
                        null,
                        trackId,
                        "",
                        TEST_DESC,
                        "",
                        getLastValidTrackPoint(trackId).position(),
                        null
                ));

        assertEquals(TEST_DESC, contentProviderUtils.getMarker(markerId).description());
    }

    /**
     * Tests the method {@link ContentProviderUtils#updateMarker(Context, Marker)}.
     */
    @Test
    public void testUpdateMarker() {
        Track.Id trackId = new Track.Id(System.currentTimeMillis());
        TestDataUtil.createTrackAndInsert(contentProviderUtils, trackId, 10);

        // Insert at first.
        Marker.Id markerId = contentProviderUtils.insertMarker(
                new Marker(
                        null,
                        trackId,
                        "",
                        TEST_DESC,
                        "",
                        getLastValidTrackPoint(trackId).position(),
                        null
                ));

        // Update
        contentProviderUtils.updateMarker(context, new Marker(
                markerId,
                trackId,
                "",
                TEST_DESC_NEW,
                "",
                getLastValidTrackPoint(trackId).position(),
                null
        ));

        assertEquals(TEST_DESC_NEW, contentProviderUtils.getMarker(markerId).description());
    }

    /**
     * Tests the method {@link ContentProviderUtils#updateMarker(Context, Marker)}.
     */
    @Test
    public void testUpdateMarker_withPhoto() throws IOException {
        // tests after update marker with photo the photo remains in the storage.

        Track.Id trackId = new Track.Id(System.currentTimeMillis());
        TestDataUtil.createTrackAndInsert(contentProviderUtils, trackId, 10);

        // Insert at first.
        TrackPoint trackPoint = getLastValidTrackPoint(trackId);
        Marker.Id markerId = contentProviderUtils.insertMarker(TestDataUtil.createMarkerWithPhoto(context, trackId, trackPoint, TEST_DESC));

        File dir = new File(FileUtils.getPhotoDir(context), "" + trackId.id());
        assertTrue(dir.exists());
        assertTrue(dir.isDirectory());
        assertEquals(1, dir.list().length);

        // Update
        MarkerBuilder marker = new MarkerBuilder(contentProviderUtils.getMarker(markerId));
        marker.setName(TEST_NAME_NEW);
        marker.setDescription(TEST_DESC_NEW);
        contentProviderUtils.updateMarker(context, marker.getMarker());

        assertEquals(TEST_NAME_NEW, contentProviderUtils.getMarker(markerId).name());
        assertEquals(TEST_DESC_NEW, contentProviderUtils.getMarker(markerId).description());
        assertTrue(dir.exists());
        assertTrue(dir.isDirectory());
        assertEquals(1, dir.list().length);
    }

    /**
     * Tests the method {@link ContentProviderUtils#updateMarker(Context, Marker)}.
     */
    @Test
    public void testUpdateMarker_delPhotoAndDir() throws IOException {
        // tests after update marker if user deletes the photo then file photo is deleted from the storage. Also empty directory is deleted.

        Track.Id trackId = new Track.Id(System.currentTimeMillis());
        TestDataUtil.createTrackAndInsert(contentProviderUtils, trackId, 10);

        // Insert at first.
        TrackPoint trackPoint = getLastValidTrackPoint(trackId);
        Marker marker = TestDataUtil.createMarkerWithPhoto(context, trackId, trackPoint, TEST_DESC);
        Marker.Id markerId = contentProviderUtils.insertMarker(marker);

        File dir = new File(FileUtils.getPhotoDir(context), "" + trackId.id());
        assertTrue(dir.exists());
        assertTrue(dir.isDirectory());
        assertEquals(1, dir.list().length);

        // Update
        MarkerBuilder markerBuilder = new MarkerBuilder(contentProviderUtils.getMarker(markerId));
        markerBuilder.setName(TEST_NAME_NEW);
        markerBuilder.setDescription(TEST_DESC_NEW);
        markerBuilder.setPhotoUrl(null);
        contentProviderUtils.updateMarker(context, markerBuilder.getMarker());

        assertEquals(TEST_NAME_NEW, contentProviderUtils.getMarker(markerId).name());
        assertEquals(TEST_DESC_NEW, contentProviderUtils.getMarker(markerId).description());
        assertFalse(dir.exists());
    }

    /**
     * Tests the method {@link ContentProviderUtils#updateMarker(Context, Marker)}.
     */
    @Test
    public void testUpdateMarker_delPhotoNotDir() throws IOException {
        // tests after update marker if user deletes the photo then file photo is deleted from the storage. Directory remains if there are more photos from other markers.

        Track.Id trackId = new Track.Id(System.currentTimeMillis());
        TestDataUtil.createTrackAndInsert(contentProviderUtils, trackId, 10);

        // Insert two markers with photos.
        TrackPoint trackPoint = getLastValidTrackPoint(trackId);
        Marker marker = TestDataUtil.createMarkerWithPhoto(context, trackId, trackPoint, TEST_DESC);
        Marker otherMarker = TestDataUtil.createMarkerWithPhoto(context, trackId, trackPoint, TEST_DESC);
        Marker.Id markerId = contentProviderUtils.insertMarker(marker);
        contentProviderUtils.insertMarker(otherMarker);

        File dir = new File(FileUtils.getPhotoDir(context), "" + trackId.id());
        assertTrue(dir.exists());
        assertTrue(dir.isDirectory());
        assertEquals(2, dir.list().length);

        // Update one marker deleting photo.
        MarkerBuilder markerBuilder = new MarkerBuilder(contentProviderUtils.getMarker(markerId));
        markerBuilder.setPhotoUrl(null);
        contentProviderUtils.updateMarker(context, markerBuilder.getMarker());

        // then
        marker = contentProviderUtils.getMarker(markerId);
        assertEquals(TEST_DESC, contentProviderUtils.getMarker(markerId).description());
        assertNull(marker.photoUrl());
        assertTrue(dir.exists());
        assertTrue(dir.isDirectory());
        assertEquals(1, dir.list().length);
    }

    /**
     * Tests the method {@link ContentProviderUtils#bulkInsertTrackPoint(List, Track.Id)}.
     */
    @Test
    public void testBulkInsertTrackPoint() {
        // given
        Track.Id trackId = new Track.Id(System.currentTimeMillis());
        Pair<Track, List<TrackPoint>> track = TestDataUtil.createTrack(trackId, 10);
        TestDataUtil.insertTrackWithLocations(contentProviderUtils, track.first, track.second);

        // when
        contentProviderUtils.bulkInsertTrackPoint(track.second, trackId);
        try (Cursor cursor = contentProviderUtils.getTrackPointCursor(trackId, null)) {
            // then
            assertEquals(20, cursor.getCount());
        }

        // when
        contentProviderUtils.bulkInsertTrackPoint(track.second.subList(0, 8), trackId);
        try (Cursor cursor = contentProviderUtils.getTrackPointCursor(trackId, null)) {
            // then
            assertEquals(28, cursor.getCount());
        }
    }

    /**
     * Tests the method
     * {@link ContentProviderUtils#insertTrackPoint(TrackPoint, Track.Id)}.
     */
    @Test
    public void testInsertTrackPoint() {
        // Insert track, point at first.
        Track.Id trackId = new Track.Id(System.currentTimeMillis());
        TestDataUtil.createTrackAndInsert(contentProviderUtils, trackId, 10);

        contentProviderUtils.insertTrackPoint(TestDataUtil.createTrackPoint(22), trackId);
        // when
        try (Cursor cursor = contentProviderUtils.getTrackPointCursor(trackId, null)) {
            // then
            assertEquals(11, cursor.getCount());
        }
    }

    @Test
    public void testInsertAndLoadTrackPoint() {
        // given
        Track.Id trackId = new Track.Id(System.currentTimeMillis());
        TestDataUtil.createTrackAndInsert(contentProviderUtils, trackId, 10);

        TrackPoint trackPoint = new TrackPoint(
                null,
                TrackPoint.Type.TRACKPOINT,
                TestDataUtil.createTrackPoint(5).position(),
                null,
                HeartRate.of(1),
                null,
                Cadence.of(2),
                Power.of(3),
                null
        );

        // when
        contentProviderUtils.insertTrackPoint(trackPoint, trackId);

        // then
        List<TrackPoint> trackPoints = TestDataUtil.getTrackPoints(contentProviderUtils, trackId);
        assertNotNull(trackPoints.get(10).heartRate());
        assertEquals(trackPoint.heartRate(), trackPoints.get(10).heartRate());
        assertEquals(trackPoint.cadence(), trackPoints.get(10).cadence());
        assertEquals(trackPoint.power(), trackPoints.get(10).power());
    }

    @Test
    public void testGetTrackPointCursor_asc() {
        // given
        Track.Id trackId = new Track.Id(System.currentTimeMillis());
        Pair<Track, List<TrackPoint>> track = TestDataUtil.createTrack(trackId, 10);
        contentProviderUtils.insertTrack(track.first);

        List<TrackPoint.Id> trackpointIds = track.second.stream()
                .map(it -> ContentUris.parseId(contentProviderUtils.insertTrackPoint(it, track.first.id())))
                .map(TrackPoint.Id::new).toList();

        // when
        try (Cursor cursor = contentProviderUtils.getTrackPointCursor(trackId, trackpointIds.get(8))) {
            // then
            assertEquals(2, cursor.getCount());
        }
    }

    @Test
    public void testGetTrackPointIterator_asc() {
        // given
        Track.Id trackId = new Track.Id(System.currentTimeMillis());
        Pair<Track, List<TrackPoint>> track = TestDataUtil.createTrack(trackId, 10);
        contentProviderUtils.insertTrack(track.first);

        List<TrackPoint.Id> trackpointIds = track.second.stream()
                .map(it -> ContentUris.parseId(contentProviderUtils.insertTrackPoint(it, track.first.id())))
                .map(TrackPoint.Id::new).toList();

        TrackPoint.Id startTrackPointId = trackpointIds.get(0);

        // when
        TrackPointIterator trackPointIterator = contentProviderUtils.getTrackPointIterator(trackId, trackpointIds.get(0));

        // then
        for (int i = 0; i < trackpointIds.size(); i++) {
            assertTrue(trackPointIterator.hasNext());
            TrackPoint trackPoint = trackPointIterator.next();
            assertEquals(startTrackPointId.id() + i, trackPoint.id().id());

            Position position = trackPoint.position();
            assertEquals(TestDataUtil.INITIAL_LATITUDE + i / 10000.0, position.latitude(), 0.01);
            assertEquals(TestDataUtil.INITIAL_LONGITUDE - i / 10000.0, position.longitude(), 0.01);
            assertEquals(i / 100.0, position.horizontalAccuracy().distance_m(), 0.01);
            assertEquals(i * TestDataUtil.ALTITUDE_INTERVAL, position.altitude().toM(), 0.01);
        }
        assertFalse(trackPointIterator.hasNext());
    }

    @Test
    public void testFormatIdListForUri() {
        assertEquals("", ContentProviderUtils.formatIdListForUri());
        assertEquals("12", ContentProviderUtils.formatIdListForUri(new Track.Id(12)));
        assertEquals("42,43,44", ContentProviderUtils.formatIdListForUri(new Track.Id(42), new Track.Id(43), new Track.Id(44)));
    }

    @Test
    public void testGetSensorStats_noSensorData() {
        // given
        List<TrackPoint> trackPointList = new ArrayList<>();
        TrackPoint trackPoint = new TrackPoint(
                null,
                TrackPoint.Type.TRACKPOINT,
                TestDataUtil.createTrackPoint(1).position(),
                null,
                null,
                null,
                null,
                null,
                null
        );

        trackPointList.add(trackPoint);
        Track.Id trackId = new Track.Id(System.currentTimeMillis());
        Track track = TestDataUtil.createTrack(trackId);
        TestDataUtil.insertTrackWithLocations(contentProviderUtils, track, trackPointList);

        // when
        SensorStatistics sensorStatistics = contentProviderUtils.getSensorStats(trackId);

        // then
        assertFalse(sensorStatistics.hasHeartRate());
        assertFalse(sensorStatistics.hasCadence());
        assertFalse(sensorStatistics.hasPower());
    }

    @Test
    public void testGetSensorStats_needAtLeastTwoTrackPointsFalse() {
        // given
        /*
         * time elapsed    hr      cadence     power       track type
         * 0               140     90           300           0
         */
        Instant start = Instant.now();
        TestSensorDataUtil sensorDataUtil = new TestSensorDataUtil();
        sensorDataUtil.add(start, 140f, 90f, 300f, TrackPoint.Type.TRACKPOINT);

        Track.Id trackId = new Track.Id(start.toEpochMilli());
        Track track = TestDataUtil.createTrack(trackId);
        TestDataUtil.insertTrackWithLocations(contentProviderUtils, track, sensorDataUtil.getTrackPointList());

        // when
        SensorStatistics sensorStatistics = contentProviderUtils.getSensorStats(trackId);

        // then
        assertFalse(sensorStatistics.hasHeartRate());
        assertFalse(sensorStatistics.hasCadence());
        assertFalse(sensorStatistics.hasPower());
    }

    @Test
    public void testGetSensorStats_needAtLeastTwoTrackPointsTrue() {
        // given
        /*
         * time elapsed    hr      cadence     power       track type
         * 0               140     90          300         -1
         * 1               140     90          300         1
         */
        Instant start = Instant.now();
        TestSensorDataUtil sensorDataUtil = new TestSensorDataUtil();
        sensorDataUtil.add(start, 140f, 90f, 300f, TrackPoint.Type.SEGMENT_START_AUTOMATIC);
        sensorDataUtil.add(start.plus(1, ChronoUnit.SECONDS), 140f, 90f, 300f, TrackPoint.Type.SEGMENT_END_MANUAL);

        Track.Id trackId = new Track.Id(start.toEpochMilli());
        Track track = TestDataUtil.createTrack(trackId);
        TestDataUtil.insertTrackWithLocations(contentProviderUtils, track, sensorDataUtil.getTrackPointList());

        // when
        SensorStatistics sensorStatistics = contentProviderUtils.getSensorStats(trackId);
        TestSensorDataUtil.SensorDataStats stats = sensorDataUtil.computeStats();

        // then
        assertTrue(sensorStatistics.hasHeartRate());
        assertEquals(sensorStatistics.avgHeartRate().getBPM(), stats.avgHr, 0f);
        assertEquals(sensorStatistics.maxHeartRate().getBPM(), stats.maxHr, 0f);
        assertTrue(sensorStatistics.hasCadence());
        assertEquals(sensorStatistics.avgCadence().getRPM(), stats.avgCadence, 0f);
        assertEquals(sensorStatistics.maxCadence().getRPM(), stats.maxCadence, 0f);
        assertTrue(sensorStatistics.hasPower());
        assertEquals(sensorStatistics.avgPower().getW(), stats.avgPower, 0f);
    }

    @Test
    public void testGetSensorStats_onlyHr() {
        // given
        /*
         * time elapsed    hr      cadence     power       track type
         * 0               140     NULL        NULL        -1
         * 1               140     NULL        NULL        1
         */
        Instant start = Instant.now();
        TestSensorDataUtil sensorDataUtil = new TestSensorDataUtil();
        sensorDataUtil.add(start, 140f, null, null, TrackPoint.Type.SEGMENT_START_AUTOMATIC);
        sensorDataUtil.add(start.plus(1, ChronoUnit.SECONDS), 140f, null, null, TrackPoint.Type.SEGMENT_END_MANUAL);

        Track.Id trackId = new Track.Id(start.toEpochMilli());
        Track track = TestDataUtil.createTrack(trackId);
        TestDataUtil.insertTrackWithLocations(contentProviderUtils, track, sensorDataUtil.getTrackPointList());

        // when
        SensorStatistics sensorStatistics = contentProviderUtils.getSensorStats(trackId);
        TestSensorDataUtil.SensorDataStats stats = sensorDataUtil.computeStats();

        // then
        assertTrue(sensorStatistics.hasHeartRate());
        assertEquals(sensorStatistics.avgHeartRate().getBPM(), stats.avgHr, 0f);
        assertEquals(sensorStatistics.maxHeartRate().getBPM(), stats.maxHr, 0f);
        assertFalse(sensorStatistics.hasCadence());
        assertFalse(sensorStatistics.hasPower());
    }

    @Test
    public void testGetSensorStats_onlyCadence() {
        // given
        /*
         * time elapsed    hr      cadence     power       track type
         * 0               NULL    90          NULL        -1
         * 1               NULL    90          NULL        1
         */
        Instant start = Instant.now();
        TestSensorDataUtil sensorDataUtil = new TestSensorDataUtil();
        sensorDataUtil.add(start, null, 90f, null, TrackPoint.Type.SEGMENT_START_AUTOMATIC);
        sensorDataUtil.add(start.plus(1, ChronoUnit.SECONDS), null, 90f, null, TrackPoint.Type.SEGMENT_END_MANUAL);

        Track.Id trackId = new Track.Id(start.toEpochMilli());
        Track track = TestDataUtil.createTrack(trackId);
        TestDataUtil.insertTrackWithLocations(contentProviderUtils, track, sensorDataUtil.getTrackPointList());

        // when
        SensorStatistics sensorStatistics = contentProviderUtils.getSensorStats(trackId);
        TestSensorDataUtil.SensorDataStats stats = sensorDataUtil.computeStats();

        // then
        assertFalse(sensorStatistics.hasHeartRate());
        assertTrue(sensorStatistics.hasCadence());
        assertEquals(sensorStatistics.avgCadence().getRPM(), stats.avgCadence, 0f);
        assertEquals(sensorStatistics.maxCadence().getRPM(), stats.maxCadence, 0f);
        assertFalse(sensorStatistics.hasPower());
    }

    @Test
    public void testGetSensorStats_onlyPower() {
        // given
        /*
         * time elapsed    hr      cadence     power       track type
         * 0               NULL    NULL        300         -1
         * 1               NULL    NULL        300         1
         */
        Instant start = Instant.now();
        TestSensorDataUtil sensorDataUtil = new TestSensorDataUtil();
        sensorDataUtil.add(start, null, null, 300f, TrackPoint.Type.SEGMENT_START_AUTOMATIC);
        sensorDataUtil.add(start.plus(1, ChronoUnit.SECONDS), null, null, 300f, TrackPoint.Type.SEGMENT_END_MANUAL);

        Track.Id trackId = new Track.Id(start.toEpochMilli());
        Track track = TestDataUtil.createTrack(trackId);
        TestDataUtil.insertTrackWithLocations(contentProviderUtils, track, sensorDataUtil.getTrackPointList());

        // when
        SensorStatistics sensorStatistics = contentProviderUtils.getSensorStats(trackId);
        TestSensorDataUtil.SensorDataStats stats = sensorDataUtil.computeStats();

        // then
        assertFalse(sensorStatistics.hasHeartRate());
        assertFalse(sensorStatistics.hasCadence());
        assertTrue(sensorStatistics.hasPower());
        assertEquals(sensorStatistics.avgPower().getW(), stats.avgPower, 0f);
    }

    @Test
    public void testGetSensorStats() {
        // given
        /*
         * time elapsed    hr      cadence     power       track type
         * 0               140     75          250         -1
         * 2               148     80          300         0
         * 1               150     82          325         0
         * 7               160     90          275         0
         * 4               155     85          280         0
         * 1               155     84          295         1
         */
        Instant start = Instant.now();
        TestSensorDataUtil sensorDataUtil = new TestSensorDataUtil();
        sensorDataUtil.add(start, 140f, 75f, 250f, TrackPoint.Type.SEGMENT_START_AUTOMATIC);
        sensorDataUtil.add(start.plus(2, ChronoUnit.SECONDS), 148f, 80f, 300f, TrackPoint.Type.TRACKPOINT);
        sensorDataUtil.add(start.plus(3, ChronoUnit.SECONDS), 150f, 82f, 325f, TrackPoint.Type.TRACKPOINT);
        sensorDataUtil.add(start.plus(10, ChronoUnit.SECONDS), 160f, 90f, 275f, TrackPoint.Type.TRACKPOINT);
        sensorDataUtil.add(start.plus(14, ChronoUnit.SECONDS), 155f, 85f, 280f, TrackPoint.Type.TRACKPOINT);
        sensorDataUtil.add(start.plus(15, ChronoUnit.SECONDS), 155f, 84f, 295f, TrackPoint.Type.SEGMENT_END_MANUAL);

        Track.Id trackId = new Track.Id(start.toEpochMilli());
        Track track = TestDataUtil.createTrack(trackId);
        TestDataUtil.insertTrackWithLocations(contentProviderUtils, track, sensorDataUtil.getTrackPointList());

        // when
        SensorStatistics sensorStatistics = contentProviderUtils.getSensorStats(trackId);
        TestSensorDataUtil.SensorDataStats stats = sensorDataUtil.computeStats();

        // then
        assertEquals(sensorStatistics.avgHeartRate().getBPM(), stats.avgHr, 0f);
        assertEquals(sensorStatistics.maxHeartRate().getBPM(), stats.maxHr, 0f);
        assertEquals(sensorStatistics.avgCadence().getRPM(), stats.avgCadence, 0f);
        assertEquals(sensorStatistics.maxCadence().getRPM(), stats.maxCadence, 0f);
        assertEquals(sensorStatistics.avgPower().getW(), stats.avgPower, 0f);
    }

    @Test
    public void testGetSensorStats_withManualResume() {
        // given
        /*
         * time elapsed    hr      cadence     power       track type
         * 0               140     75          250         -1
         * 2               148     80          300         0
         * 1               150     82          325         0
         * 3               174     88          400         0
         * 20              127     54          175         -2
         * 3               160     90          275         0
         * 7               155     85          280         0
         * 3               150     90          267         0
         * 3               170     90          240         0
         * 2               155     84          295         1
         */
        Instant start = Instant.now();
        TestSensorDataUtil sensorDataUtil = new TestSensorDataUtil();
        sensorDataUtil.add(start, 140f, 75f, 250f, TrackPoint.Type.SEGMENT_START_AUTOMATIC);
        sensorDataUtil.add(start.plus(2, ChronoUnit.SECONDS), 148f, 80f, 300f, TrackPoint.Type.TRACKPOINT);
        sensorDataUtil.add(start.plus(3, ChronoUnit.SECONDS), 150f, 82f, 325f, TrackPoint.Type.TRACKPOINT);
        sensorDataUtil.add(start.plus(6, ChronoUnit.SECONDS), 174f, 88f, 400f, TrackPoint.Type.TRACKPOINT);
        sensorDataUtil.add(start.plus(26, ChronoUnit.SECONDS), 127f, 54f, 175f, TrackPoint.Type.SEGMENT_START_MANUAL);
        sensorDataUtil.add(start.plus(29, ChronoUnit.SECONDS), 160f, 90f, 275f, TrackPoint.Type.TRACKPOINT);
        sensorDataUtil.add(start.plus(36, ChronoUnit.SECONDS), 155f, 85f, 280f, TrackPoint.Type.TRACKPOINT);
        sensorDataUtil.add(start.plus(39, ChronoUnit.SECONDS), 150f, 90f, 267f, TrackPoint.Type.TRACKPOINT);
        sensorDataUtil.add(start.plus(42, ChronoUnit.SECONDS), 170f, 90f, 240f, TrackPoint.Type.TRACKPOINT);
        sensorDataUtil.add(start.plus(44, ChronoUnit.SECONDS), 155f, 84f, 295f, TrackPoint.Type.SEGMENT_END_MANUAL);

        Track.Id trackId = new Track.Id(start.toEpochMilli());
        Track track = TestDataUtil.createTrack(trackId);
        TestDataUtil.insertTrackWithLocations(contentProviderUtils, track, sensorDataUtil.getTrackPointList());

        // when
        SensorStatistics sensorStatistics = contentProviderUtils.getSensorStats(trackId);
        TestSensorDataUtil.SensorDataStats stats = sensorDataUtil.computeStats();

        // then
        assertEquals(sensorStatistics.avgHeartRate().getBPM(), stats.avgHr, 0f);
        assertEquals(sensorStatistics.maxHeartRate().getBPM(), stats.maxHr, 0f);
        assertEquals(sensorStatistics.avgCadence().getRPM(), stats.avgCadence, 0f);
        assertEquals(sensorStatistics.maxCadence().getRPM(), stats.maxCadence, 0f);
        assertEquals(sensorStatistics.avgPower().getW(), stats.avgPower, 0f);
    }

    @Test
    public void testGetSensorStats_withStartAutomatic() {
        // given
        /*
         * time elapsed    hr      cadence     power       track type
         * 0               140     75          250         -1
         * 2               148     80          300         0
         * 1               150     82          325         0
         * 3               174     88          400         0
         * 20              127     54          175         -1
         * 3               160     90          275         0
         * 7               155     85          280         0
         * 3               150     90          267         0
         * 3               170     90          240         0
         * 2               155     84          295         1
         */
        Instant start = Instant.now();
        TestSensorDataUtil sensorDataUtil = new TestSensorDataUtil();
        sensorDataUtil.add(start, 140f, 75f, 250f, TrackPoint.Type.SEGMENT_START_AUTOMATIC);
        sensorDataUtil.add(start.plus(2, ChronoUnit.SECONDS), 148f, 80f, 300f, TrackPoint.Type.TRACKPOINT);
        sensorDataUtil.add(start.plus(3, ChronoUnit.SECONDS), 150f, 82f, 325f, TrackPoint.Type.TRACKPOINT);
        sensorDataUtil.add(start.plus(6, ChronoUnit.SECONDS), 174f, 88f, 400f, TrackPoint.Type.TRACKPOINT);
        sensorDataUtil.add(start.plus(26, ChronoUnit.SECONDS), 127f, 54f, 175f, TrackPoint.Type.SEGMENT_START_AUTOMATIC);
        sensorDataUtil.add(start.plus(29, ChronoUnit.SECONDS), 160f, 90f, 275f, TrackPoint.Type.TRACKPOINT);
        sensorDataUtil.add(start.plus(36, ChronoUnit.SECONDS), 155f, 85f, 280f, TrackPoint.Type.TRACKPOINT);
        sensorDataUtil.add(start.plus(39, ChronoUnit.SECONDS), 150f, 90f, 267f, TrackPoint.Type.TRACKPOINT);
        sensorDataUtil.add(start.plus(42, ChronoUnit.SECONDS), 170f, 90f, 240f, TrackPoint.Type.TRACKPOINT);
        sensorDataUtil.add(start.plus(44, ChronoUnit.SECONDS), 155f, 84f, 295f, TrackPoint.Type.SEGMENT_END_MANUAL);

        Track.Id trackId = new Track.Id(start.toEpochMilli());
        Track track = TestDataUtil.createTrack(trackId);
        TestDataUtil.insertTrackWithLocations(contentProviderUtils, track, sensorDataUtil.getTrackPointList());

        // when
        SensorStatistics sensorStatistics = contentProviderUtils.getSensorStats(trackId);
        TestSensorDataUtil.SensorDataStats stats = sensorDataUtil.computeStats();

        // then
        assertEquals(sensorStatistics.avgHeartRate().getBPM(), stats.avgHr, 0f);
        assertEquals(sensorStatistics.maxHeartRate().getBPM(), stats.maxHr, 0f);
        assertEquals(sensorStatistics.avgCadence().getRPM(), stats.avgCadence, 0f);
        assertEquals(sensorStatistics.maxCadence().getRPM(), stats.maxCadence, 0f);
        assertEquals(sensorStatistics.avgPower().getW(), stats.avgPower, 0f);
    }

    private Pair<SensorStatistics, TestSensorDataUtil.SensorDataStats> testGetSensorStats_randomData(int totalPoints, boolean withStartSegments) {
        // given
        Instant start = Instant.now();
        TestSensorDataUtil sensorDataUtil = new TestSensorDataUtil();
        Random random = new Random();
        for (int i = 0; i < totalPoints; i++) {
            int randomNum = withStartSegments ? random.nextInt(50) - 2 : 0;
            TrackPoint.Type type = randomNum >= 0 ? TrackPoint.Type.TRACKPOINT : TrackPoint.Type.getById(randomNum);
            float randomHr = random.nextFloat() * (200f - 90f) + 90f;
            float randomCadence = random.nextFloat() * (110f - 40f) + 40f;
            float randomPower = random.nextFloat() * (500f - 100f) + 100f;
            sensorDataUtil.add(start.plus(i, ChronoUnit.SECONDS), randomHr, randomCadence, randomPower, type);
        }
        sensorDataUtil.add(start.plus(totalPoints, ChronoUnit.SECONDS), 155f, 84f, 295f, TrackPoint.Type.SEGMENT_END_MANUAL);

        Track.Id trackId = new Track.Id(start.toEpochMilli());
        Track track = TestDataUtil.createTrack(trackId);
        TestDataUtil.insertTrackWithLocations(contentProviderUtils, track, sensorDataUtil.getTrackPointList());

        // when
        SensorStatistics sensorStatistics = contentProviderUtils.getSensorStats(trackId);
        TestSensorDataUtil.SensorDataStats stats = sensorDataUtil.computeStats();

        return Pair.create(sensorStatistics, stats);
    }

    @Test
    public void testGetSensorStats_veryLongActivity12h() {
        Pair<SensorStatistics, TestSensorDataUtil.SensorDataStats> result = testGetSensorStats_randomData(43200 / 6, false);
        assertEquals(result.first.avgHeartRate().getBPM(), result.second.avgHr, 0.01f);
        assertEquals(result.first.maxHeartRate().getBPM(), result.second.maxHr, 0.01f);
        assertEquals(result.first.avgCadence().getRPM(), result.second.avgCadence, 0.01f);
        assertEquals(result.first.maxCadence().getRPM(), result.second.maxCadence, 0.01f);
        assertEquals(result.first.avgPower().getW(), result.second.avgPower, 0.01f);
    }

    @Test
    public void testGetSensorStats_withSeveralRandomStartSegments() {
        Pair<SensorStatistics, TestSensorDataUtil.SensorDataStats> result = testGetSensorStats_randomData(5000, true);
        assertEquals(result.first.avgHeartRate().getBPM(), result.second.avgHr, 0.01f);
        assertEquals(result.first.maxHeartRate().getBPM(), result.second.maxHr, 0.01f);
        assertEquals(result.first.avgCadence().getRPM(), result.second.avgCadence, 0.01f);
        assertEquals(result.first.maxCadence().getRPM(), result.second.maxCadence, 0.01f);
        assertEquals(result.first.avgPower().getW(), result.second.avgPower, 0.01f);
    }

    private TrackPoint getLastValidTrackPoint(Track.Id trackId) {
        try (TrackPointIterator trackPointsCursor = contentProviderUtils.getTrackPointIterator(trackId, null)) {
            List<TrackPoint> trackpoints = new ArrayList<>();
            while (trackPointsCursor.hasNext())
                trackpoints.add(trackPointsCursor.next());
            trackpoints = trackpoints.reversed();

            return trackpoints.reversed()
                    .stream()
                    .filter(it -> List.of(TrackPoint.Type.TRACKPOINT, TrackPoint.Type.SEGMENT_START_AUTOMATIC).contains(it.type()))
                    .findFirst().orElseThrow();

        }
    }
}
