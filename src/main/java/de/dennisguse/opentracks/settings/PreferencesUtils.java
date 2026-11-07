/*
 * Copyright 2012 Google Inc.
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

package de.dennisguse.opentracks.settings;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;

import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.color.DynamicColors;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.data.models.ActivityType;
import de.dennisguse.opentracks.data.models.Distance;
import de.dennisguse.opentracks.data.models.Track;
import de.dennisguse.opentracks.sensors.driver.SensorType;

/**
 * Utilities to access preferences stored in {@link SharedPreferences}.
 *
 * @author Jimmy Shih
 */
public class PreferencesUtils {

    private static final int PREFERENCES_VERSION = 2;

    private PreferencesUtils() {
    }

    private static SharedPreferences sharedPreferences;

    private static Resources resources;

    /**
     * Must be called during application startup.
     */
    public static void initPreferences(final Context context) {
        PreferencesUtils.resources = context.getResources();
        PreferencesUtils.sharedPreferences = getDefaultSharedPreferences(context);

        PreferencesOpenHelper.newInstance(PREFERENCES_VERSION).check();
    }

    /**
     * Same file androidx.preference's PreferenceManager.getDefaultSharedPreferences() used, so installed settings are kept.
     */
    public static SharedPreferences getDefaultSharedPreferences(Context context) {
        return context.getSharedPreferences(context.getPackageName() + "_preferences", Context.MODE_PRIVATE);
    }

    public static void registerOnSharedPreferenceChangeListener(SharedPreferences.OnSharedPreferenceChangeListener changeListener) {
        sharedPreferences.registerOnSharedPreferenceChangeListener(changeListener);
        changeListener.onSharedPreferenceChanged(sharedPreferences, null);
    }

    public static void unregisterOnSharedPreferenceChangeListener(SharedPreferences.OnSharedPreferenceChangeListener changeListener) {
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(changeListener);
    }

    public static String getDefaultActivityTypeLocalized() {
        return getString(R.string.default_activity_key, resources.getString(R.string.default_activity_default));
    }

    /**
     * Gets a preference key
     *
     * @param keyId the key id
     */
    private static String getKey(int keyId) {
        return resources.getString(keyId);
    }

    /**
     * Compares if keyId and key belong to the same shared preference key.
     *
     * @param keyId The resource id of the key
     * @param key   The key of the preference
     * @return true if key == null or key belongs to keyId
     */
    public static boolean isKey(int keyId, String key) {
        return key == null || key.equals(getKey(keyId));
    }

    public static boolean isKey(List<Integer> keyIds, String key) {
        for (int keyId : keyIds) {
            if (isKey(keyId, key)) {
                return true;
            }
        }
        return false;
    }

    private static boolean getBoolean(int keyId, boolean defaultValue) {
        return sharedPreferences.getBoolean(getKey(keyId), defaultValue);
    }

