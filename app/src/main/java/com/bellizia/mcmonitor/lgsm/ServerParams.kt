package com.bellizia.mcmonitor.lgsm

import com.bellizia.mcmonitor.data.ServerConfig

/** Una riga di configurazione di LinuxGSM: chiave, valore e se è commentata. */
data class ServerParam(
    val key: String,
    val value: String,
    val commented: Boolean
) {
    val description: String get() = ServerParams.describe(key)
}

/**
 * I parametri del server LinuxGSM, tutti modificabili.
 *
 * Il file `lgsm/config-lgsm/<script>/<script>.cfg` è un elenco di assegnazioni
 * che sovrascrivono i valori di fabbrica: quanto RAM dare a Java, quale versione
 * scaricare, quanti backup tenere. Finora l'app ne toccava una sola,
 * `mcversion`, e per il resto bisognava collegarsi a mano.
 *
 * Le modifiche passano da awk e non da sed: il valore può contenere barre,
 * e-commerciali e virgolette, che in una sostituzione sed avrebbero significati
 * propri e rovinerebbero il file.
 */
object ServerParams {

    const val EXIT_NO_CONFIG = 96

    private val KEY = Regex("^[A-Za-z_][A-Za-z0-9_]{0,40}$")

    fun isValidKey(key: String) = KEY.matches(key)

    /**
     * Il valore finisce fra virgolette doppie in un file che LinuxGSM legge con
     * `source`, quindi passa dalle mani della shell.
     *
     * Vanno protetti i caratteri che spezzerebbero il file: una virgoletta chiude
     * l'assegnazione in anticipo e quel che resta del valore diventa una riga di
     * comando, una barra si mangia il carattere dopo, un apice inverso spaiato
     * lascia il file troncato a metà e `source` fallisce.
     *
     * Il dollaro invece si lascia stare: le configurazioni di LinuxGSM sono piene
     * di ${'$'}{serverfiles} e ${'$'}HOME, e proteggerlo li farebbe arrivare al
     * server scritti alla lettera. Non è una porta che si apre: chi arriva a
     * questa schermata ha già le chiavi SSH di quel computer.
     */
    fun quoteValue(value: String): String = value.trim()
        .replace(Regex("""[\r\n]+"""), " ")
        .take(300)
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("`", "\\`")

    fun readAll(cfg: ServerConfig): String {
        val f = Lgsm.path(GameVersion.configPath(cfg))
        return "[ -f $f ] || { echo 'CONFIG NON TROVATA'; exit $EXIT_NO_CONFIG; }; cat $f"
    }

    fun parse(raw: String): List<ServerParam> {
        val riga = Regex("""^(#?)\s*([A-Za-z_][A-Za-z0-9_]{0,40})=(.*)$""")
        return Lgsm.clean(raw).lines().mapNotNull { linea ->
            val m = riga.find(linea.trim()) ?: return@mapNotNull null
            val valore = m.groupValues[3].trim().removeSurrounding("\"").removeSurrounding("'")
            ServerParam(
                key = m.groupValues[2],
                value = valore,
                commented = m.groupValues[1] == "#"
            )
        }.distinctBy { it.key to it.commented }
    }

    /**
     * Scrive un parametro: se c'è lo sostituisce dov'è, se manca lo aggiunge in
     * fondo, e in ogni caso tiene una copia datata del file. La riga commentata
     * con la stessa chiave viene lasciata dov'è: è la documentazione di LinuxGSM
     * e toglierla renderebbe il file più povero.
     */
    fun set(cfg: ServerConfig, key: String, value: String): String {
        require(isValidKey(key)) { "chiave non valida: $key" }
        val f = Lgsm.path(GameVersion.configPath(cfg))
        val riga = Lgsm.sq("$key=\"${quoteValue(value)}\"")
        // La riga arriva ad awk dall'ambiente e non da `-v`: awk sulle assegnazioni
        // da riga di comando interpreta a sua volta le sequenze di escape, e
        // disferebbe la protezione appena messa da quoteValue.
        return "f=$f; [ -f \"\$f\" ] || { echo 'CONFIG NON TROVATA'; exit $EXIT_NO_CONFIG; }; " +
                "cp \"\$f\" \"\$f.mcmonitor.bak.\$(date +%Y%m%d%H%M%S)\"; " +
                "MCM_RIGA=$riga awk 'BEGIN{fatto=0; riga=ENVIRON[\"MCM_RIGA\"]} " +
                "/^[[:space:]]*$key=/{ if(!fatto){print riga; fatto=1} next } " +
                "{print} END{ if(!fatto) print riga }' \"\$f\" > \"\$f.mcmonitor.tmp\" && " +
                "mv \"\$f.mcmonitor.tmp\" \"\$f\" && " +
                "echo 'SCRITTO'; grep -n \"^[[:space:]]*$key=\" \"\$f\""
    }

