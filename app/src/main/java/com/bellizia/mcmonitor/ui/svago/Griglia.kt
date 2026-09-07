package com.bellizia.mcmonitor.ui.svago

/**
 * Il campo di gioco e le regole del movimento.
 *
 * È una griglia a turni: una mossa tua, una mossa di tutto il resto. Niente
 * accade fuori dai turni, e da questo dipende tutto — un livello si può
 * ragionare invece che subire, e le stesse mosse danno sempre lo stesso esito.
 *
 * Qui dentro non c'è niente di Android: nessun disegno, nessuna vista, nessun
 * tempo che scorre. Serve a poterlo provare per intero senza uno schermo, che
 * per un rompicapo è l'unico modo di sapere se un livello si risolve davvero.
 */

/** Cosa c'è sotto i piedi. */
enum class Suolo {
    NORMALE,

    /**
     * Ci si scivola sopra: chi entra prosegue finché non sbatte.
     *
     * È la regola che cambia più di tutte, perché non cambia cosa puoi fare ma
     * cosa succede quando ti muovi. Su ghiaccio non scegli dove arrivare:
     * scegli in che direzione partire, e la mappa decide il resto.
     */
    GHIACCIO,

    /** Dove si vince. */
    USCITA,
}

/** Cosa occupa una casella. Il vuoto è l'assenza. */
enum class Blocco(
    /**
     * Il livello di piccone che serve per romperlo: 0 a mani nude, 4 il
     * diamante. Null vuol dire che non si rompe con nessun piccone.
     */
    val durezza: Int?,
    /** Se si può spingere invece che rompere. */
    val spingibile: Boolean = false,
) {
    TERRA(0),
    PIETRA(1),
    FERRO(2),
    DIAMANTE(3),

    /**
     * Il muro per ventotto livelli, e una porta nell'ultimo.
     *
     * Serve un piccone di diamante, che nei primi biomi non esiste: fino ad
     * allora l'ossidiana è semplicemente il bordo del mondo.
     */
    OSSIDIANA(4),

    /**
     * La ghiaia non si rompe: si spinge. È l'unico blocco che si comporta come
     * la cassa del Sokoban, ed è quello con cui si fanno i tappi sul ghiaccio.
     */
    GHIAIA(null, spingibile = true),
}

/** Un piccone per terra, o quello che si ha in mano. */
enum class Piccone(val livello: Int, val durabilita: Int) {
    LEGNO(1, 12),
    PIETRA(2, 16),
    FERRO(3, 20),
    DIAMANTE(4, 24),
}

data class Punto(val x: Int, val y: Int)

enum class Direzione(val dx: Int, val dy: Int) {
    SU(0, -1), GIU(0, 1), SINISTRA(-1, 0), DESTRA(1, 0)
}

/** Quello che si ha in mano adesso, e quanto gli resta. */
data class InMano(val piccone: Piccone, val colpiRimasti: Int) {
    val scarico: Boolean get() = colpiRimasti <= 0
}

/** Com'è finita una mossa. */
enum class Esito {
    /** Ci si è mossi. */
    MOSSO,

    /** Si è rotto un blocco: la mossa è servita a quello. */
    SCAVATO,

    /** Una picconata data, ma il blocco è ancora in piedi. */
    CREPATO,

    /** Niente: muro, piccone insufficiente o scarico. Il turno non si consuma. */
    NULLA,

    /** Si è arrivati all'uscita. */
    VINTO,
}

data class Mossa(val esito: Esito, val stato: Stato)

/**
 * Tutto quello che serve a sapere com'è messa la partita.
 *
 * È un dato immutabile e ogni mossa ne produce uno nuovo. Costa qualche copia
 * in più, e in cambio si ottengono tre cose che valgono molto di più: un
 * «annulla» che è tenere il vecchio stato, un risolutore che può provare strade
 * senza sporcare niente, e prove che confrontano stati invece di ispezionare
 * variabili.
 */
