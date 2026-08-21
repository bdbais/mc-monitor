package com.bellizia.mcmonitor.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.bellizia.mcmonitor.R
import com.bellizia.mcmonitor.ui.HomeActivity

/**
 * Canali e costruzione delle notifiche. Stato del server e movimenti dei
 * giocatori stanno su canali distinti: così Android permette di silenziare gli
 * uni senza perdere gli altri.
 */
object Notifications {

    const val CHANNEL_SERVICE = "monitoraggio"
    const val CHANNEL_STATUS = "stato-server"
    const val CHANNEL_PLAYERS = "giocatori"

    const val ID_SERVICE = 1

    private var nextId = 100

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SERVICE,
                "Monitoraggio attivo",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Avviso permanente mentre l'app controlla i server" }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_STATUS,
                "Stato del server",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Server andato offline o tornato online" }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PLAYERS,
                "Giocatori",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Entrate e uscite dei giocatori" }
        )
    }

    /** Notifica permanente del servizio: dice cosa sta facendo e su quanti server. */
    fun service(context: Context, text: String): Notification =
        NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setContentTitle("MC Monitor")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openApp(context))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    fun event(context: Context, channel: String, title: String, text: String) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val notification = NotificationCompat.Builder(context, channel)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .setPriority(
                if (channel == CHANNEL_STATUS) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT
            )
            .build()
        manager.notify(nextId++, notification)
    }

    private fun openApp(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, HomeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )
}
