package com.bellizia.mcmonitor.lgsm

import com.bellizia.mcmonitor.data.ServerConfig

/** Un file di mod presente sul server. */
data class InstalledMod(
    val fileName: String,
    val sizeBytes: Long,
    val modifiedEpoch: Long
) {
    /** I mod disattivati restano sul disco con il suffisso .disabled. */
    val enabled: Boolean get() = !fileName.endsWith(".disabled")

    val jarName: String get() = fileName.removeSuffix(".disabled")

    private val stem: String get() = jarName.removeSuffix(".jar")

    /** "fabric-api-0.102.0+1.21.jar" -> nome "fabric-api", versione "0.102.0+1.21". */
    val name: String
        get() = SPLIT.find(stem)?.groupValues?.get(1)?.replace('_', ' ') ?: stem

    val version: String?
        get() = SPLIT.find(stem)?.groupValues?.get(2)

    val sizeLabel: String
        get() = if (sizeBytes >= 1024 * 1024) "%.1f MB".format(sizeBytes / 1048576.0)
        else "${sizeBytes / 1024} KB"

    private companion object {
        val SPLIT = Regex("^(.*?)[-_]v?([0-9][A-Za-z0-9.+_-]*)$")
    }
}

/** Cosa gira sul server: da qui dipendono i mod compatibili. */
data class ServerEnvironment(
    /** Quella scritta nel log dal server stesso: cosa sta girando davvero. */
    val runningVersion: String?,
    /** Quella chiesta a LinuxGSM in `mcversion`: può anche essere "latest". */
    val configuredVersion: String?,
    val loader: String,
    val modsDir: String,
    val hasModsDir: Boolean
) {
    /**
     * La versione da usare per cercare i mod e per scegliere il loader.
     *
     * Prima quella che gira, poi quella configurata — ma solo se è un numero:
     * `mcversion="latest"` dice a LinuxGSM di prendere l'ultima, e passarla a
     * Modrinth o a Fabric non avrebbe senso.
     */
    val minecraftVersion: String?
        get() = runningVersion ?: configuredVersion?.takeIf { isRealVersion(it) }

    /** Come lo si è saputo, per dirlo a chi legge invece di far finta di niente. */
    val versionSource: String?
        get() = when {
            runningVersion != null -> "in esecuzione"
            minecraftVersion != null -> "da configurazione"
            else -> null
        }

    val isVanilla: Boolean get() = loader == "vanilla"


    val loaderLabel: String
        get() = when (loader) {
            "fabric" -> "Fabric"
            "forge" -> "Forge"
            "neoforge" -> "NeoForge"
            "quilt" -> "Quilt"
            "paper" -> "Paper/Spigot"
            "vanilla" -> "vanilla (nessun mod loader)"
            else -> "loader non riconosciuto"
        }

    private companion object {
        /** "1.21.10" sì, "latest" e "snapshot" no. */
        val NUMERO = Regex("""^[0-9]+(\.[0-9]+)+$""")

        fun isRealVersion(value: String) = NUMERO.matches(value.trim())
    }
}

/**
 * Comandi per gestire i file dei mod sul server. I nomi file e gli URL passano
 * sempre da [safeFileName] e [isTrustedUrl]: sono gli unici valori che arrivano
 * da una fonte esterna e finiscono in una riga di shell.
 */
object Mods {

    const val EXIT_DOWNLOAD_FAILED = 92
    const val EXIT_HASH_MISMATCH = 93
    const val EXIT_NO_TOOL = 94

    /** Modrinth serve i file solo da questo host: tutto il resto viene rifiutato. */
    private const val CDN_HOST = "cdn.modrinth.com"

    private val SAFE_NAME = Regex("^[A-Za-z0-9._+()\\[\\]-]{1,120}\\.jar$")

    fun safeFileName(name: String): Boolean = SAFE_NAME.matches(name)

    fun isTrustedUrl(url: String): Boolean =
        runCatching { java.net.URI(url) }.getOrNull()?.let {
            it.scheme == "https" && it.host == CDN_HOST
        } == true

