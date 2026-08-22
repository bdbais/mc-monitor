package com.bellizia.mcmonitor.data

import com.bellizia.mcmonitor.lgsm.Admin
import com.bellizia.mcmonitor.lgsm.AdminMessage
import com.bellizia.mcmonitor.lgsm.Presence
import com.bellizia.mcmonitor.ssh.SshManager

/**
 * Chi sta amministrando lo stesso server, e la chat per mettersi d'accordo.
 *
 * Tutto passa dal computer del server: nessun servizio esterno, nessun account.
 * I comandi sono minuscoli — scrivere un file, leggerne una manciata — e possono
 * fallire in silenzio: se il segnale non parte, l'unica conseguenza è che gli
 * altri non ti vedono, e non vale la pena fermare quello che si stava facendo.
 */
object PresenceRepository {

    private fun cfg() = Prefs.load()

    /** Si fa vivo, eventualmente dichiarando cosa sta facendo di delicato. */
    suspend fun heartbeat(doing: String = "") {
        val c = cfg()
        if (!c.isComplete) return
        runCatching {
            SshManager.exec(c, Presence.heartbeat(Prefs.deviceId, Prefs.adminName, doing), 15_000)
        }
    }

    /** Toglie il proprio segnale: chiudendo l'app o cambiando server. */
    suspend fun goodbye() {
        val c = cfg()
        if (!c.isComplete) return
        runCatching { SshManager.exec(c, Presence.goodbye(Prefs.deviceId), 10_000) }
    }

    suspend fun admins(): List<Admin> {
        val c = cfg()
        if (!c.isComplete) return emptyList()
        val r = SshManager.exec(c, Presence.list(), 20_000)
        return Presence.parseAdmins(r.text)
    }

    /** Chi altro è collegato adesso, escluso questo telefono. */
    suspend fun others(): List<Admin> =
        runCatching { admins() }.getOrDefault(emptyList())
            .filter { it.id != Presence.safeId(Prefs.deviceId) && it.active }

    suspend fun messages(): List<AdminMessage> {
        val c = cfg()
        if (!c.isComplete) return emptyList()
        val r = SshManager.exec(c, Presence.read(), 20_000)
        return Presence.parseMessages(r.text)
    }

    /**
     * [about] lega il messaggio a un giocatore (le note su ban e whitelist),
     * [broadcast] lo segna come "a tutti": gli altri vengono avvisati invece di
     * trovarlo per caso.
     */
    suspend fun send(text: String, about: String = "", broadcast: Boolean = false) {
        val c = cfg()
        if (!c.isComplete || text.isBlank()) return
        SshManager.exec(
            c,
            Presence.send(Prefs.deviceId, Prefs.adminName, text, about, broadcast),
            20_000
        )
    }

    /** Le note lasciate dagli amministratori su un giocatore, dalla piu' recente. */
    suspend fun notesAbout(player: String): List<AdminMessage> =
        runCatching { messages() }.getOrDefault(emptyList())
            .filter { it.about.equals(player, ignoreCase = true) }
            .sortedByDescending { it.epochSeconds }

    /** Quanti messaggi non ha ancora visto questo telefono. */
    suspend fun unread(): List<AdminMessage> {
        val c = cfg()
        if (!c.isComplete) return emptyList()
        val tutti = runCatching { messages() }.getOrDefault(emptyList())
        return Presence.unread(tutti, Prefs.deviceId, Prefs.chatLastRead(c.id))
    }

    /** Segna letto fino all'ultimo messaggio ricevuto. */
    fun markRead(messages: List<AdminMessage>) {
        val ultimo = messages.maxOfOrNull { it.epochSeconds } ?: return
        Prefs.setChatLastRead(Prefs.load().id, ultimo)
    }
}
