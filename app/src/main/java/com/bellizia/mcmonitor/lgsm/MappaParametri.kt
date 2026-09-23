package com.bellizia.mcmonitor.lgsm

import com.bellizia.mcmonitor.data.ServerConfig
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Le impostazioni delle mappe web, quelle poche che si cambiano davvero.
 *
 * Fin qui l'app le mappe le installava e le apriva, ma la configurazione non
 * l'aveva mai toccata: i tre programmi scrivono in tre formati diversi, e
 * leggerli per intero vorrebbe dire conoscerli tutti e tre per sempre. Sta in
 * piedi ancora — per *cercare* la porta si guardano le porte, che sono vere
 * comunque sia scritta la configurazione. Ma c'è un caso in cui non basta.
 *
 * **BlueMap appena installato non parte.** Aspetta che qualcuno scriva
 * `accept-download: true`, perché per disegnare il mondo deve scaricare da
 * Mojang i file del gioco e non lo fa senza permesso. Finché quella riga dice
 * `false` non c'è nessuna mappa e nessuna porta aperta: l'app la installava,
 * riavviava, non trovava niente e diceva «forse sta ancora disegnando». Una
 * porta che non si apre mai, e un'attesa che non finisce.
 *
 * Quindi le impostazioni si toccano, ma **solo queste**: quelle senza cui la
 * mappa non funziona, e quelle che dicono dove si affaccia. Tutto il resto del
 * file resta roba dei tre programmi.
 *
 * I percorsi non sono quelli che verrebbe da indovinare: Dynmap e squaremap non
 * stanno sotto `config/`, ma in una cartella loro nella radice del server.
 */
object MappaParametri {

    data class Parametro(
        val id: String,
        val titolo: String,
        val spiegazione: String,
        /** Il file, relativo alla radice del server. */
        val file: String,
        /** La chiave per intero: `settings` → `internal-webserver` → `port`. */
        val percorso: List<String>,
        val tipo: Tipo,
        /** Il valore di fabbrica, già come lo si legge a schermo. */
        val diFabbrica: String,
        /** Se nel file il valore va fra virgolette. */
        val virgolette: Boolean = false,
        /**
         * Se nel file la domanda è scritta al contrario.
         *
         * `disable-webserver: false` vuol dire che il sito è **acceso**. Chi lo
         * legge come «acceso: no» lo spegne credendo di accenderlo, e questa è
         * l'unica riga dell'app che lo sappia.
         */
        val invertito: Boolean = false,
        val min: Int = 0,
        val max: Int = 0,
        val avviso: ((String) -> String?)? = null,
    ) {
        val booleano: Boolean get() = tipo == Tipo.INTERRUTTORE
    }

    private val PORTA_ALTA = "Sotto la 1024 non può: il server di Minecraft non gira da amministratore."

    private fun porta(file: String, percorso: List<String>, diFabbrica: String) = Parametro(
        id = "porta",
        titolo = "Porta",
        spiegazione = "Su quale porta si affaccia la mappa. L'app la trova da sola, " +
                "quindi cambiarla serve solo se quella porta è già di qualcun altro.",
        file = file,
        percorso = percorso,
        tipo = Tipo.NUMERO,
        diFabbrica = diFabbrica,
        min = 1024,
        max = 65535,
        avviso = { v -> (v.toIntOrNull() ?: 0).let { if (it in 1..1023) PORTA_ALTA else null } },
    )

    private fun indirizzo(file: String, percorso: List<String>, virgolette: Boolean) = Parametro(
        id = "indirizzo",
        titolo = "Da dove si può raggiungere",
        spiegazione = "0.0.0.0 vuol dire da tutta la rete, 127.0.0.1 solo dal computer " +
                "del server. Dal telefono la mappa passa comunque dentro il " +
                "collegamento che l'app usa già, quindi 127.0.0.1 è la scelta " +
                "prudente: la mappa resta tua e nessuno la trova girando.",
        file = file,
        percorso = percorso,
        tipo = Tipo.TESTO,
        diFabbrica = "0.0.0.0",
        virgolette = virgolette,
    )

