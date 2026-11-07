package de.dennisguse.opentracks.settings;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import de.dennisguse.opentracks.R;

@RunWith(AndroidJUnit4.class)
public class PreferencesOpenHelperTest {

    private final Context context = ApplicationProvider.getApplicationContext();

    @Test
    public void test_check_updatesLastVersion() {
        SharedPreferences sharedPreferences = PreferencesUtils.getDefaultSharedPreferences(context);
        sharedPreferences.edit().putInt(context.getString(R.string.prefs_last_version_key), 0).commit();

        PreferencesOpenHelper.newInstance(2).check();

        assertEquals(2, sharedPreferences.getInt(context.getString(R.string.prefs_last_version_key), -1));
    }
}
