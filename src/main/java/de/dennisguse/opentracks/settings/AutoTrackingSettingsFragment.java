package de.dennisguse.opentracks.settings;

import android.os.Bundle;
import androidx.preference.PreferenceFragmentCompat;
import de.dennisguse.opentracks.R;

public class AutoTrackingSettingsFragment extends PreferenceFragmentCompat {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.settings_auto_tracking);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getActivity() instanceof SettingsActivity) {
            ((SettingsActivity) getActivity()).getSupportActionBar().setTitle(R.string.settings_auto_tracking_title);
        }
    }
}