    private fun modsDir(cfg: ServerConfig) = "${cfg.serverFiles.trimEnd('/')}/mods"

    /**
     * Versione di gioco e mod loader, dedotti dal log di avvio, dai file presenti
     * e dalla configurazione di LinuxGSM.
     *
     * Il log è la fonte più attendibile — la riga la scrive il server stesso — ma
     * su un server mai avviato non c'è, e prima si finiva per chiedere la versione
     * a chi sta usando l'app. La versione, invece, e scritta in `mcversion` nel
     * file di configurazione: da lì si legge senza chiedere niente a nessuno.
     */
    fun detectEnvironment(cfg: ServerConfig): String {
        val sf = Lgsm.path(cfg.serverFiles.trimEnd('/'))
        val conf = Lgsm.path(GameVersion.configPath(cfg))
        return """
            sf=$sf
            conf=$conf
            ver=${'$'}(grep -aho 'server version [0-9][0-9.]*' "${'$'}sf/logs/latest.log" 2>/dev/null | tail -1 | awk '{print ${'$'}NF}')
            if [ -z "${'$'}ver" ] && command -v zgrep >/dev/null 2>&1; then
              ver=${'$'}(zgrep -aho 'server version [0-9][0-9.]*' "${'$'}sf"/logs/*.log.gz 2>/dev/null | tail -1 | awk '{print ${'$'}NF}')
            fi
            loader=vanilla
            if [ -d "${'$'}sf/libraries/net/fabricmc" ] || ls "${'$'}sf" 2>/dev/null | grep -qi '^fabric'; then loader=fabric; fi
            if [ -d "${'$'}sf/libraries/net/neoforged" ] || ls "${'$'}sf" 2>/dev/null | grep -qi 'neoforge'; then loader=neoforge; fi
            if [ -d "${'$'}sf/libraries/net/minecraftforge" ] || ls "${'$'}sf" 2>/dev/null | grep -qi '^forge'; then loader=forge; fi
            if [ -d "${'$'}sf/libraries/org/quiltmc" ]; then loader=quilt; fi
            if [ -d "${'$'}sf/plugins" ] && [ ! -d "${'$'}sf/mods" ]; then loader=paper; fi
            echo "version=${'$'}ver"
            echo "cfgversion=${'$'}(grep -hoE '^[[:space:]]*mcversion="?[^"#[:space:]]+' "${'$'}conf" 2>/dev/null | head -1 | sed 's/.*=//; s/"//g')"
            echo "loader=${'$'}loader"
            if [ -d "${'$'}sf/mods" ]; then echo "modsdir=yes"; else echo "modsdir=no"; fi
        """.trimIndent()
    }

    /** Elenco dei file di mod: nome, dimensione ed epoca di modifica separati da tab. */
    fun listMods(cfg: ServerConfig): String {
        val d = Lgsm.path(modsDir(cfg))
        return "d=$d; [ -d \"\$d\" ] || { echo 'NO_MODS_DIR'; exit 0; }; " +
                "cd \"\$d\" && for f in *.jar *.jar.disabled; do [ -e \"\$f\" ] || continue; " +
                "printf '%s\\t%s\\t%s\\n' \"\$f\" \"\$(stat -c %s \"\$f\" 2>/dev/null || echo 0)\" " +
                "\"\$(stat -c %Y \"\$f\" 2>/dev/null || echo 0)\"; done"
    }

