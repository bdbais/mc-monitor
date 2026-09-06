package com.bellizia.mcmonitor.ui.svago

/**
 * I due campi disegnati a mano.
 *
 * Sono pochi di proposito: due bastano a far capire di cosa si tratta, e la
 * macchina che ne genera a migliaia sta da un'altra parte. Qui non serve —
 * disegnarne due a mano costa venti righe, generarli costa un risolutore.
 *
 * Il primo insegna che scavare si può e che costa; il secondo che sul ghiaccio
 * non si sceglie dove arrivare ma solo da che parte partire.
 */
object Livelli {

    /**
     * Legge un campo da un disegno.
     *
     * Sta qui e non nelle prove perché il disegno è la forma in cui un campo si
     * legge davvero: contare le coordinate a mano è il modo più rapido per
     * scrivere un livello impossibile senza accorgersene.
     *
     * ```
     * .  vuoto        _  ghiaccio      U  uscita       @  giocatore
     * #  ossidiana    t  terra         p  pietra       f  ferro
     * g  ghiaia       1..4  un piccone per terra
     * ```
     * La lettera **maiuscola** di un blocco vuol dire che sotto c'è ghiaccio:
     * `T` è terra su ghiaccio.
     */
    fun disegna(vararg righe: String, inMano: Piccone? = null): Stato {
        val larghezza = righe.first().length
        val suolo = ArrayList<Suolo>(larghezza * righe.size)
        val blocchi = HashMap<Punto, Blocco>()
        val picconi = HashMap<Punto, Piccone>()
        var giocatore = Punto(0, 0)

        righe.forEachIndexed { y, riga ->
            require(riga.length == larghezza) { "la riga $y è lunga diversamente dalle altre" }
            riga.forEachIndexed { x, c ->
                val p = Punto(x, y)
                suolo += when {
                    c == '_' -> Suolo.GHIACCIO
                    c == 'U' -> Suolo.USCITA
                    c.isUpperCase() && c != 'U' -> Suolo.GHIACCIO
                    else -> Suolo.NORMALE
                }
                when (c.lowercaseChar()) {
                    '#' -> blocchi[p] = Blocco.OSSIDIANA
                    't' -> blocchi[p] = Blocco.TERRA
                    'p' -> blocchi[p] = Blocco.PIETRA
                    'f' -> blocchi[p] = Blocco.FERRO
                    'd' -> blocchi[p] = Blocco.DIAMANTE
                    'g' -> blocchi[p] = Blocco.GHIAIA
                    '@' -> giocatore = p
                    '1' -> picconi[p] = Piccone.LEGNO
                    '2' -> picconi[p] = Piccone.PIETRA
                    '3' -> picconi[p] = Piccone.FERRO
                    '4' -> picconi[p] = Piccone.DIAMANTE
                }
            }
        }
        return Stato(
            larghezza = larghezza,
            altezza = righe.size,
            suolo = suolo,
            blocchi = blocchi,
            picconi = picconi,
            giocatore = giocatore,
            inMano = inMano?.let { InMano(it, it.durabilita) },
        )
    }

    data class Campo(
        val titolo: String,
        val suggerimento: String,
        val crea: () -> Stato,
    )

    val TUTTI = listOf(
        /*
         * Uno. La terra si toglie a mani nude, la pietra no: il piccone sta
         * dall'altra parte del passaggio, quindi prima si scava e poi lo si va
         * a prendere. Nessuna mossa può rovinare niente -- il primo livello non
         * deve poter finire in una situazione senza uscita.
         */
        Campo(
            titolo = "Pianura",
            suggerimento = "La terra si toglie a mani nude. Per la pietra serve altro.",
        ) {
            disegna(
                "##########",
                "#@.t.....#",
                "####.#####",
                "#..1.p..U#",
                "##########",
            )
        },

        /*
         * Due. Sul ghiaccio non ci si ferma dove si vuole: ci si ferma dove
         * qualcosa ti ferma. Il blocco di terra in mezzo al corridoio e' il
         * primo appiglio, ed e' anche il modo di scoprire che si puo' scavare
         * stando sul ghiaccio.
         */
        Campo(
            titolo = "Ghiacciaio",
            suggerimento = "Sul ghiaccio non ti fermi finché non sbatti.",
        ) {
            disegna(
                "##########",
                "#@.......#",
                "#___T____#",
                "#######U.#",
                "##########",
            )
        },
    )
}
