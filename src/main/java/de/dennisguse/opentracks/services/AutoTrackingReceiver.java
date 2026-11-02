package de.dennisguse.opentracks.services;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.preference.PreferenceManager;
import de.dennisguse.opentracks.R;

public class AutoTrackingReceiver extends BroadcastReceiver {
    private static final String TAG = AutoTrackingReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        String action = intent.getAction();
        Log.i(TAG, "Received action: " + action);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        boolean autoStartPower = prefs.getBoolean(context.getString(R.string.settings_auto_start_power_key), true);
        boolean autoStopPower = prefs.getBoolean(context.getString(R.string.settings_auto_stop_power_key), true);
        boolean autoStartBt = prefs.getBoolean(context.getString(R.string.settings_auto_start_bluetooth_key), false);

        if (Intent.ACTION_POWER_CONNECTED.equals(action)) {
            if (autoStartPower) {
                Log.i(TAG, "Power connected: starting car tracking");
                startRecording(context);
            }
        } else if (Intent.ACTION_POWER_DISCONNECTED.equals(action)) {
            if (autoStopPower) {
                Log.i(TAG, "Power disconnected: stopping car tracking");
                stopRecording(context);
            }
        } else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
            if (autoStartBt) {
                Log.i(TAG, "Bluetooth connected: starting car tracking");
                startRecording(context);
            }
        } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
            if (autoStartBt) {
                Log.i(TAG, "Bluetooth disconnected: stopping car tracking");
                stopRecording(context);
            }
        }
    }

    private void startRecording(Context context) {
        TrackRecordingServiceConnection.executeForeground(context, (service, connection) -> {
            if (!service.isRecording()) {
                service.startNewTrack();
            }
        });
    }

    private void stopRecording(Context context) {
        TrackRecordingServiceConnection.execute(context, (service, connection) -> {
            if (service.isRecording()) {
                service.endCurrentTrack();
            }
        });
    }
}