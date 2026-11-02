/*
 * Copyright 2008 Google Inc.
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

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.Build;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.Arrays;

import de.dennisguse.opentracks.BuildConfig;
import de.dennisguse.opentracks.data.models.TrackPoint;
import de.dennisguse.opentracks.data.tables.MarkerColumns;
import de.dennisguse.opentracks.data.tables.TrackPointsColumns;
import de.dennisguse.opentracks.data.tables.TracksColumns;
import de.dennisguse.opentracks.publicapi.DataProvider;
import de.dennisguse.opentracks.settings.PreferencesUtils;

/**
 * A {@link ContentProvider} that handles access to track points, tracks, and markers tables.
 * <p>
 * Data consistency is enforced using Foreign Key Constraints within the database incl. cascading deletes.
 *
 * @author Leif Hendrik Wilden
 */
public class CustomContentProvider extends ContentProvider {

    // The authority (the first part of the URI) for the app's content provider.
    static final String AUTHORITY_PACKAGE = BuildConfig.APPLICATION_ID + ".content";

    // The base URI for the app's content provider.
    public static final String CONTENT_BASE_URI = "content://" + AUTHORITY_PACKAGE;
    private static final String TAG = CustomContentProvider.class.getSimpleName();

    private static final String SQL_LIST_DELIMITER = ",";

    private static final int TOTAL_DELETED_ROWS_VACUUM_THRESHOLD = 10000;

    private final UriMatcher uriMatcher;

    private SQLiteDatabase db;

    /**
     * The string representing the query that compute sensor stats from trackpoints table.
     * It computes the average for heart rate, cadence and power (duration-based average) and the maximum for heart rate, cadence and power.
     * Finally, it ignores manual pause (SEGMENT_START_MANUAL).
     */
    private final String SENSOR_STATS_QUERY =
            "WITH time_select AS " +
                    "(SELECT t1." + TrackPointsColumns.TIME + " * (t1." + TrackPointsColumns.TYPE + " NOT IN (" + TrackPoint.Type.SEGMENT_START_MANUAL.type_db + ")) time_value " +
                    "FROM " + TrackPointsColumns.TABLE_NAME + " t1 " +
                    "WHERE t1." + TrackPointsColumns._ID + " > t." + TrackPointsColumns._ID + " AND t1." + TrackPointsColumns.TRACKID + " = ? ORDER BY _id LIMIT 1) " +

                    "SELECT " +
                    "SUM(t." + TrackPointsColumns.SENSOR_HEARTRATE + " * (COALESCE(MAX(t." + TrackPointsColumns.TIME + ", (SELECT time_value FROM time_select)), t." + TrackPointsColumns.TIME + ") - t." + TrackPointsColumns.TIME + ")) " +
                    "/ " +
                    "SUM(COALESCE(MAX(t." + TrackPointsColumns.TIME + ", (SELECT time_value FROM time_select)), t." + TrackPointsColumns.TIME + ") - t." + TrackPointsColumns.TIME + ") " + TrackPointsColumns.ALIAS_AVG_HR + ", " +

                    "MAX(t." + TrackPointsColumns.SENSOR_HEARTRATE + ") " + TrackPointsColumns.ALIAS_MAX_HR + ", " +

                    "SUM(t." + TrackPointsColumns.SENSOR_CADENCE + " * (COALESCE(MAX(t." + TrackPointsColumns.TIME + ", (SELECT time_value FROM time_select)), t." + TrackPointsColumns.TIME + ") - t." + TrackPointsColumns.TIME + ")) " +
                    "/ " +
                    "SUM(COALESCE(MAX(t." + TrackPointsColumns.TIME + ", (SELECT time_value FROM time_select)), t." + TrackPointsColumns.TIME + ") - t." + TrackPointsColumns.TIME + ") " + TrackPointsColumns.ALIAS_AVG_CADENCE + ", " +

                    "MAX(t." + TrackPointsColumns.SENSOR_CADENCE + ") " + TrackPointsColumns.ALIAS_MAX_CADENCE + ", " +