    /**
     * Scarica il mod direttamente dal server: la banda è la sua, non quella del
     * telefono. Lo sha1 dichiarato da Modrinth viene verificato prima di
     * mettere il file al suo posto.
     */
    fun install(cfg: ServerConfig, url: String, fileName: String, sha1: String): String {
        require(isTrustedUrl(url)) { "URL non attendibile: $url" }
        require(safeFileName(fileName)) { "nome file non valido: $fileName" }
        require(sha1.matches(Regex("^[a-fA-F0-9]{40}$"))) { "hash sha1 non valido" }

        val d = Lgsm.path(modsDir(cfg))
        val u = Lgsm.sq(url)
        val f = Lgsm.sq(fileName)
        return "d=$d; mkdir -p \"\$d\" || exit 1; tmp=\"\$d/.mcmonitor.part\"; " +
                "if command -v curl >/dev/null 2>&1; then curl -fsSL --max-time 600 -o \"\$tmp\" $u; " +
                "elif command -v wget >/dev/null 2>&1; then wget -q --timeout=600 -O \"\$tmp\" $u; " +
                "else echo 'NESSUNO STRUMENTO DI DOWNLOAD (curl o wget)'; exit $EXIT_NO_TOOL; fi || " +
                "{ rm -f \"\$tmp\"; echo 'DOWNLOAD FALLITO'; exit $EXIT_DOWNLOAD_FAILED; }; " +
                "got=\$(sha1sum \"\$tmp\" 2>/dev/null | cut -d' ' -f1); " +
                "if [ -n \"\$got\" ] && [ \"\$got\" != '${sha1.lowercase()}' ]; then rm -f \"\$tmp\"; " +
                "echo \"HASH DIVERSO: atteso ${sha1.lowercase()}, ottenuto \$got\"; exit $EXIT_HASH_MISMATCH; fi; " +
                "mv \"\$tmp\" \"\$d/$fileName\" && echo \"installato $fileName (\$(stat -c %s \"\$d/$fileName\") byte, sha1 \$got)\""
    }

    // ------------------------------------------------------- mod loader

    private const val FABRIC_HOST = "meta.fabricmc.net"
    const val LAUNCH_JAR = "fabric-server-launch.jar"

    fun isFabricUrl(url: String): Boolean =
        runCatching { java.net.URI(url) }.getOrNull()?.let {
            it.scheme == "https" && it.host == FABRIC_HOST
        } == true

    /**
     * Scarica il jar di avvio di Fabric dentro serverfiles. `-L` segue il redirect
     * verso il maven di Fabric; il risultato deve essere uno zip (i jar lo sono),
     * altrimenti si è scaricata una pagina di errore.
     */
    fun installLoader(cfg: ServerConfig, url: String): String {
        require(isFabricUrl(url)) { "URL non attendibile: $url" }
        val sf = Lgsm.path(cfg.serverFiles.trimEnd('/'))
        val u = Lgsm.sq(url)
        return "cd $sf || exit 1; tmp='.$LAUNCH_JAR.part'; " +
                "if command -v curl >/dev/null 2>&1; then curl -fsSL --max-time 600 -o \"\$tmp\" $u; " +
                "elif command -v wget >/dev/null 2>&1; then wget -q --timeout=600 -O \"\$tmp\" $u; " +
                "else echo 'NESSUNO STRUMENTO DI DOWNLOAD'; exit $EXIT_NO_TOOL; fi || " +
                "{ rm -f \"\$tmp\"; echo 'DOWNLOAD FALLITO'; exit $EXIT_DOWNLOAD_FAILED; }; " +
                "if ! head -c2 \"\$tmp\" | grep -q 'PK'; then rm -f \"\$tmp\"; " +
                "echo 'IL FILE SCARICATO NON E UN JAR'; exit $EXIT_DOWNLOAD_FAILED; fi; " +
                "mv \"\$tmp\" $LAUNCH_JAR && mkdir -p mods && " +
                "echo \"scaricato $LAUNCH_JAR (\$(stat -c %s $LAUNCH_JAR) byte), cartella mods pronta\""
    }

    const val EXIT_NO_LOADER = 98

