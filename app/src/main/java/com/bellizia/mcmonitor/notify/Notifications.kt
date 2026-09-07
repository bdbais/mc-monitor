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
    const val CHANNEL_CHAT = "messaggi-admin"
    const val CHANNEL_SICUREZZA = "sicurezza"

    const val ID_SERVICE = 1

    /** Sempre la stessa: i messaggi non letti sono un avviso solo, che si aggiorna. */
    const val ID_CHAT = 2

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
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CHAT,
                "Messaggi fra amministratori",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Messaggi lasciati dagli altri amministratori del server" }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SICUREZZA,
                "Controllo di sicurezza",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Il controllo settimanale su quanto e' chiuso il server"
            }
        )
    }

    /**
     * Messaggi non letti degli altri amministratori.
     *
     * Il numero passato con setNumber e' quello che i lanciatori mostrano sul
     * pallino accanto all'icona: Android non ha un modo diretto per scriverci
     * sopra, il conteggio arriva sempre da una notifica.
     */
    fun adminMessages(context: Context, count: Int, preview: String) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (count <= 0) {
            manager.cancel(ID_CHAT)
            return
        }
        val titolo = if (count == 1) "Un messaggio dagli admin" else "$count messaggi dagli admin"
        manager.notify(
            ID_CHAT,
            NotificationCompat.Builder(context, CHANNEL_CHAT)
                .setContentTitle(titolo)
                .setContentText(preview)
                .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(openApp(context))
                .setNumber(count)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
        )
    }

    fun clearAdminMessages(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(ID_CHAT)
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
