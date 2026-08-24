package com.bellizia.mcmonitor.lgsm

import com.bellizia.mcmonitor.data.ServerConfig
import java.util.Base64

/** Un messaggio in attesa: verra' consegnato al prossimo collegamento. */
data class Messaggio(
    val quando: Long,
    val giocatore: String,
    val testo: String
) {
    /** La riga come sta nel file sul server: e' anche la chiave per cancellarla. */
    fun riga(): String = quando.toString() + "\t" + giocatore + "\t" +
            Base64.getEncoder().encodeToString(testo.toByteArray(Charsets.UTF_8))
}

/** Un messaggio gia' partito, come risulta dal registro sul server. */
data class Consegna(
    val quando: String,
    val giocatore: String,
    val testo: String,
    /** Vero quando il messaggio era illeggibile ed e' stato messo da parte. */
    val illeggibile: Boolean = false
)

/**
 * La posta per chi non c'e'.
 *
 * L'admin scrive a un giocatore che in quel momento non e' collegato, e il
 * messaggio gli arriva in chat quando rientra.
 *
 * Sta sul server e non sul telefono per un motivo solo: il telefono non e'
 * acceso nel momento in cui il giocatore entra, e un messaggio che arriva solo
 * se l'admin ha l'app aperta non e' una posta, e' una coincidenza.
 *
 * La consegna la fa `posta.sh`, installato sul server e lanciato da cron ogni
 * minuto. Quando non c'e' niente in attesa esce subito.
 *
 * Chi scrive nella cassetta -- lo script da una parte, l'app dall'altra --
 * prende prima lo stesso lucchetto: senza, un messaggio accodato dal telefono
 * mentre lo script riscrive il file sparirebbe.
 */
object Posta {

    /** Oltre questa lunghezza la chat del gioco taglia comunque. */
    const val LIMITE_TESTO = 200

    /** Una coda piu' lunga di cosi' vuol dire che qualcosa non sta consegnando. */
    const val MAX_IN_ATTESA = 50

    /** Codice di uscita convenzionale: la cassetta e' piena. */
    const val EXIT_PIENA = 85

    /** Codice di uscita convenzionale: la cassetta e' occupata da qualcun altro. */
    const val EXIT_OCCUPATA = 84

    /** Il nome del lavoro nei marcatori del crontab, accanto a quello del backup. */
    const val LAVORO = "posta"

    private val NOME = Regex("^[A-Za-z0-9_]{1,16}$")

    fun nomeValido(nome: String): Boolean = NOME.matches(nome)

    /**
     * Il testo come puo' finire in una riga di console.
     *
     * Un a capo dentro `send-keys -l` manderebbe mezzo comando, e l'altra meta'
     * finirebbe in console come se l'avesse scritta l'admin.
     */
    fun ripulisci(testo: String): String =
        testo.replace(Regex("[\\r\\n\\t]+"), " ").trim().take(LIMITE_TESTO)

    // ------------------------------------------------------------- percorsi

    // La stessa ripulitura dei marcatori del crontab, e dalla stessa funzione:
    // due copie che divergono vorrebbero dire cassetta e blocco di cron con nomi
    // diversi per lo stesso server.
    private fun slug(cfg: ServerConfig) = Cron.normalizzaSlug(cfg.slug)

    private const val BASE = "\"\$HOME\"/.mcmonitor/posta"

    fun fileCassetta(cfg: ServerConfig) = "$BASE/${slug(cfg)}.txt"

    fun fileScript(cfg: ServerConfig) = "$BASE/${slug(cfg)}.sh"

    fun fileConsegnati(cfg: ServerConfig) = "$BASE/${slug(cfg)}-consegnati.log"

    /** Dove va caricato lo script, relativo alla home: il percorso che vuole SFTP. */
    fun percorsoScript(cfg: ServerConfig) = ".mcmonitor/posta/${slug(cfg)}.sh"

    private const val PREPARA =
        "mkdir -p $BASE && chmod 700 \"\$HOME\"/.mcmonitor $BASE"

    /** La cartella deve esistere prima che SFTP ci scriva dentro. */
    fun preparaCartella(): String = PREPARA

    // -------------------------------------------------------- il lucchetto

