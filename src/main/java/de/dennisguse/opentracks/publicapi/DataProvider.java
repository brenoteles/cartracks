package de.dennisguse.opentracks.publicapi;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Map;

import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.data.ContentProviderUtils;
import de.dennisguse.opentracks.data.CustomContentProvider;
import de.dennisguse.opentracks.data.models.Track;
import de.dennisguse.opentracks.data.tables.MarkerColumns;
import de.dennisguse.opentracks.data.tables.TrackPointsColumns;
import de.dennisguse.opentracks.data.tables.TracksColumns;
import de.dennisguse.opentracks.settings.PreferencesUtils;

/**
 * Create an {@link Intent} to request showing tracks on a Map or a Dashboard.
 * The receiving {@link android.app.Activity} gets temporary access to the {@link TracksColumns} and the {@link TrackPointsColumns} (incl. update).
 */
public class DataProvider {

    private static final String TAG = DataProvider.class.getSimpleName();

    private static final String ACTION_DASHBOARD = "Intent.OpenTracks-Dashboard";

    private static final String ACTION_DASHBOARD_PAYLOAD = ACTION_DASHBOARD + ".Payload";

    /**
     * Assume "1" if not present.
     */
    private static final String EXTRAS_PROTOCOL_VERSION = "PROTOCOL_VERSION";

    /**
     * version 1: the initial version.
     * version 2: replaced pause/resume trackpoints for track segmentation (lat=100 / lat=200) by TrackPoint.Type.
     * version 3: renamed track database columns.
     */
    private static final int CURRENT_VERSION = 2;

    private static final String EXTRAS_OPENTRACKS_IS_RECORDING_THIS_TRACK = "EXTRAS_OPENTRACKS_IS_RECORDING_THIS_TRACK";
    private static final String EXTRAS_SHOULD_KEEP_SCREEN_ON = "EXTRAS_SHOULD_KEEP_SCREEN_ON";
    private static final String EXTRAS_SHOW_WHEN_LOCKED = "EXTRAS_SHOULD_KEEP_SCREEN_ON";
    private static final String EXTRAS_SHOW_FULLSCREEN = "EXTRAS_SHOULD_FULLSCREEN";

    private static final int TRACK_URI_INDEX = 0;
    private static final int TRACKPOINTS_URI_INDEX = 1;
    private static final int MARKERS_URI_INDEX = 2;

    public static final Map<String, String> DATA_PROJECTIONMAP_TRACKS = Map.ofEntries(
            //V2
            Map.entry("_id", TracksColumns._ID),
            Map.entry("name", TracksColumns.NAME),
            Map.entry("description", TracksColumns.DESCRIPTION),
            Map.entry("category", TracksColumns.ACTIVITY_TYPE + " AS category"),
            Map.entry("activity_type_localized", TracksColumns.ACTIVITY_TYPE_LOCALIZED),
            Map.entry("starttime", TracksColumns.STARTTIME + " AS starttime"),
            Map.entry("stoptime", TracksColumns.STOPTIME + " AS stoptime"),
            Map.entry("totaltime", TracksColumns.TOTALTIME + " AS totaltime"),
            Map.entry("movingtime", TracksColumns.MOVINGTIME + " AS movingtime"),
            Map.entry("totaldistance", TracksColumns.TOTALDISTANCE + " AS totaldistance"),
            Map.entry("maxspeed", TracksColumns.MAXSPEED + " AS maxspeed"),
            Map.entry("avgspeed", "null AS avgspeed"),
            Map.entry("avgmovingspeed", "null AS avgmovingspeed"),
            Map.entry("minelevation", TracksColumns.MIN_ALTITUDE + " AS minelevation"),
            Map.entry("maxelevation", TracksColumns.MAX_ALTITUDE + " AS maxelevation"),
            Map.entry("elevationgain", TracksColumns.ALTITUDE_GAIN + " AS elevationgain"),
            Map.entry("elevationloss", TracksColumns.ALTITUDE_LOSS + " AS elevationloss"),

            //V3
//            Map.entry("_id", TracksColumns._ID),
//            Map.entry("name",TracksColumns.NAME),
//            Map.entry("description",TracksColumns.DESCRIPTION),
            Map.entry("activity_type", TracksColumns.ACTIVITY_TYPE),
//            Map.entry("activity_type_localized",TracksColumns.ACTIVITY_TYPE_LOCALIZED),
            Map.entry("time_start", TracksColumns.STARTTIME),
            Map.entry("time_stop", TracksColumns.STOPTIME),
            Map.entry("duration_total", TracksColumns.TOTALTIME),
            Map.entry("duration_moving", TracksColumns.MOVINGTIME),
            Map.entry("distance", TracksColumns.TOTALDISTANCE),
            Map.entry("speed_max", TracksColumns.MAXSPEED),
            Map.entry("altitude_min", TracksColumns.MIN_ALTITUDE),
            Map.entry("altitude_max", TracksColumns.MAX_ALTITUDE),
            Map.entry("altitude_gain", TracksColumns.ALTITUDE_GAIN),
            Map.entry("altitude_loss", TracksColumns.ALTITUDE_LOSS)
    );

