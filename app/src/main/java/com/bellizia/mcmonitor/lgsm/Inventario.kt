package com.bellizia.mcmonitor.lgsm

import com.bellizia.mcmonitor.data.ServerConfig

/** Un oggetto in una casella. */
data class Oggetto(
    /** Il numero della casella, com'è scritto nel file. */
    val casella: Int,
    /** L'identificativo completo, per esempio `minecraft:diamond_pickaxe`. */
    val id: String,
    val quantita: Int,
    /** Il nome che il giocatore gli ha dato con l'incudine, se c'è. */
    val nomeDato: String? = null,
    /** Quanto è consumato, da 0 (nuovo) a 1 (sta per rompersi). Null se non si usura. */
    val usura: Double? = null,
    val incantato: Boolean = false
) {
    /** `minecraft:diamond_pickaxe` → `diamond pickaxe`. */
    val nomeCorto: String
        get() = id.substringAfter(':').replace('_', ' ')

    /** Quello da mostrare nella casella: il nome scelto dal giocatore ha la precedenza. */
    val etichetta: String get() = nomeDato ?: nomeCorto
}

/** Dove sta ogni cosa addosso al giocatore. */
data class Equipaggiamento(
    val testa: Oggetto? = null,
    val petto: Oggetto? = null,
    val gambe: Oggetto? = null,
    val piedi: Oggetto? = null,
    val manoSecondaria: Oggetto? = null
) {
    val vuoto: Boolean
        get() = testa == null && petto == null && gambe == null &&
                piedi == null && manoSecondaria == null
}

/** Com'è messo un giocatore, letto dal suo file. */
data class Giocatore(
    val equipaggiamento: Equipaggiamento,
    /** Le nove caselle in basso, dalla prima all'ultima. Le vuote sono null. */
    val cintura: List<Oggetto?>,
    /** Le tre file dello zaino, 27 caselle. Le vuote sono null. */
    val zaino: List<Oggetto?>,
    val bauleDellEnd: List<Oggetto?>,
    /** Quale casella della cintura ha in mano, da 0 a 8. */
    val inMano: Int,
    val vita: Double?,
    val fame: Int?,
    val livelli: Int?,
    val dimensione: String?,
    val posizione: Triple<Double, Double, Double>?
) {
    val cuori: String? get() = vita?.let { "%.1f".format(it / 2.0) }

    val oggetti: List<Oggetto>
        get() = (cintura + zaino + bauleDellEnd + listOfNotNull(
            equipaggiamento.testa, equipaggiamento.petto, equipaggiamento.gambe,
            equipaggiamento.piedi, equipaggiamento.manoSecondaria
        )).filterNotNull()
}

/**
 * Cosa c'è nell'inventario di un giocatore.
 *
 * Si legge da `world/playerdata/<uuid>.dat`, che Minecraft riscrive quando il
 * giocatore esce e a ogni salvataggio del mondo. Per un giocatore collegato in
 * quel momento il file è quindi **vecchio**: prima di leggerlo conviene chiedere
 * al server di salvare, e se non si può, dirlo invece di mostrare l'inventario
 * di ieri come se fosse di adesso.
 *
 * I numeri delle caselle non sono un'invenzione nostra, sono quelli di
 * Minecraft: 0-8 la cintura, 9-35 lo zaino, 100-103 l'armatura dal basso verso
 * l'alto, -106 la mano secondaria. Restano stabili da anni ed è per questo che
 * si possono scrivere qui.
 */
object Inventario {

    private const val PIEDI = 100
    private const val GAMBE = 101
    private const val PETTO = 102
    private const val TESTA = 103
    private const val MANO_SECONDARIA = -106

    fun leggi(radice: Tag.Gruppo): Giocatore {
        val tutti = oggettiDi(radice.elenco("Inventory"))
        val perCasella = tutti.associateBy { it.casella }

        return Giocatore(
            equipaggiamento = Equipaggiamento(
                testa = perCasella[TESTA],
                petto = perCasella[PETTO],
                gambe = perCasella[GAMBE],
                piedi = perCasella[PIEDI],
                manoSecondaria = perCasella[MANO_SECONDARIA]
            ),
            cintura = (0..8).map { perCasella[it] },
            zaino = (9..35).map { perCasella[it] },
            bauleDellEnd = oggettiDi(radice.elenco("EnderItems")).let { fine ->
                val m = fine.associateBy { it.casella }
                (0..26).map { m[it] }
            },
            inMano = (radice.numero("SelectedItemSlot") ?: 0L).toInt().coerceIn(0, 8),
            vita = radice.decimale("Health"),
            fame = radice.numero("foodLevel")?.toInt(),
            livelli = radice.numero("XpLevel")?.toInt(),
            dimensione = dimensione(radice),
            posizione = radice.elenco("Pos")
                .mapNotNull { (it as? Tag.Decimale)?.valore }
                .takeIf { it.size == 3 }
                ?.let { Triple(it[0], it[1], it[2]) }
        )
    }

