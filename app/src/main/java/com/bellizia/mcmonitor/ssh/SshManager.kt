package com.bellizia.mcmonitor.ssh

import com.bellizia.mcmonitor.data.Prefs
import com.bellizia.mcmonitor.data.ServerConfig
import com.bellizia.mcmonitor.lgsm.Lgsm
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Logger
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import java.util.Properties

data class ExecResult(val exitCode: Int, val stdout: String, val stderr: String) {
    val ok: Boolean get() = exitCode == 0
    val text: String get() = if (stdout.isNotBlank()) stdout else stderr
}

class SshException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Esegue comandi remoti su una sola sessione SSH riutilizzata, serializzando le
 * chiamate: LinuxGSM non ama due comandi di controllo in parallelo.
 */
object SshManager {

    private const val TCP_TIMEOUT = 12_000
    private const val SSH_TIMEOUT = 25_000

    private val lock = Mutex()
    private var session: Session? = null
    private var sessionKey: String? = null

    suspend fun exec(cfg: ServerConfig, command: String, timeoutMs: Long = 60_000): ExecResult =
        withContext(Dispatchers.IO) {
            lock.withLock {
                val (s, reused) = try {
                    obtain(cfg)
                } catch (e: Exception) {
                    close()
                    throw SshException(friendly(e), e)
                }
                try {
                    run(s, command, timeoutMs)
                } catch (first: Exception) {
                    close()
                    // Solo una sessione riciclata merita un secondo tentativo: se la
                    // connessione era appena stata aperta, ritentare raddoppia l'attesa.
                    if (!reused) throw SshException(friendly(first), first)
                    try {
                        run(obtain(cfg).first, command, timeoutMs)
                    } catch (e: Exception) {
                        close()
                        throw SshException(friendly(e), e)
                    }
                }
            }
        }

    /** Comodo per il pulsante "prova connessione": ritorna la banner/uname del server. */
    suspend fun test(cfg: ServerConfig): String {
        val r = exec(cfg, "uname -sr && id -un", timeoutMs = 25_000)
        if (!r.ok && r.stdout.isBlank()) throw SshException(r.stderr.ifBlank { "comando fallito" })
        return r.text.trim()
    }

    fun disconnect() {
        synchronized(this) { close() }
    }

    /**
     * Inoltra una porta remota (tipicamente RCON su 127.0.0.1) dentro il tunnel SSH
     * e restituisce la porta locale su cui collegarsi. Così RCON resta chiuso al
     * mondo esterno e la password non viaggia mai in chiaro.
     */
    suspend fun openTunnel(cfg: ServerConfig, remotePort: Int): Int = withContext(Dispatchers.IO) {
        lock.withLock {
            try {
                val (s, _) = obtain(cfg)
                val existing = s.portForwardingL.firstOrNull { it.endsWith(":127.0.0.1:$remotePort") }
                if (existing != null) {
                    existing.substringBefore(':').toIntOrNull()?.let { return@withLock it }
                }
                s.setPortForwardingL(0, "127.0.0.1", remotePort)
            } catch (e: Exception) {
                close()
                throw SshException("Tunnel SSH verso la porta $remotePort non riuscito: ${friendly(e)}", e)
            }
        }
    }

