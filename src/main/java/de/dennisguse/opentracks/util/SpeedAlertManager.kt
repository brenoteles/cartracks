package de.dennisguse.opentracks.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class SpeedAlertManager(private val context: Context) {

    private var toneGenerator: ToneGenerator? = null
    private var vibrator: Vibrator? = null
    private var lastAlertTimeMs: Long = 0

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Checks if current speed exceeds configured limit and emits non-intrusive sound/vibration with cooldown.
     * @return true if speed limit is exceeded (for UI indicators)
     */
    fun checkSpeed(currentSpeedKmh: Double, speedLimitKmh: Int, isEnabled: Boolean): Boolean {
        if (!isEnabled || speedLimitKmh <= 0) return false
        val isExceeded = currentSpeedKmh > speedLimitKmh

        if (isExceeded) {
            val now = System.currentTimeMillis()
            if (now - lastAlertTimeMs > 4000) { // 4 seconds cooldown
                lastAlertTimeMs = now
                triggerBeepAndVibrate()
            }
        }
        return isExceeded
    }

    private fun triggerBeepAndVibrate() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 250)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(300)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun release() {
        try {
            toneGenerator?.release()
            toneGenerator = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
