package com.bellizia.mcmonitor.rcon

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

/**
 * Verifica la codifica dei pacchetti RCON contro un finto server che parla il
 * protocollo Source, compreso il caso della risposta spezzata in più pacchetti.
 *
 * E poi cosa dice l'app quando RCON non va: quel pezzo è arrivato da uno
 * screenshot vero, dove l'attivazione finiva con «FALLITA: Errore RCON». Quel
 * testo è quello che resta quando l'eccezione non ha messaggio, e chi legge non
 * sa se ha sbagliato la password o se il server sta ancora partendo — cioè non
 * sa se deve correggere qualcosa o solo aspettare.
 */
class RconClientTest {

    private var server: ServerSocket? = null

    @After
    fun stop() {
        runCatching { server?.close() }
    }

    private fun startServer(
        expectedPassword: String,
        responses: Map<String, List<String>>
    ): Int {
        val socket = ServerSocket(0)
        server = socket
        thread(isDaemon = true) {
            runCatching {
                val client = socket.accept()
                val input = DataInputStream(client.getInputStream())
                val output = DataOutputStream(client.getOutputStream())
                while (!client.isClosed) {
                    val packet = readPacket(input) ?: break
                    when (packet.type) {
                        3 -> { // autenticazione
                            val id = if (packet.body == expectedPassword) packet.id else -1
                            writePacket(output, id, 2, "")
                        }
                        2 -> { // comando: risposta eventualmente su più pacchetti
                            (responses[packet.body] ?: listOf("")).forEach {
                                writePacket(output, packet.id, 0, it)
                            }
                        }
                        else -> writePacket(output, packet.id, 0, "") // sentinella
                    }
                }
            }
        }
        return socket.localPort
    }

    private class Raw(val id: Int, val type: Int, val body: String)

    private fun readPacket(input: DataInputStream): Raw? {
        val header = ByteArray(4)
        return try {
            input.readFully(header)
            val length = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).int
            val payload = ByteArray(length)
            input.readFully(payload)
            val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
            val id = buffer.int
            val type = buffer.int
            val body = ByteArray(length - 10)
            buffer.get(body)
            Raw(id, type, String(body, StandardCharsets.UTF_8))
        } catch (e: Exception) {
            null
        }
    }

    private fun writePacket(output: DataOutputStream, id: Int, type: Int, body: String) {
        val payload = body.toByteArray(StandardCharsets.UTF_8)
        val buffer = ByteBuffer.allocate(payload.size + 14).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(payload.size + 10)
        buffer.putInt(id)
        buffer.putInt(type)
        buffer.put(payload)
        buffer.put(0)
        buffer.put(0)
        output.write(buffer.array())
        output.flush()
    }

    @Test
    fun `autentica ed esegue un comando`() {
        val port = startServer(
            "segreta123",
            mapOf("list" to listOf("There are 2 of a max of 20 players online: Fede, Luca"))
        )
        val client = RconClient("127.0.0.1", port, "segreta123", timeoutMs = 4_000)
        client.connect()
        assertEquals("There are 2 of a max of 20 players online: Fede, Luca", client.exec("list"))
        client.close()
    }

    @Test
    fun `ricompone una risposta divisa su più pacchetti`() {
        val port = startServer(
            "segreta123",
            mapOf("help" to listOf("prima parte ", "seconda parte ", "terza parte"))
        )
        val client = RconClient("127.0.0.1", port, "segreta123", timeoutMs = 4_000)
        client.connect()
        assertEquals("prima parte seconda parte terza parte", client.exec("help"))
        client.close()
    }

    @Test
    fun `password errata viene segnalata`() {
        val port = startServer("giusta123", emptyMap())
        val client = RconClient("127.0.0.1", port, "sbagliata123", timeoutMs = 4_000)
        val error = runCatching { client.connect() }.exceptionOrNull()
        assertTrue(error is RconException)
        assertTrue(error!!.message!!.contains("rifiutata"))
    }

    /**
     * Un server che accetta il collegamento e chiude subito.
     *
     * Non è un caso di laboratorio: è come si comporta un server Minecraft
     * appena riavviato, che la porta l'ha già aperta ma il thread RCON non l'ha
     * ancora avviato.
     */
    private fun chiudeSubito(): Int {
        val socket = ServerSocket(0)
        server = socket
        thread(isDaemon = true) { runCatching { socket.accept().close() } }
        return socket.localPort
    }

    @Test
    fun `un server che chiude senza rispondere non diventa un errore muto`() {
        val client = RconClient("127.0.0.1", chiudeSubito(), "segreto", timeoutMs = 3_000)
        val errore = runCatching { client.connect() }.exceptionOrNull()

        assertTrue("atteso RconException, arrivato $errore", errore is RconException)
        val detto = errore!!.message.orEmpty()
        // La chiusura secca arriva come EOFException su Linux e come
        // SocketException su Windows, e nessuna delle due ha un messaggio
        // utile: quello che arriva a schermo dev'essere questo.
        assertFalse("il messaggio è ancora quello muto: $detto", detto == "Errore RCON")
        assertTrue("non dice della password: $detto", detto.contains("password"))
        assertTrue("non dice di riprovare: $detto", detto.contains("riprova"))
    }

    @Test
    fun `un'eccezione senza messaggio non arriva all'utente come tale`() {
        assertEquals("Rete assente.", RconManager.descrivi(RconException("Rete assente.")))
        val muta = RconManager.descrivi(java.io.EOFException())
        assertFalse(muta.isBlank())
        assertTrue("non dice di che guasto si tratta: $muta", muta.contains("EOFException"))
    }

    @Test
    fun `porta chiusa produce un errore leggibile`() {
        val free = ServerSocket(0).use { it.localPort }
        val client = RconClient("127.0.0.1", free, "qualsiasi1", timeoutMs = 2_000)
        val error = runCatching { client.connect() }.exceptionOrNull()
        assertTrue(error is RconException)
    }
}