    /**
     * Prova la connessione stadio per stadio (DNS, TCP, handshake, autenticazione,
     * comandi) e restituisce un report leggibile: serve a capire *dove* si blocca.
     */
    suspend fun diagnose(cfg: ServerConfig): String = withContext(Dispatchers.IO) {
        val out = StringBuilder()
        fun line(text: String) = out.append(text).append('\n')

        disconnect()
        line("Server: ${cfg.label}")
        line("Autenticazione: ${if (cfg.privateKey.isNotBlank()) "chiave privata" else "password"}")
        line("")

        // 1. DNS
        val started = System.currentTimeMillis()
        val addresses = try {
            InetAddress.getAllByName(cfg.host).also {
                line("[OK] DNS: ${it.joinToString { a -> a.hostAddress ?: "?" }} (${ms(started)})")
            }
        } catch (e: Exception) {
            line("[KO] DNS: host non risolto (${e.message})")
            line("     Controlla il nome host, oppure usa direttamente l'indirizzo IP.")
            return@withContext out.toString()
        }

        // 2. TCP
        val tcpStart = System.currentTimeMillis()
        val banner = try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(addresses[0], cfg.port), TCP_TIMEOUT)
                socket.soTimeout = 5_000
                line("[OK] TCP ${addresses[0].hostAddress}:${cfg.port} aperta (${ms(tcpStart)})")
                runCatching { socket.getInputStream().bufferedReader().readLine() }.getOrNull()
            }
        } catch (e: SocketTimeoutException) {
            line("[KO] TCP ${cfg.port}: timeout dopo ${ms(tcpStart)}")
            line("     Il telefono non arriva al server: rete mobile invece del Wi-Fi di casa,")
            line("     IP privato non raggiungibile da fuori, firewall o port forwarding mancante.")
            return@withContext out.toString()
        } catch (e: Exception) {
            line("[KO] TCP ${cfg.port}: ${e.message}")
            return@withContext out.toString()
        }
        if (banner != null) line("     Banner: $banner")

        // 3. Handshake + autenticazione
        val sshStart = System.currentTimeMillis()
        val log = JSchLog()
        JSch.setLogger(log)
        val s = try {
            createSession(cfg).also {
                line("[OK] Handshake e autenticazione riuscite (${ms(sshStart)})")
                line("     Versione server: ${it.serverVersion}")
                line("     Fingerprint: ${fingerprintOf(it)}")
            }
        } catch (e: Exception) {
            line("[KO] SSH dopo ${ms(sshStart)}: ${e.javaClass.simpleName}: ${e.message}")
            line("")
            line("Traccia JSch:")
            log.lines.takeLast(25).forEach { line("  $it") }
            JSch.setLogger(null)
            return@withContext out.toString()
        }
        JSch.setLogger(null)

        // 4. Comandi
        line("")
        Lgsm.probes(cfg).forEach { (title, command) ->
            val result = runCatching { run(s, command, 25_000) }
            val text = result.fold(
                onSuccess = { Lgsm.clean(it.text).trim().ifBlank { "(nessun output)" } },
                onFailure = { "ERRORE: ${it.message}" }
            )
            line("--- $title")
            text.lineSequence().take(8).forEach { line("    $it") }
        }
        runCatching { s.disconnect() }
        out.toString()
    }

    // ---------------------------------------------------------------- interno

    private fun obtain(cfg: ServerConfig): Pair<Session, Boolean> {
        if (!cfg.isComplete) {
            throw SshException("Configurazione incompleta: apri la scheda Impostazioni.")
        }
        val key = "${cfg.user}@${cfg.host}:${cfg.port}/${cfg.password.hashCode()}/${cfg.privateKey.hashCode()}"
        val current = session
        if (current != null && current.isConnected && sessionKey == key) return current to true
        close()

        val s = createSession(cfg)
        session = s
        sessionKey = key
        return s to false
    }

    private fun createSession(cfg: ServerConfig): Session {
        // Fallire subito sul TCP evita di attribuire a SSH un problema di rete.
        try {
            Socket().use { it.connect(InetSocketAddress(cfg.host, cfg.port), TCP_TIMEOUT) }
        } catch (e: SocketTimeoutException) {
            throw SshException(
                "Nessuna risposta da ${cfg.host}:${cfg.port} entro ${TCP_TIMEOUT / 1000}s.\n" +
                        "Il telefono non raggiunge il server: controlla di essere sulla stessa rete " +
                        "(o di usare l'IP pubblico), il firewall e il port forwarding.", e
            )
        } catch (e: UnknownHostException) {
            throw SshException("Host non trovato: ${cfg.host}", e)
        } catch (e: ConnectException) {
            throw SshException("Connessione rifiutata su ${cfg.host}:${cfg.port}.", e)
        }

        val jsch = JSch()
        if (cfg.privateKey.isNotBlank()) {
            jsch.addIdentity(
                "mcmonitor",
                cfg.privateKey.trim().plus("\n").toByteArray(StandardCharsets.UTF_8),
                null,
                cfg.keyPassphrase.takeIf { it.isNotBlank() }?.toByteArray(StandardCharsets.UTF_8)
            )
        }

        val s = jsch.getSession(cfg.user, cfg.host, cfg.port)
        if (cfg.password.isNotBlank()) s.setPassword(cfg.password)
        s.setConfig(Properties().apply {
            // La verifica dell'host avviene subito dopo, confrontando il fingerprint memorizzato.
            setProperty("StrictHostKeyChecking", "no")
            setProperty(
                "PreferredAuthentications",
                if (cfg.privateKey.isNotBlank()) "publickey,keyboard-interactive,password"
                else "password,keyboard-interactive,publickey"
            )
        })
        s.serverAliveInterval = 20_000
        s.timeout = SSH_TIMEOUT
        s.connect(SSH_TIMEOUT)

        val fingerprint = fingerprintOf(s)
        val known = cfg.hostKeyFingerprint
        if (known.isBlank()) {
            if (fingerprint.isNotBlank()) Prefs.saveHostKey(fingerprint)
        } else if (fingerprint.isNotBlank() && !known.equals(fingerprint, ignoreCase = true)) {
            s.disconnect()
            throw SshException(
                "La chiave host è cambiata!\nAttesa: $known\nRicevuta: $fingerprint\n" +
                        "Se il cambio è legittimo, azzera la chiave salvata dalle Impostazioni."
            )
        }
        return s
    }

    private fun fingerprintOf(s: Session): String =
        runCatching { s.hostKey?.getFingerPrint(JSch()) ?: "" }.getOrDefault("")

    private fun run(s: Session, command: String, timeoutMs: Long): ExecResult {
        val channel = s.openChannel("exec") as ChannelExec
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        try {
            channel.setCommand(command)
            channel.setOutputStream(out)
            channel.setErrStream(err)
            channel.setPty(false)
            channel.connect(15_000)

            val deadline = System.currentTimeMillis() + timeoutMs
            while (!channel.isClosed) {
                if (System.currentTimeMillis() > deadline) {
                    throw SshException("Il comando non è terminato entro ${timeoutMs / 1000}s.")
                }
                Thread.sleep(120)
            }
            return ExecResult(
                exitCode = channel.exitStatus,
                stdout = out.toString(StandardCharsets.UTF_8.name()),
                stderr = err.toString(StandardCharsets.UTF_8.name())
            )
        } finally {
            runCatching { channel.disconnect() }
        }
    }

    private fun close() {
        runCatching { session?.disconnect() }
        session = null
        sessionKey = null
    }

    private fun ms(from: Long) = "${System.currentTimeMillis() - from} ms"

    private fun friendly(e: Exception): String = when {
        e is SshException -> e.message ?: "Errore SSH"
        e is UnknownHostException -> "Host non trovato: controlla indirizzo e DNS."
        e is ConnectException -> "Connessione rifiutata: server spento o porta SSH errata."
        e.message?.contains("Auth fail", true) == true ||
                e.message?.contains("Auth cancel", true) == true ->
            "Autenticazione fallita: utente, password o chiave non validi."
        e.message?.contains("USERAUTH fail", true) == true ->
            "Chiave rifiutata dal server (o passphrase errata)."
        e.message?.contains("invalid privatekey", true) == true ->
            "Chiave privata non valida: incolla il file completo, righe BEGIN/END comprese."
        e.message?.contains("timeout", true) == true || e is SocketTimeoutException ->
            "Timeout durante la connessione SSH. Usa \"Diagnostica\" per vedere a che punto si ferma."
        else -> "${e.javaClass.simpleName}: ${e.message ?: "errore sconosciuto"}"
    }

    /** Raccoglie i messaggi interni di JSch per mostrarli nella diagnostica. */
    private class JSchLog : Logger {
        val lines = mutableListOf<String>()
        override fun isEnabled(level: Int) = true
        override fun log(level: Int, message: String?) {
            if (message != null) lines.add(message)
        }
    }
}