    static int getInt(int keyId, int defaultValue) {
        try {
            return sharedPreferences.getInt(getKey(keyId), defaultValue);
        } catch (ClassCastException e) {
            //Ignore
        }

        //NOTE: We assume that the data was stored as String due to use of ListPreference.
        try {
            String stringValue = sharedPreferences.getString(getKey(keyId), null);
            return Integer.parseInt(stringValue);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static float getFloat(int keyId, float defaultValue) {
        try {
            return sharedPreferences.getFloat(getKey(keyId), defaultValue);
        } catch (ClassCastException e) {
            //Ignore
        }

        //NOTE: We assume that the data was stored as String due to use of ListPreference.
        try {
            String stringValue = sharedPreferences.getString(getKey(keyId), null);
            return Float.parseFloat(stringValue);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static long getLong(int keyId, long defaultValue) {
        try {
            return sharedPreferences.getLong(getKey(keyId), defaultValue);
        } catch (ClassCastException e) {
            // Ignore.
        }

        try {
            String stringValue = sharedPreferences.getString(getKey(keyId), null);
            return Long.parseLong(stringValue);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static String getString(int keyId, String defaultValue) {
        return sharedPreferences.getString(getKey(keyId), defaultValue);
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    public static void setString(int keyId, String value) {
        Editor editor = sharedPreferences.edit();
        editor.putString(getKey(keyId), value);
        editor.apply();
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    public static void setString(int keyId, int valueId) {
        setString(keyId, resources.getString(valueId));
    }

    static void setInt(int keyId, int value) {
        Editor editor = sharedPreferences.edit();
        editor.putInt(getKey(keyId), value);
        editor.apply();
    }

    public static UnitSystem getUnitSystem() {
        final String STATS_UNIT_DEFAULT = resources.getString(R.string.stats_units_default);

        final String VALUE = getString(R.string.stats_units_key, STATS_UNIT_DEFAULT);
        return Arrays.stream(UnitSystem.values())
                .filter(d -> VALUE.equals(resources.getString(d.getPreferenceId(), STATS_UNIT_DEFAULT)))
                .findFirst()
                .orElse(UnitSystem.defaultUnitSystem()); //TODO This AGAIN defines the default
    }

    public static void setUnit(UnitSystem unitSystem) {
        setString(R.string.stats_units_key, unitSystem.getPreferenceId());
    }

    //TODO Check if actually needed or can be superseeded by a flexible default in getUnit()
    public static void applyDefaultUnit() {
        if (getString(R.string.stats_units_key, "").isEmpty()) {
            if (!Locale.US.equals(Locale.getDefault())) {
                setUnit(UnitSystem.METRIC);
            } else {
                setUnit(UnitSystem.IMPERIAL_FEET);
            }
        }
    }

    public static boolean isReportSpeed(String activityTypeLocalized) {
        final String STATS_RATE_DEFAULT = resources.getString(R.string.stats_rate_default);
        String currentStatsRate = getString(R.string.stats_rate_key, STATS_RATE_DEFAULT);
        if (currentStatsRate.equals(getString(R.string.stats_rate_speed_or_pace_default, STATS_RATE_DEFAULT))) {
            return ActivityType.findByLocalizedString(resources, activityTypeLocalized)
                    .isShowSpeedPreferred();
        }

        return currentStatsRate.equals(resources.getString(R.string.stats_rate_speed));
    }

    public static boolean isReportSpeed(Track track) {
        return isReportSpeed(track.activityTypeLocalized());
    }

    public static SensorType getSensorType(String address) {
        return Arrays.stream(SensorType.values())
                .filter(it -> it.getPreferenceValue().equals(address))
                .findFirst()
                .orElse(SensorType.REMOTE);
    }

    public static boolean shouldShowStatsOnLockscreen() {
        final boolean STATS_SHOW_ON_LOCKSCREEN_DEFAULT = resources.getBoolean(R.bool.stats_show_on_lockscreen_while_recording_default);
        return getBoolean(R.string.stats_show_on_lockscreen_while_recording_key, STATS_SHOW_ON_LOCKSCREEN_DEFAULT);
    }

    public static boolean shouldKeepScreenOn() {
        final boolean DEFAULT = resources.getBoolean(R.bool.stats_keep_screen_on_while_recording_default);
        return getBoolean(R.string.stats_keep_screen_on_while_recording_key, DEFAULT);
    }

    public static boolean shouldUseFullscreen() {
        final boolean DEFAULT = resources.getBoolean(R.bool.stats_fullscreen_while_recording_default);
        return getBoolean(R.string.stats_fullscreen_while_recording_key, DEFAULT);
    }

    public static Distance getRecordingDistanceInterval() {
        return Distance.of(getInt(R.string.recording_distance_interval_key, getRecordingDistanceIntervalDefaultInternal()));
    }

    private static int getRecordingDistanceIntervalDefaultInternal() {
        return Integer.parseInt(resources.getString(R.string.recording_distance_interval_default));
    }

    public static Distance getMaxRecordingDistance() {
        final int MAX_RECORDING_DISTANCE = Integer.parseInt(resources.getString(R.string.max_recording_distance_default));
        return Distance.of(getInt(R.string.max_recording_distance_key, MAX_RECORDING_DISTANCE));
    }

    //TODO Rename to getGpsMinSamplingInterval
    public static Duration getMinSamplingInterval() {
        final Duration MIN_SAMPLING_INTERVAL = getMinSamplingIntervalDefault();
        return Duration.ofSeconds(getInt(R.string.min_sampling_interval_key, (int) MIN_SAMPLING_INTERVAL.getSeconds()));
    }

    public static Duration getMinSamplingIntervalDefault() {
        return Duration.ofSeconds(Integer.parseInt(resources.getString(R.string.min_sampling_interval_default)));
    }

    public static Distance getThresholdHorizontalAccuracy() {
        final int RECORDING_GPS_ACCURACY = Integer.parseInt(resources.getString(R.string.recording_gps_accuracy_default));
        return Distance.of(getInt(R.string.recording_gps_accuracy_key, RECORDING_GPS_ACCURACY));
    }

    public static Duration getIdleDurationTimeout() {
        final int DEFAULT = Integer.parseInt(resources.getString(R.string.idle_duration_default));
        int value = getInt(R.string.idle_duration_key, DEFAULT);
        return Duration.ofSeconds(value);
    }

    record ThemeConfig(int themeResourceId, int dayNight, boolean dynamicColor) {
    }

    private static ThemeConfig getThemeMode() {
        final String defaultValue = getKey(R.string.theme_default);
        final String value = getString(R.string.theme_key, defaultValue);

        switch (value) {
            case "0" -> {
                return new ThemeConfig(R.style.DayNightColorTheme, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, false);
            }
            case "1" -> {
                return new ThemeConfig(R.style.DayNightDynamicTheme, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, true);
            }
            case "2" -> {
                return new ThemeConfig(R.style.DayNightColorTheme, AppCompatDelegate.MODE_NIGHT_NO, false);
            }
            case "3" -> {
                return new ThemeConfig(R.style.DayNightDynamicTheme, AppCompatDelegate.MODE_NIGHT_NO, true);
            }
            case "4" -> {
                return new ThemeConfig(R.style.DayNightColorTheme, AppCompatDelegate.MODE_NIGHT_YES, false);
            }
            case "5" -> {
                return new ThemeConfig(R.style.DayNightDynamicTheme, AppCompatDelegate.MODE_NIGHT_YES, true);
            }
            case "6" -> {
                return new ThemeConfig(R.style.NightOledColorTheme, AppCompatDelegate.MODE_NIGHT_YES, false);
            }
            case "7" -> {
                return new ThemeConfig(R.style.NightOledDynamicTheme, AppCompatDelegate.MODE_NIGHT_YES, true);
            }

            default -> {
                return new ThemeConfig(R.style.DayNightColorTheme, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, false);
            }
        }
    }

    public static void applyTheme(Context context) {
        context.setTheme(getThemeMode().themeResourceId());
    }

    public static void applyNightMode() {
        ThemeConfig themeConfig = getThemeMode();

        AppCompatDelegate.setDefaultNightMode(themeConfig.dayNight);
    }

    public static void applyNightModeAndDynamicColors(Application application) {
        ThemeConfig themeConfig = getThemeMode();

        applyNightMode();

        if (application != null && themeConfig.dynamicColor)
            DynamicColors.applyToActivitiesIfAvailable(application);
    }

    /**
     * Every getter passes its own default, so nothing has to be written up front. The old
     * PreferenceManager.setDefaultValues(R.xml.settings_*) inflated OpenTracks preference
     * screens whose classes no longer exist and crashed a fresh install.
     */
    public static void resetPreferences(Context context, boolean readAgain) {
        if (readAgain) {
            sharedPreferences.edit().clear().commit();
        }
    }

    public static int getTotalRowsDeleted() {
        return getInt(R.string.total_rows_deleted_key, 0);
    }

    public static void addTotalRowsDeleted(final int totalRowsDeletedToAdd) {
        int newTotalRowsDeleted = getTotalRowsDeleted() + totalRowsDeletedToAdd;
        setInt(R.string.total_rows_deleted_key, newTotalRowsDeleted);
    }

    public static void resetTotalRowsDeleted() {
        setInt(R.string.total_rows_deleted_key, 0);
    }

}
