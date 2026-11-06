package de.dennisguse.opentracks.util

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** The Bluetooth device (car stereo / multimedia) that marks "in the car". */
object CarBluetooth {
    private const val PREFS = "CarTracksPrefs"
    private const val KEY_ADDRESS = "car_bt_address"
    private const val KEY_NAME = "car_bt_name"

    fun address(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ADDRESS, null)

    fun name(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_NAME, null) ?: "Carro"

    fun save(context: Context, address: String?, name: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ADDRESS, address)
            .putString(KEY_NAME, name)
            .apply()
    }

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    /** Paired devices as (address, name), sorted by name; empty without permission or Bluetooth. */
    @SuppressLint("MissingPermission")
    fun pairedDevices(context: Context): List<Pair<String, String>> {
        if (!hasPermission(context)) return emptyList()
        return runCatching {
            context.getSystemService(BluetoothManager::class.java)?.adapter?.bondedDevices.orEmpty()
                .map { it.address to (it.name ?: it.address) }
                .sortedBy { it.second.lowercase() }
        }.getOrDefault(emptyList())
    }
}
