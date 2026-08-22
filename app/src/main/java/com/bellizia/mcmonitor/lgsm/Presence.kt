package com.bellizia.mcmonitor.lgsm

import org.json.JSONObject

/** Un amministratore visto sul computer, con quando si è fatto vivo l'ultima volta. */
data class Admin(
    val id: String,
    val name: String,
    /** Secondi trascorsi dall'ultimo segnale, calcolati sull'orologio del server. */
    val secondsAgo: Long,
    /** Cosa sta facendo adesso: vuoto se non sta facendo niente di delicato. */
    val doing: String
) {
    /** Attivo: si è fatto vivo entro il doppio dell'intervallo di battito. */
    val active: Boolean get() = secondsAgo <= Presence.ACTIVE_SECONDS

    val busy: Boolean get() = doing.isNotBlank()

    val whenLabel: String
        get() = when {
            secondsAgo < 60 -> "adesso"
            secondsAgo < 3600 -> "${secondsAgo / 60} min fa"
            secondsAgo < 86_400 -> "${secondsAgo / 3600} ore fa"
            else -> "${secondsAgo / 86_400} giorni fa"
        }
}

/** Un messaggio della chat fra amministratori. */
data class AdminMessage(
    val id: String,
    val name: String,
    val text: String,
    val epochSeconds: Long,
    /** Nome del giocatore a cui il messaggio si riferisce, se e' una nota. */
    val about: String = "",
    /** Segnato "a tutti": chi lo riceve viene avvisato, non lo legge per caso. */
    val broadcast: Boolean = false
) {
    val isNote: Boolean get() = about.isNotBlank()
}

/**
 * Chi altro sta amministrando questo server, e la chat per mettersi d'accordo.
 *
 * Non c'è nessun servizio in mezzo: l'unica cosa che tutti i telefoni vedono è il
 * computer del server, quindi la presenza è un file per telefono dentro
 * `~/.mcmonitor/presence` e la chat è un file di righe in fondo al quale si
 * aggiunge. L'orologio è sempre quello del server: i telefoni possono averne uno
 * sballato, e un messaggio "arrivato fra due ore" confonderebbe e basta.
 *
 * Serve soprattutto a non pestarsi i piedi: mentre qualcuno riavvia, gli altri lo
 * vedono scritto prima di premere lo stesso pulsante.
 */
object Presence {

    /** Cartella dei segnali, sotto la home dell'utente SSH. */
    private const val DIR = "\"\$HOME\"/.mcmonitor"

    /** Oltre questo tempo un segnale è vecchio e l'amministratore non c'è più. */
    const val ACTIVE_SECONDS = 150L

    /** Ogni quanto rifarsi vivi: il doppio sta sotto la soglia di cui sopra. */
    const val HEARTBEAT_SECONDS = 60L

    private val SAFE = Regex("^[A-Za-z0-9 _.-]{1,20}$")

    /**
     * Il nome finisce in una riga di shell e in un JSON: si accettano solo
     * lettere, cifre e pochi segni, e in caso contrario si ripiega su "admin".
     */
    fun safeName(name: String): String {
        val pulito = name.trim()
            .replace(Regex("[^A-Za-z0-9 _.-]"), "")
            .replace(Regex(" +"), " ")
            .take(20)
            .trim()
        return if (pulito.isNotBlank() && SAFE.matches(pulito)) pulito else "admin"
    }

    fun safeId(id: String): String =
        id.filter { it.isLetterOrDigit() }.take(16).ifBlank { "sconosciuto" }

    /**
     * Segnala che questo telefono c'è. Il timestamp lo mette il server con `date`:
     * così tutti i confronti avvengono su un solo orologio.
     */
    fun heartbeat(id: String, name: String, doing: String = ""): String {
        val i = safeId(id)
        val n = safeName(name)
        val d = if (doing.isBlank()) "" else safeName(doing)
        return "d=$DIR/presence; mkdir -p \"\$d\" && " +
                "printf '{\"id\":\"%s\",\"nome\":\"%s\",\"ts\":%s,\"azione\":\"%s\"}\\n' " +
                "'$i' '$n' \"\$(date +%s)\" '$d' > \"\$d/$i.json\" && echo ok"
    }

    /** Toglie il proprio segnale: usato quando si chiude l'app o si cambia server. */
    fun goodbye(id: String): String =
        "rm -f $DIR/presence/${safeId(id)}.json 2>/dev/null; echo ok"