    /**
     * Mette il corpo dentro lo stesso lucchetto che prende lo script.
     *
     * Lo script lo tiene per una frazione di secondo, quindi trovarlo occupato
     * e' raro; se pero' e' rimasto li' dopo un kill o una caduta di corrente,
     * dopo cinque minuti si considera morto, altrimenti la cassetta resterebbe
     * bloccata per sempre.
     */
    private fun conLucchetto(cfg: ServerConfig, corpo: String): String =
        "f=${fileCassetta(cfg)}; L=\"\$f.lock\"; preso=0; i=0; " +
                "while [ \$i -lt 8 ]; do " +
                "if mkdir \"\$L\" 2>/dev/null; then preso=1; break; fi; " +
                "[ -n \"\$(find \"\$L\" -maxdepth 0 -mmin +5 2>/dev/null)\" ] && rm -rf \"\$L\" 2>/dev/null; " +
                "i=\$((i+1)); sleep 1; " +
                "done; " +
                "[ \"\$preso\" = 1 ] || { echo 'CASSETTA OCCUPATA'; exit $EXIT_OCCUPATA; }; " +
                "trap 'rm -rf \"\$L\" 2>/dev/null' EXIT; " +
                corpo

    // -------------------------------------------------------------- lettura

    /**
     * Legge la cassetta in base64 fra marcatori.
     *
     * Come per il crontab: la shell di login puo' stampare di suo (un saluto in
     * `.bashrc`, un `neofetch`), e senza i marcatori quella roba entrerebbe fra
     * i messaggi. In lettura non serve il lucchetto: al massimo si vede la
     * cassetta un istante prima di una consegna.
     */
    fun leggi(cfg: ServerConfig): String =
        "f=${fileCassetta(cfg)}; echo '@@INIZIO'; [ -f \"\$f\" ] && base64 <\"\$f\"; echo '@@FINE'"

    fun parseLeggi(raw: String): List<Messaggio> {
        val dentro = Lgsm.clean(raw)
            .substringAfter("@@INIZIO", "")
            .substringBefore("@@FINE", "")
        val b64 = dentro.filter { !it.isWhitespace() }
        if (b64.isEmpty()) return emptyList()

        val contenuto = decodifica(b64) ?: return emptyList()
        return contenuto.lineSequence().mapNotNull { riga ->
            val p = riga.split('\t')
            if (p.size != 3) return@mapNotNull null
            val quando = p[0].toLongOrNull() ?: return@mapNotNull null
            if (!nomeValido(p[1])) return@mapNotNull null
            val testo = decodifica(p[2])?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Messaggio(quando, p[1], testo)
        }.sortedBy { it.quando }.toList()
    }

    /** Il registro di quello che e' gia' partito, dal piu' recente. */
    fun leggiConsegnati(cfg: ServerConfig): String =
        "f=${fileConsegnati(cfg)}; echo '@@INIZIO'; [ -f \"\$f\" ] && tail -n 60 \"\$f\" | base64; echo '@@FINE'"

    fun parseConsegnati(raw: String): List<Consegna> {
        val dentro = Lgsm.clean(raw)
            .substringAfter("@@INIZIO", "")
            .substringBefore("@@FINE", "")
        val contenuto = decodifica(dentro.filter { !it.isWhitespace() }) ?: return emptyList()
        return contenuto.lineSequence().mapNotNull { riga ->
            val p = riga.split('\t')
            if (p.size < 4) return@mapNotNull null
            val illeggibile = p[1] == "ILLEGGIBILE"
            val chi = if (illeggibile) p[2] else p[1]
            val testo = decodifica(p[3]) ?: return@mapNotNull null
            Consegna(p[0], chi, testo, illeggibile)
        }.toList().asReversed()
    }

    private fun decodifica(b64: String): String? = runCatching {
        String(Base64.getDecoder().decode(b64.filter { !it.isWhitespace() }), Charsets.UTF_8)
    }.getOrNull()

    // ------------------------------------------------------------ scrittura

