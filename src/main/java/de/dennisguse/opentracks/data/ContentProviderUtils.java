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

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import de.dennisguse.opentracks.data.models.ActivityType;
import de.dennisguse.opentracks.data.models.Altitude;
import de.dennisguse.opentracks.data.models.AltitudeExtremities;
import de.dennisguse.opentracks.data.models.AltitudeGainLoss;
import de.dennisguse.opentracks.data.models.Cadence;
import de.dennisguse.opentracks.data.models.Distance;
import de.dennisguse.opentracks.data.models.HeartRate;
import de.dennisguse.opentracks.data.models.Marker;
import de.dennisguse.opentracks.data.models.Position;
import de.dennisguse.opentracks.data.models.Power;
import de.dennisguse.opentracks.data.models.Speed;
import de.dennisguse.opentracks.data.models.Statistics;
import de.dennisguse.opentracks.data.models.Temperature;
import de.dennisguse.opentracks.data.models.Track;
import de.dennisguse.opentracks.data.models.TrackBuilder;
import de.dennisguse.opentracks.data.models.TrackPoint;
import de.dennisguse.opentracks.data.statistics.SensorStatistics;
import de.dennisguse.opentracks.data.tables.MarkerColumns;
import de.dennisguse.opentracks.data.tables.TrackPointsColumns;
import de.dennisguse.opentracks.data.tables.TracksColumns;
import de.dennisguse.opentracks.ui.markers.MarkerUtils;
import de.dennisguse.opentracks.util.FileUtils;

/**
 * {@link ContentProviderUtils} implementation.
 *
 * @author Leif Hendrik Wilden
 */
//TODO Check if do {} while can be replaced by while (cursor.moveToNext)
public class ContentProviderUtils {

    private static final String TAG = ContentProviderUtils.class.getSimpleName();

    private static final String ID_SEPARATOR = ",";

    private final ContentResolver contentResolver;

    public ContentProviderUtils(Context context) {
        contentResolver = context.getContentResolver();
    }

    @VisibleForTesting
    public ContentProviderUtils(ContentResolver contentResolver) {
        this.contentResolver = contentResolver;
    }

    public static Track createTrack(Cursor cursor) {
        int idIndex = cursor.getColumnIndexOrThrow(TracksColumns._ID);
        int uuidIndex = cursor.getColumnIndexOrThrow(TracksColumns.UUID);
        int nameIndex = cursor.getColumnIndexOrThrow(TracksColumns.NAME);
        int descriptionIndex = cursor.getColumnIndexOrThrow(TracksColumns.DESCRIPTION);
        int activityTypeIndex = cursor.getColumnIndexOrThrow(TracksColumns.ACTIVITY_TYPE);
        int activityTypeLocalizedIndex = cursor.getColumnIndexOrThrow(TracksColumns.ACTIVITY_TYPE_LOCALIZED);
        int startTimeIndex = cursor.getColumnIndexOrThrow(TracksColumns.STARTTIME);
        int startTimeOffsetIndex = cursor.getColumnIndexOrThrow(TracksColumns.STARTTIME_OFFSET);
        int stopTimeIndex = cursor.getColumnIndexOrThrow(TracksColumns.STOPTIME);
        int totalDistanceIndex = cursor.getColumnIndexOrThrow(TracksColumns.TOTALDISTANCE);
        int totalTimeIndex = cursor.getColumnIndexOrThrow(TracksColumns.TOTALTIME);
        int movingTimeIndex = cursor.getColumnIndexOrThrow(TracksColumns.MOVINGTIME);
        int maxSpeedIndex = cursor.getColumnIndexOrThrow(TracksColumns.MAXSPEED);
        int minAltitudeIndex = cursor.getColumnIndexOrThrow(TracksColumns.MIN_ALTITUDE);
        int maxAltitudeIndex = cursor.getColumnIndexOrThrow(TracksColumns.MAX_ALTITUDE);
        int altitudeGainIndex = cursor.getColumnIndexOrThrow(TracksColumns.ALTITUDE_GAIN);
        int altitudeLossIndex = cursor.getColumnIndexOrThrow(TracksColumns.ALTITUDE_LOSS);

        Statistics trackStatistics = new Statistics(
                Instant.ofEpochMilli(cursor.getLong(startTimeIndex)),
                Instant.ofEpochMilli(cursor.getLong(stopTimeIndex)),
                Duration.ofMillis(cursor.getLong(totalTimeIndex)),
                Duration.ofMillis(cursor.getLong(movingTimeIndex)),

                Distance.of(cursor.getFloat(totalDistanceIndex)),

                Speed.of(cursor.getFloat(maxSpeedIndex)),
                cursor.isNull(minAltitudeIndex) || cursor.isNull(maxAltitudeIndex) ? null : new AltitudeExtremities(cursor.getFloat(minAltitudeIndex), cursor.getFloat(maxAltitudeIndex)),
                cursor.isNull(altitudeGainIndex) || cursor.isNull(altitudeLossIndex) ? null : new AltitudeGainLoss(cursor.getFloat(altitudeGainIndex), cursor.getFloat(altitudeLossIndex)),

                null,
                null
        );

        return new Track(
                new Track.Id(cursor.getLong(idIndex)),
                UUIDUtils.fromBytes(cursor.getBlob(uuidIndex)),
                !cursor.isNull(nameIndex) ? cursor.getString(nameIndex) : "",
                !cursor.isNull(descriptionIndex) ? cursor.getString(descriptionIndex) : "",
                !cursor.isNull(activityTypeLocalizedIndex) ? cursor.getString(activityTypeLocalizedIndex) : "",
                !cursor.isNull(activityTypeIndex) ? ActivityType.findBy(cursor.getString(activityTypeIndex)) : ActivityType.findBy(null), //TODO Can this happen?
                ZoneOffset.ofTotalSeconds(cursor.getInt(startTimeOffsetIndex)),
                trackStatistics
        );
    }

