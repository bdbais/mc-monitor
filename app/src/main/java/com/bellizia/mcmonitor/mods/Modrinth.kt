package com.bellizia.mcmonitor.mods

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Un progetto trovato su Modrinth. */
data class ModProject(
    val id: String,
    val slug: String,
    val title: String,
    val description: String,
    val author: String,
    val downloads: Long,
    val categories: List<String>
) {
    val downloadsLabel: String
        get() = when {
            downloads >= 1_000_000 -> "%.1fM".format(downloads / 1_000_000.0)
            downloads >= 1_000 -> "%.0fk".format(downloads / 1_000.0)
            else -> downloads.toString()
        }
}

/** Una versione pubblicata, con il file da scaricare. */
data class ModFile(
    val versionId: String,
    val versionNumber: String,
    val name: String,
    val datePublished: String,
    val gameVersions: List<String>,
    val loaders: List<String>,
    val fileName: String,
    val url: String,
    val sha1: String,
    val requiredDependencies: List<String>
) {
    val dateLabel: String get() = datePublished.take(10)
}

class ModrinthException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Client dell'API pubblica di Modrinth (v2). Nessuna chiave necessaria: serve
 * però uno User-Agent che identifichi l'applicazione, come chiede la loro documentazione.
 */
/**
 * I mod che quasi tutti mettono, con il perche' in una riga.
 *
 * Non e' una classifica: sono quelli che su un server piccolo risolvono i
 * problemi che si presentano per primi — il server che arranca, i mostri che
 * cancellano le costruzioni, i backup dimenticati.
 */
object RecommendedMods {

    data class Suggestion(val name: String, val slug: String, val why: String, val loader: String)

    val list = listOf(
        Suggestion(
            "Fabric API",
            "fabric-api",
            "Non fa niente da solo, ma quasi tutti gli altri mod lo pretendono: si installa per primo.",
            "fabric"
        ),
        Suggestion(
            "Lithium",
            "lithium",
            "Rende il server piu' veloce senza cambiare niente nel gioco. Il primo da mettere se scatta.",
            "fabric"
        ),
        Suggestion(
            "FerriteCore",
            "ferrite-core",
            "Riduce la memoria usata: utile sui computer piccoli e sui server con molti chunk.",
            "fabric"
        ),
        Suggestion(
            "Spark",
            "spark",
            "Dice cosa sta rallentando il server, con un rapporto da leggere invece di tirare a indovinare.",
            "fabric"
        ),
        Suggestion(
            "Chunky",
            "chunky",
            "Genera la mappa in anticipo: i giocatori non aspettano il mondo mentre esplorano.",
            "fabric"
        ),
        Suggestion(
            "Simple Voice Chat",
            "simple-voice-chat",
            "Chat vocale di prossimita': si sentono solo quelli vicini. Serve anche ai giocatori.",
            "fabric"
        ),
        Suggestion(
            "Dynmap",
            "dynmap",
            "La mappa del mondo in una pagina web, aggiornata mentre si gioca.",
            "fabric"
        ),
        Suggestion(
            "Vanilla Tweaks / Anti Xray",
            "anti-xray",
            "Nasconde i minerali a chi bara con le texture trasparenti.",
            "fabric"
        )
    )
}

object Modrinth {

    private const val BASE = "https://api.modrinth.com/v2"
    private const val USER_AGENT = "github.com/bdbais/mc-monitor/1.8 (app Android MC Monitor)"

    /**
     * Ricerca filtrata per versione di gioco e loader: senza quei vincoli si
     * finirebbe per installare mod che il server non caricherà mai.
     */
    suspend fun search(
        query: String,
        gameVersion: String?,
        loader: String?,
        limit: Int = 25
    ): List<ModProject> = withContext(Dispatchers.IO) {
        val facets = buildList {
            add(listOf("project_type:mod"))
            gameVersion?.takeIf { it.isNotBlank() }?.let { add(listOf("versions:$it")) }
            loader?.takeIf { it.isNotBlank() && it != "vanilla" }?.let { add(listOf("categories:$it")) }
        }
        val url = "$BASE/search?limit=$limit&index=relevance" +
                "&query=${enc(query)}&facets=${enc(facetsJson(facets))}"

        val hits = JSONObject(get(url)).optJSONArray("hits") ?: JSONArray()
        (0 until hits.length()).mapNotNull { i ->
            val o = hits.optJSONObject(i) ?: return@mapNotNull null
            ModProject(
                id = o.optString("project_id"),
                slug = o.optString("slug"),
                title = o.optString("title"),
                description = o.optString("description"),
                author = o.optString("author"),
                downloads = o.optLong("downloads"),
                categories = o.optJSONArray("categories").toStringList()
            )
        }
    }