    public static final Map<String, String> DATA_PROJECTIONMAP_TRACKPOINTS = Map.ofEntries(
            //V2 and V3
            Map.entry("_id", TrackPointsColumns._ID),
            Map.entry("trackid", TrackPointsColumns.TRACKID),
            Map.entry("latitude", TrackPointsColumns.LATITUDE),
            Map.entry("longitude", TrackPointsColumns.LONGITUDE),
            Map.entry("time", TrackPointsColumns.TIME),
            Map.entry("type", TrackPointsColumns.TYPE),
            Map.entry("speed", TrackPointsColumns.SPEED)
    );
    public static final Map<String, String> DATA_PROJECTIONMAP_MARKERS = Map.ofEntries(
            //V2
            Map.entry("_id", MarkerColumns._ID),
            Map.entry("trackid", MarkerColumns.TRACKID),
            Map.entry("name", MarkerColumns.NAME),
            Map.entry("description", MarkerColumns.DESCRIPTION),
            Map.entry("category", MarkerColumns.TYPE_LOCALIZED + " AS category"),
            Map.entry("latitude", MarkerColumns.LATITUDE),
            Map.entry("longitude", MarkerColumns.LONGITUDE),
            Map.entry("photoUrl", MarkerColumns.PHOTOURL),
            Map.entry("icon", "null AS icon"),

            //V3
//            Map.entry("_id", MarkerColumns._ID),
//            Map.entry("trackid",MarkerColumns.TRACKID),
//            Map.entry("name",MarkerColumns.NAME),
//            Map.entry("description",MarkerColumns.DESCRIPTION),
            Map.entry("marker_type_localized", MarkerColumns.TYPE_LOCALIZED)
//            Map.entry("latitude",MarkerColumns.LATITUDE),
//            Map.entry("longitude",MarkerColumns.LONGITUDE),
//            Map.entry("photoUrl",MarkerColumns.PHOTOURL)
    );


    private DataProvider() {
    }

    /**
     * Send intent to show tracks on a map (needs an another app) as resource URIs.
     *
     * @param context     the context
     * @param isRecording are we currently recording?
     * @param trackIds    the track ids
     */
    public static void showTrackOnMap(Context context, boolean isRecording, Track.Id... trackIds) {
        startDashboard(context, isRecording, null, null, trackIds);
    }

    /**
     * Send intent to show tracks on a map (needs an another app) as resource URIs.
     * By providing a targetPackage and targetClass an explicit intent can be sent,
     * thus bypassing the need for the user to select an app.
     *
     * @param context       the context
     * @param isRecording   are we currently recording?
     * @param targetPackage the target package
     * @param targetClass   the target class
     * @param trackIds      the track ids
     */
    public static void startDashboard(Context context, boolean isRecording, @Nullable String targetPackage, @Nullable String targetClass, Track.Id... trackIds) {
        if (trackIds.length == 0) {
            return;
        }

        String trackIdList = ContentProviderUtils.formatIdListForUri(trackIds);

        ArrayList<Uri> uris = new ArrayList<>();
        uris.add(TRACK_URI_INDEX, Uri.withAppendedPath(CustomContentProvider.UrlType.DASHBOARD_TRACKS_BY_IDS.getUri(), trackIdList));
        uris.add(TRACKPOINTS_URI_INDEX, Uri.withAppendedPath(CustomContentProvider.UrlType.DASHBOARD_TRACKPOINTS_BY_TRACKIDS.getUri(), trackIdList));
        uris.add(MARKERS_URI_INDEX, Uri.withAppendedPath(CustomContentProvider.UrlType.DASHBOARD_MARKERS_BY_TRACKIDS.getUri(), trackIdList));

        Intent intent = new Intent(ACTION_DASHBOARD);
        intent.putExtra(EXTRAS_PROTOCOL_VERSION, CURRENT_VERSION);

        intent.putParcelableArrayListExtra(ACTION_DASHBOARD_PAYLOAD, uris);

        intent.putExtra(EXTRAS_SHOULD_KEEP_SCREEN_ON, PreferencesUtils.shouldKeepScreenOn());
        intent.putExtra(EXTRAS_SHOW_WHEN_LOCKED, PreferencesUtils.shouldShowStatsOnLockscreen());
        intent.putExtra(EXTRAS_OPENTRACKS_IS_RECORDING_THIS_TRACK, isRecording);
        if (isRecording) {
            intent.putExtra(EXTRAS_SHOW_FULLSCREEN, PreferencesUtils.shouldUseFullscreen());
        }

        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        ClipData clipData = ClipData.newRawUri(null, uris.get(TRACK_URI_INDEX));
        clipData.addItem(new ClipData.Item(uris.get(TRACKPOINTS_URI_INDEX)));
        clipData.addItem(new ClipData.Item(uris.get(MARKERS_URI_INDEX)));
        intent.setClipData(clipData);

        if (targetPackage != null && targetClass != null) {
            Log.i(TAG, "Starting dashboard activity with explicit intent (package=" + targetPackage + ", class=" + targetClass + ")");
            intent.setClassName(targetPackage, targetClass);
        } else {
            Log.i(TAG, "Starting dashboard activity with generic intent (package=" + targetPackage + ", class=" + targetClass + ")");
        }

        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "Dashboard not installed; cannot start it.");
            Toast.makeText(context, R.string.show_on_dashboard_not_installed, Toast.LENGTH_SHORT).show();
        }
    }
}
