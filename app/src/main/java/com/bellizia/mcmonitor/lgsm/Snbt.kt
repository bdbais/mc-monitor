package com.bellizia.mcmonitor.lgsm

/**
 * NBT in forma di testo, quello che il server risponde a `data get entity`.
 *
 * È la stessa roba che sta nei file del mondo, scritta in lettere invece che in
 * byte. Serve perché per la via del comando non occorre nessun accesso ai file:
 * basta RCON. Così l'inventario di un giocatore si vede anche sui server dove
 * l'app entra solo da lì — e senza chiedere al server di salvare e aspettare.
 *
 * Produce gli stessi [Tag] del lettore binario, quindi tutto quello che sa già
 * leggere un inventario continua a funzionare senza sapere da dove sia arrivato.
 */
object Snbt {

    class SnbtRotto(messaggio: String) : Exception(messaggio)

    /** Un albero troppo profondo è un albero costruito apposta. */
    private const val MAX_PROFONDITA = 64

    /**
     * Il prefisso che il server mette davanti alla risposta:
     * `Baisso has the following entity data: {...}`.
     *
     * Cambia con la lingua del server, quindi non lo si cerca: si va al primo
     * `{` o `[` e si legge da lì.
     */
    fun leggiRisposta(risposta: String): Tag.Gruppo {
        val inizio = risposta.indexOfFirst { it == '{' }
        if (inizio < 0) throw SnbtRotto("nella risposta non c'e' nessun dato")
        return leggi(risposta.substring(inizio))
    }

    /**
     * Il valore di una risposta a percorso singolo: `data get entity <n> Health`.
     *
     * Qui non c'e' nessuna graffa da cui partire, e il prefisso cambia con la
     * lingua del server: non lo si puo' cercare. Ma il valore e' sempre in
     * fondo — il messaggio e' costruito da un modello che finisce col valore —
     * quindi si prende l'ultimo pezzo e si prova a leggerlo.
     *
     * Se la risposta e' un errore («No entity was found») l'ultimo pezzo e' una
     * parola qualsiasi: esce un [Tag.Testo], e chi chiedeva un numero non lo
     * trova. E' voluto: meglio nessun dato che un dato inventato.
     */
    fun valoreDiRisposta(risposta: String): Tag? {
        val strutturale = risposta.indexOfFirst { it == '{' || it == '[' }
        val pezzo = if (strutturale >= 0) {
            risposta.substring(strutturale)
        } else {
            risposta.trim().split(Regex("""\s+""")).lastOrNull()?.takeIf { it.isNotBlank() } ?: return null
        }
        return runCatching {
            val lettore = Lettore(pezzo)
            lettore.salta()
            lettore.valore(0)
        }.getOrNull()
    }

    fun leggi(testo: String): Tag.Gruppo {
        val lettore = Lettore(testo)
        lettore.salta()
        val tag = lettore.valore(0)
        return tag as? Tag.Gruppo ?: throw SnbtRotto("il dato non e' un gruppo")
    }

    private class Lettore(val testo: String) {
        var i = 0

        fun salta() {
            while (i < testo.length && testo[i].isWhitespace()) i++
        }

        fun guarda(): Char =
            if (i < testo.length) testo[i] else throw SnbtRotto("il dato finisce a meta'")

        fun mangia(atteso: Char) {
            salta()
            if (guarda() != atteso) throw SnbtRotto("atteso «$atteso» in posizione $i")
            i++
        }

        fun valore(profondita: Int): Tag {
            if (profondita > MAX_PROFONDITA) throw SnbtRotto("troppi livelli annidati")
            salta()
            return when (guarda()) {
                '{' -> gruppo(profondita)
                '[' -> elenco(profondita)
                '"', '\'' -> Tag.Testo(stringaFraApici())
                else -> semplice()
            }
        }

        fun gruppo(profondita: Int): Tag.Gruppo {
            mangia('{')
            val campi = LinkedHashMap<String, Tag>()
            salta()
            if (guarda() == '}') { i++; return Tag.Gruppo(campi) }
            while (true) {
                salta()
                val chiave = if (guarda() == '"' || guarda() == '\'') stringaFraApici() else nudo()
                mangia(':')
                campi[chiave] = valore(profondita + 1)
                salta()
                when (guarda()) {
                    ',' -> i++
                    '}' -> { i++; return Tag.Gruppo(campi) }
                    else -> throw SnbtRotto("atteso «,» o «}» in posizione $i")
                }
            }
        }

