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
    val minecraftVersion: String?,
    val loader: String,
    val modsDir: String,
    val hasModsDir: Boolean
) {
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
     * Versione di gioco e mod loader, dedotti dal log di avvio e dai file presenti.
     * Il log è la fonte più attendibile: la riga la scrive il server stesso.
     */
    fun detectEnvironment(cfg: ServerConfig): String {
        val sf = Lgsm.path(cfg.serverFiles.trimEnd('/'))
        return """
            sf=$sf
            ver=${'$'}(grep -aho 'server version [0-9][0-9.]*' "${'$'}sf/logs/latest.log" 2>/dev/null | tail -1 | awk '{print ${'$'}NF}')
            loader=vanilla
            if [ -d "${'$'}sf/libraries/net/fabricmc" ] || ls "${'$'}sf" 2>/dev/null | grep -qi '^fabric'; then loader=fabric; fi
            if [ -d "${'$'}sf/libraries/net/neoforged" ] || ls "${'$'}sf" 2>/dev/null | grep -qi 'neoforge'; then loader=neoforge; fi
            if [ -d "${'$'}sf/libraries/net/minecraftforge" ] || ls "${'$'}sf" 2>/dev/null | grep -qi '^forge'; then loader=forge; fi
            if [ -d "${'$'}sf/libraries/org/quiltmc" ]; then loader=quilt; fi
            if [ -d "${'$'}sf/plugins" ] && [ ! -d "${'$'}sf/mods" ]; then loader=paper; fi
            echo "version=${'$'}ver"
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

    /**
     * Fa avviare LinuxGSM con il jar di Fabric: sostituisce solo la parte `-jar x.jar`
     * della riga startparameters, lasciando intatto il resto (memoria, nogui, ecc.).
     */
    fun useLoaderInConfig(cfg: ServerConfig): String {
        val f = Lgsm.path(GameVersion.configPath(cfg))
        return "[ -f $f ] || { echo 'CONFIG NON TROVATA'; exit ${GameVersion.EXIT_NO_CONFIG}; }; " +
                "cp $f \"$f.mcmonitor.bak.\$(date +%Y%m%d%H%M%S)\"; " +
                "if grep -qE '^[[:space:]]*startparameters=' $f; then " +
                "sed -i -E '/^[[:space:]]*startparameters=/ s|-jar[[:space:]]+[^[:space:]\"]+|-jar $LAUNCH_JAR|' $f; " +
                "else printf '%s\\n' 'startparameters=\"-Xmx\${javaram}M -Xms\${javaram}M -jar $LAUNCH_JAR nogui\"' >> $f; fi; " +
                "echo 'avvio configurato:'; grep -E '^[[:space:]]*startparameters=' $f"
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
            minecraftVersion = value("version")?.takeIf { it.isNotBlank() },
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