    /**
     * Fino alla 1.16 la dimensione era un numero, dopo è una stringa.
     * Un server vecchio non deve far comparire "null" a schermo.
     */
    private fun dimensione(radice: Tag.Gruppo): String? {
        radice.testo("Dimension")?.let { return leggibile(it) }
        return when (radice.numero("Dimension")?.toInt()) {
            0 -> "Mondo normale"
            -1 -> "Nether"
            1 -> "End"
            else -> null
        }
    }

    private fun leggibile(id: String): String = when (id.substringAfter(':')) {
        "overworld" -> "Mondo normale"
        "the_nether" -> "Nether"
        "the_end" -> "End"
        else -> id.substringAfter(':').replace('_', ' ')
    }

    private fun oggettiDi(voci: List<Tag>): List<Oggetto> =
        voci.mapNotNull { (it as? Tag.Gruppo)?.let(::oggetto) }

    /**
     * Un oggetto, in tutti i modi in cui Minecraft l'ha scritto negli anni.
     *
     * Dalla 1.20.5 `Count` è diventato `count` e `tag` è diventato
     * `components`, con nomi di campo nuovi. Le due forme convivono nei mondi
     * veri, perché un mondo aggiornato conserva i file dei giocatori che non
     * sono più entrati.
     */
    private fun oggetto(g: Tag.Gruppo): Oggetto? {
        val id = g.testo("id")?.takeIf { it.isNotBlank() } ?: return null
        // "minecraft:air" occupa una casella nel file ma per chi guarda è vuota.
        if (id.substringAfter(':') == "air") return null
        val casella = (g.numero("Slot") ?: g.numero("slot"))?.toInt() ?: return null
        val quanti = (g.numero("Count") ?: g.numero("count") ?: 1L).toInt()

        val vecchio = g.gruppo("tag")
        val nuovo = g.gruppo("components")

        return Oggetto(
            casella = casella,
            id = id,
            quantita = quanti.coerceAtLeast(1),
            nomeDato = nomeDato(vecchio, nuovo),
            usura = usura(id, vecchio, nuovo),
            incantato = incantato(vecchio, nuovo)
        )
    }

    /**
     * Il nome dato con l'incudine.
     *
     * È JSON dentro NBT — dalla 1.13 il nome è un componente di testo — e qui
     * non si apre un parser JSON per una riga: si prende il testo fra virgolette
     * dopo "text", e se non c'è si lascia stare. Un nome mostrato male è meglio
     * di una schermata che non si apre.
     */
    private fun nomeDato(vecchio: Tag.Gruppo?, nuovo: Tag.Gruppo?): String? {
        val grezzo = vecchio?.gruppo("display")?.testo("Name")
            ?: nuovo?.testo("minecraft:custom_name")
            ?: return null
        val fraVirgolette = Regex("\"text\"\\s*:\\s*\"([^\"]*)\"").find(grezzo)?.groupValues?.get(1)
        return (fraVirgolette ?: grezzo.trim('"', ' ')).takeIf { it.isNotBlank() }
    }

    // ----------------------------------------- prendere il file dal server

    /** Codice di uscita convenzionale: quel giocatore non ha un file. */
    const val EXIT_NIENTE_FILE = 83

    private val UUID_VALIDO = Regex("^[0-9a-fA-F-]{32,36}$")

    /**
     * Chi e' chi, secondo il server.
     *
     * `usercache.json` tiene la corrispondenza fra nome e identificativo per
     * chiunque sia entrato almeno una volta. E' l'unica fonte che funziona anche
     * a server spento: la whitelist ce l'ha solo per chi e' ammesso, e chiedere
     * a Mojang vorrebbe dire mandare fuori il nome di una persona.
     */
    fun comandoUtenti(cfg: ServerConfig): String {
        val f = Lgsm.path("${cfg.serverFiles.trimEnd('/')}/usercache.json")
        return "f=$f; [ -f \"\$f\" ] || { echo '(niente usercache)'; exit 0; }; cat \"\$f\""
    }