    /**
     * Elenco dei segnali più l'ora del server. I file più vecchi di un giorno
     * vengono buttati: sono telefoni che non torneranno.
     */
    fun list(): String = """
        d=$DIR/presence
        mkdir -p "${'$'}d"
        find "${'$'}d" -type f -name '*.json' -mtime +1 -delete 2>/dev/null
        echo "ora=${'$'}(date +%s)"
        for f in "${'$'}d"/*.json; do [ -f "${'$'}f" ] || continue; cat "${'$'}f"; done
    """.trimIndent()

    fun parseAdmins(raw: String): List<Admin> {
        val text = Lgsm.clean(raw)
        val now = Regex("(?m)^ora=(\\d+)$").find(text)?.groupValues?.get(1)?.toLongOrNull()
            ?: return emptyList()
        return text.lines()
            .map { it.trim() }
            .filter { it.startsWith("{") && it.endsWith("}") }
            .mapNotNull { riga ->
                runCatching {
                    val o = JSONObject(riga)
                    Admin(
                        id = o.optString("id"),
                        name = o.optString("nome").ifBlank { "admin" },
                        secondsAgo = (now - o.optLong("ts")).coerceAtLeast(0),
                        doing = o.optString("azione")
                    )
                }.getOrNull()
            }
            .filter { it.id.isNotBlank() }
            .distinctBy { it.id }
            .sortedBy { it.secondsAgo }
    }

    // ------------------------------------------------------------------- chat

    /** Massimo di righe rilette: la chat serve a coordinarsi, non a fare archivio. */
    private const val TAIL = 200

    /**
     * Un messaggio per riga, aggiunto in fondo. L'append di una riga corta è
     * atomico abbastanza da reggere due telefoni che scrivono insieme.
     */
    fun send(
        id: String,
        name: String,
        text: String,
        about: String = "",
        broadcast: Boolean = false
    ): String {
        val i = safeId(id)
        val n = safeName(name)
        val a = about.filter { it.isLetterOrDigit() || it == '_' }.take(16)
        // Il testo entra in un JSON scritto da printf: virgolette e barre
        // rovescerebbero la riga, e a capo la spezzerebbero in due messaggi.
        val pulito = text.trim().take(500)
            .replace(Regex("""[\r\n\t]"""), " ")
            .replace("\\", "/")
            .replace("\"", "'")
        return "d=$DIR; mkdir -p \"\$d\" && " +
                "printf '{\"id\":\"%s\",\"nome\":\"%s\",\"ts\":%s,\"testo\":\"%s\",\"su\":\"%s\",\"tutti\":%s}\\n' " +
                "'$i' '$n' \"\$(date +%s)\" ${Lgsm.sq(pulito)} '$a' '${if (broadcast) "true" else "false"}' " +
                ">> \"\$d/chat.log\" && echo ok"
    }

    fun read(): String = """
        f=$DIR/chat.log
        echo "ora=${'$'}(date +%s)"
        [ -f "${'$'}f" ] && tail -n $TAIL "${'$'}f"
        exit 0
    """.trimIndent()

    fun parseMessages(raw: String): List<AdminMessage> {
        val text = Lgsm.clean(raw)
        return text.lines()
            .map { it.trim() }
            .filter { it.startsWith("{") && it.endsWith("}") }
            .mapNotNull { riga ->
                runCatching {
                    val o = JSONObject(riga)
                    val testo = o.optString("testo")
                    if (testo.isBlank()) return@runCatching null
                    AdminMessage(
                        id = o.optString("id"),
                        name = o.optString("nome").ifBlank { "admin" },
                        text = testo,
                        epochSeconds = o.optLong("ts"),
                        about = o.optString("su"),
                        broadcast = o.optString("tutti") == "true" || o.optBoolean("tutti")
                    )
                }.getOrNull()
            }
    }

    /**
     * I messaggi non ancora letti da questo telefono: i propri non contano, e
     * nemmeno quelli piu' vecchi dell'ultima volta che la chat e' stata aperta.
     */
    fun unread(messages: List<AdminMessage>, myId: String, lastRead: Long): List<AdminMessage> =
        messages.filter { it.id != safeId(myId) && it.epochSeconds > lastRead }

    /**
     * Riassunto per la barra: quanti altri ci sono e se qualcuno sta facendo
     * qualcosa di delicato. Se stanno solo guardando, basta il numero.
     */
    fun summary(admins: List<Admin>, myId: String): String {
        val altri = admins.filter { it.id != myId && it.active }
        if (altri.isEmpty()) return ""
        val occupato = altri.firstOrNull { it.busy }
        if (occupato != null) return "${occupato.name} sta facendo: ${occupato.doing}"
        return if (altri.size == 1) "Anche ${altri[0].name} è collegato" else "Altri ${altri.size} collegati"
    }
}
