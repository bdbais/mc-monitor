package com.bellizia.mcmonitor.lgsm

import com.bellizia.mcmonitor.data.ServerConfig

/**
 * Installazione di LinuxGSM su una home vuota e messa in sicurezza del server.
 *
 * Tutto gira come utente non privilegiato: LinuxGSM è pensato per stare dentro una
 * home. L'unica cosa che richiede l'amministratore sono i pacchetti di sistema, che
 * l'app verifica ma non può installare.
 */
object Provision {

    const val EXIT_ALREADY_THERE = 97
    const val EXIT_BAD_SCRIPT = 98

    private const val LGSM_URL = "https://linuxgsm.sh"

    /** Impostazioni scritte in server.properties, con il motivo di ciascuna. */
    val SECURITY_SETTINGS = listOf(
        Triple("online-mode", "true", "verifica gli account Mojang: senza, chiunque può entrare col nome di un altro"),
        Triple("white-list", "true", "entra solo chi è stato ammesso"),
        Triple("enforce-whitelist", "true", "la whitelist vale anche per gli operatori già collegati"),
        Triple("enforce-secure-profile", "true", "messaggi in chat firmati, contro l'impersonazione"),
        Triple("enable-command-block", "false", "nessun blocco comandi: è la via più semplice per un abuso"),
        Triple("broadcast-rcon-to-ops", "false", "i comandi da app non finiscono nella chat degli operatori"),
        Triple("spawn-protection", "16", "area di spawn non modificabile dai non operatori"),
        Triple("sync-chunk-writes", "true", "scritture del mondo sincrone: meno danni in caso di crash")
    )

    /** Il server è "vuoto" se nella directory non c'è lo script di LinuxGSM. */
    fun inspect(cfg: ServerConfig): String {
        val dir = Lgsm.path(cfg.lgsmDir.trimEnd('/'))
        val script = Lgsm.path("${cfg.lgsmDir.trimEnd('/')}/${cfg.script}")
        return "echo \"home=\$HOME\"; echo \"utente=\$(id -un)\"; " +
                "if [ -d $dir ]; then echo 'dir=si'; else echo 'dir=no'; fi; " +
                "if [ -f $script ]; then echo 'script=si'; else echo 'script=no'; fi; " +
                "if [ -d $dir/serverfiles ]; then echo 'serverfiles=si'; else echo 'serverfiles=no'; fi; " +
                "echo \"spazio=\$(df -Pm \"\$HOME\" 2>/dev/null | awk 'NR==2{print \$4}')\"; " +
                "if [ -w \"\$(dirname $dir)\" ] || [ -w $dir ]; then echo 'scrivibile=si'; else echo 'scrivibile=no'; fi"
    }

    /**
     * Scarica lo script ufficiale di LinuxGSM e crea l'istanza. Prima di eseguirlo
     * si controlla che sia davvero uno script di shell e non una pagina di errore.
     */
    fun installLinuxGsm(cfg: ServerConfig): String {
        val dir = Lgsm.path(cfg.lgsmDir.trimEnd('/'))
        val script = cfg.script
        require(Regex("^[A-Za-z0-9_-]{1,32}$").matches(script)) { "nome script non valido" }

        return "mkdir -p $dir && cd $dir || exit 1; " +
                "if [ -f ./$script ]; then echo 'LinuxGSM risulta già installato qui'; exit $EXIT_ALREADY_THERE; fi; " +
                "if command -v curl >/dev/null 2>&1; then curl -fsSL --max-time 120 -o linuxgsm.sh $LGSM_URL; " +
                "elif command -v wget >/dev/null 2>&1; then wget -q --timeout=120 -O linuxgsm.sh $LGSM_URL; " +
                "else echo 'NESSUNO STRUMENTO DI DOWNLOAD'; exit ${Mods.EXIT_NO_TOOL}; fi || " +
                "{ echo 'DOWNLOAD DI linuxgsm.sh FALLITO'; exit ${Mods.EXIT_DOWNLOAD_FAILED}; }; " +
                "if ! head -1 linuxgsm.sh | grep -q '^#!/'; then " +
                "rm -f linuxgsm.sh; echo 'IL FILE SCARICATO NON E UNO SCRIPT'; exit $EXIT_BAD_SCRIPT; fi; " +
                "chmod 700 linuxgsm.sh && ./linuxgsm.sh $script 2>&1 | tail -20; " +
                "[ -f ./$script ] && echo 'istanza $script creata'"
    }

    /** `auto-install` scarica il server ed è la parte lunga: diversi minuti. */
    fun autoInstall(cfg: ServerConfig): String =
        "${Lgsm.action(cfg, "auto-install")} | tail -30"

    /**
     * Scrive in server.properties le impostazioni che riducono la superficie di
     * attacco, tenendo una copia del file, e ne restringe i permessi: contiene la
     * password RCON.
     */
    fun harden(cfg: ServerConfig): String {
        val file = "${cfg.serverFiles.trimEnd('/')}/server.properties"
        val f = Lgsm.path(file)
        val writes = SECURITY_SETTINGS.joinToString("; ") { (key, value, _) ->
            val escaped = key.replace(".", "\\.")
            "if grep -q '^$escaped=' $f; then sed -i 's|^$escaped=.*|$key=$value|' $f; " +
                    "else printf '%s\\n' '$key=$value' >> $f; fi"
        }
        return "[ -f $f ] || { echo 'server.properties non trovato: il server non è ancora stato installato'; exit ${GameVersion.EXIT_NO_CONFIG}; }; " +
                Lgsm.backupFirst(f) +
                writes + "; " +
                "chmod 600 $f && echo 'permessi di server.properties ristretti al solo proprietario'; " +
                "echo 'impostazioni applicate:'; " +
                "grep -E '^(online-mode|white-list|enforce-whitelist|enforce-secure-profile|enable-command-block|broadcast-rcon-to-ops|spawn-protection|sync-chunk-writes)=' $f"
    }

    fun parseInspection(raw: String): ServerInspection {
        val text = Lgsm.clean(raw)
        fun value(key: String) = Regex("(?m)^$key=(.*)$").find(text)?.groupValues?.get(1)?.trim()
        return ServerInspection(
            home = value("home"),
            user = value("utente"),
            hasDirectory = value("dir") == "si",
            hasScript = value("script") == "si",
            hasServerFiles = value("serverfiles") == "si",
            freeMegabytes = value("spazio")?.toLongOrNull(),
            writable = value("scrivibile") == "si"
        )
    }
}

data class ServerInspection(
    val home: String?,
    val user: String?,
    val hasDirectory: Boolean,
    val hasScript: Boolean,
    val hasServerFiles: Boolean,
    val freeMegabytes: Long?,
    val writable: Boolean
) {
    val isEmpty: Boolean get() = !hasScript

    /** Il jar più i file del mondo stanno larghi in 2 GB; sotto è imprudente. */
    val enoughSpace: Boolean get() = (freeMegabytes ?: 0) >= 2048
}
