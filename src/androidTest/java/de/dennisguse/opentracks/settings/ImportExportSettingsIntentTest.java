package de.dennisguse.opentracks.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import de.dennisguse.opentracks.BuildConfig;
import de.dennisguse.opentracks.R;

@RunWith(AndroidJUnit4.class)
public class ImportExportSettingsIntentTest {

    private final Context context = ApplicationProvider.getApplicationContext();

    @Test
    public void applicationIdResource_matchesInstalledPackage() {
        assertEquals(BuildConfig.APPLICATION_ID, context.getString(R.string.applicationId));
        assertEquals(context.getPackageName(), context.getString(R.string.applicationId));
    }

    @Test
    public void importExportPreferenceTargets_areResolvableInCurrentPackage() throws PackageManager.NameNotFoundException {
        assertResolvable("de.dennisguse.opentracks.io.file.importer.DirectoryChooserActivity$ImportDirectoryChooserActivity");
        assertResolvable("de.dennisguse.opentracks.io.file.importer.DirectoryChooserActivity$ExportDirectoryChooserActivity");
        assertResolvable("de.dennisguse.opentracks.io.file.importer.DirectoryChooserActivity$ExportDirectoryChooserOneFileActivity");
        assertResolvable("de.dennisguse.opentracks.io.file.importer.DirectoryChooserActivity$DefaultTrackExportDirectoryChooserActivity");
    }

    private void assertResolvable(String activityClassName) throws PackageManager.NameNotFoundException {
        String applicationId = context.getString(R.string.applicationId);
        ComponentName componentName = new ComponentName(applicationId, activityClassName);

        ActivityInfo activityInfo = context.getPackageManager().getActivityInfo(componentName, 0);
        assertEquals(applicationId, activityInfo.packageName);

        ResolveInfo resolveInfo = context.getPackageManager().resolveActivity(new Intent().setComponent(componentName), 0);
        assertNotNull(resolveInfo);
    }
}
