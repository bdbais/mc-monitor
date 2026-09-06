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
        // Minecraft spezza le risposte lunghe in pezzi da 4096 byte esatti, e un
        // pezzo più corto è l'ultimo: è così che si sa dove finisce. I pezzi qui
        // sono fatti della stessa misura, o il test proverebbe una cosa che
        // nessun server fa.
        val pieno = "a".repeat(4096)
        val coda = "fine"
        val port = startServer("segreta123", mapOf("help" to listOf(pieno, pieno, coda)))
        val client = RconClient("127.0.0.1", port, "segreta123", timeoutMs = 4_000)
        client.connect()
        assertEquals(pieno + pieno + coda, client.exec("help"))
        client.close()
    }

    /**
     * Un server che chiude appena vede un pacchetto che non conosce.
     *
     * È Minecraft: il trucco della sentinella — un pacchetto vuoto di tipo 0
     * mandato dopo il comando per sapere dove finisce la risposta — lui non lo
     * prevede, e chiude. Nel log del server si vedeva «Thread RCON Client
     * started» e «shutting down» nello stesso secondo, a ogni comando, con
     * l'autenticazione riuscita; dall'app sembrava sbagliata la password.
     */
    private fun chiudeSuTipoSconosciuto(risposta: String): Int {
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
                        3 -> writePacket(output, packet.id, 2, "")
                        2 -> writePacket(output, packet.id, 0, risposta)
                        else -> { client.close(); return@runCatching }
                    }
                }
            }
        }
        return socket.localPort
    }

    @Test
    fun `un server che chiude dopo aver risposto ha finito, non e' guasto`() {
        val port = chiudeSuTipoSconosciuto("There are 0 of a max of 20 players online:")
        val client = RconClient("127.0.0.1", port, "segreta123", timeoutMs = 4_000)
        client.connect()
        assertEquals("There are 0 of a max of 20 players online:", client.exec("list"))
        client.close()
    }

    @Test
    fun `una risposta lunga esatta seguita dalla chiusura non va persa`() {
        // Il caso che sfugge alla regola del pezzo corto: la risposta e' lunga
        // esattamente quanto un pezzo pieno, quindi l'app ne aspetta un altro, e
        // invece il server chiude. Senza il trattamento della chiusura, una
        // risposta gia' arrivata per intero diventerebbe un errore.
        val pieno = "b".repeat(4096)
        val socket = ServerSocket(0)
        server = socket
        thread(isDaemon = true) {
            runCatching {
                val client = socket.accept()
                val input = DataInputStream(client.getInputStream())
                val output = DataOutputStream(client.getOutputStream())
                writePacket(output, readPacket(input)!!.id, 2, "") // autenticazione
                writePacket(output, readPacket(input)!!.id, 0, pieno)
                Thread.sleep(100)
                client.close()
            }
        }
        val client = RconClient("127.0.0.1", socket.localPort, "segreta123", timeoutMs = 4_000)
        client.connect()
        assertEquals(pieno, client.exec("help"))
        client.close()
    }

    @Test
    fun `dopo il comando non parte nessun pacchetto di troppo`() {
        // La prova che la sentinella non c'e' piu': questo finto server registra
        // i tipi che riceve, e deve vedere solo l'autenticazione e il comando.
        val tipi = java.util.Collections.synchronizedList(mutableListOf<Int>())
        val socket = ServerSocket(0)
        server = socket
        thread(isDaemon = true) {
            runCatching {
                val client = socket.accept()
                val input = DataInputStream(client.getInputStream())
                val output = DataOutputStream(client.getOutputStream())
                while (!client.isClosed) {
                    val packet = readPacket(input) ?: break
                    tipi.add(packet.type)
                    when (packet.type) {
                        3 -> writePacket(output, packet.id, 2, "")
                        else -> writePacket(output, packet.id, 0, "ok")
                    }
                }
            }
        }
        val client = RconClient("127.0.0.1", socket.localPort, "segreta123", timeoutMs = 4_000)
        client.connect()
        client.exec("list")
        client.close()
        Thread.sleep(300)
        assertEquals("tipi ricevuti: $tipi", listOf(3, 2), tipi.toList())
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