    @VisibleForTesting
    public void deleteAllTracks(Context context) {
        // Delete tracks last since it triggers a database vacuum call
        contentResolver.delete(CustomContentProvider.UrlType.TRACKS.getUri(), null, null);

        File dir = FileUtils.getPhotoDir(context);
        FileUtils.deleteDirectoryRecurse(dir);
    }

    public void deleteTracks(Context context, @NonNull List<Track.Id> trackIds) {
        // Delete track folder resources.
        for (Track.Id trackId : trackIds) {
            FileUtils.deleteDirectoryRecurse(FileUtils.getPhotoDir(context, trackId));
        }

        String whereClause = String.format(TracksColumns._ID + " IN (%s)", TextUtils.join(",", Collections.nCopies(trackIds.size(), "?")));
        contentResolver.delete(CustomContentProvider.UrlType.TRACKS.getUri(), whereClause, trackIds.stream().map(trackId -> Long.toString(trackId.id())).toArray(String[]::new));
    }

    public void deleteTrack(Context context, @NonNull Track.Id trackId) {
        // Delete track folder resources.
        FileUtils.deleteDirectoryRecurse(FileUtils.getPhotoDir(context, trackId));
        contentResolver.delete(CustomContentProvider.UrlType.TRACKS.getUri(), TracksColumns._ID + "=?", new String[]{Long.toString(trackId.id())});
    }

    //TODO Only use for tests; also move to tests.
    @VisibleForTesting
    public List<Track> getTracks() {
        ArrayList<Track> tracks = new ArrayList<>();
        try (Cursor cursor = getTrackCursor(null, null, TracksColumns._ID)) {
            if (cursor != null && cursor.moveToFirst()) {
                tracks.ensureCapacity(cursor.getCount());
                do {
                    tracks.add(createTrack(cursor));
                } while (cursor.moveToNext());
            }
        }
        return tracks;
    }

    public TrackListIterator searchTracks(String searchQuery) {
        // Needed, because MARKER_COUNT is a virtual column and has to be explicitly requested.
        final String[] PROJECTION = new String[]{
                TracksColumns._ID,
                TracksColumns.NAME,
                TracksColumns.DESCRIPTION,
                TracksColumns.ACTIVITY_TYPE,
                TracksColumns.ACTIVITY_TYPE_LOCALIZED,
                TracksColumns.STARTTIME,
                TracksColumns.STARTTIME_OFFSET,
                TracksColumns.TOTALDISTANCE,
                TracksColumns.TOTALTIME,
                TracksColumns.MARKER_COUNT,
        };

        String selection = null;
        String[] selectionArgs = null;
        final String sortOrder = TracksColumns.STARTTIME + " DESC";

        if (searchQuery != null) {
            selection = TracksColumns.NAME + " LIKE ? OR " +
                    TracksColumns.DESCRIPTION + " LIKE ? OR " +
                    TracksColumns.ACTIVITY_TYPE_LOCALIZED + " LIKE ?";
            selectionArgs = new String[]{"%" + searchQuery + "%", "%" + searchQuery + "%", "%" + searchQuery + "%"};
        }

        return new TrackListIterator(contentResolver.query(CustomContentProvider.UrlType.TRACKS.getUri(), PROJECTION, selection, selectionArgs, sortOrder));
    }

    public Track getTrack(@NonNull Track.Id trackId) {
        try (Cursor cursor = getTrackCursor(TracksColumns._ID + "=?", new String[]{Long.toString(trackId.id())}, null)) {
            if (cursor != null && cursor.moveToNext()) {
                return createTrack(cursor);
            }
        }
        return null;
    }