        /**
         * Elenchi e file di numeri.
         *
         * `[B; 1b, 2b]`, `[I; …]` e `[L; …]` sono file di numeri e non elenchi:
         * si riconoscono dalla lettera e dal punto e virgola subito dopo la
         * parentesi. Trattarli come elenchi normali non romperebbe niente qui,
         * ma i tipi tornerebbero diversi da quelli del lettore binario, e a quel
         * punto le due strade smetterebbero di essere intercambiabili.
         */
        fun elenco(profondita: Int): Tag {
            mangia('[')
            salta()
            if (i + 1 < testo.length && testo[i] in "BIL" && testo[i + 1] == ';') {
                val tipo = testo[i]
                i += 2
                val numeri = mutableListOf<Long>()
                salta()
                if (guarda() == ']') { i++ } else {
                    while (true) {
                        salta()
                        numeri += numeroSecco()
                        salta()
                        when (guarda()) {
                            ',' -> i++
                            ']' -> { i++; break }
                            else -> throw SnbtRotto("atteso «,» o «]» in posizione $i")
                        }
                    }
                }
                return if (tipo == 'B') {
                    Tag.Byte(ByteArray(numeri.size) { numeri[it].toByte() })
                } else {
                    Tag.Numeri(numeri.toLongArray())
                }
            }

            val voci = mutableListOf<Tag>()
            salta()
            if (guarda() == ']') { i++; return Tag.Elenco(voci) }
            while (true) {
                voci += valore(profondita + 1)
                salta()
                when (guarda()) {
                    ',' -> i++
                    ']' -> { i++; return Tag.Elenco(voci) }
                    else -> throw SnbtRotto("atteso «,» o «]» in posizione $i")
                }
            }
        }

        fun stringaFraApici(): String {
            val apice = guarda()
            i++
            val out = StringBuilder()
            while (true) {
                if (i >= testo.length) throw SnbtRotto("stringa senza chiusura")
                val c = testo[i]
                when {
                    c == '\\' -> {
                        i++
                        if (i >= testo.length) throw SnbtRotto("stringa senza chiusura")
                        out.append(testo[i]); i++
                    }
                    c == apice -> { i++; return out.toString() }
                    else -> { out.append(c); i++ }
                }
            }
        }

        /**
         * Il nome di un campo, senza apici.
         *
         * Qui i due punti NON fanno parte del nome: sono il separatore che viene
         * subito dopo. Nei valori invece sì — `minecraft:stone` è una parola
         * sola — ed è per questo che i due casi hanno due funzioni diverse.
         */
        fun nudo(): String {
            val inizio = i
            while (i < testo.length && (testo[i].isLetterOrDigit() || testo[i] in "_-.+")) i++
            if (i == inizio) throw SnbtRotto("atteso un nome in posizione $i")
            return testo.substring(inizio, i)
        }

        fun numeroSecco(): Long =
            semplice().let { (it as? Tag.Numero)?.valore ?: throw SnbtRotto("atteso un numero") }

        /**
         * Numeri, booleani e parole.
         *
         * La lettera in fondo dice il tipo — `1b` è un byte, `1L` un long, `1.0f`
         * un float — e va tolta prima di leggere il numero. Un numero senza
         * lettera con la virgola è un double, senza virgola un int.
         */
        fun semplice(): Tag {
            val inizio = i
            while (i < testo.length && (testo[i].isLetterOrDigit() || testo[i] in "_-.+:")) i++
            val grezzo = testo.substring(inizio, i)
            if (grezzo.isEmpty()) throw SnbtRotto("valore vuoto in posizione $i")

            when (grezzo.lowercase()) {
                "true" -> return Tag.Numero(1)
                "false" -> return Tag.Numero(0)
            }

            val ultima = grezzo.last()
            val corpo = if (ultima.isLetter()) grezzo.dropLast(1) else grezzo
            if (corpo.isNotEmpty() && corpo.none { it.isLetter() }) {
                when (ultima.lowercaseChar()) {
                    'b', 's', 'l' -> corpo.toLongOrNull()?.let { return Tag.Numero(it) }
                    'f', 'd' -> corpo.toDoubleOrNull()?.let { return Tag.Decimale(it) }
                }
                if (!ultima.isLetter()) {
                    grezzo.toLongOrNull()?.let { return Tag.Numero(it) }
                    grezzo.toDoubleOrNull()?.let { return Tag.Decimale(it) }
                }
            }
            // Non è un numero: è una parola, e vale come testo — `minecraft:stone`
            // arriva spesso senza apici.
            return Tag.Testo(grezzo)
        }
    }
}
