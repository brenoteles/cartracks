package de.dennisguse.opentracks.settings;

import de.dennisguse.opentracks.R;

class PreferencesOpenHelper {

    private final int version;

    private PreferencesOpenHelper(int version) {
        this.version = version;
    }

    static PreferencesOpenHelper newInstance(int version) {
        return new PreferencesOpenHelper(version);
    }

    void check() {
        int lastVersion = PreferencesUtils.getInt(R.string.prefs_last_version_key, 0);
        if (version > lastVersion) {
            onUpgrade();
        } else if (version < lastVersion) {
            onDowngrade();
        }
    }

    private void onUpgrade() {
        PreferencesUtils.setInt(R.string.prefs_last_version_key, version);
        for (int i = 1; i <= version; i++) {
            switch (i) {
                case 1 -> upgradeFrom0to1();
                case 2 -> upgradeFrom1to2();
                default -> throw new RuntimeException("Not implemented: upgrade to " + version);
            }
        }
    }

    private void upgradeFrom0to1() {
        // No-op: customizable recording layout was removed.
    }

    private void upgradeFrom1to2() {
        // No-op: customizable recording layout was removed.
    }

    private void onDowngrade() {
        // No-op: customizable recording layout was removed.
    }
}
