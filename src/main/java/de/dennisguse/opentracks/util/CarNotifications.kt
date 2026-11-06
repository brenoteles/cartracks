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
    private const val CHANNEL_CAR = "cartracks_car_bluetooth"
    private const val ID_AUTO_STOP = 2001
    private const val ID_CAR_CONNECTED = 2002
    private const val ID_MAINTENANCE_BASE = 3000

    private fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_TRIPS, "Viagens", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_MAINTENANCE, "Lembretes de manutenção", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_CAR, "Carro conectado", NotificationManager.IMPORTANCE_HIGH))
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
    @JvmOverloads
    fun autoStopped(context: Context, text: String = "O carro ficou parado por 10 minutos. Viagem e local de estacionamento salvos.") {
        if (!canPost(context)) return
        ensureChannels(context)
        val n = NotificationCompat.Builder(context, CHANNEL_TRIPS)
            .setSmallIcon(R.drawable.ic_logo_color_24dp)
            .setContentTitle("Viagem encerrada automaticamente")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(ID_AUTO_STOP, n)
    }

    /** The car's Bluetooth connected: one tap opens the app and starts the trip. */
    @JvmStatic
    fun carConnected(context: Context, carName: String) {
        if (!canPost(context)) return
        ensureChannels(context)
        val start = de.dennisguse.opentracks.services.QuickControls.startIntent(context)
        val n = NotificationCompat.Builder(context, CHANNEL_CAR)
            .setSmallIcon(R.drawable.ic_logo_color_24dp)
            .setContentTitle("$carName conectado")
            .setContentText("Toque para iniciar a viagem")
            .setContentIntent(start)
            .addAction(0, "Iniciar viagem", start)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setTimeoutAfter(15 * 60 * 1000L)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(ID_CAR_CONNECTED, n)
    }

    @JvmStatic
    fun clearCarConnected(context: Context) = NotificationManagerCompat.from(context).cancel(ID_CAR_CONNECTED)

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
