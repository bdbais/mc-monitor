package com.bellizia.mcmonitor.lgsm

import org.json.JSONArray
import org.json.JSONObject

/**
 * Una sequenza di comandi con un nome.
 *
 * Le cose che si fanno spesso sono quasi sempre più di un comando: mettere il
 * server in manutenzione vuol dire avvisare, aspettare, salvare. A mano si
 * sbaglia l'ordine o si dimentica un pezzo proprio quando c'è fretta.
 */
data class Macro(
    val id: String,
    val name: String,
    val description: String,
    val commands: List<String>,
    val builtin: Boolean = false,
    /** Cambia il mondo o disturba chi sta giocando: prima si chiede conferma. */
    val risky: Boolean = false
) {
    val steps: Int get() = commands.size

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("nome", name)
        put("descrizione", description)
        put("comandi", JSONArray(commands))
        put("delicata", risky)
    }

    companion object {
        fun fromJson(o: JSONObject): Macro? {
            val comandi = o.optJSONArray("comandi") ?: return null
            val lista = (0 until comandi.length())
                .mapNotNull { comandi.optString(it).takeIf { c -> c.isNotBlank() } }
            if (lista.isEmpty()) return null
            return Macro(
                id = o.optString("id").ifBlank { return null },
                name = o.optString("nome").ifBlank { "senza nome" },
                description = o.optString("descrizione"),
                commands = lista,
                builtin = false,
                risky = o.optBoolean("delicata")
            )
        }
    }
}

/**
 * Le macro: quelle già pronte e le regole per scriverne di proprie.
 *
 * Un comando può contenere un buco fra parentesi angolari — `<giocatore>`,
 * `<secondi>` — che viene chiesto al momento di eseguire. `!attendi N` non è un
 * comando di Minecraft: è una pausa, e serve perché "avviso, aspetto, salvo" è
 * la forma di quasi tutte le macro utili.
 */
object Macros {

    const val WAIT = "!attendi"
    const val MAX_WAIT_SECONDS = 600

    private val PLACEHOLDER = Regex("<([a-zà-ù0-9_]{1,20})>")

    /** I buchi da riempire, nell'ordine in cui compaiono e senza ripetizioni. */
    fun placeholders(commands: List<String>): List<String> =
        commands.flatMap { riga -> PLACEHOLDER.findAll(riga).map { it.groupValues[1] }.toList() }
            .distinct()

    /**
     * Il valore finisce dentro un comando di Minecraft che parte per la console:
     * un a capo lo spezzerebbe in due comandi, e il secondo non l'avrebbe scritto
     * nessuno.
     */
    fun cleanValue(value: String): String =
        value.replace(Regex("""[\r\n]+"""), " ").trim().take(100)

    fun fill(commands: List<String>, values: Map<String, String>): List<String> =
        commands.map { riga ->
            PLACEHOLDER.replace(riga) { m -> cleanValue(values[m.groupValues[1]].orEmpty()) }
        }

    /** Se questa riga è una pausa, quanti secondi dura. */
    fun waitSeconds(command: String): Int? {
        val t = command.trim()
        if (!t.startsWith(WAIT)) return null
        val n = t.removePrefix(WAIT).trim().toIntOrNull() ?: return 0
        return n.coerceIn(0, MAX_WAIT_SECONDS)
    }

    /** Una riga vuota o con un a capo non è un comando: le macro le rifiutano. */
    fun isValidCommand(command: String): Boolean {
        val t = command.trim()
        return t.isNotBlank() && t.length <= 250 && !t.contains('\n') && !t.contains('\r')
    }

    fun parseCommands(text: String): List<String> =
        text.lines().map { it.trim() }.filter { it.isNotBlank() }.filter { isValidCommand(it) }

    // ------------------------------------------------------------- catalogo