    public Track getTrack(@NonNull UUID trackUUID) {
        String trackUUIDsearch = UUIDUtils.toHex(trackUUID);
        try (Cursor cursor = getTrackCursor("hex(" + TracksColumns.UUID + ")=?", new String[]{trackUUIDsearch}, null)) {
            if (cursor != null && cursor.moveToNext()) {
                return createTrack(cursor);
            }
        }
        return null;
    }

    /**
     * Gets a track cursor.
     * The caller owns the returned cursor and is responsible for closing it.
     *
     * @param selection     the selection. Can be null
     * @param selectionArgs the selection arguments. Can be null
     * @param sortOrder     the sort order. Can be null
     */
    Cursor getTrackCursor(String selection, String[] selectionArgs, String sortOrder) {
        return contentResolver.query(CustomContentProvider.UrlType.TRACKS.getUri(), null, selection, selectionArgs, sortOrder);
    }

    /**
     * Inserts a track.
     * NOTE: This doesn't insert any trackPoints.
     * NOTE: if the track.uuid is null, a random value will be assigned.
     *
     * @param track the track
     * @return the content provider URI of the inserted track.
     */
    public Track.Id insertTrack(Track track) {
        //TODO Unset trackId as it should be database generated
        if (track.uuid() == null) {
            track = new TrackBuilder(track, UUID.randomUUID()).getTrack();
        }
        Uri uri = contentResolver.insert(CustomContentProvider.UrlType.TRACKS.getUri(), createContentValues(track));
        return new Track.Id(ContentUris.parseId(uri));
    }

    /**
     * Updates a track.
     * NOTE: This doesn't update any trackPoints.
     *
     * @param track the track
     */
    public void updateTrack(Track track) {
        contentResolver.update(CustomContentProvider.UrlType.TRACKS.getUri(), createContentValues(track), TracksColumns._ID + "=?", new String[]{Long.toString(track.id().id())});
    }

    private ContentValues createContentValues(Track track) {
        ContentValues values = new ContentValues();

        if (track.id() != null) {
            values.put(TracksColumns._ID, track.id().id());
        }
        values.put(TracksColumns.UUID, UUIDUtils.toBytes(track.uuid()));
        values.put(TracksColumns.NAME, track.name());
        values.put(TracksColumns.DESCRIPTION, track.description());
        values.put(TracksColumns.ACTIVITY_TYPE, track.activityType() != null ? track.activityType().getId() : null);
        values.put(TracksColumns.ACTIVITY_TYPE_LOCALIZED, track.activityTypeLocalized());
        values.put(TracksColumns.STARTTIME_OFFSET, track.zoneOffset().getTotalSeconds());

        values.putAll(createContentValues(track.statistics()));

        return values;
    }

    public void updateTrackStatistics(@NonNull Track.Id trackId, @NonNull Statistics trackStatistics) {
        contentResolver.update(CustomContentProvider.UrlType.TRACKS.getUri(), createContentValues(trackStatistics), TracksColumns._ID + "=?", new String[]{Long.toString(trackId.id())});
    }

    private ContentValues createContentValues(Statistics trackStatistics) {
        ContentValues values = new ContentValues();
        values.put(TracksColumns.STARTTIME, trackStatistics.startTime().toEpochMilli());
        values.put(TracksColumns.STOPTIME, trackStatistics.stopTime().toEpochMilli());
        values.put(TracksColumns.TOTALDISTANCE, trackStatistics.totalDistance().toM());
        values.put(TracksColumns.TOTALTIME, trackStatistics.totalDuration().toMillis());
        values.put(TracksColumns.MOVINGTIME, trackStatistics.movingDuration().toMillis());
        values.put(TracksColumns.MAXSPEED, trackStatistics.maxSpeed().toMPS());
        if (trackStatistics.altitudeExtremities() != null) {
            values.put(TracksColumns.MIN_ALTITUDE, trackStatistics.altitudeExtremities().min_m());
            values.put(TracksColumns.MAX_ALTITUDE, trackStatistics.altitudeExtremities().max_m());
        }
        if (trackStatistics.altitudeGainLoss() != null) {
            values.put(TracksColumns.ALTITUDE_GAIN, trackStatistics.altitudeGainLoss().gain_m());
            values.put(TracksColumns.ALTITUDE_LOSS, trackStatistics.altitudeGainLoss().loss_m());
        }
        return values;
    }

