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
     * Cosa ha risposto ogni porta quando le si è chiesto chi è.
     *
     * @param riconosciute porta → slug della mappa che si è presentata lì.
     * @param web le porte che hanno risposto qualcosa a una richiesta HTTP,
     *   anche senza farsi riconoscere: sono pagine web, quindi *possono* essere
     *   una mappa. Le altre no.
     */
    data class Sondaggio(val riconosciute: Map<Int, String>, val web: Set<Int>) {
        /**
         * Una sonda che non ha trovato niente da nessuna parte non è la prova
         * che non ci sia niente: è più probabile che sul server non ci siano né
         * `curl` né `wget`. In quel caso vale come non fatta, e si torna a
         * ragionare sulle sole porte in ascolto.
         */
        val utile: Boolean get() = riconosciute.isNotEmpty() || web.isNotEmpty()
    }

    /**
     * Chiede a ogni porta chi è.
     *
     * Qui prima si chiedeva a chi usa l'app: «sono in ascolto la 8100 e la
     * 9090, quale delle due è la mappa?». Ma quella risposta ce l'ha il server,
     * e gliela si può chiedere: una mappa è un sito, un sito risponde, e nella
     * pagina che manda c'è scritto come si chiama. Le tre si presentano tutte e
     * tre nel titolo della loro pagina.
     *
     * La richiesta parte **dal server verso se stesso** — `127.0.0.1` — non dal
     * telefono: così funziona anche quando la mappa è in ascolto solo sul
     * locale, che è il caso normale e quello giusto.
     *
     * Oltre alla pagina si guardano i due file di configurazione che Dynmap e
     * BlueMap pubblicano: costano una richiesta e servono al caso della pagina
     * che si disegna da sola col JavaScript e da ferma non dice niente.
     *
     * `wget` dietro `curl` perché su una macchina spoglia c'è l'uno o l'altro,
     * quasi mai tutti e due.
     */
    fun comandoSonda(porte: List<Int>): String {
        if (porte.isEmpty()) return "echo"
        val prendi = "prendi() { curl -fsS -m 3 \"${'$'}1\" 2>/dev/null || " +
                "wget -qO- -T 3 \"${'$'}1\" 2>/dev/null; }"
        val giri = porte.joinToString("\n") { p ->
            "echo '=== PORTA $p'; { " +
                    "prendi 'http://127.0.0.1:$p/'; " +
                    "prendi 'http://127.0.0.1:$p/settings.json'; " +
                    "prendi 'http://127.0.0.1:$p/up/configuration'; " +
                    "} | head -c 4000; echo"
        }
        return "$prendi\n$giri"
    }

    /**
     * Chi si è presentato, e dove.
     *
     * Si contano le volte che compare il nome di ognuna delle tre invece di
     * fermarsi alla prima trovata: la pagina di una mappa nomina se stessa
     * decine di volte e le altre al massimo una, in un commento o in un link, e
     * fermarsi alla prima farebbe vincere quella sbagliata per un'inezia. Se
     * due vanno pari, nessuna delle due viene riconosciuta: meglio non sapere
     * che sapere male.
     */
    fun leggiSonda(risposta: String): Sondaggio {
        val riconosciute = mutableMapOf<Int, String>()
        val web = mutableSetOf<Int>()
        var porta = 0
        val corpo = StringBuilder()

        fun chiudi() {
            if (porta > 0) {
                val testo = corpo.toString()
                if (testo.isNotBlank()) web += porta
                val conteggio = CATALOGO
                    .map { it to Regex(it.nelNomeFile, RegexOption.IGNORE_CASE).findAll(testo).count() }
                    .filter { it.second > 0 }
                    .sortedByDescending { it.second }
                val netta = conteggio.size == 1 ||
                        (conteggio.size > 1 && conteggio[0].second > conteggio[1].second)
                if (netta) riconosciute[porta] = conteggio[0].first.slug
            }
            porta = 0
            corpo.setLength(0)
        }

        risposta.lineSequence().forEach { riga ->
            val marcatore = Regex("""^=== PORTA (\d{1,5})$""").find(riga.trim())
            if (marcatore != null) {
                chiudi()
                porta = marcatore.groupValues[1].toInt()
            } else {
                corpo.append(riga).append('\n')
            }
        }
        chiudi()
        return Sondaggio(riconosciute, web)
    }

    /**
     * Le porte che possono davvero essere questa mappa.
     *
     * Quando la sonda ha funzionato restano solo le porte che rispondono a una
     * richiesta web — una porta muta non è un sito e quindi non è una mappa — e
     * si tolgono quelle dove si è presentata **un'altra** delle tre: due mappe
     * installate insieme è un caso raro ma non assurdo, e lì la porta dell'una
     * non va mai proposta per l'altra.
     *
     * Quasi sempre questo basta da solo: delle porte comparse dopo
     * l'installazione, una sola parla HTTP.
     */
    fun restano(mappa: Mappa, candidate: List<Int>, sondaggio: Sondaggio? = null): List<Int> {
        val s = sondaggio?.takeIf { it.utile } ?: return candidate
        // Se qualcuna si è nominata, le altre non sono in gara: una porta che
        // non ha detto niente non può battere una che ha detto di essere lei.
        val sue = candidate.filter { s.riconosciute[it] == mappa.slug }
        if (sue.isNotEmpty()) return sue
        return candidate.filter { s.riconosciute[it] == null && it in s.web }
    }

    /** Cosa fare, dopo aver guardato le porte. */
    sealed interface Scelta {
        /** Si è capito: è questa. */
        data class Aprila(val porta: Int) : Scelta

        /** Restano più possibilità vere, e la differenza la sa solo chi guarda. */
        data class Chiedi(val fra: List<Int>) : Scelta

        /** C'è una porta scritta a mano in configurazione, e lì non risponde nessuno. */
        data class FissataMuta(val porta: Int) : Scelta

        /** Non c'è niente da aprire da nessuna parte. */
        data object NienteDaAprire : Scelta
    }

    /**
     * Quale porta aprire, e cosa dire quando non si sa.
     *
     * L'ordine delle risposte è la regola:
     *
     * 1. **la porta scritta a mano**, se c'è. Non si discute e non si corregge:
     *    chi l'ha scritta sa qualcosa che l'app non può sapere. Se lì non
     *    risponde nessuno lo si dice e ci si ferma — ripiegare su un'altra
     *    porta vorrebbe dire aprire la mappa di un mondo diverso facendola
     *    passare per questa;
     * 2. **la porta dove la mappa ha detto di essere lei**, se è una sola. Non
     *    è una supposizione come le altre: è la mappa che risponde e si nomina;
     * 3. la porta che ha funzionato l'ultima volta, se è ancora lì. Serve a chi
     *    la mappa se l'è messa dietro qualcosa che non si fa riconoscere, e
     *    serve a distinguere due mappe uguali su due server diversi;
     * 4. la porta predefinita della mappa scelta;
     * 5. l'unica rimasta, se ne è rimasta una sola.
     *
     * **Due mappe uguali sulla stessa macchina** — due server Minecraft nello
     * stesso Linux, che è il caso per cui la porta si può scrivere a mano — si
     * presentano tutte e due con lo stesso nome. Lì la predefinita non è una
     * risposta: sarebbe testa o croce, e una delle due facce mostra il mondo
     * sbagliato dicendo che è il tuo. Quindi si chiede, e la risposta si
     * ricorda.
     */
    fun scegliLaPorta(
        mappa: Mappa,
        candidate: List<Int>,
        ricordata: Int = 0,
        fissata: Int = 0,
        sondaggio: Sondaggio? = null,
    ): Scelta {
        if (fissata > 0) {
            return if (fissata in candidate) Scelta.Aprila(fissata) else Scelta.FissataMuta(fissata)
        }
        val rimaste = restano(mappa, candidate, sondaggio)
        val s = sondaggio?.takeIf { it.utile }
        val dettesi = rimaste.filter { s?.riconosciute?.get(it) == mappa.slug }
        return when {
            dettesi.size == 1 -> Scelta.Aprila(dettesi.first())
            ricordata > 0 && ricordata in rimaste -> Scelta.Aprila(ricordata)
            dettesi.size > 1 -> Scelta.Chiedi(dettesi)
            mappa.portaPredefinita in rimaste -> Scelta.Aprila(mappa.portaPredefinita)
            rimaste.size == 1 -> Scelta.Aprila(rimaste.first())
            rimaste.isEmpty() -> Scelta.NienteDaAprire
            else -> Scelta.Chiedi(rimaste)
        }
    }

    /**
     * La porta, quando basta un numero o niente.
     *
     * La regola vera sta in [scegliLaPorta], che sa anche dire *perche'* non ha
     * scelto. Questa serve dove quella distinzione non c'e': durante
     * l'installazione, dove si aspetta soltanto che la mappa si affacci.
     */
    fun portaDellaMappa(
        mappa: Mappa,
        candidate: List<Int>,
        ricordata: Int = 0,
        sondaggio: Sondaggio? = null,
    ): Int? = (scegliLaPorta(mappa, candidate, ricordata, 0, sondaggio) as? Scelta.Aprila)?.porta

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
