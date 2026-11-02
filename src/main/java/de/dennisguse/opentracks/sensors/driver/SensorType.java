package de.dennisguse.opentracks.sensors.driver;

public enum SensorType {
    NONE("NONE"), //TODO Use R.string.sensor_type_value_none
    INTERNAL("INTERNAL"), //TODO Use R.string.sensor_type_value_none

    //NOTE: preferenceValue of REMOTE should not be used anywhere.
    REMOTE("*");

    private final String preferenceValue;

    SensorType(String preferenceValue) {
        this.preferenceValue = preferenceValue;
    }

    public String getPreferenceValue() {
        return preferenceValue;
    }
}
