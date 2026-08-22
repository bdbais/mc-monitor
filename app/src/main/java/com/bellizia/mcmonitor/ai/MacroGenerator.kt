package com.bellizia.mcmonitor.ai

import com.bellizia.mcmonitor.lgsm.Macro
import com.bellizia.mcmonitor.lgsm.Macros
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MacroAiException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * I servizi che sanno scrivere una macro, scelti fra quelli che hanno un piano
 * gratuito e danno una chiave in due minuti senza chiedere una carta.
 */
enum class AiProvider(
    val id: String,
    val label: String,
    val where: String,
    val model: String
) {
    GOOGLE(
        id = "google",
        label = "Google AI Studio (Gemini)",
        where = "https://aistudio.google.com/apikey",
        model = "gemini-2.0-flash"
    ),
    GROQ(
        id = "groq",
        label = "Groq",
        where = "https://console.groq.com/keys",
        model = "llama-3.3-70b-versatile"
    );

    companion object {
        fun byId(id: String): AiProvider = entries.firstOrNull { it.id == id } ?: GOOGLE
    }
}

/** Quello che il servizio ha proposto, gia' passato al setaccio. */
data class GeneratedMacro(
    val macro: Macro,
    /** I comandi buttati via, con il motivo: si dice, non si nasconde. */
    val rejected: List<String>
)

/**
 * Fa scrivere una macro a un servizio di intelligenza artificiale.
 *
 * La chiave e' di chi usa l'app, non dell'app: si prende gratis dal servizio e
 * resta su questo telefono. Senza chiave qui non succede niente, e nessuna delle
 * altre funzioni ne ha bisogno.
 *
 * Quello che torna indietro non viene creduto sulla parola. Un modello puo'
 * scrivere `stop` mentre stanno giocando in venti, o `op` a un nome che non hai
 * mai sentito: comandi legittimi, che pero' nessuno ha chiesto. Per questo passa
 * da [safe], che tiene solo i verbi di una lista, e quello che resta fuori viene
 * mostrato invece che tolto di nascosto. Non e' una regola per te: dalla Console
 * quei comandi li scrivi quando vuoi. E' una regola per quello che scrive
 * qualcun altro al posto tuo.
 */
object MacroGenerator {

    /**
     * I verbi ammessi in una macro scritta da un modello: cambiano il mondo o
     * parlano ai giocatori, ma non toccano chi puo' entrare e non spengono niente.
     */
    private val ALLOWED = setOf(
        "say", "tell", "msg", "w", "me", "title", "tellraw", "playsound", "particle",
        "time", "weather", "gamerule", "difficulty", "worldborder", "setworldspawn",
        "spawnpoint", "gamemode", "give", "clear", "item", "enchant", "xp", "experience",
        "effect", "attribute", "summon", "kill", "damage", "ride", "tp", "teleport",
        "spreadplayers", "execute", "setblock", "fill", "clone", "place", "loot",
        "scoreboard", "team", "bossbar", "tag", "advancement", "recipe", "trigger",
        "save-all", "save-off", "save-on", "list", "seed", "locate", "spectate",
        "data", "forceload", "schedule", "function"
    )

    /**
     * I verbi che restano fuori anche se somigliano a quelli sopra: spengono il
     * server, decidono chi puo' entrare, o danno i poteri di amministratore.
     */
    private val FORBIDDEN = setOf(
        "stop", "restart", "reload", "op", "deop", "ban", "ban-ip", "banlist",
        "pardon", "pardon-ip", "kick", "whitelist", "publish", "debug", "jfr",
        "perf", "datapack", "setidletimeout", "transfer"
    )

    fun verb(command: String): String =
        command.trim().removePrefix("/").substringBefore(' ').lowercase()

    /** Un comando si tiene se e' scritto bene, se e' in elenco e se non e' vietato. */
    fun safe(command: String): Boolean {
        val riga = command.trim()
        if (riga.startsWith(Macros.WAIT)) return Macros.waitSeconds(riga) != null
        if (!Macros.isValidCommand(riga)) return false
        val v = verb(riga)
        return v in ALLOWED && v !in FORBIDDEN
    }

    fun prompt(request: String, minecraft: String, loader: String): String = """
        Sei un amministratore di server Minecraft. Scrivi una macro: una sequenza di
        comandi da console per il server, in ordine.

        Richiesta: $request

        Server: Minecraft ${minecraft.ifBlank { "versione non nota" }}, ${loader.ifBlank { "vanilla" }}.

        Regole:
        - rispondi SOLO con un oggetto JSON: {"nome": "...", "descrizione": "...", "comandi": ["...", "..."]}
        - nome e descrizione in italiano, la descrizione dice a cosa serve in una riga
        - i comandi senza la barra iniziale, uno per elemento dell'array
        - per far passare del tempo usa la riga "${Macros.WAIT} 30" (30 secondi), che non e' un comando di Minecraft
        - dove serve un nome di giocatore scrivi <giocatore>: verra' chiesto a chi lancia la macro.
          Allo stesso modo <x> <y> <z> per le coordinate e <messaggio> per un testo
        - avvisa in chat con "say" prima delle cose che disturbano chi sta giocando
        - non usare mai: stop, restart, op, deop, ban, kick, whitelist, reload
        - al massimo 15 comandi
    """.trimIndent()