    //TODO Align with fillTrackPoint
    public Marker createMarker(Cursor cursor) {
        int idIndex = cursor.getColumnIndexOrThrow(MarkerColumns._ID);
        int nameIndex = cursor.getColumnIndexOrThrow(MarkerColumns.NAME);
        int descriptionIndex = cursor.getColumnIndexOrThrow(MarkerColumns.DESCRIPTION);
        int typeLocalizedIndex = cursor.getColumnIndexOrThrow(MarkerColumns.TYPE_LOCALIZED);
        int trackIdIndex = cursor.getColumnIndexOrThrow(MarkerColumns.TRACKID);
        int longitudeIndex = cursor.getColumnIndexOrThrow(MarkerColumns.LONGITUDE);
        int latitudeIndex = cursor.getColumnIndexOrThrow(MarkerColumns.LATITUDE);
        int timeIndex = cursor.getColumnIndexOrThrow(MarkerColumns.TIME);
        int altitudeIndex = cursor.getColumnIndexOrThrow(MarkerColumns.ALTITUDE);
        int accuracyIndex = cursor.getColumnIndexOrThrow(MarkerColumns.HORIZONTAL_ACCURACY);
        int bearingIndex = cursor.getColumnIndexOrThrow(MarkerColumns.BEARING);
        int photoUrlIndex = cursor.getColumnIndexOrThrow(MarkerColumns.PHOTOURL);


        Double latitude = null;
        Double longitude = null;
        Altitude.WGS84 altitude = null;
        Distance horizontalAccuracy = null;
        Float bearing = null;
        if (!cursor.isNull(longitudeIndex) && !cursor.isNull(latitudeIndex)) {
            latitude = (((double) cursor.getInt(latitudeIndex)) / 1E6);
            longitude = (((double) cursor.getInt(longitudeIndex)) / 1E6);
        }
        if (!cursor.isNull(altitudeIndex)) {
            altitude = Altitude.WGS84.of(cursor.getFloat(altitudeIndex));
        }
        if (!cursor.isNull(accuracyIndex)) {
            horizontalAccuracy = Distance.of(cursor.getFloat(accuracyIndex));
        }
        if (!cursor.isNull(bearingIndex)) {
            bearing = cursor.getFloat(bearingIndex);
        }

        Position position = new Position(
                Instant.ofEpochMilli(cursor.getLong(timeIndex)),
                latitude,
                longitude,
                horizontalAccuracy,
                altitude,
                null,
                bearing,
                null);

        Uri photoUrl = null;
        if (!cursor.isNull(photoUrlIndex)) {
            if (cursor.getString(photoUrlIndex).isEmpty()) {
                // Before v4.18.0: a marker without a picture as URL ""
                // TODO Data should be migrated.
                photoUrl = null;
            } else {
                photoUrl = Uri.parse(cursor.getString(photoUrlIndex));
            }
        }

        return new Marker(
                new Marker.Id(cursor.getLong(idIndex)),
                new Track.Id(cursor.getLong(trackIdIndex)),
                !cursor.isNull(nameIndex) ? cursor.getString(nameIndex) : "",
                !cursor.isNull(descriptionIndex) ? cursor.getString(descriptionIndex) : "",
                !cursor.isNull(typeLocalizedIndex) ? cursor.getString(typeLocalizedIndex) : "",
                position,
                photoUrl
        );
    }

    public void deleteMarker(Context context, Marker.Id markerId) {
        final Marker marker = getMarker(markerId);
        deleteMarkerPhoto(context, marker);
        contentResolver.delete(CustomContentProvider.UrlType.MARKERS.getUri(), MarkerColumns._ID + "=?", new String[]{Long.toString(markerId.id())});
    }

    /**
     * @return null if not able to get the next marker number.
     */
    public Integer getNextMarkerNumber(@NonNull Track.Id trackId) {
        String[] projection = {MarkerColumns._ID};
        String selection = MarkerColumns.TRACKID + "=?";
        String[] selectionArgs = new String[]{Long.toString(trackId.id())};
        try (Cursor cursor = getMarkerCursor(projection, selection, selectionArgs, MarkerColumns._ID, -1)) {
            if (cursor != null) {
                return cursor.getCount();
            }
        }
        return null;
    }

    public Marker getMarker(@NonNull Marker.Id markerId) {
        try (Cursor cursor = getMarkerCursor(null, MarkerColumns._ID + "=?", new String[]{Long.toString(markerId.id())}, MarkerColumns._ID, 1)) {
            if (cursor != null && cursor.moveToFirst()) {
                return createMarker(cursor);
            }
        }
        return null;
    }

    public MarkerIterator getMarkerIterator(@NonNull Track.Id trackId) {
        return new MarkerIterator(this, trackId, -1);
    }

    public MarkerIterator getMarkerIterator(@NonNull Track.Id trackId, int maxCount) {
        return new MarkerIterator(this, trackId, maxCount);
    }

    /**
     * The caller owns the returned cursor and is responsible for closing it.
     *
     * @param trackId  the track id
     * @param maxCount the maximum number of markers to return. -1 for no limit
     */
    Cursor getMarkerCursor(@NonNull Track.Id trackId, int maxCount) {
        String selection;
        String[] selectionArgs;

        selection = MarkerColumns.TRACKID + "=?";
        selectionArgs = new String[]{Long.toString(trackId.id())};

        return getMarkerCursor(null, selection, selectionArgs, MarkerColumns._ID, maxCount);
    }