                    "SUM(t." + TrackPointsColumns.SENSOR_POWER + " * (COALESCE(MAX(t." + TrackPointsColumns.TIME + ", (SELECT time_value FROM time_select)), t." + TrackPointsColumns.TIME + ") - t." + TrackPointsColumns.TIME + ")) " +
                    "/ " +
                    "SUM(COALESCE(MAX(t." + TrackPointsColumns.TIME + ", (SELECT time_value FROM time_select)), t." + TrackPointsColumns.TIME + ") - t." + TrackPointsColumns.TIME + ") " + TrackPointsColumns.ALIAS_AVG_POWER + ", " +

                    "MAX(t." + TrackPointsColumns.SENSOR_POWER + ") " + TrackPointsColumns.ALIAS_MAX_POWER + " " +

                    "FROM " + TrackPointsColumns.TABLE_NAME + " t " +
                    "WHERE t." + TrackPointsColumns.TRACKID + " = ? " +
                    "AND t." + TrackPointsColumns.TYPE + " NOT IN (" + TrackPoint.Type.SEGMENT_START_MANUAL.type_db + ")";

    public CustomContentProvider() {
        uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        UrlType[] urlTypes = UrlType.values();
        for (int i = 0; i < urlTypes.length; i++) {
            uriMatcher.addURI(AUTHORITY_PACKAGE, urlTypes[i].getPathWithFilter(), i);
        }
    }

    @Override
    public boolean onCreate() {
        CustomSQLiteOpenHelper databaseHelper = new CustomSQLiteOpenHelper(getContext());
        try {
            db = databaseHelper.getWritableDatabase();
            // Necessary to enable cascade deletion from Track to TrackPoints and Markers
            db.setForeignKeyConstraintsEnabled(true);
        } catch (SQLiteException e) {
            Log.e(TAG, "Unable to open database for writing.", e);
        }
        return db != null;
    }

