package com.bellizia.mcmonitor.lgsm

import com.bellizia.mcmonitor.data.ServerConfig

/** Versione e ramo attualmente configurati in LinuxGSM. */
data class VersionConfig(
    val version: String?,
    val branch: String?,
    val branchKey: String
)

/**
 * LinuxGSM decide quale versione scaricare leggendo `mcversion` dal proprio file
 * di configurazione; dopo la modifica serve `./script update` per applicarla.
 */
object GameVersion {

    const val EXIT_NO_CONFIG = 96

    private val SAFE_VERSION = Regex("^[A-Za-z0-9._-]{1,40}$")

    fun isValidVersion(version: String) = SAFE_VERSION.matches(version)

    fun configPath(cfg: ServerConfig) =
        "${cfg.lgsmDir.trimEnd('/')}/lgsm/config-lgsm/${cfg.script}/${cfg.script}.cfg"

    fun readConfig(cfg: ServerConfig): String {
        val f = Lgsm.path(configPath(cfg))
        return "[ -f $f ] || { echo 'CONFIG NON TROVATA'; exit $EXIT_NO_CONFIG; }; " +
                "grep -E '^[[:space:]]*mc(version|branc[h]?)=' $f || echo '(nessuna riga mcversion)'"
    }

    /**
     * Scrive versione e ramo tenendo una copia del file. Il nome della chiave del
     * ramo si conserva com'è: alcune installazioni hanno `mcbranc`, altre `mcbranch`.
     */
    fun setVersion(cfg: ServerConfig, version: String, branch: String, branchKey: String): String {
        require(isValidVersion(version)) { "versione non valida: $version" }
        require(isValidVersion(branch)) { "ramo non valido: $branch" }
        require(branchKey == "mcbranch" || branchKey == "mcbranc") { "chiave ramo non valida" }

        val f = Lgsm.path(configPath(cfg))
        return "[ -f $f ] || { echo 'CONFIG NON TROVATA'; exit $EXIT_NO_CONFIG; }; " +
                "cp $f \"$f.mcmonitor.bak.\$(date +%Y%m%d%H%M%S)\"; " +
                "if grep -qE '^[[:space:]]*mcversion=' $f; then " +
                "sed -i 's|^[[:space:]]*mcversion=.*|mcversion=\"$version\"|' $f; " +
                "else printf '%s\\n' 'mcversion=\"$version\"' >> $f; fi; " +
                "if grep -qE '^[[:space:]]*$branchKey=' $f; then " +
                "sed -i 's|^[[:space:]]*$branchKey=.*|$branchKey=\"$branch\"|' $f; " +
                "else printf '%s\\n' '$branchKey=\"$branch\"' >> $f; fi; " +
                "echo 'configurazione aggiornata:'; grep -E '^[[:space:]]*mc(version|branc[h]?)=' $f"
    }

    /** `update` scarica la versione indicata e riavvia il server da solo. */
    fun update(cfg: ServerConfig) = Lgsm.action(cfg, "update")

    fun backup(cfg: ServerConfig) = Lgsm.action(cfg, "backup")

    fun parseConfig(raw: String): VersionConfig {
        val text = Lgsm.clean(raw)
        fun value(pattern: String) = Regex("(?m)^[\\s]*$pattern=[\"']?([^\"'\\s#]+)")
            .find(text)?.groupValues?.get(1)

        val branchKey = if (Regex("(?m)^[\\s]*mcbranch=").containsMatchIn(text)) "mcbranch" else "mcbranc"
        return VersionConfig(
            version = value("mcversion"),
            branch = value("mcbranc[h]?"),
            branchKey = branchKey
        )
    }
}
