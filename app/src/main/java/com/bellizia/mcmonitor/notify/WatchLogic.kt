package com.bellizia.mcmonitor.notify

import com.bellizia.mcmonitor.data.ServerConfig

/** Ultimo stato conosciuto di un server. `null` = mai osservato. */
data class WatchState(
    val reachable: Boolean? = null,
    val running: Boolean? = null,
    val players: Set<String>? = null
)

/** Cosa il server risulta essere in questo momento. */
data class Observation(
    val reachable: Boolean,
    val running: Boolean? = null,
    val players: Set<String>? = null
)

data class WatchEvent(val channel: String, val title: String, val text: String)

/**
 * Decide quali notifiche mandare confrontando lo stato precedente con quello
 * appena osservato. È logica pura, separata dal servizio, così i casi delicati
 * — il primo giro, il server fermo, gli eventi disattivati — si possono provare.
 */
object WatchLogic {

    fun evaluate(
        server: ServerConfig,
        previous: WatchState,
        now: Observation
    ): Pair<WatchState, List<WatchEvent>> {
        val events = mutableListOf<WatchEvent>()
        val name = server.displayName

        if (!now.reachable) {
            if (previous.reachable == true && server.notifyOffline) {
                events += WatchEvent(
                    Notifications.CHANNEL_STATUS,
                    "$name: irraggiungibile",
                    "Nessuna risposta SSH da ${server.host}. La macchina potrebbe essere spenta."
                )
            }
            // Perdendo il contatto si dimenticano i giocatori: al ritorno si
            // riparte da una fotografia nuova invece di annunciare finti rientri.
            return WatchState(reachable = false, running = null, players = null) to events
        }

        if (previous.reachable == false && server.notifyOnline) {
            events += WatchEvent(
                Notifications.CHANNEL_STATUS,
                "$name: di nuovo raggiungibile",
                "La connessione SSH funziona. Server ${if (now.running == true) "avviato" else "fermo"}."
            )
        }

        val running = now.running
        if (previous.running != null && running != null && previous.running != running) {
            if (!running && server.notifyOffline) {
                events += WatchEvent(
                    Notifications.CHANNEL_STATUS,
                    "$name: server offline",
                    "LinuxGSM riporta il server fermo."
                )
            }
            if (running && server.notifyOnline) {
                events += WatchEvent(
                    Notifications.CHANNEL_STATUS,
                    "$name: server online",
                    "Il server è tornato attivo."
                )
            }
        }

        if (running != true) {
            return WatchState(reachable = true, running = running, players = null) to events
        }

        val players = now.players
        if (players == null) {
            return WatchState(true, true, previous.players) to events
        }

        val before = previous.players
        if (before != null) {
            val joined = players - before
            val left = before - players
            if (joined.isNotEmpty() && server.notifyJoin) {
                events += WatchEvent(
                    Notifications.CHANNEL_PLAYERS,
                    if (joined.size == 1) "${joined.first()} è entrato" else "${joined.size} giocatori entrati",
                    "$name: ${joined.joinToString(", ")}\nOnline ora: ${players.size}"
                )
            }
            if (left.isNotEmpty() && server.notifyLeave) {
                events += WatchEvent(
                    Notifications.CHANNEL_PLAYERS,
                    if (left.size == 1) "${left.first()} è uscito" else "${left.size} giocatori usciti",
                    "$name: ${left.joinToString(", ")}\nOnline ora: ${players.size}"
                )
            }
        }
        return WatchState(true, true, players) to events
    }
}
