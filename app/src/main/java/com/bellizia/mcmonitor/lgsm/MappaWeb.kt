package com.bellizia.mcmonitor.lgsm

/**
 * Le mappe del mondo che si guardano dal browser.
 *
 * Prima qui c'era solo una casella dove incollare un indirizzo, e per avere un
 * indirizzo da incollare bisognava già sapere cosa sono Dynmap e BlueMap,
 * installarne uno a mano e sapere su che porta si mette. Cioè: la casella
 * serviva a chi non ne aveva bisogno.
 *
 * Queste tre sono mod come le altre e si installano con la macchina che c'è già
 * per le mod. Quello che cambia è che dopo l'installazione **si mettono in
 * ascolto su una porta**, e trovarla è la parte che qui si fa da soli: i tre
 * programmi scrivono la loro configurazione in tre formati diversi, e leggerla
 * vorrebbe dire conoscerli tutti e tre per sempre. Si guarda invece **quali
 * porte sono comparse** dopo il riavvio, che è vero comunque sia scritta la
 * configurazione.
 *
 * Nessuna regola sul firewall: la mappa si raggiunge dentro il tunnel SSH che
 * l'app apre già per RCON. Dal di fuori quella porta resta chiusa, ed è giusto
 * così — una mappa aperta a tutti dice a chiunque dove hai costruito casa.
 */
object MappaWeb {

    data class Mappa(
        val nome: String,
        /** Il nome su Modrinth: è da lì che si scarica. */
        val slug: String,
        /** La porta che usa appena installata, se non la si cambia. */
        val portaPredefinita: Int,
        /** Come si riconosce fra i file già installati. */
        val nelNomeFile: String,
        /** Una riga per chi non sa cosa sia nessuna delle tre. */
        val comeE: String,
    )

    val CATALOGO = listOf(
        Mappa(
            nome = "BlueMap",
            slug = "bluemap",
            portaPredefinita = 8100,
            nelNomeFile = "bluemap",
            comeE = "Il mondo in tre dimensioni, come se ci volassi sopra. " +
                    "È la più bella da guardare ed è anche quella che fa lavorare " +
                    "di più il computer la prima volta che disegna tutto.",
        ),
        Mappa(
            nome = "Dynmap",
            slug = "dynmap",
            portaPredefinita = 8123,
            nelNomeFile = "dynmap",
            comeE = "La mappa vista dall'alto, come quella di un navigatore. " +
                    "È la più vecchia e la più diffusa: se qualcuno ti aiuta, " +
                    "probabilmente conosce questa.",
        ),
        Mappa(
            nome = "squaremap",
            slug = "squaremap",
            portaPredefinita = 8080,
            nelNomeFile = "squaremap",
            comeE = "Anche questa vista dall'alto, ma fatta per pesare poco: " +
                    "è quella da scegliere se il computer è piccolo.",
        ),
    )

    fun perSlug(slug: String): Mappa? = CATALOGO.firstOrNull { it.slug == slug }

    /**
     * Quale mappa è già installata, guardando i nomi dei file delle mod.
     *
     * Si guarda il nome del file e non la configurazione perché il file c'è
     * comunque, anche quando la mod è installata ma spenta — e «ce l'hai già,
     * è solo spenta» è una risposta diversa da «non ce l'hai».
     */
    fun installata(nomiFile: List<String>): Mappa? = CATALOGO.firstOrNull { mappa ->
        nomiFile.any { it.lowercase().contains(mappa.nelNomeFile) }
    }

    /** Riconosce la mappa da un indirizzo già salvato, per dire di quale si tratta. */
    fun dallaPorta(porta: Int): Mappa? = CATALOGO.firstOrNull { it.portaPredefinita == porta }

    /**
     * Le porte in ascolto sul server, per capire dove si è messa la mappa.
     *
     * Si tolgono quelle che sappiamo già cosa sono — SSH, il gioco, RCON — così
     * quello che resta è quasi sempre una sola porta ed è quella giusta. Le
     * porte sotto la 1024 non le può aprire il server di Minecraft, e quindi
     * non sono sue.
     */
    fun comandoPorteInAscolto(): String =
        "ss -ltn 2>/dev/null || netstat -ltn 2>/dev/null || echo NESSUNA"

    /**
     * Le porte lette dalla risposta, senza quelle che sappiamo già cosa sono.
     *
     * La risposta si legge qui e non con una catena di `grep` sul server: la
     * riga di `ss` cambia forma da una macchina all'altra — `0.0.0.0:22`,
     * `*:25565`, `[::]:8100`, `127.0.0.1:8100` — e una catena di comandi che
     * sbaglia a leggerla non lo dice, torna semplicemente meno porte. Qui invece
     * si può provare con le righe vere.
     */
    fun porteCandidate(risposta: String, gioco: Int, rcon: Int): List<Int> {
        val note = setOf(22, gioco, rcon)
        return risposta.lineSequence()
            .drop(1) // l'intestazione: "Local Address:Port" non è una porta
            .mapNotNull { riga ->
                Regex("""[\d.*\]]:(\d{1,5})""").find(riga)?.groupValues?.get(1)?.toIntOrNull()
            }
            .filter { it in 1024..65535 && it !in note }
            .distinct()
            .sorted()
            .toList()
    }

    /**
     * Quale porta proporre.
     *
     * Se quella predefinita della mappa scelta è in ascolto è quella, senza
     * discutere. Altrimenti si prende l'unica rimasta; se ne restano diverse non
     * si tira a indovinare, perché aprire la porta sbagliata mostra una pagina
     * bianca e fa credere che l'installazione sia fallita.
     */
    fun portaDellaMappa(mappa: Mappa, candidate: List<Int>): Int? = when {
        mappa.portaPredefinita in candidate -> mappa.portaPredefinita
        candidate.size == 1 -> candidate.first()
        else -> null
    }

    /**
     * L'indirizzo da aprire, dentro il tunnel.
     *
     * La porta locale non è quella del server: il tunnel ne sceglie una libera
     * sul telefono, e quella del server sta dall'altro capo.
     */
    fun indirizzoNelTunnel(portaLocale: Int): String = "http://127.0.0.1:$portaLocale"

    /** Un indirizzo salvato che punta al tunnel non vale niente domani: cambia ogni volta. */
    fun daNonSalvare(url: String): Boolean =
        url.contains("127.0.0.1") || url.contains("localhost", ignoreCase = true)
}