    private val BLUEMAP = listOf(
        Parametro(
            id = "accept-download",
            titolo = "Permesso di scaricare i file del gioco",
            spiegazione = "Per disegnare il mondo BlueMap ha bisogno delle texture " +
                    "ufficiali, e le scarica da Mojang. Non lo fa senza permesso, e " +
                    "finché il permesso non c'è non parte affatto.",
            file = "config/bluemap/core.conf",
            percorso = listOf("accept-download"),
            tipo = Tipo.INTERRUTTORE,
            diFabbrica = "false",
            avviso = { v ->
                if (v == "true") null
                else "Finché è spento BlueMap non parte: nessuna mappa, nessuna porta aperta."
            },
        ),
        Parametro(
            id = "enabled",
            titolo = "Mappa web accesa",
            spiegazione = "Spegnendola BlueMap continua a disegnare il mondo ma non lo " +
                    "mostra a nessuno.",
            file = "config/bluemap/webserver.conf",
            percorso = listOf("enabled"),
            tipo = Tipo.INTERRUTTORE,
            diFabbrica = "true",
        ),
        porta("config/bluemap/webserver.conf", listOf("port"), "8100"),
        indirizzo("config/bluemap/webserver.conf", listOf("ip"), virgolette = true),
    )

    private val DYNMAP = listOf(
        Parametro(
            id = "enabled",
            titolo = "Mappa web accesa",
            spiegazione = "Dynmap sa anche appoggiarsi a un sito esterno invece di " +
                    "tenerne uno suo. Spenta qui, dall'app non si apre più.",
            file = "dynmap/configuration.txt",
            percorso = listOf("disable-webserver"),
            tipo = Tipo.INTERRUTTORE,
            diFabbrica = "true",
            invertito = true,
        ),
        porta("dynmap/configuration.txt", listOf("webserver-port"), "8123"),
        indirizzo("dynmap/configuration.txt", listOf("webserver-bindaddress"), virgolette = false),
    )

    private val SQUAREMAP = listOf(
        Parametro(
            id = "enabled",
            titolo = "Mappa web accesa",
            spiegazione = "Spenta, squaremap continua a disegnare il mondo ma non lo " +
                    "mostra a nessuno.",
            file = "squaremap/config.yml",
            percorso = listOf("settings", "internal-webserver", "enabled"),
            tipo = Tipo.INTERRUTTORE,
            diFabbrica = "true",
        ),
        porta("squaremap/config.yml", listOf("settings", "internal-webserver", "port"), "8080"),
        indirizzo("squaremap/config.yml", listOf("settings", "internal-webserver", "bind"), virgolette = false),
        Parametro(
            id = "web-address",
            titolo = "Indirizzo che vedono i giocatori",
            spiegazione = "squaremap lo dice ai giocatori che hanno la sua mod sul " +
                    "computer. Non è la porta: è quello che va scritto nel browser. " +
                    "Se cambi la porta e non cambi questo, loro continuano a " +
                    "bussare a quella di ieri.",
            file = "squaremap/config.yml",
            percorso = listOf("settings", "web-address"),
            tipo = Tipo.TESTO,
            diFabbrica = "http://localhost:8080",
        ),
    )

    fun per(mappa: MappaWeb.Mappa): List<Parametro> = when (mappa.slug) {
        "bluemap" -> BLUEMAP
        "dynmap" -> DYNMAP
        "squaremap" -> SQUAREMAP
        else -> emptyList()
    }

    fun file(mappa: MappaWeb.Mappa): List<String> = per(mappa).map { it.file }.distinct()

    /** Il valore come si legge a schermo, partendo da quello scritto nel file. */
    fun aSchermo(p: Parametro, nelFile: String?): String {
        val v = nelFile ?: return p.diFabbrica
        if (!p.invertito) return v
        return when (v) {
            "true" -> "false"
            "false" -> "true"
            else -> v
        }
    }

    /** Il testo da mettere nel file, partendo da quello che si è scelto a schermo. */
    fun nelFile(p: Parametro, aSchermo: String): String {
        val v = if (!p.invertito) aSchermo else when (aSchermo) {
            "true" -> "false"
            "false" -> "true"
            else -> aSchermo
        }
        return if (p.virgolette) "\"" + v.replace("\"", "") + "\"" else v
    }

