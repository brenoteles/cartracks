package de.dennisguse.opentracks.publicapi;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import de.dennisguse.opentracks.data.CustomContentProvider;

@RunWith(AndroidJUnit4.class)
public class DataProviderTest {

    private final ContentResolver resolver = ApplicationProvider.getApplicationContext().getContentResolver();

    @Test
    public void supportsVersion2_tracks() {
        // given
        Uri uri = Uri.parse(CustomContentProvider.CONTENT_BASE_URI + "/dashboard/tracks/1");
        String[] expectedTracksColumns = new String[]{
                "_id",
                "name",
                "description",
                "category",
                "activity_type_localized",
                "starttime",
                "stoptime",
                "totaltime",
                "movingtime",
                "totaldistance",
                "maxspeed",
                "avgspeed",
                "avgmovingspeed",
                "minelevation",
                "maxelevation",
                "elevationgain",
                "elevationloss"
        };

        // when
        try (Cursor cursor = resolver.query(
                uri,
                expectedTracksColumns,
                null,
                null,
                null
        )) {
            // then
            assertEquals("vnd.android.cursor.dir/vnd.de.dennisguse.track", resolver.getType(uri));

            assertNotNull(cursor);
            assertArrayEquals(expectedTracksColumns, cursor.getColumnNames());
        }
    }

    @Test
    public void supportsVersion2_trackpoints() {
        // given
        Uri uri = Uri.parse(CustomContentProvider.CONTENT_BASE_URI + "/dashboard/trackpoints/1");
        String[] expectedTrackpointsColumns = {
                "_id",
                "trackid",
                "latitude",
                "longitude",
                "time",
                "type",
                "speed"
        };

        // when
        try (Cursor cursor = resolver.query(
                uri,
                expectedTrackpointsColumns,
                null,
                null,
                null
        )) {
            // then
            assertEquals("vnd.android.cursor.dir/vnd.de.dennisguse.trackpoint", resolver.getType(uri));

            assertNotNull(cursor);
            assertArrayEquals(expectedTrackpointsColumns, cursor.getColumnNames());
        }
    }

    @Test
    public void supportsVersion2_markers() {
        // given
        Uri uri = Uri.parse(CustomContentProvider.CONTENT_BASE_URI + "/dashboard/markers/1");
        String[] expectedMarkersColumns = {
                "_id",
                "name",
                "description",
                "category",
                "trackid",
                "longitude",
                "latitude",
                "photoUrl",
                "icon"
        };

        // when
        try (Cursor cursor = resolver.query(
                uri,
                expectedMarkersColumns,
                null,
                null,
                null
        )) {
            // then
            assertEquals("vnd.android.cursor.dir/vnd.de.dennisguse.waypoint", resolver.getType(uri));

            assertNotNull(cursor);
            assertArrayEquals(expectedMarkersColumns, cursor.getColumnNames());
        }
    }

    @Test
    public void supportsVersion3_tracks() {
        // given
        Uri uri = Uri.parse(CustomContentProvider.CONTENT_BASE_URI + "/dashboard/tracks/1");
        String[] expectedTracksColumns = {
                "_id",
                "name",
                "description",
                "activity_type",
                "activity_type_localized",
                "time_start",
                "time_stop",
                "duration_total",
                "duration_moving",
                "distance",
                "speed_max",
                "altitude_min",
                "altitude_max",
                "altitude_gain",
                "altitude_loss"
        };

        // when
        try (Cursor cursor = resolver.query(
                uri,
                expectedTracksColumns,
                null,
                null,
                null
        )) {
            // then
            assertEquals("vnd.android.cursor.dir/vnd.de.dennisguse.track", resolver.getType(uri));

            assertNotNull(cursor);
            assertArrayEquals(expectedTracksColumns, cursor.getColumnNames());
        }
    }

    @Test
    public void supportsVersion3_trackpoints() {
        //given
        Uri uri = Uri.parse(CustomContentProvider.CONTENT_BASE_URI + "/dashboard/trackpoints/1");
        String[] expectedTrackpointsColumns = {
                "_id",
                "trackid",
                "latitude",
                "longitude",
                "time",
                "type",
                "speed"
        };

        // when
        try (Cursor cursor = resolver.query(
                uri,
                expectedTrackpointsColumns,
                null,
                null,
                null
        )) {
            //then
            assertEquals("vnd.android.cursor.dir/vnd.de.dennisguse.trackpoint", resolver.getType(uri));

            assertNotNull(cursor);
            assertArrayEquals(expectedTrackpointsColumns, cursor.getColumnNames());
        }
    }

    @Test
    public void supportsVersion3_markers() {
        // given
        Uri uri = Uri.parse(CustomContentProvider.CONTENT_BASE_URI + "/dashboard/markers/1");
        String[] expectedMarkersColumns = {
                "_id",
                "name",
                "description",
                "marker_type_localized",
                "trackid",
                "latitude",
                "longitude",
                "photoUrl"
        };

        // when
        try (Cursor cursor = resolver.query(
                uri,
                expectedMarkersColumns,
                null,
                null,
                null
        )) {
            // then
            assertEquals("vnd.android.cursor.dir/vnd.de.dennisguse.waypoint", resolver.getType(uri));

            assertNotNull(cursor);
            assertArrayEquals(expectedMarkersColumns, cursor.getColumnNames());
        }
    }
}
