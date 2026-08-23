package com.bellizia.mcmonitor.lgsm

import com.bellizia.mcmonitor.data.ServerConfig

/** Che fine ha fatto il server. */
enum class Stato(val frase: String) {
    IN_ESECUZIONE("Il server sta girando."),
    MAI_PARTITO("Il server non è mai partito."),
    PARTITO_E_MORTO("Era partito, e poi si è fermato da solo."),
    FERMATO("Il server è fermo, ed è stato fermato apposta."),
    NON_SO("Non riesco a capire in che stato sia.")
}

/** Quello che si riesce a dire su perché non parte. */
data class Diagnosi(
    val stato: Stato,
    /** La riga con cui LinuxGSM avvia il server. È la cosa più utile che ci sia. */
    val rigaDiAvvio: String?,
    /** Il motivo probabile, in italiano. Vuoto se non si è riconosciuto niente. */
    val motivo: String,
    /** Cosa fare, e dove. */
    val rimedio: String,
    /** Dove si va a mettere le mani: "ripristino", "parametri", "mod", "". */
    val dove: String,
    val script: String,
    val console: String,
    val gioco: String,
    val lock: String
) {
    val haUnMotivo: Boolean get() = motivo.isNotBlank()
}

/**
 * Perché il server non è partito.
 *
 * Quando un server non parte, la scheda Console non serve: legge il log del
 * gioco, che è quello dell'ultimo avvio *riuscito*, e mostrandolo senza dirlo fa
 * credere che vada tutto bene. Il motivo vero sta in due file che l'app non ha
 * mai guardato — quello che ha deciso LinuxGSM e quello che ha stampato Java
 * prima di morire.
 *
 * Le firme qui sotto servono a mettere in cima la spiegazione più probabile, non
 * a nascondere il resto: i log si vedono comunque, per intero.
 */
object Avvio {

    private val FIRME: List<Triple<Regex, String, Pair<String, String>>> = listOf(
        Triple(
            Regex("(?i)executable was not found|executable not found"),
            "Il programma che LinuxGSM deve avviare non c'è.",
            "Guarda la riga di avvio qui sopra: il file che nomina non esiste nella " +
                    "cartella del server. Succede se un mod loader è stato tolto o " +
                    "rinominato. Da Ripristino puoi rimettere la configurazione di prima." to "ripristino"
        ),
        Triple(
            Regex("(?i)Unable to access jarfile"),
            "Java non trova il file che deve eseguire.",
            "Il jar nominato nella riga di avvio non esiste. Se hai appena installato " +
                    "un mod loader, rimetti la configurazione di prima da Ripristino." to "ripristino"
        ),
        Triple(
            Regex("(?i)UnrecognizedOptionException|Failed to start the minecraft server"),
            "La riga di avvio contiene qualcosa che il server non capisce.",
            "Quasi sempre è un secondo \"-jar\" o delle opzioni di memoria finite dopo " +
                    "il nome del jar. Guarda la riga qui sopra: deve avere un solo -jar. " +
                    "Da Ripristino rimetti la configurazione di prima." to "ripristino"
        ),
        Triple(
            Regex("(?i)UnsupportedClassVersionError|class file version"),
            "La versione di Java sul computer è troppo vecchia per questo Minecraft.",
            "Dalla 1.20.5 in poi serve Java 21. Va aggiornato sul computer del server, " +
                    "e non si può fare da qui." to ""
        ),
        Triple(
            Regex("(?i)OutOfMemoryError|Could not reserve enough space|java.lang.OutOfMemory"),
            "Il server ha finito la memoria.",
            "Alza javaram dalle impostazioni tecniche — con i mod 1024 MB quasi mai " +
                    "bastano — ma non oltre quello che il computer ha davvero." to "parametri"
        ),
        Triple(
            Regex("(?i)Mod resolution failed|requires fabric-api|Incompatible mods|Missing dependenc"),
            "Un mod non va d'accordo con gli altri, o gliene manca uno.",
            "Il log qui sotto dice quale. Dalla scheda Mod puoi toglierlo o installare " +
                    "quello che manca." to "mod"
        ),
        Triple(
            Regex("(?i)You need to agree to the EULA|eula=false"),
            "Le condizioni d'uso di Minecraft non sono state accettate.",
            "Vanno accettate scrivendo eula=true nel file eula.txt dentro la cartella " +
                    "del server." to ""
        ),
        Triple(
            Regex("(?i)Address already in use|FAILED TO BIND TO PORT"),
            "La porta è già occupata da un altro server.",
            "Due server sulla stessa porta non possono stare accesi insieme: cambia la " +
                    "porta a uno dei due dalle impostazioni." to "impostazioni"
        ),
        Triple(
            Regex("(?i)COPIA DI SICUREZZA NON RIUSCITA"),
            "Una modifica è stata fermata prima di toccare il file.",
            "L'app non è riuscita a fare la copia di sicurezza e si è fermata: il file " +
                    "non è stato modificato." to ""
        )
    )

