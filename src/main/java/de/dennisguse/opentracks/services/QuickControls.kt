package de.dennisguse.opentracks.services

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.RemoteViews
import de.dennisguse.opentracks.CarMainActivity
import de.dennisguse.opentracks.R

/**
 * Home-screen widget and Quick Settings tile that start/stop a trip. Both open CarMainActivity
 * with [ACTION_TOGGLE]: on Android 14+ a location foreground service can only start while the
 * app is visible, so the activity performs the toggle.
 */
object QuickControls {
    const val ACTION_TOGGLE = "de.dennisguse.cartracks.action.TOGGLE_RECORDING"
    private const val PREFS = "CarTracksPrefs"
    private const val KEY = "quick_is_recording"

    fun isRecording(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    /** Called by the recording service whenever a trip starts or ends. */
    @JvmStatic
    fun setRecording(context: Context, recording: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, recording).apply()
        RecordWidget.refresh(context)
        TileService.requestListeningState(context, ComponentName(context, RecordTileService::class.java))
    }

    fun toggleIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 42,
        Intent(context, CarMainActivity::class.java)
            .setAction(ACTION_TOGGLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}

class RecordWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, views(context)) }
    }

    companion object {
        fun views(context: Context): RemoteViews {
            val recording = QuickControls.isRecording(context)
            return RemoteViews(context.packageName, R.layout.widget_record).apply {
                setTextViewText(R.id.widget_state, if (recording) "● GRAVANDO" else "PARADO")
                setTextColor(R.id.widget_state, if (recording) 0xFFE14822.toInt() else 0xFFBDC1A3.toInt())
                setTextViewText(R.id.widget_action, if (recording) "■  ENCERRAR VIAGEM" else "▶  INICIAR VIAGEM")
                setInt(R.id.widget_action, "setBackgroundColor", if (recording) 0xFFE14822.toInt() else 0xFFE8FF3A.toInt())
                setTextColor(R.id.widget_action, if (recording) 0xFFFFFFFF.toInt() else 0xFF0C0C14.toInt())
                setOnClickPendingIntent(R.id.widget_root, QuickControls.toggleIntent(context))
            }
        }

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, RecordWidget::class.java))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, views(context))
        }
    }
}

class RecordTileService : TileService() {
    override fun onStartListening() {
        val tile = qsTile ?: return
        val recording = QuickControls.isRecording(this)
        tile.state = if (recording) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = if (recording) "Encerrar viagem" else "Iniciar viagem"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = if (recording) "Gravando" else "CarTracks"
        tile.icon = Icon.createWithResource(this, R.drawable.ic_logo_color_24dp)
        tile.updateTile()
    }

    override fun onClick() {
        // From the lock screen, ask to unlock first; otherwise the activity cannot open.
        if (isLocked) unlockAndRun { launchToggle() } else launchToggle()
    }

    private fun launchToggle() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(QuickControls.toggleIntent(this))
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(
                Intent(this, CarMainActivity::class.java).setAction(QuickControls.ACTION_TOGGLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