    /**
     * Legge la risposta e scarta quello che non passa il setaccio. Se non resta
     * niente e' un errore: una macro vuota non si salva.
     */
    fun parse(json: String): GeneratedMacro {
        val o = runCatching { JSONObject(estraiJson(json)) }
            .getOrElse { throw MacroAiException("La risposta non e' leggibile come macro.") }

        val array = o.optJSONArray("comandi") ?: JSONArray()
        val tutti = (0 until array.length())
            .mapNotNull { array.optString(it).trim().removePrefix("/").takeIf { c -> c.isNotBlank() } }

        val buoni = tutti.filter { safe(it) }
        val scartati = tutti.filterNot { safe(it) }
        if (buoni.isEmpty()) {
            throw MacroAiException(
                "Nessun comando utilizzabile nella risposta." +
                        if (scartati.isEmpty()) "" else "\n\nScartati:\n" + scartati.joinToString("\n") { "· $it" }
            )
        }

        return GeneratedMacro(
            macro = Macro(
                id = "",
                name = o.optString("nome").trim().ifBlank { "Macro generata" }.take(60),
                description = o.optString("descrizione").trim().take(200),
                commands = buoni.take(15),
                builtin = false,
                // Nessuno l'ha riletta prima di te: la conferma prima di lanciarla
                // dice che questa cambia le cose.
                risky = true
            ),
            rejected = scartati
        )
    }

    /** Alcuni modelli incorniciano il JSON con ```json ... ```: si toglie la cornice. */
    private fun estraiJson(raw: String): String {
        val t = raw.trim()
        val dentro = t.substringAfter("```json", "").substringAfter("```", t)
        val testo = if (dentro.isBlank()) t else dentro.substringBefore("```")
        val inizio = testo.indexOf('{')
        val fine = testo.lastIndexOf('}')
        return if (inizio >= 0 && fine > inizio) testo.substring(inizio, fine + 1) else testo
    }

    // ------------------------------------------------------------------ rete

    suspend fun generate(
        provider: AiProvider,
        key: String,
        request: String,
        minecraft: String,
        loader: String
    ): GeneratedMacro = withContext(Dispatchers.IO) {
        if (key.isBlank()) throw MacroAiException("Manca la chiave del servizio.")
        if (request.isBlank()) throw MacroAiException("Scrivi cosa deve fare la macro.")

        val testo = prompt(request, minecraft, loader)
        val risposta = when (provider) {
            AiProvider.GOOGLE -> google(key, testo)
            AiProvider.GROQ -> groq(key, testo)
        }
        parse(risposta)
    }

    private fun google(key: String, prompt: String): String {
        val corpo = JSONObject()
            .put("contents", JSONArray().put(
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))
            ))
            .put("generationConfig", JSONObject().put("responseMimeType", "application/json"))
            .toString()

        // La chiave viaggia nell'intestazione e non nell'indirizzo: negli indirizzi
        // finisce nei log di chiunque stia in mezzo.
        val risposta = post(
            url = "https://generativelanguage.googleapis.com/v1beta/models/${AiProvider.GOOGLE.model}:generateContent",
            body = corpo,
            headers = mapOf("x-goog-api-key" to key)
        )
        return JSONObject(risposta)
            .optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)
            ?.optString("text").orEmpty()
            .ifBlank { throw MacroAiException("Il servizio ha risposto senza contenuto.") }
    }

    private fun groq(key: String, prompt: String): String {
        val corpo = JSONObject()
            .put("model", AiProvider.GROQ.model)
            .put("messages", JSONArray().put(
                JSONObject().put("role", "user").put("content", prompt)
            ))
            .put("response_format", JSONObject().put("type", "json_object"))
            .toString()

        val risposta = post(
            url = "https://api.groq.com/openai/v1/chat/completions",
            body = corpo,
            headers = mapOf("Authorization" to "Bearer $key")
        )
        return JSONObject(risposta)
            .optJSONArray("choices")?.optJSONObject(0)
            ?.optJSONObject("message")?.optString("content").orEmpty()
            .ifBlank { throw MacroAiException("Il servizio ha risposto senza contenuto.") }
    }

    /**
     * Traduce l'errore del servizio in una frase utile.
     *
     * Una chiave sbagliata non risponde sempre 401: Google risponde 400 e spiega
     * il motivo nel corpo. Senza guardarci dentro, a chi usa l'app arriverebbe una
     * pagina di JSON al posto di "controlla la chiave".
     */
    fun spiega(code: Int, body: String): String {
        val b = body.lowercase()
        val chiaveStorta = b.contains("api key not valid") || b.contains("api_key_invalid") ||
                b.contains("invalid api key") || b.contains("invalid_api_key") ||
                b.contains("unauthorized") || code == 401 || code == 403
        if (chiaveStorta) {
            return "Chiave rifiutata dal servizio: controlla di averla copiata tutta, " +
                    "e che sia quella del servizio scelto."
        }
        if (b.contains("quota") || b.contains("rate limit")) {
            return "Hai finito le richieste gratuite per ora: riprova più tardi."
        }
        if (b.contains("model") && b.contains("not found")) {
            return "Il servizio non ha più il modello che l'app chiede: serve un aggiornamento dell'app."
        }
        return "Il servizio ha risposto $code.\n${body.take(300)}"
    }

    private fun post(url: String, body: String, headers: Map<String, String>): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code == 429) {
                throw MacroAiException("Hai finito le richieste gratuite per ora: riprova più tardi.")
            }
            if (code !in 200..299) {
                val dettaglio = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw MacroAiException(spiega(code, dettaglio))
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } catch (e: MacroAiException) {
            throw e
        } catch (e: Exception) {
            throw MacroAiException("Servizio non raggiungibile: ${e.message}", e)
        } finally {
            connection.disconnect()
        }
    }
}
