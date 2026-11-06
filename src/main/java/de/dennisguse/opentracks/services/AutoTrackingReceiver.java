package de.dennisguse.opentracks.services;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import de.dennisguse.opentracks.util.CarBluetooth;
import de.dennisguse.opentracks.util.CarNotifications;

/**
 * Reacts only to the Bluetooth device the user picked as "the car" (Ajustes). Connecting offers a
 * one-tap "Iniciar viagem" notification: Android 14+ only lets a location foreground service start
 * while the app is visible. Disconnecting (engine off) ends the trip and saves the parking spot.
 */
public class AutoTrackingReceiver extends BroadcastReceiver {
    private static final String TAG = AutoTrackingReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        String carAddress = CarBluetooth.INSTANCE.address(context);
        if (device == null || carAddress == null || !carAddress.equalsIgnoreCase(device.getAddress())) {
            return;
        }

        String action = intent.getAction();
        boolean recording = QuickControls.INSTANCE.isRecording(context);
        Log.i(TAG, "Car Bluetooth " + action + ", recording=" + recording);

        if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action) && !recording) {
            CarNotifications.carConnected(context, CarBluetooth.INSTANCE.name(context));
        } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
            CarNotifications.clearCarConnected(context);
            if (recording) {
                // receivers may not bind services themselves; the running recording service keeps the app alive
                TrackRecordingServiceConnection.execute(context.getApplicationContext(), (service, connection) ->
                        service.endTripAndPark("Bluetooth do carro desligado. Viagem e local de estacionamento salvos."));
            }
        }
    }
}
