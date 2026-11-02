package de.dennisguse.opentracks.util;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import de.dennisguse.opentracks.data.ContentProviderUtils;
import de.dennisguse.opentracks.services.TrackRecordingService;

public final class TestEnvironmentUtils {

    private TestEnvironmentUtils() {
    }

    public static void stopTrackRecordingService() {
        Context context = ApplicationProvider.getApplicationContext();
        context.stopService(new Intent(context, TrackRecordingService.class));
    }

    public static void deleteAllTracks() {
        Context context = ApplicationProvider.getApplicationContext();
        new ContentProviderUtils(context).deleteAllTracks(context);
    }

    public static void resetTrackRecordingServiceAndDeleteTracks() {
        stopTrackRecordingService();
        deleteAllTracks();
    }
}
