package com.bellizia.mcmonitor.lgsm

/**
 * Come sta RCON, detto sulla prima pagina.
 *
 * «Acceso» e «funzionante» sono due cose diverse, e per due giorni sono state
 * confuse: il file diceva `enable-rcon=true`, la porta era in ascolto, e i
 * comandi non arrivavano. Da nessuna parte si poteva vedere la differenza senza
 * entrare nel server e provare.
 *
 * Qui la differenza si dice per intero. Le due cose che il file racconta —
 * acceso, e con quale porta — non bastano: finché qualcuno non ha davvero
 * chiesto qualcosa e ottenuto una risposta, lo stato è **da provare**, e lo si
 * scrive così invece di scrivere un verde che non è stato verificato.
 */
object SegnaleRcon {

    enum class Stato {
        /** Nel file del server RCON è spento: non c'è niente da provare. */
        SPENTO,

        /** Il server lo ha acceso, ma l'app non ha la password: non può bussare. */
        SENZA_PASSWORD,

        /** Tutto al posto giusto, ma nessuno ha ancora provato. */
        DA_PROVARE,

        /** Provato adesso: il server ha risposto. */
        RISPONDE,

        /** Provato adesso: niente. */
        NON_RISPONDE,
    }

    /**
     * Da cosa si parte, prima di aver bussato.
     *
     * Non si parte mai da [Stato.RISPONDE]: sarebbe una cosa non verificata
     * detta come verificata, ed è esattamente l'errore che ha fatto perdere due
     * giorni.
     */
    fun iniziale(accesoNelFile: Boolean, conPassword: Boolean): Stato = when {
        !accesoNelFile -> Stato.SPENTO
        !conPassword -> Stato.SENZA_PASSWORD
        else -> Stato.DA_PROVARE
    }

    /** Cosa si può scrivere dopo aver bussato. */
    fun dopoLaProva(riuscita: Boolean): Stato =
        if (riuscita) Stato.RISPONDE else Stato.NON_RISPONDE

    /**
     * L'etichetta, scritta per chi non sa cosa sia RCON.
     *
     * Dice cosa cambia per lui — «i comandi funzionano» — e non il nome della
     * cosa che funziona.
     */
    fun etichetta(stato: Stato): String = when (stato) {
        Stato.SPENTO -> "RCON spento · i comandi non arrivano"
        Stato.SENZA_PASSWORD -> "RCON acceso · all'app manca la password"
        Stato.DA_PROVARE -> "RCON acceso · provo…"
        Stato.RISPONDE -> "RCON funziona · il server risponde"
        Stato.NON_RISPONDE -> "RCON acceso ma non risponde"
    }

    /** Quanto è tranquillizzante: verde solo quando è stato verificato davvero. */
    fun colore(stato: Stato): Int = when (stato) {
        Stato.RISPONDE -> 0xFF5FBF6B.toInt()      // grass
        Stato.NON_RISPONDE -> 0xFFEF5350.toInt()  // danger
        Stato.SENZA_PASSWORD -> 0xFFFFB300.toInt() // warning
        Stato.SPENTO, Stato.DA_PROVARE -> 0xFF9DB3A7.toInt() // text_dim
    }

    /** Se val la pena bussare: senza password o con RCON spento si sa già. */
    fun daProvare(stato: Stato): Boolean = stato == Stato.DA_PROVARE
}
