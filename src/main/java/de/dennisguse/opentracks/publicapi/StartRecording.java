package de.dennisguse.opentracks.publicapi;

import android.os.Bundle;

import androidx.annotation.NonNull;

import de.dennisguse.opentracks.data.ContentProviderUtils;
import de.dennisguse.opentracks.data.models.ActivityType;
import de.dennisguse.opentracks.data.models.Track;
import de.dennisguse.opentracks.data.models.TrackBuilder;
import de.dennisguse.opentracks.services.TrackRecordingService;
import de.dennisguse.opentracks.settings.PreferencesUtils;

public class StartRecording extends AbstractAPIActivity {

    public static final String EXTRA_TRACK_NAME = "TRACK_NAME";
    public static final String EXTRA_TRACK_ACTIVITY_TYPE_LOCALIZED = "TRACK_CATEGORY"; //TODO Update constant
    public static final String EXTRA_TRACK_ACTIVITY_TYPE_ID = "TRACK_ICON"; //TODO Update constant
    public static final String EXTRA_TRACK_DESCRIPTION = "TRACK_DESCRIPTION";

    public static final String EXTRA_STATS_TARGET_PACKAGE = "STATS_TARGET_PACKAGE";
    public static final String EXTRA_STATS_TARGET_CLASS = "STATS_TARGET_CLASS";

    private static final String TAG = StartRecording.class.getSimpleName();

    protected void execute(TrackRecordingService service) {
        Track.Id trackId = service.startNewTrack();
        if (trackId != null) {
            Bundle bundle = getIntent().getExtras();
            if (bundle != null) {
                updateTrackMetadata(trackId, bundle);

                if (PreferencesUtils.isPublicAPIDashboardEnabled()) {
                    startDashboardAPI(trackId, bundle);
                }
            }
        }
    }

    private void updateTrackMetadata(@NonNull Track.Id trackId, @NonNull Bundle bundle) {
        ContentProviderUtils contentProviderUtils = new ContentProviderUtils(this);
        TrackBuilder track = new TrackBuilder(contentProviderUtils.getTrack(trackId));

        if (bundle.getString(EXTRA_TRACK_NAME) != null)
            track.setName(bundle.getString(EXTRA_TRACK_NAME));

        if (bundle.getString(EXTRA_TRACK_DESCRIPTION) != null)
            track.setDescription(bundle.getString(EXTRA_TRACK_DESCRIPTION));

        if (bundle.getString(EXTRA_TRACK_ACTIVITY_TYPE_ID) != null)
            track.setActivityType(ActivityType.findBy(bundle.getString(EXTRA_TRACK_ACTIVITY_TYPE_ID)));

        if (bundle.getString(EXTRA_TRACK_ACTIVITY_TYPE_LOCALIZED) != null)
            track.setActivityTypeLocalized(bundle.getString(EXTRA_TRACK_ACTIVITY_TYPE_LOCALIZED));

        contentProviderUtils.updateTrack(track.getTrack());
    }

    private void startDashboardAPI(@NonNull Track.Id trackId, @NonNull Bundle bundle) {
        String targetPackage = bundle.getString(EXTRA_STATS_TARGET_PACKAGE, null);
        String targetClass = bundle.getString(EXTRA_STATS_TARGET_CLASS, null);
        if (targetClass != null && targetPackage != null) {
            DataProvider.startDashboard(this, true, targetPackage, targetClass, trackId);
        }
    }

    @Override
    protected boolean isPostExecuteStopService() {
        return false;
    }

    @Override
    protected boolean requiresForeground() {
        return true;
    }
}