    /**
     * Fa avviare a LinuxGSM il jar di Fabric invece di quello vanilla.
     *
     * LinuxGSM compone la riga di avvio così: `preexecutable executable
     * startparameters`, cioè di fabbrica `java -Xmx1024M -jar
     * ./minecraft_server.jar nogui`. La variabile che decide QUALE jar viene
     * eseguito è quindi `executable`, e solo quella.
     *
     * Fino alla 1.25 l'app scriveva invece dentro `startparameters`, e su un file
     * di istanza vuoto — che è il caso normale, quel file nasce vuoto — finiva per
     * aggiungere un secondo `-jar` DOPO il primo:
     *
     *     java -Xmx1024M -jar ./minecraft_server.jar -Xmx1024M -jar fabric... nogui
     *
     * Java smette di leggere opzioni appena incontra `-jar <file>`: partiva il
     * server vanilla e tutto il resto gli arrivava come argomenti, che il suo
     * lettore di argomenti non riconosce. Il server moriva subito, e Fabric non
     * veniva caricato mai.
     */
    fun useLoaderInConfig(cfg: ServerConfig, jarName: String = LAUNCH_JAR): String {
        require(safeFileName(jarName)) { "nome file non valido: $jarName" }
        val f = Lgsm.path(GameVersion.configPath(cfg))
        val jar = Lgsm.path("${cfg.serverFiles.trimEnd('/')}/$jarName")

        // Se il jar non c'è, scrivere executable lascerebbe il server incapace di
        // partire: LinuxGSM si ferma con "executable was not found".
        val controllo = "[ -f $f ] || { echo 'CONFIG NON TROVATA'; exit ${GameVersion.EXIT_NO_CONFIG}; }; " +
                "[ -f $jar ] || { echo 'IL JAR DI FABRIC NON CE'; exit $EXIT_NO_LOADER; }; "

        // Una riga startparameters con dentro un -jar è sbagliata comunque: gli
        // argomenti dopo il nome del jar non sono opzioni della macchina Java, e
        // se è quella scritta dalla versione rotta dell'app è la causa del guasto.
        // Si commenta, non si cancella: resta lì da leggere.
        val togliVecchia = "awk '/^[[:space:]]*startparameters=.*-jar/{ print \"# tolta da MC Monitor: \" \$0; next } " +
                "{print}' $f > \"\$mcm_f.mcmonitor.tmp\" && mv \"\$mcm_f.mcmonitor.tmp\" $f; "

        val riga = Lgsm.sq("executable=\"./$jarName\"")
        val scrivi = "MCM_RIGA=$riga awk 'BEGIN{fatto=0; riga=ENVIRON[\"MCM_RIGA\"]} " +
                "/^[[:space:]]*executable=/{ if(!fatto){print riga; fatto=1} next } " +
                "{print} END{ if(!fatto) print riga }' $f > \"\$mcm_f.mcmonitor.tmp\" && " +
                "mv \"\$mcm_f.mcmonitor.tmp\" $f; "

        return controllo + Lgsm.backupFirst(f) + togliVecchia + scrivi +
                "echo 'avvio configurato:'; " +
                "grep -E '^[[:space:]]*(executable|startparameters|javaram)=' $f"
    }

    /**
     * Rimette il server sul jar vanilla, per tornare indietro senza aprire un
     * terminale. Il mondo non si tocca: cambia solo quale programma lo apre.
     */
    fun useVanillaInConfig(cfg: ServerConfig): String {
        val f = Lgsm.path(GameVersion.configPath(cfg))
        return "[ -f $f ] || { echo 'CONFIG NON TROVATA'; exit ${GameVersion.EXIT_NO_CONFIG}; }; " +
                Lgsm.backupFirst(f) +
                "awk '/^[[:space:]]*executable=/{ print \"# tolta da MC Monitor: \" \$0; next } " +
                "{print}' $f > \"\$mcm_f.mcmonitor.tmp\" && mv \"\$mcm_f.mcmonitor.tmp\" $f; " +
                "echo 'tornato al programma di fabbrica:'; " +
                "grep -E '^[[:space:]]*(executable|startparameters)=' $f || echo '(nessuna riga: vale il valore di fabbrica)'"
    }