    /**
     * @param updateMarker the marker with updated data.
     * @return true if successful.
     */
    public boolean updateMarker(Context context, Marker updateMarker) {
        Marker savedMarker = getMarker(updateMarker.id());
        if (updateMarker.photoUrl() == null) {
            deleteMarkerPhoto(context, savedMarker);
        }
        int rows = contentResolver.update(CustomContentProvider.UrlType.MARKERS.getUri(), createContentValues(updateMarker, updateMarker.trackId()), MarkerColumns._ID + "=?", new String[]{Long.toString(updateMarker.id().id())});
        return rows == 1;
    }

    public Marker.Id insertMarker(@NonNull Marker marker) {
        assert marker.id() == null;
        assert marker.trackId() != null;

        Uri uri = contentResolver.insert(CustomContentProvider.UrlType.MARKERS.getUri(), createContentValues(marker, marker.trackId()));
        return new Marker.Id(ContentUris.parseId(uri));
    }

    private void deleteMarkerPhoto(Context context, Marker marker) {
        if (marker != null && marker.photoUrl() != null) {
            Uri uri = marker.photoUrl();
            File file = MarkerUtils.buildInternalPhotoFile(context, marker.trackId(), uri);
            if (file.exists()) {
                File parent = file.getParentFile();
                file.delete();
                if (parent.listFiles().length == 0) {
                    parent.delete();
                }
            }
        }
    }

    ContentValues createContentValues(@NonNull Marker marker, @NonNull Track.Id trackId) {
        ContentValues values = new ContentValues();

        if (marker.id() != null) {
            values.put(MarkerColumns._ID, marker.id().id());
        }
        values.put(MarkerColumns.TRACKID, trackId.id());

        values.put(MarkerColumns.NAME, marker.name());
        values.put(MarkerColumns.DESCRIPTION, marker.description());
        values.put(MarkerColumns.TYPE_LOCALIZED, marker.typeLocalized());

        values.put(MarkerColumns.LONGITUDE, (int) (marker.position().longitude() * 1E6));
        values.put(MarkerColumns.LATITUDE, (int) (marker.position().latitude() * 1E6));
        values.put(MarkerColumns.TIME, marker.position().time().toEpochMilli());
        if (marker.position().hasAltitude()) {
            values.put(MarkerColumns.ALTITUDE, marker.position().altitude().toM());
        }
        if (marker.position().hasHorizontalAccuracy()) {
            values.put(MarkerColumns.HORIZONTAL_ACCURACY, marker.position().horizontalAccuracy().toM());
        }
        if (marker.position().hasBearing()) {
            values.put(MarkerColumns.BEARING, marker.position().bearing());
        }

        values.put(MarkerColumns.PHOTOURL, marker.photoUrl() != null ? marker.photoUrl().toString() : null);
        return values;
    }

    /**
     * @param projection    the projection
     * @param selection     the selection
     * @param selectionArgs the selection args
     * @param sortOrder     the sort order
     * @param maxCount      the maximum number of markers
     */
    private Cursor getMarkerCursor(String[] projection, String selection, String[] selectionArgs, String sortOrder, int maxCount) {
        if (sortOrder == null) {
            sortOrder = MarkerColumns._ID;
        }
        if (maxCount >= 0) {
            sortOrder += " LIMIT " + maxCount;
        }
        return contentResolver.query(CustomContentProvider.UrlType.MARKERS.getUri(), projection, selection, selectionArgs, sortOrder);
    }

    public MarkerIterator searchMarkers(Track.Id trackId, String query) {
        String selection = null;
        String[] selectionArgs = null;
        String sortOrder = null;

        if (query == null) {
            if (trackId != null) {
                selection = MarkerColumns.TRACKID + " = ?";
                selectionArgs = new String[]{Long.toString(trackId.id())};
            }
        } else {
            selection = MarkerColumns.NAME + " LIKE ? OR " +
                    MarkerColumns.DESCRIPTION + " LIKE ? OR " +
                    MarkerColumns.TYPE_LOCALIZED + " LIKE ?";
            selectionArgs = new String[]{"%" + query + "%", "%" + query + "%", "%" + query + "%"};
            sortOrder = BaseColumns._ID + " DESC";
        }

        return new MarkerIterator(this, getMarkerCursor(null, selection, selectionArgs, sortOrder, -1));
    }