    @Override
    public int delete(@NonNull Uri url, String where, String[] selectionArgs) {
        String table = switch (getUrlType(url)) {
            case TRACKPOINTS -> TrackPointsColumns.TABLE_NAME;
            case TRACKS -> TracksColumns.TABLE_NAME;
            case MARKERS -> MarkerColumns.TABLE_NAME;
            default -> throw new IllegalArgumentException("Unknown URL " + url);
        };

        Log.w(TAG, "Deleting from table " + table);
        int totalChangesBefore = getTotalChanges();
        int deletedRowsFromTable;
        try {
            db.beginTransaction();
            deletedRowsFromTable = db.delete(table, where, selectionArgs);
            Log.i(TAG, "Deleted " + deletedRowsFromTable + " rows of table " + table);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        notifyChanges(url);

        int totalChanges = getTotalChanges() - totalChangesBefore;
        Log.i(TAG, "Deleted " + totalChanges + " total rows from database");

        PreferencesUtils.addTotalRowsDeleted(totalChanges);
        int totalRowsDeleted = PreferencesUtils.getTotalRowsDeleted();
        if (totalRowsDeleted > TOTAL_DELETED_ROWS_VACUUM_THRESHOLD) {
            Log.i(TAG, "TotalRowsDeleted " + totalRowsDeleted + ", starting to vacuum the database.");
            db.execSQL("VACUUM");
            PreferencesUtils.resetTotalRowsDeleted();
        }

        return deletedRowsFromTable;
    }

    private int getTotalChanges() {
        int totalCount;
        try (Cursor cursor = db.rawQuery("SELECT total_changes()", null)) {
            cursor.moveToNext();
            totalCount = cursor.getInt(0);
        }
        return totalCount;
    }

    @Override
    public String getType(@NonNull Uri url) {
        return getUrlType(url).contentType;
    }

    @Override
    public Uri insert(@NonNull Uri url, ContentValues initialValues) {
        if (initialValues == null) {
            initialValues = new ContentValues();
        }
        Uri result;
        try {
            db.beginTransaction();
            result = insertContentValues(url, getUrlType(url), initialValues);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        notifyChanges(url);

        return result;
    }

    @Override
    public int bulkInsert(@NonNull Uri url, @NonNull ContentValues[] valuesBulk) {
        int numInserted;
        try {
            // Use a transaction in order to make the insertions run as a single batch
            db.beginTransaction();

            UrlType urlType = getUrlType(url);
            for (numInserted = 0; numInserted < valuesBulk.length; numInserted++) {
                ContentValues contentValues = valuesBulk[numInserted];
                if (contentValues == null) {
                    contentValues = new ContentValues();
                }
                insertContentValues(url, urlType, contentValues);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        notifyChanges(url);

        return numInserted;
    }

    @Override
    public Cursor query(@NonNull Uri url, String[] projection, String selection, String[] selectionArgs, String sort) {
        SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();
        String sortOrder = null;
        String groupBy = null;
        //TODO queryBuilder.setStrict(true);
        //TODO queryBuilder.appendWhere() should use selectionArgs?
        switch (getUrlType(url)) {
            case TRACKPOINTS -> {
                queryBuilder.setTables(TrackPointsColumns.TABLE_NAME);
                sortOrder = sort != null ? sort : BaseColumns._ID;
            }
            case TRACKPOINTS_BY_ID -> {
                queryBuilder.setTables(TrackPointsColumns.TABLE_NAME);
                queryBuilder.appendWhere(TrackPointsColumns._ID + "=" + ContentUris.parseId(url));
            }
            case TRACKPOINTS_BY_TRACKID -> {
                queryBuilder.setTables(TrackPointsColumns.TABLE_NAME);
                queryBuilder.appendWhere(TrackPointsColumns.TRACKID + " IN (" + TextUtils.join(SQL_LIST_DELIMITER, ContentProviderUtils.parseTrackIdsFromUri(url)) + ")");
            }
            case DASHBOARD_TRACKPOINTS_BY_TRACKIDS -> {
                queryBuilder.setTables(TrackPointsColumns.TABLE_NAME);
                queryBuilder.appendWhere(TrackPointsColumns.TRACKID + " IN (" + TextUtils.join(SQL_LIST_DELIMITER, ContentProviderUtils.parseTrackIdsFromUri(url)) + ")");
                queryBuilder.setProjectionMap(DataProvider.DATA_PROJECTIONMAP_TRACKPOINTS);
            }
            case TRACKS -> {
                if (projection != null && Arrays.asList(projection).contains(TracksColumns.MARKER_COUNT)) {
                    queryBuilder.setTables(TracksColumns.TABLE_NAME + " LEFT OUTER JOIN (SELECT " + MarkerColumns.TRACKID + " AS markerTrackId, COUNT(*) AS " + TracksColumns.MARKER_COUNT + " FROM " + MarkerColumns.TABLE_NAME + " GROUP BY " + MarkerColumns.TRACKID + ") ON (" + TracksColumns.TABLE_NAME + "." + TracksColumns._ID + "= markerTrackId)");
                } else {
                    queryBuilder.setTables(TracksColumns.TABLE_NAME);
                }
                sortOrder = sort != null ? sort : BaseColumns._ID;
            }
            case TRACKS_BY_ID -> {
                queryBuilder.setTables(TracksColumns.TABLE_NAME);
                queryBuilder.appendWhere(TracksColumns._ID + " IN (" + TextUtils.join(SQL_LIST_DELIMITER, ContentProviderUtils.parseTrackIdsFromUri(url)) + ")");
            }
            case DASHBOARD_TRACKS_BY_IDS -> {
                queryBuilder.setTables(TracksColumns.TABLE_NAME);
                queryBuilder.appendWhere(TracksColumns._ID + " IN (" + TextUtils.join(SQL_LIST_DELIMITER, ContentProviderUtils.parseTrackIdsFromUri(url)) + ")");
                queryBuilder.setProjectionMap(DataProvider.DATA_PROJECTIONMAP_TRACKS);
            }
            case TRACKS_SENSOR_STATS -> {
                //TODO Replace with SQLQueryBuilder?
                long trackId = ContentUris.parseId(url);
                return db.rawQuery(SENSOR_STATS_QUERY, new String[]{String.valueOf(trackId), String.valueOf(trackId)});
            }
            case TRACKS_AGGREGATED_STATISTICS -> {
                queryBuilder.setTables(TracksColumns.TABLE_NAME);
                projection = new String[]{
                        "COUNT(*) AS " + TracksColumns.TRACK_COUNT,
                        TracksColumns.ACTIVITY_TYPE_LOCALIZED,
                        "SUM(" + TracksColumns.MOVINGTIME + ") AS " + TracksColumns.MOVINGTIME,
                        "SUM(" + TracksColumns.TOTALDISTANCE + ") AS " + TracksColumns.TOTALDISTANCE,
                        "MAX(" + TracksColumns.MAXSPEED + ") AS " + TracksColumns.MAXSPEED
                };

                sortOrder = TracksColumns.TRACK_COUNT + " DESC";
                groupBy = TracksColumns.ACTIVITY_TYPE_LOCALIZED;
            }
            case MARKERS -> {
                queryBuilder.setTables(MarkerColumns.TABLE_NAME);
                sortOrder = sort != null ? sort : BaseColumns._ID;
            }
            case MARKERS_BY_ID -> {
                queryBuilder.setTables(MarkerColumns.TABLE_NAME);
                queryBuilder.appendWhere(MarkerColumns._ID + "=" + ContentUris.parseId(url));
            }
            case MARKERS_BY_TRACKID -> {
                queryBuilder.setTables(MarkerColumns.TABLE_NAME);
                queryBuilder.appendWhere(MarkerColumns.TRACKID + " IN (" + TextUtils.join(SQL_LIST_DELIMITER, ContentProviderUtils.parseTrackIdsFromUri(url)) + ")");
            }
            case DASHBOARD_MARKERS_BY_TRACKIDS -> {
                queryBuilder.setTables(MarkerColumns.TABLE_NAME);
                queryBuilder.appendWhere(MarkerColumns.TRACKID + " IN (" + TextUtils.join(SQL_LIST_DELIMITER, ContentProviderUtils.parseTrackIdsFromUri(url)) + ")");
                queryBuilder.setProjectionMap(DataProvider.DATA_PROJECTIONMAP_MARKERS);
            }
            default -> throw new IllegalArgumentException("Unknown url " + url);
        }
        Cursor cursor = queryBuilder.query(db, projection, selection, selectionArgs, groupBy, null, sortOrder);
        cursor.setNotificationUri(getContext().getContentResolver(), url);
        return cursor;
    }

    @Override
    public int update(@NonNull Uri url, ContentValues values, String where, String[] selectionArgs) {
        String table;
        switch (getUrlType(url)) {
            case TRACKPOINTS -> table = TrackPointsColumns.TABLE_NAME;
            case TRACKS -> table = TracksColumns.TABLE_NAME;
            case MARKERS -> table = MarkerColumns.TABLE_NAME;
            default -> throw new IllegalArgumentException("Unknown url " + url);
        }

        int count;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();
            queryBuilder.setTables(table);
            count = queryBuilder.update(db, values, where, selectionArgs);
        } else {
            try {
                db.beginTransaction();
                count = db.update(table, values, where, selectionArgs);
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        }

        notifyChanges(url);

        return count;
    }

    private void notifyChanges(@NonNull Uri uri) {
        getContext().getContentResolver().notifyChange(uri, null, false);
        // Also notify dashboard URIs for compatibility
        Uri dashboardUri = getDashboardUri(uri);
        if (dashboardUri != null) {
            getContext().getContentResolver().notifyChange(dashboardUri, null, false);
        }
    }

    private Uri getDashboardUri(Uri uri) {
        String path = uri.getPath();
        if (path == null) return null;
        String dashboardPath;
        if (path.startsWith("/tracks")) {
            dashboardPath = path.replaceFirst("/tracks", "/dashboard/tracks");
        } else if (path.startsWith("/markers")) {
            dashboardPath = path.replaceFirst("/markers", "/dashboard/markers");
        } else if (path.startsWith("/trackpoints")) {
            dashboardPath = path.replaceFirst("/trackpoints", "/dashboard/trackpoints");
        } else {
            return null;
        }
        return Uri.parse(CONTENT_BASE_URI + dashboardPath);
    }

    @NonNull
    private UrlType getUrlType(Uri url) {
        UrlType[] urlTypes = UrlType.values();
        int matchIndex = uriMatcher.match(url);
        if (0 <= matchIndex && matchIndex < urlTypes.length) {
            return urlTypes[matchIndex];
        }

        throw new IllegalArgumentException("Unknown URL " + url);
    }

    /**
     * Inserts a content based on the url type.
     *
     * @param url           the content url
     * @param urlType       the url type
     * @param contentValues the content values
     */
    private Uri insertContentValues(Uri url, UrlType urlType, ContentValues contentValues) {
        return switch (urlType) {
            case TRACKPOINTS -> insertTrackPoint(url, contentValues);
            case TRACKS -> insertTrack(url, contentValues);
            case MARKERS -> insertMarker(url, contentValues);
            default -> throw new IllegalArgumentException("Unknown url " + url);
        };
    }

    private Uri insertTrackPoint(Uri url, ContentValues values) {
        boolean hasTime = values.containsKey(TrackPointsColumns.TIME);
        if (!hasTime) {
            throw new IllegalArgumentException("Latitude, longitude, and time values are required.");
        }
        long rowId = db.insert(TrackPointsColumns.TABLE_NAME, TrackPointsColumns._ID, values);
        if (rowId >= 0) {
            return ContentUris.appendId(UrlType.TRACKPOINTS_BY_ID.getUri().buildUpon(), rowId).build();
        }
        throw new SQLiteException("Failed to insert a track point " + url);
    }

    private Uri insertTrack(Uri url, ContentValues contentValues) {
        long rowId = db.insert(TracksColumns.TABLE_NAME, TracksColumns._ID, contentValues);
        if (rowId >= 0) {
            return ContentUris.appendId(UrlType.TRACKS.getUri().buildUpon(), rowId).build();
        }
        throw new SQLException("Failed to insert a track " + url);
    }

    private Uri insertMarker(Uri url, ContentValues contentValues) {
        long rowId = db.insert(MarkerColumns.TABLE_NAME, MarkerColumns._ID, contentValues);
        if (rowId >= 0) {
            return ContentUris.appendId(UrlType.MARKERS.getUri().buildUpon(), rowId).build();
        }
        throw new SQLException("Failed to insert a marker " + url);
    }

    public enum UrlType {
        TRACKPOINTS("/trackpoints", "", "vnd.android.cursor.dir/vnd.de.dennisguse.trackpoint"),
        TRACKPOINTS_BY_ID("/trackpoints", "#", "vnd.android.cursor.dir/vnd.de.dennisguse.trackpoint"),
        //TODO Check if still used
        TRACKPOINTS_BY_TRACKID("/trackpoints/trackid", "*", "vnd.android.cursor.dir/vnd.de.dennisguse.trackpoint"),
        TRACKS("/tracks", "", "vnd.android.cursor.dir/vnd.de.dennisguse.track"),
        TRACKS_BY_ID("/tracks", "#", "vnd.android.cursor.dir/vnd.de.dennisguse.track"),
        TRACKS_SENSOR_STATS("/tracks/sensorstats", "#", "vnd.android.cursor.dir/vnd.de.dennisguse.sensorstats"),
        TRACKS_AGGREGATED_STATISTICS("/tracks/aggregated/", "", "vnd.android.cursor.dir/vnd.de.dennisguse.aggregatedstatistics"),
        MARKERS("/markers", "", "vnd.android.cursor.dir/vnd.de.dennisguse.waypoint"),
        MARKERS_BY_ID("/markers", "#", "vnd.android.cursor.dir/vnd.de.dennisguse.waypoint"),
        @Deprecated //TODO Should not be used anymore
        MARKERS_BY_TRACKID("/markers/trackid", "*", "vnd.android.cursor.dir/vnd.de.dennisguse.waypoint"),

        DASHBOARD_TRACKS_BY_IDS("/dashboard/tracks", "*", "vnd.android.cursor.dir/vnd.de.dennisguse.track"),
        DASHBOARD_TRACKPOINTS_BY_TRACKIDS("/dashboard/trackpoints", "*", "vnd.android.cursor.dir/vnd.de.dennisguse.trackpoint"),
        DASHBOARD_MARKERS_BY_TRACKIDS("/dashboard/markers", "*", "vnd.android.cursor.dir/vnd.de.dennisguse.waypoint");

        private final String path;

        private final String filter;

        private final String contentType;

        UrlType(@NonNull String path, String filter, @NonNull String contentType) {
            this.path = path;
            this.filter = filter;
            this.contentType = contentType;
        }

        public Uri getUri() {
            return Uri.parse(CONTENT_BASE_URI + path);
        }

        private String getPathWithFilter() {
            if (TextUtils.isEmpty(filter))
                return path;
            return path + "/" + filter;
        }
    }
}