    /**
     * La riga con cui LinuxGSM avvierà davvero il server, presa da `details`.
     *
     * È la prova del nove, e non costa niente: `details` legge la configurazione
     * e non avvia niente. Prima l'app dichiarava l'installazione riuscita sul solo
     * codice di uscita, che qui non dice assolutamente niente.
     */
    fun parseLaunchLine(details: String): String? {
        val text = Lgsm.clean(details)
        val righe = text.lines().map { it.trim() }
        val i = righe.indexOfFirst { it.contains("Command-line Parameters", ignoreCase = true) }
        val candidate = if (i >= 0) righe.drop(i + 1) else righe
        return candidate.firstOrNull { it.contains("java") && it.contains("-jar") }
    }

    /** Se la riga di avvio è sana: un solo `-jar`, e punta al jar che ci aspettiamo. */
    fun launchLineOk(riga: String?, jarName: String = LAUNCH_JAR): Boolean {
        if (riga == null) return false
        val quanti = Regex("""(^|\s)-jar(\s|$)""").findAll(riga).count()
        return quanti == 1 && riga.contains(jarName)
    }

    // ----------------------------------------------------------- modpack

    private val SAFE_PATH = Regex("^[A-Za-z0-9._+()\\[\\] -]+(/[A-Za-z0-9._+()\\[\\] -]+)*$")

    /** Percorso relativo dentro serverfiles: niente risalite, niente percorsi assoluti. */
    fun safeRelativePath(path: String): Boolean =
        path.isNotBlank() && !path.startsWith("/") && !path.contains("..") &&
                !path.contains("//") && SAFE_PATH.matches(path)

    fun packDir(cfg: ServerConfig) = "${cfg.lgsmDir.trimEnd('/')}/tmp-mcmonitor"

    fun preparePackDir(cfg: ServerConfig): String {
        val d = Lgsm.path(packDir(cfg))
        return "command -v unzip >/dev/null 2>&1 || " +
                "{ echo 'SUL SERVER MANCA unzip: installalo per gestire i modpack'; exit $EXIT_NO_TOOL; }; " +
                "mkdir -p $d && echo pronto"
    }

    /** L'indice del pacchetto si legge senza estrarre nulla. */
    fun readPackIndex(cfg: ServerConfig, packFileName: String): String {
        val pack = Lgsm.path("${packDir(cfg)}/$packFileName")
        return "unzip -p $pack modrinth.index.json 2>/dev/null || " +
                "{ echo 'INDICE NON TROVATO: il file non sembra un .mrpack'; exit 95; }"
    }

    /** Scarica un file del pacchetto nella sua posizione dentro serverfiles. */
    fun installPackFile(cfg: ServerConfig, url: String, relativePath: String, sha1: String): String {
        require(isTrustedUrl(url)) { "URL non attendibile: $url" }
        require(safeRelativePath(relativePath)) { "percorso non valido: $relativePath" }

        val target = "${cfg.serverFiles.trimEnd('/')}/$relativePath"
        val t = Lgsm.path(target)
        val u = Lgsm.sq(url)
        val hashCheck = if (sha1.matches(Regex("^[a-fA-F0-9]{40}$"))) {
            "got=\$(sha1sum \"\$tmp\" 2>/dev/null | cut -d' ' -f1); " +
                    "if [ -n \"\$got\" ] && [ \"\$got\" != '${sha1.lowercase()}' ]; then rm -f \"\$tmp\"; " +
                    "echo \"HASH DIVERSO su $relativePath\"; exit $EXIT_HASH_MISMATCH; fi; "
        } else ""

        return "mkdir -p \"\$(dirname $t)\" || exit 1; tmp=\"$t.part\"; " +
                "if command -v curl >/dev/null 2>&1; then curl -fsSL --max-time 600 -o \"\$tmp\" $u; " +
                "elif command -v wget >/dev/null 2>&1; then wget -q --timeout=600 -O \"\$tmp\" $u; " +
                "else echo 'NESSUNO STRUMENTO DI DOWNLOAD'; exit $EXIT_NO_TOOL; fi || " +
                "{ rm -f \"\$tmp\"; echo 'DOWNLOAD FALLITO: $relativePath'; exit $EXIT_DOWNLOAD_FAILED; }; " +
                hashCheck +
                "mv \"\$tmp\" $t && echo 'ok $relativePath'"
    }

