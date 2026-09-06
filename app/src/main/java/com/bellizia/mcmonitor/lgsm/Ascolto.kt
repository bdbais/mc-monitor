package com.bellizia.mcmonitor.lgsm

import java.security.MessageDigest

/**
 * Legge il registro in cerca di uno scambio fra due giocatori.
 *
 * Le due battute non stanno scritte da nessuna parte: nel codice ci sono solo
 * le loro impronte, e la risposta da dare e' tenuta cifrata con una chiave che
 * nasce dalle battute stesse. Prima dello scambio giusto quel testo non esiste
 * nell'applicazione — non nel codice, non fra le stringhe, non nell'archivio
 * installato.
 *
 * Non e' prudenza esagerata: nella versione di rilascio l'offuscamento e'
 * spento, quindi qualunque testo scritto qui dentro si legge aprendo l'APK con
 * un programma qualunque. E il codice sorgente e' pubblico. L'unica cosa che
 * puo' restare riservata e' quello che non c'e' scritto.
 *
 * Il confronto passa dal registro **grezzo**, prima del mascheramento della
 * privacy: con la privacy accesa i nomi arrivano accorciati, e due nomi
 * accorciati possono sembrare lo stesso.
 */
object Ascolto {

    /** Chi ha detto cosa, e la frase da dare in risposta. */
    data class Scambio(val primo: String, val secondo: String, val risposta: String)

    private const val SALE = "mcmonitor/registro/1"

    private const val PRIMA = "6cc3c2f56eb48db58e9a04d5741bbbd16a35688f44890d9b910e2f29dedfd419"
    private const val SECONDA = "d23b646778332debab2307c517b7ffb64fa0f4da66c0f6d7d3de293835796e81"
    private const val CHIUSA = "74c5273242dd632ece081c8513f4852a4e64ecd97e"

    /**
     * Quante righe di conversazione possono passare fra la prima e la seconda.
     *
     * Senza un limite, due battute dette in due giorni diversi conterebbero
     * come uno scambio, e quello che deve sembrare una risposta sembrerebbe una
     * coincidenza.
     */
    private const val FINESTRA = 10

    /** Solo lettere, tutte minuscole: la punteggiatura e gli spazi non contano. */
    internal fun nudo(frase: String): String =
        frase.lowercase().filter { it in 'a'..'z' }

    internal fun impronta(testo: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest((SALE + testo).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun daEsadecimale(s: String): ByteArray =
        ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    /** Il testo si ricompone solo con le due frasi giuste in mano. */
    internal fun apri(primaNuda: String, secondaNuda: String, chiusa: String = CHIUSA): String {
        val chiave = MessageDigest.getInstance("SHA-256")
            .digest((primaNuda + secondaNuda).toByteArray(Charsets.UTF_8))
        val byte = daEsadecimale(chiusa)
        return String(
            ByteArray(byte.size) { (byte[it].toInt() xor chiave[it % chiave.size].toInt()).toByte() },
            Charsets.UTF_8
        )
    }

    /** `[12:00:00] [Server thread/INFO]: <Baisso> ciao a tutti` */
    private val RIGA = Regex("<([A-Za-z0-9_]{1,16})>\\s*(.*)$")

    /**
     * Cerca lo scambio nel registro.
     *
     * Servono **due persone diverse**: la stessa che si risponde da sola non
     * conta. E' la sola regola che rende la cosa quello che deve essere —
     * qualcosa che si tramanda, non qualcosa che si scopre da soli.
     *
     * Le tre costanti si possono passare da fuori per una ragione sola: le
     * prove non devono contenere le frasi vere, o finirebbero scritte nel
     * codice pubblico e non ci sarebbe piu' niente da nascondere. Le prove
     * verificano il meccanismo con una coppia inventata; che le costanti qui
     * sopra siano quelle giuste si verifica una volta, fuori dal repository.
     */
    fun cerca(
        registroGrezzo: String,
        prima: String = PRIMA,
        seconda: String = SECONDA,
        chiusa: String = CHIUSA,
    ): Scambio? {
        val battute = registroGrezzo.lineSequence()
            .mapNotNull { riga -> RIGA.find(riga)?.let { it.groupValues[1] to nudo(it.groupValues[2]) } }
            .toList()

        battute.forEachIndexed { i, (chi, frase) ->
            if (impronta(frase) != prima) return@forEachIndexed
            val finestra = battute.subList(i + 1, minOf(i + 1 + FINESTRA, battute.size))
            finestra.firstOrNull { (altro, risposta) ->
                !altro.equals(chi, ignoreCase = true) && impronta(risposta) == seconda
            }?.let { (altro, risposta) ->
                return Scambio(chi, altro, apri(frase, risposta, chiusa))
            }
        }
        return null
    }
}