    /**
     * Mette un messaggio in coda.
     *
     * Il testo viaggia in base64: nella riga non puo' finire ne' un a capo ne'
     * un apice, e il nome e' gia' passato dal setaccio. Cosi' quello che scrive
     * l'admin resta testo e non diventa mai un comando, ne' qui ne' in console.
     */
    fun accoda(cfg: ServerConfig, m: Messaggio): String {
        require(nomeValido(m.giocatore)) { "nome di giocatore non valido" }
        require(m.testo.isNotBlank()) { "il messaggio e' vuoto" }
        val riga = m.riga()
        require(riga.none { it == '\n' || it == '\r' || it == '\'' }) { "riga malformata" }
        return PREPARA + " && " + conLucchetto(
            cfg,
            "touch \"\$f\" && chmod 600 \"\$f\" && " +
                    // Una coda che non si svuota va guardata, non allungata.
                    "n=\$(wc -l <\"\$f\") && " +
                    "{ [ \"\$n\" -lt $MAX_IN_ATTESA ] || { echo 'CASSETTA PIENA'; exit $EXIT_PIENA; }; } && " +
                    "printf '%s\\n' ${Lgsm.sq(riga)} >>\"\$f\" && echo '@@OK'"
        )
    }

    /**
     * Toglie una riga esatta, e dice quante ne ha tolte.
     *
     * Il conteggio non e' pignoleria: se nel frattempo il messaggio e' partito
     * davvero, l'app diceva comunque "Tolto" e l'admin restava convinto di
     * averlo fermato in tempo.
     */
    fun cancella(cfg: ServerConfig, m: Messaggio): String = conLucchetto(
        cfg,
        "[ -f \"\$f\" ] || { echo '@@TOLTE 0'; exit 0; }; " +
                "prima=\$(wc -l <\"\$f\"); " +
                // ENVIRON e non -v: con -v awk rileggerebbe le sequenze di escape.
                "MCM_RIGA=${Lgsm.sq(m.riga())} awk 'BEGIN{r=ENVIRON[\"MCM_RIGA\"]} \$0!=r' \"\$f\" " +
                ">\"\$f.nuova\" && mv \"\$f.nuova\" \"\$f\" && " +
                "dopo=\$(wc -l <\"\$f\"); echo \"@@TOLTE \$((prima - dopo))\""
    )

    /** Quante righe ha tolto davvero. Null se non si e' capito. */
    fun tolte(raw: String): Int? =
        Regex("@@TOLTE\\s+(\\d+)").find(Lgsm.clean(raw))?.groupValues?.get(1)?.toIntOrNull()

    /** Butta via tutta la posta in attesa. */
    fun svuota(cfg: ServerConfig): String =
        conLucchetto(cfg, "rm -f \"\$f\" && echo '@@OK'")

    fun riuscito(raw: String): Boolean = Lgsm.clean(raw).contains("@@OK")

    fun occupata(raw: String): Boolean = Lgsm.clean(raw).contains("CASSETTA OCCUPATA")

    // --------------------------------------------------------- la consegna

    /**
     * Lo script di consegna, con i buchi riempiti per questo server.
     *
     * I ritorni a capo di Windows vengono tolti qui e non altrove: basta un
     * `\r` dopo `#!/bin/sh` perche' il server risponda "interprete non trovato",
     * e un checkout con autocrlf glieli metterebbe senza dire niente.
     */
    fun script(cfg: ServerConfig): String {
        val mc = "${cfg.serverFiles.trimEnd('/')}/logs/latest.log"
        val lgsm = "${cfg.lgsmDir.trimEnd('/')}/log/console/${cfg.script}-console.log"
        return modello()
            .replace("\r", "")
            .replace("@@POSTA@@", fileCassetta(cfg))
            .replace("@@CONSEGNATI@@", fileConsegnati(cfg))
            .replace("@@SESSIONE@@", Lgsm.sq(cfg.session))
            .replace("@@LIMITE@@", LIMITE_TESTO.toString())
            .replace("@@LOG_MC@@", Lgsm.path(mc))
            .replace("@@LOG_LGSM@@", Lgsm.path(lgsm))
    }

    private fun modello(): String =
        Posta::class.java.getResourceAsStream("/posta.sh")
            ?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("posta.sh non e' nel pacchetto")

    /** La riga di cron che lancia la consegna. */
    fun comandoCron(cfg: ServerConfig): String =
        "sh ${fileScript(cfg)} >> $BASE/consegne.log 2>&1"
}
