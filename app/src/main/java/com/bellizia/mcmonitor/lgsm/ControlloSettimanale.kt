package com.bellizia.mcmonitor.lgsm

/**
 * Il controllo di sicurezza che si rifà da solo ogni settimana.
 *
 * Un server non è sicuro una volta per sempre: si aggiunge una mod che apre una
 * porta, si spegne la whitelist «per un attimo» durante una festa e non la si
 * riaccende, si cambia una impostazione per provare una cosa. Il controllo fatto
 * il primo giorno racconta il primo giorno.
 *
 * **Non gira in sottofondo**, e non è una scorciatoia: da Android 14 un lavoro
 * periodico di un'app come questa viene rimandato o non eseguito, e sui telefoni
 * Samsung anche prima. Promettere «ogni lunedì» vorrebbe dire promettere una
 * cosa che decide il sistema operativo. Gira invece **alla prima apertura dopo
 * che è passata una settimana**, che è una promessa mantenibile: chi apre l'app
 * ha il collegamento acceso e sta già aspettando qualche secondo.
 */
object ControlloSettimanale {

    /** Sette giorni. */
    const val INTERVALLO_MS = 7L * 24 * 60 * 60 * 1000

    /**
     * Se rifarlo adesso.
     *
     * Il controllo gira solo per chi ha la modalità esperto: è quello che è
     * stato chiesto, e ha una logica — il rapporto parla di whitelist, di
     * `online-mode` e di password RCON, e a chi non le conosce direbbe che
     * qualcosa non va senza dargli modo di capire cosa.
     *
     * Resta però che un server aperto è aperto per tutti, non solo per gli
     * esperti: vedi [soloSeGrave].
     */
    fun deveGirare(esperto: Boolean, ultimoControllo: Long, adesso: Long): Boolean {
        if (!esperto) return false
        if (ultimoControllo <= 0L) return true
        // Un orologio spostato indietro non deve congelare i controlli per
        // sempre: se l'ultimo risulta nel futuro, si rifà.
        if (ultimoControllo > adesso) return true
        return adesso - ultimoControllo >= INTERVALLO_MS
    }

    /**
     * Quanto manca alla prossima, in giorni, per poterlo scrivere.
     *
     * Si arrotonda per eccesso: dire «fra 0 giorni» quando mancano sei ore è
     * peggio che dire «domani».
     */
    fun giorniAllaProssima(ultimoControllo: Long, adesso: Long): Int {
        if (ultimoControllo <= 0L || ultimoControllo > adesso) return 0
        val mancano = INTERVALLO_MS - (adesso - ultimoControllo)
        if (mancano <= 0) return 0
        return ((mancano + 24 * 60 * 60 * 1000 - 1) / (24 * 60 * 60 * 1000)).toInt()
    }

    /**
     * Se val la pena disturbare.
     *
     * Un avviso che dice «va tutto bene» una volta a settimana è un avviso che
     * si impara a scartare senza leggerlo, e il giorno che dice qualcosa di
     * serio finisce nello stesso gesto. Si parla solo quando c'è qualcosa.
     */
    fun vaSegnalato(rapporto: Rapporto): Boolean = rapporto.livello != Livello.ALTO

    /**
     * Quello che si direbbe anche a chi non ha la modalità esperto.
     *
     * Non è quello che è stato chiesto e non si fa senza dirlo: è qui perché la
     * differenza fra i due casi è una riga, e perché un server con `online-mode`
     * spento o senza password RCON è aperto a chiunque abbia trovato
     * l'indirizzo — e chi non è esperto è proprio quello che non se ne accorge
     * da solo.
     */
    fun soloSeGrave(rapporto: Rapporto): Boolean = rapporto.livello == Livello.BASSO

    /** Il titolo dell'avviso: dice subito quanto è grave. */
    fun titolo(nomeServer: String, rapporto: Rapporto): String = when (rapporto.livello) {
        Livello.BASSO -> "$nomeServer: il server è aperto"
        Livello.MEDIO -> "$nomeServer: due cose da guardare"
        Livello.ALTO -> "$nomeServer: sicurezza a posto"
    }

    /**
     * Il testo: le cose che non vanno, non il conteggio.
     *
     * «3 problemi» non dice niente e non fa aprire l'avviso. I titoli dei
     * controlli sì, e in una notifica ci stanno.
     */
    fun testo(rapporto: Rapporto): String {
        val gravi = rapporto.problemi.map { it.titolo }
        val altri = rapporto.attenzioni.map { it.titolo }
        val elenco = (gravi + altri).take(3)
        if (elenco.isEmpty()) return rapporto.riassunto()
        val quanti = gravi.size + altri.size
        return buildString {
            append(elenco.joinToString("; "))
            if (quanti > elenco.size) append(" e altre ${quanti - elenco.size}")
            append(".")
        }
    }
}