    static TrackPoint fillTrackPoint(Cursor cursor) {
        int idIndex = cursor.getColumnIndexOrThrow(TrackPointsColumns._ID);
        int typeIndex = cursor.getColumnIndexOrThrow(TrackPointsColumns.TYPE);
        int longitudeIndex = cursor.getColumnIndexOrThrow(TrackPointsColumns.LONGITUDE);
        int latitudeIndex = cursor.getColumnIndexOrThrow(TrackPointsColumns.LATITUDE);
        int timeIndex = cursor.getColumnIndexOrThrow(TrackPointsColumns.TIME);
        int altitudeIndex = cursor.getColumnIndexOrThrow(TrackPointsColumns.ALTITUDE);
        int accuracyIndex = cursor.getColumnIndexOrThrow(TrackPointsColumns.HORIZONTAL_ACCURACY);
        int accuracyVerticalIndex = cursor.getColumnIndexOrThrow(TrackPointsColumns.VERTICAL_ACCURACY);
        int speedIndex = cursor.getColumnIndexOrThrow(TrackPointsColumns.SPEED);
        int bearingIndex = cursor.getColumnIndexOrThrow(TrackPointsColumns.BEARING);
        int sensorHeartRateIndex = cursor.getColumnIndexOrThrow(TrackPointsColumns.SENSOR_HEARTRATE);
        int temperatureIndex = cursor.getColumnIndexOrThrow(TrackPointsColumns.SENSOR_TEMPERATURE);
        int sensorCadenceIndex = cursor.getColumnIndexOrThrow(TrackPointsColumns.SENSOR_CADENCE);
        int sensorDistanceIndex = cursor.getColumnIndexOrThrow(TrackPointsColumns.SENSOR_DISTANCE);
        int sensorPowerIndex = cursor.getColumnIndexOrThrow(TrackPointsColumns.SENSOR_POWER);
        int altitudeGainIndex = cursor.getColumnIndexOrThrow(TrackPointsColumns.ALTITUDE_GAIN);
        int altitudeLossIndex = cursor.getColumnIndexOrThrow(TrackPointsColumns.ALTITUDE_LOSS);

        AltitudeGainLoss altitudeGainLoss = null;
        if (!cursor.isNull(altitudeGainIndex) && !cursor.isNull(altitudeLossIndex)) {
            altitudeGainLoss = new AltitudeGainLoss(cursor.getFloat(altitudeGainIndex), cursor.getFloat(altitudeLossIndex));
        }

        return new TrackPoint(
                new TrackPoint.Id(cursor.getInt(idIndex)),
                TrackPoint.Type.getById(cursor.getInt(typeIndex)),
                new Position(
                        Instant.ofEpochMilli(cursor.getLong(timeIndex)),
                        !cursor.isNull(latitudeIndex) ? ((double) cursor.getInt(latitudeIndex)) / 1E6 : null,
                        !cursor.isNull(longitudeIndex) ? ((double) cursor.getInt(longitudeIndex)) / 1E6 : null,
                        !cursor.isNull(accuracyIndex) ? Distance.of(cursor.getFloat(accuracyIndex)) : null,
                        !cursor.isNull(altitudeIndex) ? Altitude.WGS84.of(cursor.getFloat(altitudeIndex)) : null,
                        !cursor.isNull(accuracyVerticalIndex) ? Distance.of(cursor.getFloat(accuracyVerticalIndex)) : null,
                        !cursor.isNull(bearingIndex) ? cursor.getFloat(bearingIndex) : null,
                        !cursor.isNull(speedIndex) ? Speed.of(cursor.getFloat(speedIndex)) : null
                ),
                !cursor.isNull(sensorDistanceIndex) ? Distance.of(cursor.getFloat(sensorDistanceIndex)) : null,
                !cursor.isNull(sensorHeartRateIndex) ? HeartRate.of(cursor.getFloat(sensorHeartRateIndex)) : null,
                !cursor.isNull(temperatureIndex) ? Temperature.of(cursor.getFloat(temperatureIndex)) : null,
                !cursor.isNull(sensorCadenceIndex) ? Cadence.of(cursor.getFloat(sensorCadenceIndex)) : null,
                !cursor.isNull(sensorPowerIndex) ? Power.of(cursor.getFloat(sensorPowerIndex)) : null,
                altitudeGainLoss
        );
    }

    //TODO Only used for file import; might be better to replace it.
    public int bulkInsertTrackPoint(List<TrackPoint> trackPoints, Track.Id trackId) {
        ContentValues[] values = new ContentValues[trackPoints.size()];
        for (int i = 0; i < trackPoints.size(); i++) {
            values[i] = createContentValues(trackPoints.get(i), trackId);
        }
        return contentResolver.bulkInsert(CustomContentProvider.UrlType.TRACKPOINTS_BY_ID.getUri(), values);
    }

    //TODO Set trackId in this method.
    public int bulkInsertMarkers(List<Marker> markers, Track.Id trackId) {
        ContentValues[] values = new ContentValues[markers.size()];
        for (int i = 0; i < markers.size(); i++) {
            values[i] = createContentValues(markers.get(i), trackId);
        }
        return contentResolver.bulkInsert(CustomContentProvider.UrlType.MARKERS.getUri(), values);
    }