data class Stato(
    val larghezza: Int,
    val altezza: Int,
    val suolo: List<Suolo>,
    val blocchi: Map<Punto, Blocco>,
    /** I picconi lasciati per terra. */
    val picconi: Map<Punto, Piccone>,
    val giocatore: Punto,
    val inMano: InMano?,
    /**
     * Quante picconate ha già preso ogni blocco.
     *
     * Restano dove sono anche se ci si allontana. In Minecraft la crepa si
     * richiude appena stacchi il tasto, ma qui il tempo non scorre: se si
     * azzerasse, «andare a prendere il piccone giusto e tornare» diventerebbe
     * una punizione invece che un piano.
     */
    val danni: Map<Punto, Int> = emptyMap(),
    val vinto: Boolean = false,
) {
    fun dentro(p: Punto) = p.x in 0 until larghezza && p.y in 0 until altezza

    fun suoloDi(p: Punto): Suolo =
        if (dentro(p)) suolo[p.y * larghezza + p.x] else Suolo.NORMALE

    fun bloccoIn(p: Punto): Blocco? = blocchi[p]

    /** Libera per camminarci: dentro il campo e senza blocchi. */
    fun libera(p: Punto): Boolean = dentro(p) && blocchi[p] == null
}

object Motore {

    /**
     * Quante picconate servono per rompere un blocco con quel piccone.
     *
     * Con l'attrezzo appena sufficiente ci si mette parecchio; ogni livello in
     * più è una picconata in meno, fino a un minimo di una. Così il piccone non
     * è solo una chiave che apre o non apre: continua a contare anche dopo che
     * ha aperto, e portarsi dietro quello buono ha un valore che si misura in
     * mosse.
     *
     * `2·durezza + 1 - livello`, che in pratica vuol dire:
     *
     * | blocco    | col minimo | col diamante |
     * |-----------|-----------:|-------------:|
     * | terra     |          1 |            1 |
     * | pietra    |          2 |            1 |
     * | ferro     |          3 |            1 |
     * | diamante  |          4 |            2 |
     * | ossidiana |          5 |            5 |
     */
    fun colpiNecessari(blocco: Blocco, piccone: Piccone?): Int {
        val durezza = blocco.durezza ?: return Int.MAX_VALUE
        val livello = piccone?.livello ?: 0
        if (livello < durezza) return Int.MAX_VALUE
        return maxOf(1, 2 * durezza + 1 - livello)
    }

    /**
     * A che punto è la crepa, da 0 (intatto) a [STADI]-1 (sta per cedere).
     *
     * Serve solo a disegnare, ma sta qui perché è la stessa aritmetica che
     * decide quando il blocco cade: due conti separati vorrebbero dire una
     * crepa piena su un blocco ancora sano.
     */
    fun crepa(stato: Stato, dove: Punto): Int {
        val blocco = stato.bloccoIn(dove) ?: return 0
        val fatti = stato.danni[dove] ?: 0
        if (fatti <= 0) return 0
        val servono = colpiNecessari(blocco, stato.inMano?.piccone)
        if (servono <= 1 || servono == Int.MAX_VALUE) return STADI - 1
        return ((fatti * STADI) / servono).coerceIn(0, STADI - 1)
    }

    /** Quanti stadi di crepa: gli stessi di Minecraft. */
    const val STADI = 8

    private fun Punto.piu(d: Direzione) = Punto(x + d.dx, y + d.dy)

    /**
     * Dove si ferma chi entra in [da] andando verso [d].
     *
     * Sul ghiaccio non ci si ferma: si prosegue finché la casella dopo non è
     * libera, o finché non si finisce su un suolo normale. Il conteggio dei
     * passi non è un dettaglio: senza un tetto, due specchi di ghiaccio uno di
     * fronte all'altro farebbero girare a vuoto un ciclo per sempre.
     */
    private fun scivola(stato: Stato, da: Punto, d: Direzione): Punto {
        var qui = da
        var passi = 0
        val massimo = stato.larghezza * stato.altezza
        while (stato.suoloDi(qui) == Suolo.GHIACCIO && passi < massimo) {
            val dopo = qui.piu(d)
            if (!stato.libera(dopo)) break
            qui = dopo
            passi++
        }
        return qui
    }