    /**
     * Il valore come si dice a voce.
     *
     * `true` e `false` sono parole del file, non della lingua: in una frase che
     * chiede conferma -- «scrivo questo sul server» -- vanno dette come si
     * direbbero a qualcuno.
     */
    fun etichetta(p: Parametro, valore: String): String = when {
        !p.booleano -> valore
        valore == "true" -> "acceso"
        else -> "spento"
    }

    /**
     * Cosa non va in un valore scelto, detto a chi l'ha scelto.
     *
     * Null vuol dire che va bene. Una porta fuori posto non si rifiuta in
     * silenzio: la si rifiuta dicendo perché, o sembra che l'app non funzioni.
     */
    fun controlla(p: Parametro, valore: String): String? = when {
        valore.isBlank() -> "Non può restare vuoto."
        p.tipo == Tipo.NUMERO && valore.toIntOrNull() == null -> "Ci va un numero."
        p.tipo == Tipo.NUMERO && p.max > 0 && valore.toInt() !in p.min..p.max ->
            "Fra ${p.min} e ${p.max}."
        p.tipo == Tipo.TESTO && valore.any { it.isWhitespace() } -> "Niente spazi."
        else -> null
    }

    // ------------------------------------------------------- leggere e scrivere

    private const val MARCATORE = "=== FILE "

    private fun completo(cfg: ServerConfig, file: String) =
        "${cfg.serverFiles.trimEnd('/')}/$file"

    /**
     * Legge in un colpo solo tutti i file di questa mappa.
     *
     * Con i marcatori, come per le porte: un giro di SSH invece di tre, e la
     * risposta si divide qui dove la si può provare con le righe vere.
     */
    fun comandoLeggi(cfg: ServerConfig, mappa: MappaWeb.Mappa): String =
        file(mappa).joinToString("\n") { f ->
            "echo '$MARCATORE$f'; cat ${Lgsm.path(completo(cfg, f))} 2>/dev/null"
        }.ifBlank { "echo" }

    fun leggiFile(risposta: String): Map<String, String> {
        val fuori = linkedMapOf<String, StringBuilder>()
        var attuale: StringBuilder? = null
        risposta.split("\n").forEach { riga ->
            if (riga.trimEnd().startsWith(MARCATORE)) {
                val nome = riga.trimEnd().removePrefix(MARCATORE).trim()
                attuale = StringBuilder().also { fuori[nome] = it }
            } else {
                attuale?.append(riga)?.append('\n')
            }
        }
        // Un file che non esiste ancora torna vuoto: e' diverso da un file che
        // esiste e non dice niente, ma qui vale lo stesso -- non c'e' niente da
        // mostrare e non c'e' niente da cambiare.
        return fuori.mapValues { it.value.toString().trimEnd('\n') }
            .filterValues { it.isNotBlank() }
    }

    /**
     * Scrive un file intero, dopo essersene messo da parte una copia.
     *
     * Il testo passa in base64 e non fra virgolette: un file di configurazione è
     * pieno di apici, virgolette, dollari e cancelletti, e una sola di quelle
     * cose al posto sbagliato non dà errore — riscrive il file storto. In
     * base64 non c'è niente da sbagliare.
     */
    fun comandoScrivi(cfg: ServerConfig, file: String, testo: String): String {
        val f = Lgsm.path(completo(cfg, file))
        val b64 = Base64.getEncoder().encodeToString(testo.toByteArray(StandardCharsets.UTF_8))
        return "f=$f; [ -f \"\$f\" ] || { echo 'FILE NON TROVATO'; exit 9; }; " +
                "cp \"\$f\" \"\$f.mcmonitor.bak.\$(date +%Y%m%d%H%M%S)\" || " +
                "{ echo 'COPIA DI SICUREZZA NON RIUSCITA'; exit 8; }; " +
                "printf %s '$b64' | base64 -d > \"\$f.mcmonitor.tmp\" && " +
                "mv \"\$f.mcmonitor.tmp\" \"\$f\" && echo 'SCRITTO'"
    }

    fun scritto(risposta: String): Boolean = Lgsm.clean(risposta).contains("SCRITTO")
}