    /** Versioni compatibili di un progetto, dalla più recente. */
    suspend fun versions(
        projectId: String,
        gameVersion: String?,
        loader: String?
    ): List<ModFile> = withContext(Dispatchers.IO) {
        val params = buildList {
            gameVersion?.takeIf { it.isNotBlank() }?.let { add("game_versions=${enc("[\"$it\"]")}") }
            loader?.takeIf { it.isNotBlank() && it != "vanilla" }?.let { add("loaders=${enc("[\"$it\"]")}") }
        }
        val url = "$BASE/project/$projectId/version" + if (params.isEmpty()) "" else "?${params.joinToString("&")}"
        parseVersions(get(url))
    }

    suspend fun projectTitle(projectId: String): String = withContext(Dispatchers.IO) {
        runCatching { JSONObject(get("$BASE/project/$projectId")).optString("title") }
            .getOrDefault(projectId)
            .ifBlank { projectId }
    }

    fun parseVersions(json: String): List<ModFile> {
        val array = JSONArray(json)
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val files = o.optJSONArray("files") ?: return@mapNotNull null
            // Un rilascio può contenere sorgenti o javadoc: si prende il file primario.
            val file = (0 until files.length())
                .mapNotNull { files.optJSONObject(it) }
                .let { list -> list.firstOrNull { it.optBoolean("primary") } ?: list.firstOrNull() }
                ?: return@mapNotNull null

            val deps = o.optJSONArray("dependencies") ?: JSONArray()
            val required = (0 until deps.length())
                .mapNotNull { deps.optJSONObject(it) }
                .filter { it.optString("dependency_type") == "required" }
                .mapNotNull { it.optString("project_id").takeIf { id -> id.isNotBlank() } }

            ModFile(
                versionId = o.optString("id"),
                versionNumber = o.optString("version_number"),
                name = o.optString("name"),
                datePublished = o.optString("date_published"),
                gameVersions = o.optJSONArray("game_versions").toStringList(),
                loaders = o.optJSONArray("loaders").toStringList(),
                fileName = file.optString("filename"),
                url = file.optString("url"),
                sha1 = file.optJSONObject("hashes")?.optString("sha1").orEmpty(),
                requiredDependencies = required
            )
        }
    }

    /**
     * Ritrova i mod partendo dall'impronta dei file, non dal nome.
     *
     * È così che si rimette in piedi il progetto di un altro server: l'impronta
     * sha1 identifica quel file preciso, quindi quello che arriva è la stessa
     * versione dello stesso mod, non "qualcosa che si chiama uguale". I file che
     * su Modrinth non ci sono semplicemente non tornano indietro.
     */
    suspend fun byHashes(sha1: List<String>): Map<String, ModFile> = withContext(Dispatchers.IO) {
        if (sha1.isEmpty()) return@withContext emptyMap()
        val corpo = JSONObject()
            .put("hashes", JSONArray(sha1.map { it.lowercase() }.distinct()))
            .put("algorithm", "sha1")
            .toString()

        val risposta = runCatching { JSONObject(post("$BASE/version_files", corpo)) }
            .getOrElse { throw ModrinthException("Risposta di Modrinth non leggibile.") }

        risposta.keys().asSequence().mapNotNull { hash ->
            val versione = risposta.optJSONObject(hash) ?: return@mapNotNull null
            // parseVersions lavora su un elenco: qui l'elenco è di uno.
            val file = parseVersions(JSONArray().put(versione).toString()).firstOrNull()
                ?: return@mapNotNull null
            hash.lowercase() to file
        }.toMap()
    }

    // ---------------------------------------------------------------- interno

    private fun facetsJson(facets: List<List<String>>): String =
        facets.joinToString(",", "[", "]") { group ->
            group.joinToString(",", "[", "]") { "\"$it\"" }
        }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun get(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = connection.responseCode
            if (code == 429) throw ModrinthException("Troppe richieste a Modrinth: riprova fra un minuto.")
            if (code !in 200..299) {
                throw ModrinthException("Modrinth ha risposto $code ${connection.responseMessage ?: ""}".trim())
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } catch (e: ModrinthException) {
            throw e
        } catch (e: Exception) {
            throw ModrinthException("Modrinth non raggiungibile: ${e.message}", e)
        } finally {
            connection.disconnect()
        }
    }

    private fun post(url: String, body: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code == 429) throw ModrinthException("Troppe richieste a Modrinth: riprova fra un minuto.")
            if (code !in 200..299) {
                throw ModrinthException("Modrinth ha risposto $code ${connection.responseMessage ?: ""}".trim())
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } catch (e: ModrinthException) {
            throw e
        } catch (e: Exception) {
            throw ModrinthException("Modrinth non raggiungibile: ${e.message}", e)
        } finally {
            connection.disconnect()
        }
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotBlank() } }
    }
}