    /**
     * Le macro già pronte.
     *
     * Metà sono le cose che si fanno di continuo e che a mano si sbagliano; metà
     * servono a far divertire chi gioca, che è il motivo per cui il server esiste.
     */
    val catalogue: List<Macro> = listOf(
        Macro(
            id = "b-manutenzione",
            name = "Manutenzione fra 5 minuti",
            description = "Avvisa chi sta giocando, aspetta, poi salva. Il riavvio lo dai tu dalla scheda Stato.",
            commands = listOf(
                "say Manutenzione fra 5 minuti: mettete al sicuro quello che state facendo",
                "$WAIT 240",
                "say Manutenzione fra 1 minuto",
                "$WAIT 50",
                "say Manutenzione fra 10 secondi",
                "$WAIT 10",
                "save-all flush",
                "say Mondo salvato"
            ),
            builtin = true
        ),
        Macro(
            id = "b-salva",
            name = "Salva adesso",
            description = "Scrive su disco quello che è successo finora. Da fare prima di ogni cosa rischiosa.",
            commands = listOf("save-all flush", "say Mondo salvato"),
            builtin = true
        ),
        Macro(
            id = "b-backup-prima",
            name = "Backup a caldo: prima",
            description = "Ferma la scrittura su disco e salva, così i file non cambiano mentre li copi. Poi fai il backup e lancia \"dopo\".",
            commands = listOf("save-off", "save-all flush"),
            builtin = true
        ),
        Macro(
            id = "b-backup-dopo",
            name = "Backup a caldo: dopo",
            description = "Riaccende la scrittura su disco. Da lanciare appena il backup è finito: senza, il mondo non si salva più.",
            commands = listOf("save-on", "say Backup finito"),
            builtin = true
        ),
        Macro(
            id = "b-pulizia",
            name = "Pulisci gli oggetti a terra",
            description = "Toglie gli oggetti lasciati per terra. È la prima cosa da provare quando il server arranca.",
            commands = listOf(
                "say Fra 10 secondi spariscono gli oggetti a terra: raccogliete quello che vi serve",
                "$WAIT 10",
                "kill @e[type=minecraft:item]"
            ),
            builtin = true,
            risky = true
        ),
        Macro(
            id = "b-giorno",
            name = "Buongiorno",
            description = "Riporta il giorno e il sereno. Utile quando è appena entrato qualcuno che non ha ancora un letto.",
            commands = listOf("time set day", "weather clear"),
            builtin = true
        ),
        Macro(
            id = "b-notte",
            name = "Notte tranquilla",
            description = "Notte, cielo sereno e nessun mostro: per costruire con calma al buio.",
            commands = listOf("time set night", "weather clear", "difficulty peaceful"),
            builtin = true
        ),
        Macro(
            id = "b-tempo-fermo",
            name = "Ferma il tempo",
            description = "Blocca il ciclo giorno-notte e il meteo dove sono adesso.",
            commands = listOf("gamerule doDaylightCycle false", "gamerule doWeatherCycle false"),
            builtin = true
        ),
        Macro(
            id = "b-tempo-riparte",
            name = "Fai ripartire il tempo",
            description = "Rimette in moto giorno, notte e meteo.",
            commands = listOf("gamerule doDaylightCycle true", "gamerule doWeatherCycle true"),
            builtin = true
        ),
        Macro(
            id = "b-porta-chiusa",
            name = "Chiudi il server ai nuovi",
            description = "Accende la whitelist: entra solo chi è già in elenco. Chi sta giocando resta dov'è.",
            commands = listOf("whitelist on", "whitelist reload", "say Da adesso entra solo chi è in whitelist"),
            builtin = true
        ),
        Macro(
            id = "b-tutti-spawn",
            name = "Riporta tutti allo spawn",
            description = "Teletrasporta chi è collegato alle coordinate che indichi. Serve quando qualcuno resta bloccato.",
            commands = listOf(
                "say Vi riporto tutti allo spawn",
                "tp @a <x> <y> <z>"
            ),
            builtin = true,
            risky = true
        ),
        Macro(
            id = "b-kit",
            name = "Kit di sopravvivenza per <giocatore>",
            description = "Pane, torce, un piccone e un'ascia: quanto basta per ricominciare dopo un guaio.",
            commands = listOf(
                "give <giocatore> minecraft:bread 16",
                "give <giocatore> minecraft:torch 32",
                "give <giocatore> minecraft:iron_pickaxe 1",
                "give <giocatore> minecraft:iron_axe 1",
                "tell <giocatore> Ti ho dato un kit per ripartire"
            ),
            builtin = true
        ),
        Macro(
            id = "b-cura",
            name = "Rimetti in piedi <giocatore>",
            description = "Rigenerazione, sazietà e qualche cuore in più: per chi è appena scampato a qualcosa.",
            commands = listOf(
                "effect give <giocatore> minecraft:regeneration 30 2",
                "effect give <giocatore> minecraft:saturation 10 3",
                "effect give <giocatore> minecraft:absorption 60 2",
                "tell <giocatore> Rimessa a nuovo"
            ),
            builtin = true
        ),
        Macro(
            id = "b-notte-caccia",
            name = "Notte di caccia",
            description = "Mezzanotte, difficoltà difficile e mostri svegli: una serata da sopravvivere.",
            commands = listOf(
                "say Stanotte si caccia: tenetevi vicini",
                "time set midnight",
                "difficulty hard",
                "gamerule doInsomnia true"
            ),
            builtin = true,
            risky = true
        ),
        Macro(
            id = "b-fuochi",
            name = "Fuochi d'artificio su <giocatore>",
            description = "Cinque razzi sopra la testa di qualcuno. Per i compleanni e per le case appena finite.",
            commands = listOf(
                "execute at <giocatore> run summon minecraft:firework_rocket ~ ~2 ~",
                "execute at <giocatore> run summon minecraft:firework_rocket ~1 ~3 ~1",
                "execute at <giocatore> run summon minecraft:firework_rocket ~-1 ~3 ~-1",
                "$WAIT 2",
                "execute at <giocatore> run summon minecraft:firework_rocket ~ ~4 ~",
                "execute at <giocatore> run summon minecraft:firework_rocket ~2 ~3 ~-2"
            ),
            builtin = true
        ),
        Macro(
            id = "b-polli",
            name = "Pioggia di polli su <giocatore>",
            description = "Dieci polli dal cielo. Non fa male a nessuno, ma non si dimentica.",
            commands = List(10) { "execute at <giocatore> run summon minecraft:chicken ~ ~12 ~" },
            builtin = true,
            risky = true
        ),
        Macro(
            id = "b-invisibile",
            name = "Rendi invisibile <giocatore>",
            description = "Un minuto di invisibilità. Per gli scherzi e per le cacce al tesoro.",
            commands = listOf(
                "effect give <giocatore> minecraft:invisibility 60 1 true",
                "tell <giocatore> Per un minuto non ti vede nessuno"
            ),
            builtin = true
        ),
        Macro(
            id = "b-salto",
            name = "Super salto per <giocatore>",
            description = "Un minuto di salti impossibili, con la caduta attutita.",
            commands = listOf(
                "effect give <giocatore> minecraft:jump_boost 60 4",
                "effect give <giocatore> minecraft:slow_falling 60 1",
                "tell <giocatore> Prova a saltare"
            ),
            builtin = true
        ),
        Macro(
            id = "b-temporale",
            name = "Scatena un temporale",
            description = "Pioggia e tuoni per un po'. I mostri escono anche di giorno: avvisa prima.",
            commands = listOf("say Si mette a piovere", "weather thunder 600"),
            builtin = true,
            risky = true
        ),
        Macro(
            id = "b-benvenuto",
            name = "Dai il benvenuto a <giocatore>",
            description = "Un saluto, un kit e il punto di rinascita dove sei tu. Da lanciare quando entra qualcuno di nuovo.",
            commands = listOf(
                "say Benvenuto <giocatore>!",
                "give <giocatore> minecraft:bread 8",
                "give <giocatore> minecraft:oak_log 16",
                "tell <giocatore> Se ti serve qualcosa scrivi in chat"
            ),
            builtin = true
        )
    )

    fun byId(id: String, custom: List<Macro>): Macro? =
        catalogue.firstOrNull { it.id == id } ?: custom.firstOrNull { it.id == id }
}
