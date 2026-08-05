package com.bellizia.mcmonitor.rcon

import com.bellizia.mcmonitor.data.ServerConfig
import com.bellizia.mcmonitor.ssh.SshManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Tiene aperta una connessione RCON, di norma attraverso il tunnel SSH.
 * Con RCON i comandi tornano la risposta del server invece di finire nel log,
 * quindi niente righe di servizio in `latest.log` e nessuna attesa sul tail.
 */
object RconManager {

    private val lock = Mutex()
    private var client: RconClient? = null
    private var clientKey: String? = null

    suspend fun exec(cfg: ServerConfig, command: String): String = withContext(Dispatchers.IO) {
        lock.withLock {
            try {
                obtain(cfg).exec(command)
            } catch (first: Exception) {
                // Il tunnel o la sessione possono cadere: un giro di riconnessione.
                close()
                try {
                    obtain(cfg).exec(command)
                } catch (e: Exception) {
                    close()
                    throw if (e is RconException) e else RconException(e.message ?: "Errore RCON", e)
                }
            }
        }
    }

    /** Verifica autenticazione e risposta: usata dal pulsante "Prova RCON". */
    suspend fun test(cfg: ServerConfig): String {
        val reply = exec(cfg, "list")
        return reply.ifBlank { "connesso (il server ha risposto senza testo)" }
    }

    fun disconnect() {
        synchronized(this) { close() }
    }

    private suspend fun obtain(cfg: ServerConfig): RconClient {
        if (cfg.rconPassword.isBlank()) {
            throw RconException("Password RCON non impostata: aprila nelle Impostazioni.")
        }
        val key = "${cfg.host}:${cfg.rconPort}/${cfg.rconTunnel}/${cfg.rconPassword.hashCode()}"
        val current = client
        if (current != null && current.isConnected && clientKey == key) return current
        close()

        val (host, port) = if (cfg.rconTunnel) {
            "127.0.0.1" to SshManager.openTunnel(cfg, cfg.rconPort)
        } else {
            cfg.host to cfg.rconPort
        }
        val fresh = RconClient(host, port, cfg.rconPassword)
        fresh.connect()
        client = fresh
        clientKey = key
        return fresh
    }

    private fun close() {
        runCatching { client?.close() }
        client = null
        clientKey = null
    }
}