    /**
     * Una mossa nella direzione data.
     *
     * L'ordine dei casi è la regola del gioco:
     * 1. fuori dal campo, non succede niente;
     * 2. casella libera: ci si va, e se è ghiaccio si prosegue;
     * 3. blocco spingibile: si spinge — e anche lui scivola;
     * 4. blocco da rompere: si rompe **restando fermi**, e costa un colpo di
     *    piccone. Rompere e avanzare nello stesso turno renderebbe lo scavo
     *    gratuito, e la durabilità non varrebbe niente.
     */
    fun muovi(stato: Stato, d: Direzione): Mossa {
        if (stato.vinto) return Mossa(Esito.NULLA, stato)
        val bersaglio = stato.giocatore.piu(d)
        if (!stato.dentro(bersaglio)) return Mossa(Esito.NULLA, stato)

        val blocco = stato.bloccoIn(bersaglio)

        if (blocco == null) return Mossa(Esito.MOSSO, vai(stato, bersaglio, d))

        if (blocco.spingibile) {
            val dopo = bersaglio.piu(d)
            if (!stato.libera(dopo)) return Mossa(Esito.NULLA, stato)
            // Il blocco parte e scivola per conto suo: si ferma dove trova
            // qualcosa. E' cosi' che sul ghiaccio non si posiziona un blocco,
            // si posiziona quello che lo fermera'.
            val fermo = scivola(stato, dopo, d)
            val nuovi = stato.blocchi.toMutableMap()
            nuovi.remove(bersaglio)
            nuovi[fermo] = blocco
            return Mossa(Esito.MOSSO, vai(stato.copy(blocchi = nuovi), bersaglio, d))
        }

        return scava(stato, bersaglio, blocco)
    }

    /**
     * Ci si sposta, si scivola se serve, si raccoglie quello che c'è per terra.
     *
     * Il piccone vecchio cade **dove sei** e non dove eri: se cadesse alle
     * spalle, sul ghiaccio finirebbe a mezzo corridoio di distanza e nessuno
     * saprebbe più dov'è.
     */
    private fun vai(stato: Stato, primoPasso: Punto, d: Direzione): Stato {
        val arrivo = scivola(stato, primoPasso, d)
        val trovato = stato.picconi[arrivo]

        if (trovato == null) {
            return dopoIlPasso(stato.copy(giocatore = arrivo))
        }

        val picconi = stato.picconi.toMutableMap()
        picconi.remove(arrivo)
        stato.inMano?.let { vecchio -> picconi[arrivo] = vecchio.piccone }
        return dopoIlPasso(
            stato.copy(
                giocatore = arrivo,
                picconi = picconi,
                inMano = InMano(trovato, trovato.durabilita),
            )
        )
    }

    private fun dopoIlPasso(stato: Stato): Stato =
        if (stato.suoloDi(stato.giocatore) == Suolo.USCITA) stato.copy(vinto = true) else stato

    /**
     * Rompere un blocco.
     *
     * Serve un piccone di livello sufficiente e con dei colpi ancora dentro.
     * Quando finisce, il piccone sparisce dalle mani: restare con un piccone
     * scarico in mano sarebbe la stessa cosa che non averlo, ma detta peggio.
     */
    /**
     * Una picconata.
     *
     * Non rompe più per forza: aggiunge un colpo, e il blocco cade solo quando
     * ne ha presi abbastanza. Ogni colpo costa un turno e un punto di
     * durabilità — che è il motivo per cui l'attrezzo giusto vale: con quello
     * sbagliato ma sufficiente, lo stesso blocco si porta via tre volte tanto.
     */
    private fun scava(stato: Stato, dove: Punto, blocco: Blocco): Mossa {
        val durezza = blocco.durezza ?: return Mossa(Esito.NULLA, stato)
        val mano = stato.inMano

        if (durezza > 0) {
            if (mano == null || mano.scarico) return Mossa(Esito.NULLA, stato)
            if (mano.piccone.livello < durezza) return Mossa(Esito.NULLA, stato)
        }

        val servono = colpiNecessari(blocco, mano?.piccone)
        val fatti = (stato.danni[dove] ?: 0) + 1

        // La terra si scava anche a mani nude, e allora non consuma niente.
        val manoDopo = if (durezza == 0 || mano == null) {
            mano
        } else {
            (mano.colpiRimasti - 1).let { if (it <= 0) null else mano.copy(colpiRimasti = it) }
        }

        if (fatti < servono) {
            val danni = stato.danni.toMutableMap()
            danni[dove] = fatti
            return Mossa(Esito.CREPATO, stato.copy(danni = danni, inMano = manoDopo))
        }

        val nuovi = stato.blocchi.toMutableMap()
        nuovi.remove(dove)
        val danni = stato.danni.toMutableMap()
        danni.remove(dove)
        return Mossa(Esito.SCAVATO, stato.copy(blocchi = nuovi, danni = danni, inMano = manoDopo))
    }
}
