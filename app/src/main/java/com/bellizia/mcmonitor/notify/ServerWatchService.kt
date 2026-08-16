package com.bellizia.mcmonitor.notify

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.bellizia.mcmonitor.data.McRepository
import com.bellizia.mcmonitor.data.Prefs
import com.bellizia.mcmonitor.data.ServerConfig
import com.bellizia.mcmonitor.lgsm.Lgsm
import com.bellizia.mcmonitor.ssh.SshManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Controlla periodicamente i server con le notifiche attive e avvisa quando
 * cambia qualcosa.
 *
 * È un servizio in primo piano perché Android non consente lavoro periodico
 * ravvicinato in background, e per "è entrato un giocatore" non esiste alcuna
 * push dal server: l'unica strada è interrogarlo noi.
 */
class ServerWatchService : Service() {

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, ServerWatchService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ServerWatchService::class.java))
        }

        /** Avvia o ferma in base a quanti server hanno il monitoraggio acceso. */
        fun sync(context: Context) {
            if (Prefs.servers().any { it.watching }) start(context) else stop(context)
        }
    }

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private val watches = mutableMapOf<String, WatchState>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Notifications.createChannels(this)
        startInForeground(describe())
        if (job.children.none { it.isActive }) scope.launch { loop() }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startInForeground(text: String) {
        val notification = Notifications.service(this, text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(Notifications.ID_SERVICE, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(Notifications.ID_SERVICE, notification)
        }
    }

    private fun describe(): String {
        val servers = Prefs.servers().filter { it.watching }
        return when (servers.size) {
            0 -> "Nessun server da controllare"
            1 -> "Controllo ${servers.first().displayName}"
            else -> "Controllo ${servers.size} server"
        }
    }

    private suspend fun loop() {
        while (scope.isActive) {
            val servers = Prefs.servers().filter { it.watching }
            if (servers.isEmpty()) {
                stopSelf()
                return
            }
            servers.forEach { server ->
                if (!scope.isActive) return
                runCatching { check(server) }
            }
            // L'intervallo più corto fra i server decide il ritmo del giro.
            val wait = servers.minOf { it.notifySeconds }.coerceIn(30, 3600)
            delay(wait * 1000L)
        }
    }

    /** Osserva il server e lascia decidere a [WatchLogic] cosa vale una notifica. */
    private suspend fun check(server: ServerConfig) {
        val previous = watches[server.id] ?: WatchState()

        val details = runCatching { SshManager.exec(server, Lgsm.details(server), 45_000) }
        val observation = if (details.isFailure) {
            Observation(reachable = false)
        } else {
            val status = Lgsm.parseStatus(Lgsm.clean(details.getOrThrow().text)).orEmpty()
            val running = status.contains("STARTED", true) || status.contains("ONLINE", true)
            val players = if (running && (server.notifyJoin || server.notifyLeave)) {
                runCatching { McRepository.online(server, withPositions = false).names.toSet() }
                    .getOrNull()
            } else null
            Observation(reachable = true, running = running, players = players)
        }

        val (state, events) = WatchLogic.evaluate(server, previous, observation)
        watches[server.id] = state
        events.forEach { Notifications.event(this, it.channel, it.title, it.text) }
    }

}
