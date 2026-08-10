package com.bellizia.mcmonitor.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Configurazione di un server: connessione SSH, istanza LinuxGSM e RCON.
 *
 * [serverFilesDir] punta alla cartella dove Minecraft tiene whitelist.json e
 * banned-players.json (per LinuxGSM di norma <lgsmDir>/serverfiles).
 */
data class ServerConfig(
    val id: String = "",
    val name: String = "",
    /** Nome tecnico: identifica il server e dà il nome alla sua sottocartella. */
    val slug: String = "server1",
    val host: String = "",
    val port: Int = 22,
    val user: String = "mcserver",
    val password: String = "",
    val privateKey: String = "",
    val keyPassphrase: String = "",
    // Relativo alla home dell'utente SSH: ogni server sta in una cartella sua.
    val lgsmDir: String = "~/server1",
    val script: String = "mcserver",
    val serverFilesDir: String = "",
    val useLgsmSend: Boolean = false,
    val tmuxSession: String = "",
    val rconEnabled: Boolean = false,
    val rconPort: Int = 25575,
    val rconPassword: String = "",
    val rconTunnel: Boolean = true,
    val webMapUrl: String = "",
    val mapPollSeconds: Int = 6,
    val hostKeyFingerprint: String = "",
    /** I requisiti del server si controllano una volta sola, al primo collegamento. */
    val requirementsChecked: Boolean = false
) {
    val isComplete: Boolean
        get() = host.isNotBlank() && user.isNotBlank() &&
                (password.isNotBlank() || privateKey.isNotBlank()) &&
                lgsmDir.isNotBlank() && script.isNotBlank()

    /** Cartella dei file di gioco, con default derivato dalla directory LinuxGSM. */
    val serverFiles: String
        get() = serverFilesDir.ifBlank { "${lgsmDir.trimEnd('/')}/serverfiles" }

    /** RCON utilizzabile solo se attivo e con una password impostata. */
    val rconUsable: Boolean
        get() = rconEnabled && rconPassword.isNotBlank()

    /** LinuxGSM chiama la sessione tmux come lo script, salvo personalizzazioni. */
    val session: String
        get() = tmuxSession.ifBlank { script }

    val label: String
        get() = if (host.isBlank()) "nessun server configurato" else "$user@$host:$port"

    /** Nome mostrato nell'elenco: quello scelto, o l'host, o un segnaposto. */
    val displayName: String
        get() = name.ifBlank { host.ifBlank { "Nuovo server" } }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("slug", slug)
        put("host", host)
        put("port", port)
        put("user", user)
        put("password", password)
        put("privateKey", privateKey)
        put("keyPassphrase", keyPassphrase)
        put("lgsmDir", lgsmDir)
        put("script", script)
        put("serverFilesDir", serverFilesDir)
        put("useLgsmSend", useLgsmSend)
        put("tmuxSession", tmuxSession)
        put("rconEnabled", rconEnabled)
        put("rconPort", rconPort)
        put("rconPassword", rconPassword)
        put("rconTunnel", rconTunnel)
        put("webMapUrl", webMapUrl)
        put("mapPollSeconds", mapPollSeconds)
        put("hostKeyFingerprint", hostKeyFingerprint)
        put("requirementsChecked", requirementsChecked)
    }

    companion object {
        fun fromJson(o: JSONObject) = ServerConfig(
            id = o.optString("id", UUID.randomUUID().toString()),
            name = o.optString("name"),
            slug = o.optString("slug", "server1").ifBlank { "server1" },
            host = o.optString("host"),
            port = o.optInt("port", 22),
            user = o.optString("user", "mcserver"),
            password = o.optString("password"),
            privateKey = o.optString("privateKey"),
            keyPassphrase = o.optString("keyPassphrase"),
            lgsmDir = o.optString("lgsmDir", "~/server1"),
            script = o.optString("script", "mcserver"),
            serverFilesDir = o.optString("serverFilesDir"),
            useLgsmSend = o.optBoolean("useLgsmSend", false),
            tmuxSession = o.optString("tmuxSession"),
            rconEnabled = o.optBoolean("rconEnabled", false),
            rconPort = o.optInt("rconPort", 25575),
            rconPassword = o.optString("rconPassword"),
            rconTunnel = o.optBoolean("rconTunnel", true),
            webMapUrl = o.optString("webMapUrl"),
            mapPollSeconds = o.optInt("mapPollSeconds", 6),
            hostKeyFingerprint = o.optString("hostKeyFingerprint"),
            requirementsChecked = o.optBoolean("requirementsChecked", false)
        )
    }
}

/**
 * Archivio dei server configurati. Il resto dell'app continua a chiamare [load] e
 * [save], che lavorano sul server attivo: la scelta di quale sia si fa dall'elenco.
 */
object Prefs {

