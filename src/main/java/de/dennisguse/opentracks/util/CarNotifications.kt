package de.dennisguse.opentracks.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import de.dennisguse.opentracks.CarMainActivity
import de.dennisguse.opentracks.R

/** User-facing notifications that are not the recording foreground notification. */
object CarNotifications {

    private const val CHANNEL_TRIPS = "cartracks_trips"
    private const val CHANNEL_MAINTENANCE = "cartracks_maintenance"
    private const val ID_AUTO_STOP = 2001
    private const val ID_MAINTENANCE_BASE = 3000

    private fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_TRIPS, "Viagens", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_MAINTENANCE, "Lembretes de manutenção", NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, CarMainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    @JvmStatic
    fun autoStopped(context: Context) {
        if (!canPost(context)) return
        ensureChannels(context)
        val n = NotificationCompat.Builder(context, CHANNEL_TRIPS)
            .setSmallIcon(R.drawable.ic_logo_color_24dp)
            .setContentTitle("Viagem encerrada automaticamente")
            .setContentText("O carro ficou parado por 10 minutos. Viagem e local de estacionamento salvos.")
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(ID_AUTO_STOP, n)
    }

    fun maintenanceDue(context: Context, id: Int, title: String, text: String) {
        if (!canPost(context)) return
        ensureChannels(context)
        val n = NotificationCompat.Builder(context, CHANNEL_MAINTENANCE)
            .setSmallIcon(R.drawable.ic_logo_color_24dp)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(ID_MAINTENANCE_BASE + id, n)
    }
}
