package com.bellizia.mcmonitor.lgsm

import com.bellizia.mcmonitor.data.ServerConfig

/** Un file del giocatore trovato dentro un archivio, con quanto pesa. */
data class VoceArchivio(val cosa: String, val byte: Long)

/** Dove è finito un file che c'era prima, e che adesso è stato messo da parte. */
data class MessoDaParte(val cosa: String, val percorso: String)

/**
 * I file di un solo giocatore dentro una copia di sicurezza.
 *
 * Rimettere un backup intero per restituire la roba a una persona vuol dire
 * cancellare quello che tutti gli altri hanno costruito da allora. Questo
 * riguarda soltanto i file di quella persona: `playerdata/<id>.dat`, e su
 * richiesta anche statistiche e progressi.
 *
 * Lo script sta in `resources/archivio-giocatore.sh` e non qui dentro: è lungo,
 * e uno script sh si prova lanciandolo davvero — lo fa
 * `tools/prova-archivio-giocatore.sh`.
 */
object ArchivioGiocatore {

    /** Codice di uscita convenzionale: l'archivio non c'è più. */
    const val EXIT_NO_ARCHIVIO = 95

    /** Codice di uscita convenzionale: quel giocatore in quell'archivio non c'è. */
    const val EXIT_NIENTE_GIOCATORE = 90

    /** Codice di uscita convenzionale: l'estrazione o la copia non è riuscita. */
    const val EXIT_ESTRAZIONE = 89

    /** Codice di uscita convenzionale: sul server manca `tar` o `base64`. */
    const val EXIT_STRUMENTI = 88

    private val UUID_VALIDO = Regex("^[0-9a-fA-F-]{32,36}$")

    /** Cosa si rimette a posto. */
    enum class Cosa(val chiave: String) {
        /** Solo `.dat`: inventario, posizione, vita, esperienza. */
        SOLO_INVENTARIO("dat"),

        /** Anche statistiche e progressi. */
        TUTTO("tutto"),
    }

    private enum class Modo(val chiave: String) { LEGGI("leggi"), RIMETTI("rimetti") }

    /** Legge dall'archivio il file del giocatore, senza toccare niente. */
    fun leggi(cfg: ServerConfig, archivio: String, mondo: String, uuid: String): String =
        comando(cfg, archivio, mondo, uuid, Modo.LEGGI, Cosa.SOLO_INVENTARIO)

    /** Rimette a posto i file di quel giocatore, spostando di lato quelli di adesso. */
    fun rimetti(
        cfg: ServerConfig,
        archivio: String,
        mondo: String,
        uuid: String,
        cosa: Cosa,
    ): String = comando(cfg, archivio, mondo, uuid, Modo.RIMETTI, cosa)

    private fun comando(
        cfg: ServerConfig,
        archivio: String,
        mondo: String,
        uuid: String,
        modo: Modo,
        cosa: Cosa,
    ): String {
        require(Backups.nomeValido(archivio)) { "nome di archivio non valido: $archivio" }
        require(UUID_VALIDO.matches(uuid)) { "identificativo non valido: $uuid" }

        // Il nome del mondo finisce in un percorso e in un modello per tar: fuori
        // tutto quello che potrebbe uscire dalla cartella o dire qualcosa alla shell.
        val cartella = mondo.filter { it.isLetterOrDigit() || it in "-_. " }.trim()
            .ifBlank { "world" }

        return modello()
            .replace("\r", "")
            .replace("@@ARCHIVIO@@", "${Lgsm.path(Backups.dir(cfg))}/${Lgsm.sq(archivio)}")
            .replace("@@SERVERFILES@@", Lgsm.path(cfg.serverFiles.trimEnd('/')))
            .replace("@@MONDO@@", Lgsm.sq(cartella))
            .replace("@@UUID@@", Lgsm.sq(uuid))
            .replace("@@MODO@@", modo.chiave)
            .replace("@@COSA@@", cosa.chiave)
            .replace("@@E_NO_ARCHIVIO@@", EXIT_NO_ARCHIVIO.toString())
            .replace("@@E_NIENTE_GIOCATORE@@", EXIT_NIENTE_GIOCATORE.toString())
            .replace("@@E_ESTRAZIONE@@", EXIT_ESTRAZIONE.toString())
            .replace("@@E_STRUMENTI@@", EXIT_STRUMENTI.toString())
    }

    private fun modello(): String =
        ArchivioGiocatore::class.java.getResourceAsStream("/archivio-giocatore.sh")
            ?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("archivio-giocatore.sh non e' nel pacchetto")

    // ------------------------------------------------------------- le risposte

    /** Cosa c'era per quel giocatore dentro l'archivio. */
    fun trovati(raw: String): List<VoceArchivio> =
        Regex("@@TROVATO\\s+(\\w+)=(\\d+)").findAll(Lgsm.clean(raw))
            .map { VoceArchivio(it.groupValues[1], it.groupValues[2].toLong()) }
            .toList()

    /** Dove sono finiti i file di adesso: è l'unico modo per tornare indietro. */
    fun messiDaParte(raw: String): List<MessoDaParte> =
        Regex("@@DAPARTE\\s+(\\S+)\\s+(.+)").findAll(Lgsm.clean(raw))
            .map { MessoDaParte(it.groupValues[1], it.groupValues[2].trim()) }
            .toList()

    /** Quali file sono stati rimessi davvero. */
    fun rimessi(raw: String): List<String> =
        Regex("@@RIMESSO\\s+(\\S+)").findAll(Lgsm.clean(raw))
            .map { it.groupValues[1] }
            .toList()

    /**
     * Il server ha riscritto il file mentre l'app leggeva l'archivio.
     *
     * Vuol dire che il giocatore non era fuori: quello che si è appena visto non
     * era il suo stato di adesso. Il lavoro è comunque riuscito e si torna
     * indietro, ma è una cosa che va detta invece che nascosta.
     */
    fun cambiatoSottoIPiedi(raw: String): Boolean = Lgsm.clean(raw).contains("@@CAMBIATO")

    /** Ha finito tutto. */
    fun fatto(raw: String): Boolean = Lgsm.clean(raw).contains("@@FATTO")
}