    private const val FILE = "mcmonitor"
    private const val KEY_SERVERS = "servers"
    private const val KEY_ACTIVE = "activeServer"

    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        sp = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        migrateSingleServer()
        migrateSendMode()
    }

    // ------------------------------------------------------------------ elenco

    fun servers(): List<ServerConfig> {
        val raw = sp.getString(KEY_SERVERS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                array.optJSONObject(i)?.let { ServerConfig.fromJson(it) }
            }
        }.getOrDefault(emptyList())
    }

    fun activeId(): String = sp.getString(KEY_ACTIVE, "") ?: ""

    fun setActive(id: String) {
        sp.edit().putString(KEY_ACTIVE, id).apply()
    }

    /** Il server attivo, o una configurazione vuota se non ce n'è ancora nessuno. */
    fun load(): ServerConfig {
        val all = servers()
        return all.firstOrNull { it.id == activeId() } ?: all.firstOrNull() ?: ServerConfig()
    }

    /** Aggiorna il server con lo stesso id, o lo inserisce se non c'è. */
    fun save(cfg: ServerConfig) {
        val config = if (cfg.id.isBlank()) cfg.copy(id = UUID.randomUUID().toString()) else cfg
        val all = servers().toMutableList()
        val index = all.indexOfFirst { it.id == config.id }
        if (index >= 0) all[index] = config else all.add(config)
        writeAll(all)
        if (activeId().isBlank() || activeId() == config.id) setActive(config.id)
    }

    /**
     * Ogni nuovo server prende un nome tecnico libero (server1, server2, …) e la
     * sua sottocartella nella home: due istanze non possono pestarsi i piedi.
     */
    fun add(cfg: ServerConfig = ServerConfig()): ServerConfig {
        val used = servers().map { it.slug }.toSet()
        val slug = generateSequence(1) { it + 1 }.map { "server$it" }.first { it !in used }
        val fresh = cfg.copy(
            id = UUID.randomUUID().toString(),
            slug = slug,
            lgsmDir = "~/$slug"
        )
        val all = servers().toMutableList().apply { add(fresh) }
        writeAll(all)
        return fresh
    }

    fun remove(id: String) {
        val all = servers().filterNot { it.id == id }
        writeAll(all)
        if (activeId() == id) setActive(all.firstOrNull()?.id.orEmpty())
    }

    /** Copia un server con un nuovo id: comodo per due istanze sullo stesso host. */
    fun duplicate(id: String): ServerConfig? {
        val source = servers().firstOrNull { it.id == id } ?: return null
        return add(source.copy(name = "${source.displayName} (copia)", hostKeyFingerprint = source.hostKeyFingerprint))
    }

    // ------------------------------------------------- scorciatoie sul corrente

    fun saveHostKey(fingerprint: String) = save(load().copy(hostKeyFingerprint = fingerprint))

    fun clearHostKey() = save(load().copy(hostKeyFingerprint = ""))

    /** Usata quando l'app scopre da sola che "lgsm send" non esiste su questo server. */
    fun setUseLgsmSend(enabled: Boolean) = save(load().copy(useLgsmSend = enabled))

    // ----------------------------------------------------------- migrazioni

    private fun writeAll(servers: List<ServerConfig>) {
        val array = JSONArray()
        servers.forEach { array.put(it.toJson()) }
        sp.edit().putString(KEY_SERVERS, array.toString()).apply()
    }

    /** Le versioni fino alla 1.6 tenevano un solo server in chiavi separate. */
    private fun migrateSingleServer() {
        if (sp.contains(KEY_SERVERS)) return
        val host = sp.getString("host", "") ?: ""
        if (host.isBlank()) return

        val legacy = ServerConfig(
            id = UUID.randomUUID().toString(),
            name = host,
            host = host,
            port = sp.getInt("port", 22),
            user = sp.getString("user", "mcserver") ?: "",
            password = sp.getString("password", "") ?: "",
            privateKey = sp.getString("privateKey", "") ?: "",
            keyPassphrase = sp.getString("keyPassphrase", "") ?: "",
            lgsmDir = sp.getString("lgsmDir", "/home/mcserver") ?: "",
            script = sp.getString("script", "mcserver") ?: "",
            serverFilesDir = sp.getString("serverFilesDir", "") ?: "",
            useLgsmSend = sp.getBoolean("useLgsmSend", false),
            tmuxSession = sp.getString("tmuxSession", "") ?: "",
            rconEnabled = sp.getBoolean("rconEnabled", false),
            rconPort = sp.getInt("rconPort", 25575),
            rconPassword = sp.getString("rconPassword", "") ?: "",
            rconTunnel = sp.getBoolean("rconTunnel", true),
            webMapUrl = sp.getString("webMapUrl", "") ?: "",
            mapPollSeconds = sp.getInt("mapPollSeconds", 6),
            hostKeyFingerprint = sp.getString("hostKeyFingerprint", "") ?: ""
        )
        writeAll(listOf(legacy))
        setActive(legacy.id)
    }

    /**
     * Le prime versioni usavano `lgsm send`, assente su molte installazioni e per giunta
     * silenzioso quando manca. Una volta sola si riportano tutti i server su tmux.
     */
    private fun migrateSendMode() {
        if (sp.contains("sendModeMigrated")) return
        writeAll(servers().map { it.copy(useLgsmSend = false) })
        sp.edit().putBoolean("sendModeMigrated", true).apply()
    }
}
