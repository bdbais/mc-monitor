package com.bellizia.mcmonitor.lgsm

/**
 * Far partire una macro da quello che compare nel registro.
 *
 * È una funzione comoda e con un bordo tagliente, quindi il bordo va guardato
 * per primo: **una macro che parte da una riga del registro è un modo di
 * eseguire comandi sul server.** Se la riga che la fa partire può scriverla un
 * giocatore, allora quel giocatore può far eseguire quei comandi — e nella chat
 * di Minecraft può scrivere chiunque riesca a entrare.
 *
 * Da qui discendono le tre regole di questo file.
 *
 * **Uno. Le righe di chat sono di chi le ha scritte, non del server.** Un
 * giocatore che scrive in chat `Anna joined the game` produce una riga di
 * registro che contiene quelle parole. Un innesco che cerca «è entrato
 * qualcuno» e guarda anche le righe di chat si fa comandare dal primo che ci
 * prova.
 *
 * **Due. Una macro delicata non si fa innescare da una frase in chat.** Quelle
 * che l'app segna come delicate cambiano il mondo o disturbano chi gioca: se
 * bastasse scrivere una parola per farle partire, non sarebbero più delicate.
 *
 * **Tre. Una macro non può innescare se stessa.** Se dice qualcosa in chat e
 * l'innesco guarda la chat, si richiama all'infinito — e non lo fa piano: lo fa
 * alla velocità con cui l'app rilegge il registro.
 */
object Innesco {

    /** Da dove arriva la riga che fa scattare l'innesco. */
    enum class Provenienza {
        /**
         * Righe che scrive il server: qualcuno entra, esce, muore, il server
         * fatica. Un giocatore non le può fabbricare.
         */
        SERVER,

        /**
         * Quello che un giocatore scrive in chat. Comodo, e **non fidato**:
         * ci può scrivere chiunque sia riuscito a entrare.
         */
        CHAT,
    }

    data class Regola(
        val macroId: String,
        val provenienza: Provenienza,
        /** Cosa cercare. Non è un'espressione regolare: è testo, cercato dentro la riga. */
        val testo: String,
        /** Quanti secondi devono passare prima che possa riscattare. */
        val pausaSecondi: Int = 60,
        val attiva: Boolean = true,
    )

    /** `[12:00:00] [Server thread/INFO]: <Baisso> ciao a tutti` */
    private val CHAT = Regex("]:\\s*<[A-Za-z0-9_]{1,16}>")

    /**
     * La riga è stata scritta da un giocatore?
     *
     * Si guarda la forma della riga di registro, non il suo contenuto: la parte
     * dopo `<nome>` è testo libero e non può decidere niente su se stessa.
     */
    fun scrittaDaUnGiocatore(riga: String): Boolean = CHAT.containsMatchIn(riga)

    /** Il testo che un giocatore ha scritto, senza l'intestazione del registro. */
    fun testoDiChat(riga: String): String? {
        val m = CHAT.find(riga) ?: return null
        return riga.substring(m.range.last + 1).trim()
    }

    /**
     * Questa riga fa scattare questa regola?
     *
     * Il controllo della provenienza viene **prima** del confronto del testo, e
     * non dopo: è la riga stessa a dover essere del tipo giusto, altrimenti
     * cercare le parole non ha senso.
     */
    fun combacia(riga: String, regola: Regola): Boolean {
        if (!regola.attiva || regola.testo.isBlank()) return false
        val daGiocatore = scrittaDaUnGiocatore(riga)
        return when (regola.provenienza) {
            // Una riga di chat non può mai valere come riga del server, per
            // quante parole giuste contenga.
            Provenienza.SERVER -> !daGiocatore && riga.contains(regola.testo, ignoreCase = true)
            Provenienza.CHAT ->
                daGiocatore && testoDiChat(riga)?.contains(regola.testo, ignoreCase = true) == true
        }
    }

    /**
     * Questa regola può far partire questa macro?
     *
     * Una macro delicata da una frase in chat, no. È l'unico divieto, e vale
     * anche se l'ha impostato l'amministratore: chi lo imposta pensa a sé che
     * scrive la parola, non al ragazzino che la legge da sopra la spalla.
     */
    fun ammessa(regola: Regola, macroDelicata: Boolean): Boolean =
        !(macroDelicata && regola.provenienza == Provenienza.CHAT)

    fun perche(regola: Regola, macroDelicata: Boolean): String =
        if (ammessa(regola, macroDelicata)) ""
        else "Questa macro è segnata come delicata: cambia il mondo o disturba chi gioca. " +
                "Non si può far partire da una frase scritta in chat, perché in chat può " +
                "scrivere chiunque riesca a entrare. Da una riga del server sì."

    /**
     * È passato abbastanza tempo dall'ultima volta?
     *
     * Senza questa, una macro che parla in chat mentre l'innesco guarda la chat
     * si richiama all'infinito, alla velocità con cui l'app rilegge il registro.
     */
    fun puoScattare(regola: Regola, ultimoScatto: Long, adesso: Long): Boolean {
        if (ultimoScatto <= 0L) return true
        if (ultimoScatto > adesso) return true // orologio spostato indietro
        return adesso - ultimoScatto >= regola.pausaSecondi * 1000L
    }

    /**
     * Le righe nuove da esaminare, viste quelle già viste.
     *
     * All'apertura non si guarda indietro: il registro contiene ore di
     * passato, e far partire adesso tutto quello che è successo stanotte è il
     * modo di svegliarsi con il server riavviato otto volte.
     */
    fun righeNuove(registro: String, ultimaVista: String?): List<String> {
        val righe = registro.lineSequence().filter { it.isNotBlank() }.toList()
        if (ultimaVista == null) return emptyList()
        val i = righe.indexOfLast { it == ultimaVista }
        return if (i < 0) righe else righe.drop(i + 1)
    }

    /** L'ultima riga vista, da ricordare per il giro dopo. */
    fun segnaposto(registro: String): String? =
        registro.lineSequence().lastOrNull { it.isNotBlank() }
}