    /** L'identificativo di un giocatore, cercato senza badare alle maiuscole. */
    fun uuidDi(usercache: String, nome: String): String? {
        val testo = Lgsm.clean(usercache)
        // Il file e' un elenco di {"name":"Pippo","uuid":"...","expiresOn":...}.
        // Si va di espressione regolare e non di parser: e' una riga sola, e un
        // file mezzo scritto non deve impedire di guardare un inventario.
        val re = Regex("""\{[^{}]*\}""")
        return re.findAll(testo).mapNotNull { blocco ->
            val t = blocco.value
            val n = Regex(""""name"\s*:\s*"([^"]+)"""").find(t)?.groupValues?.get(1)
            val u = Regex(""""uuid"\s*:\s*"([^"]+)"""").find(t)?.groupValues?.get(1)
            if (n != null && u != null && n.equals(nome, ignoreCase = true)) u else null
        }.firstOrNull()?.takeIf { UUID_VALIDO.matches(it) }
    }

    /**
     * Il file del giocatore, in base64.
     *
     * In base64 perche' e' NBT compresso: sono byte, e farli passare per un
     * canale che li tratta come testo li storpierebbe.
     */
    fun comandoDati(cfg: ServerConfig, mondo: String, uuid: String): String {
        require(UUID_VALIDO.matches(uuid)) { "identificativo non valido" }
        val cartella = mondo.filter { it.isLetterOrDigit() || it in "-_. " }.ifBlank { "world" }
        val f = Lgsm.path("${cfg.serverFiles.trimEnd('/')}/$cartella/playerdata/$uuid.dat")
        return "f=$f; [ -f \"\$f\" ] || { echo 'NIENTE FILE'; exit $EXIT_NIENTE_FILE; }; " +
                "echo '@@INIZIO'; base64 <\"\$f\"; echo '@@FINE'"
    }

    /** I byte del file, dal base64 fra marcatori. */
    fun byteDi(raw: String): ByteArray? {
        val b64 = Lgsm.clean(raw)
            .substringAfter("@@INIZIO", "")
            .substringBefore("@@FINE", "")
            .filter { !it.isWhitespace() }
        if (b64.isEmpty()) return null
        return runCatching { java.util.Base64.getDecoder().decode(b64) }.getOrNull()
    }

    /** Quanto è consumato: serve la resistenza massima, che dipende dall'oggetto. */
    private fun usura(id: String, vecchio: Tag.Gruppo?, nuovo: Tag.Gruppo?): Double? {
        val danno = vecchio?.numero("Damage")
            ?: nuovo?.numero("minecraft:damage")
            ?: return null
        val massimo = RESISTENZA[id.substringAfter(':')] ?: return null
        if (massimo <= 0) return null
        return (danno.toDouble() / massimo).coerceIn(0.0, 1.0)
    }

    private fun incantato(vecchio: Tag.Gruppo?, nuovo: Tag.Gruppo?): Boolean {
        if (vecchio?.campi?.containsKey("Enchantments") == true) return true
        if (vecchio?.campi?.containsKey("StoredEnchantments") == true) return true
        return nuovo?.campi?.keys?.any { it.contains("enchantment") } == true
    }

    /**
     * Quanti colpi regge un attrezzo prima di rompersi.
     *
     * Solo i materiali, non ogni singolo oggetto: la resistenza in Minecraft
     * dipende dal materiale, e tenere qui l'elenco completo vorrebbe dire
     * riscriverlo a ogni versione del gioco. Quello che non è in elenco non
     * mostra la barra, e va bene: meglio niente che un numero inventato.
     */
    private val RESISTENZA: Map<String, Int> = buildMap {
        fun set(materiale: String, durata: Int, vararg attrezzi: String) {
            attrezzi.forEach { put("${materiale}_$it", durata) }
        }
        val attrezzi = arrayOf("sword", "pickaxe", "axe", "shovel", "hoe")
        set("wooden", 59, *attrezzi)
        set("stone", 131, *attrezzi)
        set("iron", 250, *attrezzi)
        set("golden", 32, *attrezzi)
        set("diamond", 1561, *attrezzi)
        set("netherite", 2031, *attrezzi)

        put("leather_helmet", 55); put("leather_chestplate", 80)
        put("leather_leggings", 75); put("leather_boots", 65)
        put("golden_helmet", 77); put("golden_chestplate", 112)
        put("golden_leggings", 105); put("golden_boots", 91)
        put("chainmail_helmet", 165); put("chainmail_chestplate", 240)
        put("chainmail_leggings", 225); put("chainmail_boots", 195)
        put("iron_helmet", 165); put("iron_chestplate", 240)
        put("iron_leggings", 225); put("iron_boots", 195)
        put("diamond_helmet", 363); put("diamond_chestplate", 528)
        put("diamond_leggings", 495); put("diamond_boots", 429)
        put("netherite_helmet", 407); put("netherite_chestplate", 592)
        put("netherite_leggings", 555); put("netherite_boots", 481)
        put("bow", 384); put("crossbow", 465); put("trident", 250)
        put("shield", 336); put("elytra", 432); put("flint_and_steel", 64)
        put("fishing_rod", 64); put("shears", 238)
    }
}
