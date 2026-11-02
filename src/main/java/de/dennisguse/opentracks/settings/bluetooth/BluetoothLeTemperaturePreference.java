package de.dennisguse.opentracks.settings.bluetooth;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.PreferenceDialogFragmentCompat;

import java.util.List;

import de.dennisguse.opentracks.sensors.driver.TemperatureBluetooth;

public class BluetoothLeTemperaturePreference extends BluetoothLeSensorPreference {

    public BluetoothLeTemperaturePreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public BluetoothLeTemperaturePreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public BluetoothLeTemperaturePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public BluetoothLeTemperaturePreference(Context context) {
        super(context);
    }

    @Override
    public PreferenceDialogFragmentCompat createInstance() {
        return BluetoothLeSensorPreferenceDialog
                .newInstance(getKey(), List.of(TemperatureBluetooth.TEMPERATURE));
    }
}