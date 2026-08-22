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
    /** Notifiche: attivazione e scelta degli eventi, server per server. */
    val notifyEnabled: Boolean = false,
    val notifyOffline: Boolean = true,
    val notifyOnline: Boolean = true,
    val notifyJoin: Boolean = true,
    val notifyLeave: Boolean = false,
    val notifySeconds: Int = 60,
    val hostKeyFingerprint: String = "",
    /** Servono a ordinare l'elenco: quando è nato il profilo e quando l'hai aperto. */
    val createdAt: Long = 0L,
    val lastUsedAt: Long = 0L,
    /** I requisiti del server si controllano una volta sola, al primo collegamento. */
    val requirementsChecked: Boolean = false
) {
    val isComplete: Boolean
        get() = hasCredentials && lgsmDir.isNotBlank() && script.isNotBlank()

    /**
     * Basta per entrare nel computer, non ancora per comandare un server:
     * è il primo dei due passi che l'app chiede.
     */
    val hasCredentials: Boolean
        get() = host.isNotBlank() && user.isNotBlank() &&
                (password.isNotBlank() || privateKey.isNotBlank())

    /** Solo la parte di collegamento: vale per tutti i server di quel computer. */
    fun credentials(): ServerConfig = ServerConfig(
        host = host,
        port = port,
        user = user,
        password = password,
        privateKey = privateKey,
        keyPassphrase = keyPassphrase,
        hostKeyFingerprint = hostKeyFingerprint
    )

    /** Innesta le credenziali dell'utenza su un profilo, lasciando il resto com'è. */
    fun withCredentials(account: ServerConfig): ServerConfig = copy(
        host = account.host,
        port = account.port,
        user = account.user,
        password = account.password,
        privateKey = account.privateKey,
        keyPassphrase = account.keyPassphrase,
        hostKeyFingerprint = account.hostKeyFingerprint
    )

    /** Cartella dei file di gioco, con default derivato dalla directory LinuxGSM. */
    val serverFiles: String
        get() = serverFilesDir.ifBlank { "${lgsmDir.trimEnd('/')}/serverfiles" }

    /** Il monitoraggio ha senso solo se c'è almeno un evento scelto. */
    val watching: Boolean
        get() = notifyEnabled && isComplete &&
                (notifyOffline || notifyOnline || notifyJoin || notifyLeave)

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
        put("notifyEnabled", notifyEnabled)
        put("notifyOffline", notifyOffline)
        put("notifyOnline", notifyOnline)
        put("notifyJoin", notifyJoin)
        put("notifyLeave", notifyLeave)
        put("notifySeconds", notifySeconds)
        put("hostKeyFingerprint", hostKeyFingerprint)
        put("requirementsChecked", requirementsChecked)
        put("createdAt", createdAt)
        put("lastUsedAt", lastUsedAt)
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
            notifyEnabled = o.optBoolean("notifyEnabled", false),
            notifyOffline = o.optBoolean("notifyOffline", true),
            notifyOnline = o.optBoolean("notifyOnline", true),
            notifyJoin = o.optBoolean("notifyJoin", true),
            notifyLeave = o.optBoolean("notifyLeave", false),
            notifySeconds = o.optInt("notifySeconds", 60),
            hostKeyFingerprint = o.optString("hostKeyFingerprint"),
            requirementsChecked = o.optBoolean("requirementsChecked", false),
            createdAt = o.optLong("createdAt", 0L),
            lastUsedAt = o.optLong("lastUsedAt", 0L)
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
    private const val KEY_ACCOUNT = "sshAccount"

    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        sp = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        migrateSingleServer()
        migrateSendMode()
        migrateAccount()
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

    // ------------------------------------------------------------- utenza Linux

    /**
     * L'utenza con cui si entra nel computer: è il primo passo, uguale per tutti
     * i server che ci vivono dentro. Chi arriva da una versione precedente la
     * ritrova già compilata, presa dal server che stava usando.
     */
    fun account(): ServerConfig {
        val raw = sp.getString(KEY_ACCOUNT, null)
        if (!raw.isNullOrBlank()) {
            val saved = runCatching { ServerConfig.fromJson(JSONObject(raw)).credentials() }.getOrNull()
            if (saved != null) return saved
        }
        val known = servers().firstOrNull { it.id == activeId() && it.hasCredentials }
            ?: servers().firstOrNull { it.hasCredentials }
        return known?.credentials() ?: ServerConfig()
    }

    /**
     * Salvando l'utenza si aggiornano anche i server già configurati su quello
     * stesso computer: cambiare qui la password e lasciarli indietro li
     * escluderebbe tutti senza spiegazioni.
     */
    fun saveAccount(account: ServerConfig) {
        val clean = account.credentials()
        sp.edit().putString(KEY_ACCOUNT, clean.toJson().toString()).apply()
        val updated = servers().map { server ->
            if (server.host.equals(clean.host, ignoreCase = true) && server.user == clean.user) {
                server.withCredentials(clean)
            } else {
                server
            }
        }
        writeAll(updated)
    }

    /**
     * Impostazione dell'app, non del singolo server: attiva per default, perché
     * uno screenshot con l'indirizzo o la password in chiaro si condivide una
     * volta sola e non si può più riprendere.
     */
    /** Cartella scelta con il selettore di sistema (Drive, OneDrive, Dropbox, locale). */
    var backupFolder: String
        get() = sp.getString("backupFolder", "") ?: ""
        set(value) {
            sp.edit().putString("backupFolder", value).apply()
        }

    var backupEnabled: Boolean
        get() = sp.getBoolean("backupEnabled", false)
        set(value) {
            sp.edit().putBoolean("backupEnabled", value).apply()
        }

    /** Serve per rifare il backup senza chiederla ogni volta; il file resta cifrato. */
    var backupPassword: String
        get() = sp.getString("backupPassword", "") ?: ""
        set(value) {
            sp.edit().putString("backupPassword", value).apply()
        }

    var backupLast: Long
        get() = sp.getLong("backupLast", 0L)
        set(value) {
            sp.edit().putLong("backupLast", value).apply()
        }

    /** Come ordinare l elenco: "creazione" oppure "recenti". */
    var sortMode: String
        get() = sp.getString("sortMode", "creazione") ?: "creazione"
        set(value) {
            sp.edit().putString("sortMode", value).apply()
        }

    /** Segna l apertura di un server: serve all ordinamento per ultimo utilizzo. */
    fun markUsed(id: String) {
        val server = servers().firstOrNull { it.id == id } ?: return
        save(server.copy(lastUsedAt = System.currentTimeMillis()))
    }

    // ------------------------------------------------- amministratore e blocco

    /**
     * Come ti chiami quando comandi il server: comparira' accanto alle azioni e,
     * quando ci sara', nella chat fra amministratori.
     */
    var adminName: String
        get() = sp.getString("adminName", "") ?: ""
        set(value) {
            sp.edit().putString("adminName", value.trim()).apply()
        }

    /**
     * Identificativo di questa installazione: distingue i telefoni fra loro nella
     * presenza e nella chat, senza dire niente su chi li usa. Nasce a caso al
     * primo bisogno e resta finché l'app resta installata.
     */
    val deviceId: String
        get() {
            val salvato = sp.getString("deviceId", "") ?: ""
            if (salvato.isNotBlank()) return salvato
            val nuovo = UUID.randomUUID().toString().replace("-", "").take(12)
            sp.edit().putString("deviceId", nuovo).apply()
            return nuovo
        }

    /**
     * Fin dove la chat degli amministratori e' stata letta, server per server:
     * serve a contare i messaggi nuovi senza rileggerli tutti come nuovi ogni
     * volta che si apre l'app.
     */
    fun chatLastRead(serverId: String): Long = sp.getLong("chatLastRead_$serverId", 0L)

    fun setChatLastRead(serverId: String, epochSeconds: Long) {
        sp.edit().putLong("chatLastRead_$serverId", epochSeconds).apply()
    }

    /** Impronta della password che sblocca l'app, con il suo sale. Mai la password. */
    var lockHash: String
        get() = sp.getString("lockHash", "") ?: ""
        set(value) {
            sp.edit().putString("lockHash", value).apply()
        }

    var lockSalt: String
        get() = sp.getString("lockSalt", "") ?: ""
        set(value) {
            sp.edit().putString("lockSalt", value).apply()
        }

    var lockBiometric: Boolean
        get() = sp.getBoolean("lockBiometric", false)
        set(value) {
            sp.edit().putBoolean("lockBiometric", value).apply()
        }

    /** Minuti in secondo piano prima di richiedere la password. -1 = mai. */
    var lockTimeoutMinutes: Int
        get() = sp.getInt("lockTimeoutMinutes", 2)
        set(value) {
            sp.edit().putInt("lockTimeoutMinutes", value).apply()
        }

    val lockConfigured: Boolean
        get() = lockHash.isNotBlank() && lockSalt.isNotBlank()

    /** La presentazione del primo avvio si fa una volta sola, anche se si salta. */
    var welcomeDone: Boolean
        get() = sp.getBoolean("welcomeDone", false)
        set(value) {
            sp.edit().putBoolean("welcomeDone", value).apply()
        }

    var privacyMode: Boolean
        get() = sp.getBoolean("privacyMode", true)
        set(value) {
            sp.edit().putBoolean("privacyMode", value).apply()
        }

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
            lgsmDir = "~/$slug",
            createdAt = System.currentTimeMillis()
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

    /**
     * Ricorda l'impronta di un computer sull'utenza e sui profili che stanno lì.
     * L'host va passato: al primo collegamento un profilo può non esserci ancora,
     * e scriverla su una configurazione vuota creerebbe un profilo fantasma.
     */
    fun saveHostKey(host: String, fingerprint: String) {
        if (host.isBlank() || fingerprint.isBlank()) return
        val account = account()
        if (account.hasCredentials && account.host.equals(host, ignoreCase = true)) {
            saveAccount(account.copy(hostKeyFingerprint = fingerprint))
        }
        val updated = servers().map { server ->
            if (server.host.equals(host, ignoreCase = true) && server.hostKeyFingerprint.isBlank()) {
                server.copy(hostKeyFingerprint = fingerprint)
            } else {
                server
            }
        }
        writeAll(updated)
    }

    /** Dimentica l'impronta: la si riapprende al collegamento successivo. */
    fun clearHostKey() {
        val account = account()
        if (account.hasCredentials) saveAccount(account.copy(hostKeyFingerprint = ""))
        val active = servers().firstOrNull { it.id == activeId() } ?: return
        save(active.copy(hostKeyFingerprint = ""))
    }

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
     * Fino alla 1.16 le credenziali stavano dentro ogni profilo, senza un'utenza
     * a sé. Chi aggiorna deve ritrovare il primo passo già compilato, non un
     * modulo vuoto: si prende il server che stava usando e se ne salva la parte
     * di collegamento. Una volta sola, e senza toccare i profili.
     */
    private fun migrateAccount() {
        if (sp.contains(KEY_ACCOUNT)) return
        val known = servers().firstOrNull { it.id == activeId() && it.hasCredentials }
            ?: servers().firstOrNull { it.hasCredentials }
            ?: return
        sp.edit().putString(KEY_ACCOUNT, known.credentials().toJson().toString()).apply()
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
