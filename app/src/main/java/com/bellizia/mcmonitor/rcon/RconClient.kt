package com.bellizia.mcmonitor.rcon

import java.io.BufferedInputStream
import java.io.IOException
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

class RconException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Client del protocollo RCON (Source), quello usato da Minecraft.
 *
 * Formato pacchetto: lunghezza int32 LE, id int32 LE, tipo int32 LE, corpo ASCII
 * terminato da NUL, più un NUL finale. Le risposte lunghe arrivano spezzate in più
 * pacchetti: la fine si riconosce con un pacchetto sentinella inviato subito dopo.
 */
class RconClient(
    private val host: String,
    private val port: Int,
    private val password: String,
    private val timeoutMs: Int = 12_000
) {

    private companion object {
        const val TYPE_RESPONSE = 0
        const val TYPE_COMMAND = 2
        const val TYPE_AUTH = 3
        const val AUTH_FAILED_ID = -1
        const val MAX_BODY = 4096

        /**
         * Cosa dire quando il server non risponde all'autenticazione.
         *
         * Chiudere di colpo e restare muto sono la stessa notizia per chi
         * guarda, e arrivano da eccezioni diverse a seconda del sistema: su
         * Windows la chiusura secca e' una `SocketException` con un messaggio
         * in inglese sul software dell'host, che a schermo non vuol dire
         * niente. Si prende tutta la famiglia e si dice la cosa utile.
         *
         * Le cause sono due, e la seconda e' quella che capita subito dopo aver
         * acceso RCON: il thread che ascolta i comandi parte alla fine
         * dell'avvio, e la porta risulta aperta prima.
         */
        const val CHIUSO_SUBITO =
            "Il server non ha risposto al collegamento RCON. " +
                    "Di solito e' la password sbagliata, oppure il server sta " +
                    "ancora finendo di avviarsi: riprova fra un minuto."
    }

    private var socket: Socket? = null
    private var out: DataOutputStream? = null
    private var input: DataInputStream? = null
    private var nextId = 0

    val isConnected: Boolean get() = socket?.isConnected == true && socket?.isClosed == false

    fun connect() {
        close()
        val s = Socket()
        try {
            s.connect(InetSocketAddress(host, port), timeoutMs)
        } catch (e: SocketTimeoutException) {
            throw RconException("RCON non risponde su $host:$port (timeout).", e)
        } catch (e: Exception) {
            throw RconException("Impossibile aprire RCON su $host:$port: ${e.message}", e)
        }
        s.soTimeout = timeoutMs
        s.tcpNoDelay = true
        socket = s
        out = DataOutputStream(s.getOutputStream())
        input = DataInputStream(BufferedInputStream(s.getInputStream()))

        val id = ++nextId
        write(id, TYPE_AUTH, password)
        // Il protocollo prevede che una password sbagliata torni indietro come
        // pacchetto con id -1. Non tutti i server lo fanno: parecchi chiudono e
        // basta, e quella chiusura arriva qui come un EOF senza nemmeno un
        // messaggio. Detta cosi' l'utente legge "Errore RCON" e non sa se ha
        // sbagliato la password o se il server sta ancora avviandosi.
        var packet = try {
            read()
        } catch (e: IOException) {
            close()
            throw RconException(CHIUSO_SUBITO, e)
        }
        // Alcuni server mandano un pacchetto vuoto prima della risposta di autenticazione.
        if (packet.type == TYPE_RESPONSE) {
            packet = try {
                read()
            } catch (e: IOException) {
                close()
                throw RconException(CHIUSO_SUBITO, e)
            }
        }
        if (packet.id == AUTH_FAILED_ID) {
            close()
            throw RconException("Password RCON rifiutata dal server.")
        }
    }

    fun exec(command: String): String {
        if (!isConnected) connect()
        val id = ++nextId
        write(id, TYPE_COMMAND, command)
        // Sentinella: la sua risposta segna la fine di quella del comando.
        val sentinel = ++nextId
        write(sentinel, TYPE_RESPONSE, "")

        val body = StringBuilder()
        while (true) {
            val packet = try {
                read()
            } catch (e: SocketTimeoutException) {
                break // risposta già completa: alcuni server ignorano la sentinella
            } catch (e: IOException) {
                close()
                throw RconException(
                    "Il server ha chiuso la connessione RCON mentre rispondeva.", e
                )
            }
            if (packet.id == sentinel) break
            body.append(packet.body)
        }
        return body.toString().trim()
    }

    fun close() {
        runCatching { socket?.close() }
        socket = null
        out = null
        input = null
    }

    // ---------------------------------------------------------------- interno

    private class Packet(val id: Int, val type: Int, val body: String)

    private fun write(id: Int, type: Int, body: String) {
        val payload = body.toByteArray(StandardCharsets.UTF_8)
        val buffer = ByteBuffer.allocate(payload.size + 14).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(payload.size + 10)
        buffer.putInt(id)
        buffer.putInt(type)
        buffer.put(payload)
        buffer.put(0)
        buffer.put(0)
        val stream = out ?: throw RconException("Connessione RCON chiusa.")
        stream.write(buffer.array())
        stream.flush()
    }

    private fun read(): Packet {
        val stream = input ?: throw RconException("Connessione RCON chiusa.")
        val header = ByteArray(4)
        stream.readFully(header)
        val length = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).int
        if (length < 10 || length > MAX_BODY + 16) {
            throw RconException("Risposta RCON malformata (lunghezza $length).")
        }
        val payload = ByteArray(length)
        stream.readFully(payload)
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        val id = buffer.int
        val type = buffer.int
        val bodyBytes = ByteArray(length - 10)
        buffer.get(bodyBytes)
        return Packet(id, type, String(bodyBytes, StandardCharsets.UTF_8))
    }
}