    /**
     * Creates a location cursor. The caller owns the returned cursor and is responsible for closing it.
     *
     * @param trackId           the track id
     * @param startTrackPointId the starting trackPoint id. `null` to ignore
     */
    @NonNull
    Cursor getTrackPointCursor(@NonNull Track.Id trackId, TrackPoint.Id startTrackPointId) {
        String selection;
        String[] selectionArgs;
        if (startTrackPointId != null) {
            selection = TrackPointsColumns.TRACKID + "=? AND " + TrackPointsColumns._ID + ">=?";
            selectionArgs = new String[]{Long.toString(trackId.id()), Long.toString(startTrackPointId.id())};
        } else {
            selection = TrackPointsColumns.TRACKID + "=?";
            selectionArgs = new String[]{Long.toString(trackId.id())};
        }

        return getTrackPointCursor(null, selection, selectionArgs, BaseColumns._ID);
    }

    /**
     * Inserts a trackPoint.
     *
     * @param trackPoint the trackPoint
     * @param trackId    the track id
     * @return the content provider URI of the inserted trackPoint
     */
    public Uri insertTrackPoint(TrackPoint trackPoint, Track.Id trackId) {
        return contentResolver.insert(CustomContentProvider.UrlType.TRACKPOINTS_BY_ID.getUri(), createContentValues(trackPoint, trackId));
    }

    /**
     * Creates the {@link ContentValues} for a {@link TrackPoint}.
     *
     * @param trackPoint the trackPoint
     * @param trackId    the track id
     */
    private ContentValues createContentValues(TrackPoint trackPoint, Track.Id trackId) {
        ContentValues values = new ContentValues();
        values.put(TrackPointsColumns.TRACKID, trackId.id());
        values.put(TrackPointsColumns.TYPE, trackPoint.type().type_db);

        if (trackPoint.position().hasLocation()) {
            values.put(TrackPointsColumns.LATITUDE, (int) (trackPoint.position().latitude() * 1E6));
            values.put(TrackPointsColumns.LONGITUDE, (int) (trackPoint.position().longitude() * 1E6));
        }
        values.put(TrackPointsColumns.TIME, trackPoint.getTime().toEpochMilli());
        if (trackPoint.position().hasAltitude()) {
            values.put(TrackPointsColumns.ALTITUDE, trackPoint.position().altitude().toM());
        }
        if (trackPoint.position().hasHorizontalAccuracy()) {
            values.put(TrackPointsColumns.HORIZONTAL_ACCURACY, trackPoint.position().horizontalAccuracy().toM());
        }
        if (trackPoint.position().hasSpeed()) {
            values.put(TrackPointsColumns.SPEED, trackPoint.position().speed().toMPS());
        }
        if (trackPoint.position().hasBearing()) {
            values.put(TrackPointsColumns.BEARING, trackPoint.position().bearing());
        }

        if (trackPoint.heartRate() != null) {
            values.put(TrackPointsColumns.SENSOR_HEARTRATE, trackPoint.heartRate().getBPM());
        }
        if (trackPoint.temperature() != null) {
            values.put(TrackPointsColumns.SENSOR_TEMPERATURE, trackPoint.temperature().getCelsius());
        }
        if (trackPoint.cadence() != null) {
            values.put(TrackPointsColumns.SENSOR_CADENCE, trackPoint.cadence().getRPM());
        }
        if (trackPoint.sensorDistance() != null) {
            values.put(TrackPointsColumns.SENSOR_DISTANCE, trackPoint.sensorDistance().toM());
        }
        if (trackPoint.power() != null) {
            values.put(TrackPointsColumns.SENSOR_POWER, trackPoint.power().getW());
        }

        if (trackPoint.altitudeGainLoss() != null) {
            values.put(TrackPointsColumns.ALTITUDE_GAIN, trackPoint.altitudeGainLoss().gain_m());
            values.put(TrackPointsColumns.ALTITUDE_LOSS, trackPoint.altitudeGainLoss().loss_m());
        }
        return values;
    }

    /**
     * Creates a new read-only iterator over a given track's points.
     * When done with iteration, {@link TrackPointIterator#close()} must be called.
     *
     * @param trackId           the track id
     * @param startTrackPointId the starting trackPoint id. `null` to ignore
     */
    public TrackPointIterator getTrackPointIterator(Track.Id trackId, TrackPoint.Id startTrackPointId) {
        return new TrackPointIterator(this, trackId, startTrackPointId);
    }

    /**
     * Gets a trackPoint cursor.
     *
     * @param projection    the projection
     * @param selection     the selection
     * @param selectionArgs the selection arguments
     * @param sortOrder     the sort order
     */
    private Cursor getTrackPointCursor(String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return contentResolver.query(CustomContentProvider.UrlType.TRACKPOINTS_BY_ID.getUri(), projection, selection, selectionArgs, sortOrder);
    }