    /**
     * Un solo giro sul server prende tutto quello che serve. Ogni pezzo è tagliato:
     * su una connessione mobile un log intero sono megabyte inutili.
     */
    fun raccogli(cfg: ServerConfig): String {
        val dir = Lgsm.path(cfg.lgsmDir.trimEnd('/'))
        val script = cfg.script
        val gioco = Lgsm.path("${cfg.serverFiles.trimEnd('/')}/logs/latest.log")
        return """
            d=$dir
            s=${Lgsm.sq(script)}
            echo '### lock'
            ls -1 "${'$'}d"/lgsm/lock/ 2>/dev/null || echo '(nessun lock)'
            echo '### script'
            tail -n 60 "${'$'}d"/log/script/"${'$'}s"-script.log 2>/dev/null || echo '(nessun log dello script)'
            echo '### console'
            tail -n 80 "${'$'}d"/log/console/"${'$'}s"-console.log 2>/dev/null || echo '(nessun log di console)'
            echo '### gioco'
            tail -n 60 $gioco 2>/dev/null || echo '(nessun log di gioco)'
            echo '### avvio'
            cd "${'$'}d" 2>/dev/null && ./"${'$'}s" details 2>&1 | grep -iA4 'command-line' || echo '(non riesco a leggere la riga di avvio)'
            echo '### fine'
        """.trimIndent()
    }

    fun leggi(raw: String): Diagnosi {
        val text = Lgsm.clean(raw)
        fun sezione(nome: String): String =
            text.substringAfter("### $nome", "").substringBefore("###", "").trim()

        val lock = sezione("lock")
        val script = sezione("script")
        val console = sezione("console")
        val gioco = sezione("gioco")
        val avvio = sezione("avvio")

        val stato = when {
            lock.contains("-started.lock") -> Stato.IN_ESECUZIONE
            lock.contains("-stopping.lock") -> Stato.FERMATO
            // Il lock del monitoraggio resta quando il server è caduto da solo:
            // se lo si ferma apposta, LinuxGSM lo toglie.
            lock.contains("-monitoring.lock") -> Stato.PARTITO_E_MORTO
            lock.contains("nessun lock") || lock.isBlank() -> Stato.MAI_PARTITO
            else -> Stato.NON_SO
        }

        // Si cerca prima dove il motivo è più probabile: quello che ha detto Java,
        // poi quello che ha deciso LinuxGSM, poi il gioco.
        val dove = listOf(console, script, gioco).joinToString("\n")
        val firma = FIRME.firstOrNull { it.first.containsMatchIn(dove) }

        return Diagnosi(
            stato = stato,
            rigaDiAvvio = Mods.parseLaunchLine(avvio),
            motivo = firma?.second.orEmpty(),
            rimedio = firma?.third?.first.orEmpty(),
            dove = firma?.third?.second.orEmpty(),
            script = script,
            console = console,
            gioco = gioco,
            lock = lock
        )
    }

    /** Il quadro completo da incollare a chi può aiutare. */
    fun perCopiare(d: Diagnosi): String = buildString {
        append(d.stato.frase).append('\n')
        d.rigaDiAvvio?.let { append("\nRiga di avvio:\n").append(it).append('\n') }
        if (d.haUnMotivo) append("\nMotivo probabile: ").append(d.motivo).append('\n')
        append("\n--- lock ---\n").append(d.lock)
        append("\n\n--- log dello script ---\n").append(d.script)
        append("\n\n--- log di console ---\n").append(d.console)
        append("\n\n--- log di gioco ---\n").append(d.gioco)
    }
}