    /**
     * Copia il contenuto di overrides/ dentro serverfiles: sono i mod non presenti
     * su Modrinth e le configurazioni preparate da chi ha creato il pacchetto.
     */
    fun extractOverrides(cfg: ServerConfig, packFileName: String): String {
        val dir = packDir(cfg)
        val pack = Lgsm.path("$dir/$packFileName")
        val work = Lgsm.path("$dir/estratto")
        val sf = Lgsm.path(cfg.serverFiles.trimEnd('/'))
        return "rm -rf $work && mkdir -p $work && " +
                "unzip -o -q $pack 'overrides/*' -d $work 2>/dev/null; " +
                "if [ -d $work/overrides ]; then " +
                "n=\$(find $work/overrides -type f | wc -l); " +
                "cp -r $work/overrides/. $sf/ && echo \"copiati \$n file da overrides\"; " +
                "else echo 'nessun overrides nel pacchetto'; fi; rm -rf $work"
    }

    fun cleanupPack(cfg: ServerConfig): String =
        "rm -rf ${Lgsm.path(packDir(cfg))} && echo 'file temporanei rimossi'"

    fun remove(cfg: ServerConfig, fileName: String): String {
        require(safeFileName(fileName.removeSuffix(".disabled"))) { "nome file non valido" }
        val target = Lgsm.path("${modsDir(cfg)}/$fileName")
        return "rm -f -- $target && echo 'rimosso $fileName'"
    }

    /** Attiva o disattiva un mod rinominandolo: il file resta, si recupera in un tocco. */
    fun toggle(cfg: ServerConfig, fileName: String): String {
        require(safeFileName(fileName.removeSuffix(".disabled"))) { "nome file non valido" }
        val dir = modsDir(cfg)
        return if (fileName.endsWith(".disabled")) {
            val to = fileName.removeSuffix(".disabled")
            "mv -- ${Lgsm.path("$dir/$fileName")} ${Lgsm.path("$dir/$to")} && echo 'attivato $to'"
        } else {
            "mv -- ${Lgsm.path("$dir/$fileName")} ${Lgsm.path("$dir/$fileName.disabled")} && echo 'disattivato $fileName'"
        }
    }

    // --------------------------------------------------------------- parsing

    fun parseEnvironment(raw: String, cfg: ServerConfig): ServerEnvironment {
        val text = Lgsm.clean(raw)
        fun value(key: String) = Regex("(?m)^$key=(.*)$").find(text)?.groupValues?.get(1)?.trim()
        return ServerEnvironment(
            runningVersion = value("version")?.takeIf { it.isNotBlank() },
            configuredVersion = value("cfgversion")?.takeIf { it.isNotBlank() },
            loader = value("loader")?.takeIf { it.isNotBlank() } ?: "vanilla",
            modsDir = "${cfg.serverFiles.trimEnd('/')}/mods",
            hasModsDir = value("modsdir") == "yes"
        )
    }

    fun parseMods(raw: String): List<InstalledMod> {
        val text = Lgsm.clean(raw)
        if (text.contains("NO_MODS_DIR")) return emptyList()
        return text.lineSequence().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 3) return@mapNotNull null
            val name = parts[0].trim()
            if (!name.endsWith(".jar") && !name.endsWith(".jar.disabled")) return@mapNotNull null
            InstalledMod(
                fileName = name,
                sizeBytes = parts[1].trim().toLongOrNull() ?: 0L,
                modifiedEpoch = parts[2].trim().toLongOrNull() ?: 0L
            )
        }.sortedBy { it.name.lowercase() }.toList()
    }
}
