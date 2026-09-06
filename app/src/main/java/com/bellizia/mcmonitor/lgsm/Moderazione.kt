package com.bellizia.mcmonitor.lgsm

/**
 * I gesti da moderatore, quelli che si fanno guardando il cruscotto: mettere in
 * castigo chi sta rovinando la partita agli altri, e rimetterlo a posto quando
 * ha capito.
 *
 * Il punto delicato e' che meta' di questi gesti **in Minecraft non esistono**.
 * Chi arriva dai server con i plugin da' per scontato `/mute` e `/jail`, ma un
 * server appena installato non li ha, e un comando che non esiste non fallisce
 * in modo rumoroso: il server risponde «Unknown command» e chi ha premuto il
 * tasto crede di aver fatto qualcosa. E' il modo piu' facile per lasciare in
 * giro un vandalo convinti di averlo fermato.
 *
 * Quindi: quello che si puo' costruire coi comandi che ci sono, lo si
 * costruisce; quello che non c'e', lo si dice.
 */
object Moderazione {

    /** Il posto dove si finisce quando si esagera. */
    data class Prigione(
        val x: Int,
        val y: Int,
        val z: Int,
        /** In quale mondo sta: senza, un teletrasporto resta nel mondo di chi lo subisce. */
        val dimensione: String = "minecraft:overworld",
    )

    /** Dov'era prima, per poterlo rimettere li'. */
    data class Dove(val x: Int, val y: Int, val z: Int, val dimensione: String)

    /**
     * Mandare in prigione.
     *
     * Non c'e' nessun comando che lo faccia: si compone di due cose che
     * esistono. Prima la modalita' avventura — cosi' non puo' rompere niente
     * nemmeno mentre arriva — e solo dopo il teletrasporto. L'ordine non e'
     * estetico: fra un comando e l'altro passa qualche decimo di secondo, e in
     * quei decimi di secondo uno in sopravvivenza puo' ancora dare una picconata.
     *
     * Il teletrasporto passa da `execute in <dimensione>` perche' `tp` da solo
     * resta nel mondo dov'e' il giocatore: chi sta nel Nether finirebbe alle
     * stesse coordinate ma nel Nether, che e' l'ottavo posto per distanza e il
     * primo per lava.
     */
    fun incarcera(nome: String, p: Prigione): List<String> = listOf(
        "gamemode adventure $nome",
        "execute in ${p.dimensione} run tp $nome ${p.x} ${p.y} ${p.z}",
    )

    /**
     * Rimetterlo fuori.
     *
     * Se si sa dov'era lo si riporta li'; altrimenti si toglie solo il castigo e
     * lo si lascia dove sta, che e' meno gradito ma e' vero. Rimetterlo allo
     * spawn «perche' bisogna metterlo da qualche parte» vorrebbe dire spostare
     * qualcuno di migliaia di blocchi senza averlo promesso.
     */
    fun libera(nome: String, dove: Dove?): List<String> = buildList {
        add("gamemode survival $nome")
        if (dove != null) add("execute in ${dove.dimensione} run tp $nome ${dove.x} ${dove.y} ${dove.z}")
    }

    /** Il posto dove si trova adesso, da salvare prima di incarcerarlo. */
    fun dove(v: Cruscotto.Vitali): Dove? {
        val p = v.posizione ?: return null
        return Dove(
            Math.round(p.first).toInt(),
            Math.round(p.second).toInt(),
            Math.round(p.third).toInt(),
            v.dimensione ?: "minecraft:overworld",
        )
    }

    /**
     * Azzittire.
     *
     * Questo davvero non si puo' costruire: in Minecraft non c'e' modo di
     * togliere la parola a qualcuno lasciandolo in gioco. Esiste solo dove
     * qualcuno ha installato un plugin che lo aggiunge, e i plugin non si
     * chiamano tutti allo stesso modo.
     *
     * Si prova quello piu' diffuso e si guarda la risposta. Se il server dice
     * che non sa cos'e', si dice a chi ha premuto — e si dicono le due cose che
     * funzionano dappertutto.
     */
    fun comandoSilenzio(nome: String, minuti: Int?): String =
        if (minuti == null) "mute $nome" else "mute $nome ${minuti}m"

    /**
     * Il server ha risposto che quel comando non ce l'ha.
     *
     * Vanilla risponde «Unknown or incomplete command»; alcuni server modificati
     * rispondono in altro modo, e uno che risponde con la guida del comando lo
     * ha, quindi non conta come mancante.
     */
    fun comandoAssente(risposta: String): Boolean {
        val r = risposta.lowercase()
        return r.contains("unknown or incomplete command") ||
                r.contains("unknown command") ||
                r.contains("comando sconosciuto")
    }

    /** Cosa dire quando non c'e'. */
    const val SENZA_SILENZIO =
        "Questo server non sa azzittire: in Minecraft il comando non esiste, " +
                "lo aggiungono solo alcuni plugin.\n\n" +
                "Quello che puoi fare subito: mandarlo in prigione — resta in gioco ma " +
                "non puo' rompere niente — oppure espellerlo, che lo stacca e gli fa " +
                "leggere il motivo."

    /**
     * Quanto e' grave, per decidere cosa chiedere di confermare.
     *
     * La prigione si disfa in un gesto, il ban no: uno si puo' dare in fretta,
     * l'altro va confermato scrivendo il motivo, che e' anche il momento in cui
     * si ha il tempo di accorgersi di aver sbagliato nome.
     */
    enum class Gesto(val etichetta: String, val reversibile: Boolean) {
        PRIGIONE("Manda in prigione", true),
        LIBERA("Fai uscire", true),
        SILENZIO("Azzittisci", true),
        ESPELLI("Espelli", true),
        BLOCCA("Blocca", false),
    }
}
