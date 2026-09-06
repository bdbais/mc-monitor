package com.bellizia.mcmonitor.rete

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Quello che un server Minecraft racconta di sé a chi bussa senza entrare. */
data class Insegna(
    val indirizzo: String,
    val porta: Int,
    val versione: String?,
    val protocollo: Int?,
    val online: Int?,
    val massimo: Int?,
    val motd: String?,
)

/**
 * La domanda che il gioco fa a un server per riempire la riga nell'elenco dei
 * multigiocatore: come ti chiami, che versione sei, quanti ci sono dentro.
 *
 * È l'unico modo onesto per cercare un server nella rete di casa: non serve
 * nessuna password, non si entra, non si tocca niente. Si bussa e si legge il
 * cartello.
 *
 * Il formato è quello di Minecraft: numeri a lunghezza variabile (VarInt),
 * pacchetti preceduti dalla propria lunghezza. Qui c'è solo la parte di
 * costruzione e lettura, che si può provare senza rete; chi apre il collegamento
 * sta altrove.
 */
object PingServer {

    /**
     * Un numero a lunghezza variabile: sette bit per byte, e il bit più alto dice
     * "ce n'è ancora". È così che Minecraft scrive tutte le lunghezze.
     */
    fun varInt(valore: Int): ByteArray {
        var v = valore
        val out = ByteArrayOutputStream()
        do {
            var b = v and 0x7F
            v = v ushr 7
            if (v != 0) b = b or 0x80
            out.write(b)
        } while (v != 0)
        return out.toByteArray()
    }

    /** Codice di ritorno convenzionale: il flusso è finito prima del previsto. */
    class Troncato : Exception("il server ha chiuso prima di rispondere")

    /**
     * Rilegge un VarInt.
     *
     * Cinque byte al massimo: oltre, o è un server che non parla questo
     * protocollo o è qualcuno che sta cercando di far girare a vuoto un ciclo.
     */
    fun leggiVarInt(input: InputStream): Int {
        var risultato = 0
        var spostamento = 0
        while (true) {
            val b = input.read()
            if (b < 0) throw Troncato()
            risultato = risultato or ((b and 0x7F) shl spostamento)
            if (b and 0x80 == 0) return risultato
            spostamento += 7
            if (spostamento >= 35) throw Troncato()
        }
    }

    private fun stringa(testo: String): ByteArray {
        val byte = testo.toByteArray(Charsets.UTF_8)
        return varInt(byte.size) + byte
    }

    private fun pacchetto(corpo: ByteArray): ByteArray = varInt(corpo.size) + corpo

    /**
     * La stretta di mano, con `next state = 1`: sto solo guardando, non entro.
     *
     * L'indirizzo e la porta ci vanno perché un server dietro a un proxy li usa
     * per capire quale mondo servire; da soli non identificano chi bussa.
     */
    fun stretta(indirizzo: String, porta: Int, protocollo: Int = 767): ByteArray =
        pacchetto(
            varInt(0x00) +
                    varInt(protocollo) +
                    stringa(indirizzo) +
                    byteArrayOf((porta shr 8).toByte(), porta.toByte()) +
                    varInt(1)
        )

    /** La richiesta vera e propria: un pacchetto vuoto con identificativo 0. */
    fun richiestaStato(): ByteArray = pacchetto(varInt(0x00))

    /**
     * Legge la risposta: lunghezza, identificativo, e poi il JSON del cartello.
     */
    fun leggiRisposta(input: InputStream): String {
        val lunghezza = leggiVarInt(input)
        if (lunghezza <= 0 || lunghezza > 2_000_000) throw Troncato()
        val id = leggiVarInt(input)
        if (id != 0x00) throw Troncato()
        val quanti = leggiVarInt(input)
        if (quanti < 0 || quanti > 2_000_000) throw Troncato()
        val byte = ByteArray(quanti)
        var letti = 0
        while (letti < quanti) {
            val n = input.read(byte, letti, quanti - letti)
            if (n < 0) throw Troncato()
            letti += n
        }
        return String(byte, Charsets.UTF_8)
    }

    /**
     * Il cartello, dal JSON.
     *
     * La descrizione ha tre forme diverse a seconda della versione e del server:
     * una stringa secca, un oggetto con `text`, o un albero di pezzi con `extra`.
     * Vanno gestite tutte, o metà dei server risulterebbe senza nome.
     */
    fun insegna(indirizzo: String, porta: Int, json: String): Insegna? {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val versione = o.optJSONObject("version")
        val giocatori = o.optJSONObject("players")
        return Insegna(
            indirizzo = indirizzo,
            porta = porta,
            versione = versione?.optString("name")?.takeIf { it.isNotBlank() },
            protocollo = versione?.optInt("protocol", -1)?.takeIf { it >= 0 },
            online = giocatori?.optInt("online", -1)?.takeIf { it >= 0 },
            massimo = giocatori?.optInt("max", -1)?.takeIf { it >= 0 },
            motd = descrizione(o.opt("description"))?.trim()?.takeIf { it.isNotBlank() },
        )
    }

    private fun descrizione(nodo: Any?): String? = when (nodo) {
        null -> null
        is String -> ripulisci(nodo)
        is JSONObject -> buildString {
            append(ripulisci(nodo.optString("text")))
            nodo.optJSONArray("extra")?.let { extra ->
                for (i in 0 until extra.length()) append(descrizione(extra.opt(i)).orEmpty())
            }
        }
        else -> null
    }

    /** Via i codici colore `§a`, che a schermo sarebbero caratteri a caso. */
    private fun ripulisci(testo: String): String = testo.replace(Regex("§."), "")
}