    /**
     * Togliere un parametro significa commentarlo: LinuxGSM torna al suo valore
     * di fabbrica e la riga resta lì a ricordare cosa c'era.
     */
    fun disable(cfg: ServerConfig, key: String): String {
        require(isValidKey(key)) { "chiave non valida: $key" }
        val f = Lgsm.path(GameVersion.configPath(cfg))
        return "f=$f; [ -f \"\$f\" ] || { echo 'CONFIG NON TROVATA'; exit $EXIT_NO_CONFIG; }; " +
                "cp \"\$f\" \"\$f.mcmonitor.bak.\$(date +%Y%m%d%H%M%S)\"; " +
                "awk '/^[[:space:]]*$key=/{ print \"#\" \$0; next } {print}' \"\$f\" > \"\$f.mcmonitor.tmp\" && " +
                "mv \"\$f.mcmonitor.tmp\" \"\$f\" && echo 'DISATTIVATO'"
    }

    fun written(output: String) = Lgsm.clean(output).contains("SCRITTO") ||
            Lgsm.clean(output).contains("DISATTIVATO")

    // ------------------------------------------------------------- catalogo

    /**
     * I parametri che si toccano davvero, con quello che fanno detto in italiano.
     * L'elenco completo sta nella documentazione di LinuxGSM, richiamata
     * dall'aiuto della pagina: qui ci sono quelli utili su un server Minecraft.
     */
    val catalogue: List<Pair<String, String>> = listOf(
        "mcversion" to "Versione di Minecraft da scaricare, per esempio 1.20.4. Con \"latest\" prende l'ultima.",
        "mcbranch" to "Ramo delle versioni: release oppure snapshot (le versioni di prova).",
        "javaram" to "Memoria data a Java, per esempio 2G o 4096M. Troppa quanto troppo poca fanno danni.",
        "javaparms" to "Opzioni della macchina Java, per chi sa cosa sono (per esempio -Xms1G -Xmx4G).",
        "startparameters" to "Riga con cui il server viene avviato: la tocca l'app quando installa Fabric.",
        "port" to "Porta di gioco, di norma 25565. Cambiala se su questo computer ci sono più server.",
        "queryport" to "Porta di interrogazione, usata dai siti che mostrano se il server è acceso.",
        "servername" to "Nome del server nei messaggi di LinuxGSM (non è il MOTD che vedono i giocatori).",
        "ip" to "Indirizzo su cui il server si mette in ascolto: 0.0.0.0 significa tutti.",
        "updateonstart" to "Con \"on\" cerca aggiornamenti a ogni avvio. Comodo, ma allunga l'accensione.",
        "maxbackups" to "Quante copie di backup tenere prima di cancellare le più vecchie.",
        "maxbackupdays" to "Da quanti giorni in là i backup vengono buttati.",
        "stoponbackup" to "Con \"on\" ferma il server durante il backup: copia più sicura, giocatori fuori.",
        "logdays" to "Per quanti giorni tenere i log di LinuxGSM prima di cancellarli.",
        "postalert" to "Con \"on\" manda un avviso quando il server cade o riparte.",
        "discordalert" to "Avvisi su Discord: serve anche discordwebhook.",
        "discordwebhook" to "Indirizzo del webhook di Discord dove mandare gli avvisi.",
        "telegramalert" to "Avvisi su Telegram: servono anche telegramtoken e telegramchatid.",
        "telegramtoken" to "Token del bot Telegram che manda gli avvisi.",
        "telegramchatid" to "Identificativo della chat Telegram a cui mandare gli avvisi.",
        "emailalert" to "Avvisi per posta: serve anche email.",
        "email" to "Indirizzo a cui mandare gli avvisi."
    )

    private val descriptions = catalogue.toMap()

    fun describe(key: String): String = descriptions[key] ?: ""

    /** Quelli del catalogo che nel file non ci sono ancora, pronti da aggiungere. */
    fun addable(existing: List<ServerParam>): List<Pair<String, String>> {
        val presenti = existing.filter { !it.commented }.map { it.key }.toSet()
        return catalogue.filterNot { it.first in presenti }
    }
}
