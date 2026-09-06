package com.bellizia.mcmonitor.rcon

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
 * Cosa dice l'app quando RCON non va.
 *
 * Il difetto che questi test tengono fermo e' arrivato da uno screenshot vero:
 * l'attivazione di RCON finiva con «FALLITA: Errore RCON». Quel testo e' quello
 * che resta quando l'eccezione non ha messaggio — e infatti la chiusura secca di
 * un socket arriva come EOFException, che il messaggio non ce l'ha. Chi legge
 * non sa se ha sbagliato la password o se il server sta ancora partendo, cioe'
 * non sa se deve correggere qualcosa o solo aspettare.
 */
class RconClientTest {

    /** Un server finto, che fa una cosa sola e poi smette. */
    private class Finto(val comportamento: (Socket) -> Unit) : AutoCloseable {
        private val server = ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())
        val porta: Int get() = server.localPort

        init {
            thread(isDaemon = true) {
                runCatching { server.accept().use(comportamento) }
            }
        }

        override fun close() {
            runCatching { server.close() }
        }
    }

    private fun scrivi(uscita: DataOutputStream, id: Int, tipo: Int, corpo: String) {
        val payload = corpo.toByteArray(StandardCharsets.UTF_8)
        val b = ByteBuffer.allocate(payload.size + 14).order(ByteOrder.LITTLE_ENDIAN)
        b.putInt(payload.size + 10)
        b.putInt(id)
        b.putInt(tipo)
        b.put(payload)
        b.put(0)
        b.put(0)
        uscita.write(b.array())
        uscita.flush()
    }

    /** Legge un pacchetto del client e ne restituisce l'identificativo. */
    private fun leggiId(ingresso: DataInputStream): Int {
        val testa = ByteArray(4)
        ingresso.readFully(testa)
        val lunghezza = ByteBuffer.wrap(testa).order(ByteOrder.LITTLE_ENDIAN).int
        val resto = ByteArray(lunghezza)
        ingresso.readFully(resto)
        return ByteBuffer.wrap(resto).order(ByteOrder.LITTLE_ENDIAN).int
    }

    @Test
    fun `un server che chiude senza rispondere non diventa un errore muto`() {
        Finto { socket -> socket.close() }.use { finto ->
            val errore = runCatching {
                RconClient("127.0.0.1", finto.porta, "segreto", timeoutMs = 3_000).connect()
            }.exceptionOrNull()

            assertTrue("atteso RconException, arrivato $errore", errore is RconException)
            val detto = errore!!.message.orEmpty()
            assertFalse("il messaggio e' ancora quello muto: $detto", detto == "Errore RCON")
            assertTrue("non dice della password: $detto", detto.contains("password"))
            assertTrue("non dice di riprovare: $detto", detto.contains("riprova"))
        }
    }

    @Test
    fun `una password rifiutata si chiama per nome`() {
        // Il server che segue il protocollo risponde con identificativo -1. Qui
        // il messaggio deve dire «rifiutata», perche' e' su quella parola che
        // l'attivazione decide di smettere di riprovare invece di insistere per
        // un minuto su una password che resterebbe sbagliata.
        Finto { socket ->
            val ingresso = DataInputStream(socket.getInputStream())
            val uscita = DataOutputStream(socket.getOutputStream())
            leggiId(ingresso)
            scrivi(uscita, -1, 2, "")
            Thread.sleep(200)
        }.use { finto ->
            val errore = runCatching {
                RconClient("127.0.0.1", finto.porta, "sbagliata", timeoutMs = 3_000).connect()
            }.exceptionOrNull()

            assertTrue(errore is RconException)
            assertTrue(errore!!.message.orEmpty().contains("rifiutata", ignoreCase = true))
        }
    }

    @Test
    fun `quando la password e' giusta il comando torna indietro`() {
        // Se questo si rompesse, i due test qui sopra passerebbero lo stesso: un
        // client che fallisce sempre li supera entrambi.
        Finto { socket ->
            val ingresso = DataInputStream(socket.getInputStream())
            val uscita = DataOutputStream(socket.getOutputStream())
            val autenticazione = leggiId(ingresso)
            scrivi(uscita, autenticazione, 2, "")
            val comando = leggiId(ingresso)
            val sentinella = leggiId(ingresso)
            scrivi(uscita, comando, 0, "There are 2 of a max of 20 players online: Anna, Bruno")
            scrivi(uscita, sentinella, 0, "")
            Thread.sleep(200)
        }.use { finto ->
            val client = RconClient("127.0.0.1", finto.porta, "giusta", timeoutMs = 3_000)
            client.connect()
            assertEquals(
                "There are 2 of a max of 20 players online: Anna, Bruno",
                client.exec("list")
            )
            client.close()
        }
    }

    @Test
    fun `un'eccezione senza messaggio non arriva all'utente come tale`() {
        assertEquals("Rete assente.", RconManager.descrivi(RconException("Rete assente.")))
        val muta = RconManager.descrivi(java.io.EOFException())
        assertFalse(muta.isBlank())
        assertTrue("non dice di che guasto si tratta: $muta", muta.contains("EOFException"))
    }
}
