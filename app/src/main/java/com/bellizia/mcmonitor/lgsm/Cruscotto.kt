package com.bellizia.mcmonitor.lgsm

/**
 * Il cruscotto: pochi giocatori tenuti d'occhio tutti insieme.
 *
 * Non e' l'elenco dei giocatori, che serve a cercare un nome. Questo si guarda
 * di sbieco, appoggiato al tavolo mentre si fa altro, e deve rispondere a una
 * domanda sola: c'e' qualcuno che sta per mettersi nei guai, o che li sta
 * dando agli altri.
 *
 * Passa tutto da RCON, cosi' funziona anche sui server dove l'app entra solo da
 * li'. Qui c'e' la parte che si puo' provare senza rete: quali domande fare e
 * come leggere le risposte.
 */
object Cruscotto {

    /**
     * Quanti se ne tengono insieme.
     *
     * Non e' un limite tecnico, e' quanti ne stanno su uno schermo restando
     * leggibili da lontano: oltre, le schede diventano righe e il cruscotto
     * torna a essere un elenco.
     */
    const val MASSIMO = 6

    /** Sotto questa vita la scheda si accende: sono cinque cuori. */
    const val VITA_BASSA = 10.0

    /** Sotto questa fame il giocatore non corre piu' e comincia a soffrire. */
    const val FAME_BASSA = 7

    /** Come sta un giocatore adesso, per quel poco che serve al colpo d'occhio. */
    data class Vitali(
        val nome: String,
        val vita: Double? = null,
        val fame: Int? = null,
        val livelli: Int? = null,
        val posizione: Triple<Double, Double, Double>? = null,
        val dimensione: String? = null,
    ) {
        /** I cuori, che è come li conta chi gioca: la vita è il doppio. */
        val cuori: String? get() = vita?.let { "%.1f".format(it / 2.0) }

        val vuoto: Boolean
            get() = vita == null && fame == null && livelli == null && posizione == null
    }

    /** Cosa deve saltare all'occhio, in ordine di urgenza. */
    enum class Allarme { NESSUNO, FAME, VITA, MORTE }

    /**
     * I campi che si chiedono a ogni giro.
     *
     * Uno per volta, perche' `data get entity` accetta un percorso solo. Non si
     * chiede l'entita' intera: dentro c'e' anche `recipeBook`, che sono decine
     * di migliaia di byte di ricette imparate e da sole farebbero durare un
     * giro piu' dell'intervallo fra un giro e l'altro.
     */
    val CAMPI_VITALI = listOf("Health", "foodLevel", "XpLevel", "Pos", "Dimension")

    fun domandeVitali(nome: String): List<String> =
        CAMPI_VITALI.map { "data get entity $nome $it" }

    /**
     * L'inventario si chiede a parte e piu' di rado: e' la risposta piu' pesante
     * e cambia molto meno spesso della vita.
     */
    fun domandaInventario(nome: String): String = "data get entity $nome Inventory"

    /** Un nome di dimensione ha sempre questa forma; un errore del server no. */
    private val DIMENSIONE = Regex("^[a-z0-9_.-]+:[a-z0-9_./-]+$")

    /**
     * Mette insieme le risposte dei cinque campi.
     *
     * La mappa e' campo → risposta, com'e' arrivata dal server. Un campo che
     * manca o che il server non ha saputo dare resta a null, e la scheda lo
     * mostra vuoto invece di mostrare zero: zero cuori vorrebbe dire morto.
     */
    fun vitali(nome: String, risposte: Map<String, String>): Vitali {
        fun tag(campo: String): Tag? =
            risposte[campo]?.let { Snbt.valoreDiRisposta(it) }

        fun numero(campo: String): Double? = when (val t = tag(campo)) {
            is Tag.Decimale -> t.valore
            is Tag.Numero -> t.valore.toDouble()
            else -> null
        }

        val pos = (tag("Pos") as? Tag.Elenco)?.voci
            ?.mapNotNull {
                when (it) {
                    is Tag.Decimale -> it.valore
                    is Tag.Numero -> it.valore.toDouble()
                    else -> null
                }
            }
            ?.takeIf { it.size == 3 }
            ?.let { Triple(it[0], it[1], it[2]) }

        return Vitali(
            nome = nome,
            vita = numero("Health"),
            fame = numero("foodLevel")?.toInt(),
            livelli = numero("XpLevel")?.toInt(),
            posizione = pos,
            dimensione = (tag("Dimension") as? Tag.Testo)?.valore?.takeIf { DIMENSIONE.matches(it) },
        )
    }

    /**
     * Cosa segnalare.
     *
     * La vita conta piu' della fame perche' la fame si risolve da sola e la vita
     * no; e vita a zero e' morte, che e' l'unica cosa per cui vale la pena
     * interrompere quello che si sta facendo.
     */
    fun allarme(v: Vitali?): Allarme {
        if (v == null) return Allarme.NESSUNO
        val vita = v.vita
        return when {
            vita != null && vita <= 0.0 -> Allarme.MORTE
            vita != null && vita < VITA_BASSA -> Allarme.VITA
            v.fame != null && v.fame < FAME_BASSA -> Allarme.FAME
            else -> Allarme.NESSUNO
        }
    }

    /**
     * L'inventario da una risposta a `Inventory`.
     *
     * Riusa il lettore dell'inventario vero: quello che arriva da RCON e quello
     * che arriva dal file del mondo devono restare la stessa cosa, o le due
     * strade comincerebbero a mostrare due inventari diversi.
     */
    fun inventario(risposta: String): Giocatore? {
        val elenco = Snbt.valoreDiRisposta(risposta) as? Tag.Elenco ?: return null
        return Inventario.leggi(Tag.Gruppo(mapOf("Inventory" to elenco)))
    }

    /**
     * Chi tenere d'occhio, quando se ne sono scelti piu' del dovuto.
     *
     * Non si taglia in fondo alla lista: si mettono davanti quelli online,
     * perche' di un giocatore scollegato non c'e' niente da vedere e terrebbe
     * occupato un posto a chi sta giocando.
     */
    fun daMostrare(scelti: List<String>, online: Collection<String>): List<String> {
        val dentro = online.map { it.lowercase() }.toSet()
        return scelti.sortedByDescending { it.lowercase() in dentro }.take(MASSIMO)
    }
}