    public static String formatIdListForUri(Track.Id... trackIds) {
        long[] ids = new long[trackIds.length];
        for (int i = 0; i < trackIds.length; i++) {
            ids[i] = trackIds[i].id();
        }

        return formatIdListForUri(ids);
    }

    /**
     * Formats an array of IDs as comma separated string value
     *
     * @param ids array with IDs
     * @return comma separated list of ids
     */
    private static String formatIdListForUri(long[] ids) {
        StringBuilder idsPathSegment = new StringBuilder();
        for (long id : ids) {
            if (idsPathSegment.length() > 0) {
                idsPathSegment.append(ID_SEPARATOR);
            }
            idsPathSegment.append(id);
        }
        return idsPathSegment.toString();
    }

    public static String[] parseTrackIdsFromUri(Uri url) {
        return TextUtils.split(url.getLastPathSegment(), ID_SEPARATOR);
    }

    public SensorStatistics getSensorStats(@NonNull Track.Id trackId) {
        SensorStatistics sensorStatistics = null;
        try (Cursor cursor = contentResolver.query(ContentUris.withAppendedId(CustomContentProvider.UrlType.TRACKS_SENSOR_STATS.getUri(), trackId.id()), null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                final int MAX_HR_INDEX = cursor.getColumnIndexOrThrow(TrackPointsColumns.ALIAS_MAX_HR);
                final int AVG_HR_INDEX = cursor.getColumnIndexOrThrow(TrackPointsColumns.ALIAS_AVG_HR);
                final int MAX_CADENCE_INDEX = cursor.getColumnIndexOrThrow(TrackPointsColumns.ALIAS_MAX_CADENCE);
                final int AVG_CADENCE_INDEX = cursor.getColumnIndexOrThrow(TrackPointsColumns.ALIAS_AVG_CADENCE);
                final int MAX_POWER_INDEX = cursor.getColumnIndexOrThrow(TrackPointsColumns.ALIAS_MAX_POWER);
                final int AVG_POWER_INDEX = cursor.getColumnIndexOrThrow(TrackPointsColumns.ALIAS_AVG_POWER);
                sensorStatistics = new SensorStatistics(
                        !cursor.isNull(MAX_HR_INDEX) ? HeartRate.of(cursor.getFloat(MAX_HR_INDEX)) : null,
                        !cursor.isNull(AVG_HR_INDEX) ? HeartRate.of(cursor.getFloat(AVG_HR_INDEX)) : null,
                        !cursor.isNull(MAX_CADENCE_INDEX) ? Cadence.of(cursor.getFloat(MAX_CADENCE_INDEX)) : null,
                        !cursor.isNull(AVG_CADENCE_INDEX) ? Cadence.of(cursor.getFloat(AVG_CADENCE_INDEX)) : null,
                        !cursor.isNull(MAX_POWER_INDEX) ? Power.of(cursor.getFloat(MAX_POWER_INDEX)) : null,
                        !cursor.isNull(AVG_POWER_INDEX) ? Power.of(cursor.getFloat(AVG_POWER_INDEX)) : null
                );
            }

        }
        return sensorStatistics;
    }

    @NonNull
    public List<AggregatedStatistic> getAggregatedStatisticsForTracks(TrackSelection selection) {
        SelectionData data = selection.buildSelection();
        try (Cursor cursor = contentResolver.query(CustomContentProvider.UrlType.TRACKS_AGGREGATED_STATISTICS.getUri(), null, data.selection(), data.getSelectionArgs(), null)) {
            ArrayList<AggregatedStatistic> aggregatedStatistics = new ArrayList<>(cursor.getCount());
            while (cursor.moveToNext()) {
                aggregatedStatistics.add(new AggregatedStatistic(
                        cursor.getString(cursor.getColumnIndexOrThrow(TracksColumns.ACTIVITY_TYPE_LOCALIZED)),
                        cursor.getInt(cursor.getColumnIndexOrThrow(TracksColumns.TRACK_COUNT)),
                        Duration.ofMillis(cursor.getLong(cursor.getColumnIndexOrThrow(TracksColumns.MOVINGTIME))),
                        Distance.of(cursor.getFloat(cursor.getColumnIndexOrThrow(TracksColumns.TOTALDISTANCE))),
                        Speed.of(cursor.getFloat(cursor.getColumnIndexOrThrow(TracksColumns.MAXSPEED)))
                ));
            }
            return aggregatedStatistics;
        }
    }

    public record SelectionData(
            String selection,
            List<String> selectionArgs
    ) {

        public String[] getSelectionArgs() {
            if (selectionArgs == null) return null;
            return selectionArgs.toArray(String[]::new);
        }
    }
}
