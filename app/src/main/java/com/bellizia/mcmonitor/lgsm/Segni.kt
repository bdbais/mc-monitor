package com.bellizia.mcmonitor.lgsm

/**
 * I due segni che si possono mettere accanto a un nome nell'elenco giocatori.
 *
 * **Preferito** vuol dire «questo mi interessa»: sale in cima all'elenco e ci
 * resta anche quando non è collegato. Non ha limite, perché non costa niente.
 *
 * **Sorvegliato** vuol dire «questo va sul cruscotto»: ha un limite, e il
 * limite è quanti ne stanno su uno schermo restando leggibili da lontano. Sono
 * due cose diverse di proposito — i figli sono preferiti sempre, il tizio nuovo
 * che sta scavando vicino a casa di qualcun altro va sul cruscotto per
 * mezz'ora e poi basta.
 *
 * I nomi si tengono senza maiuscole: `list` e i log non sono sempre d'accordo,
 * e uno stesso giocatore non deve poter risultare segnato e non segnato.
 */
object Segni {

    /** Esito di una marcatura, per poter dire *perché* quando non si può. */
    sealed interface Esito {
        data class Fatto(val insieme: Set<String>) : Esito
        data class Pieno(val quanti: Int) : Esito
    }

    fun segnato(insieme: Set<String>, nome: String): Boolean =
        nome.lowercase() in insieme

    /** I preferiti non hanno limite: si accende e si spegne, e basta. */
    fun cambiaPreferito(insieme: Set<String>, nome: String): Set<String> {
        val chiave = nome.lowercase()
        return if (chiave in insieme) insieme - chiave else insieme + chiave
    }

    /**
     * Il cruscotto invece è pieno a sei.
     *
     * Togliere il più vecchio per far posto sarebbe più comodo da programmare,
     * ma vorrebbe dire che smetti di guardare qualcuno senza che nessuno te lo
     * dica — e il cruscotto serve proprio a non perdere di vista una persona.
     * Meglio dire che è pieno e lasciare scegliere chi togliere.
     */
    fun cambiaSorvegliato(insieme: Set<String>, nome: String): Esito {
        val chiave = nome.lowercase()
        if (chiave in insieme) return Esito.Fatto(insieme - chiave)
        if (insieme.size >= Cruscotto.MASSIMO) return Esito.Pieno(insieme.size)
        return Esito.Fatto(insieme + chiave)
    }

    /** Cosa dire quando il cruscotto è pieno. */
    fun spiegazionePieno(quanti: Int): String =
        "Il cruscotto tiene $quanti giocatori, che è quanti se ne leggono su uno " +
                "schermo da lontano. Togli l'occhio a qualcuno per fare posto."

    /**
     * L'ordine dell'elenco: prima i preferiti, poi gli altri.
     *
     * Dentro i due gruppi l'ordine di partenza non si tocca: è quello che ha
     * dato il server, e chi guarda l'elenco due volte di seguito non deve
     * trovare le righe rimescolate.
     */
    fun ordina(nomi: List<String>, preferiti: Set<String>): List<String> {
        val (primi, dopo) = nomi.partition { it.lowercase() in preferiti }
        return primi + dopo
    }

    /**
     * Ripulisce quello che è rimasto scritto da prima.
     *
     * Un insieme salvato da una versione che permetteva più segni, o da una che
     * teneva i nomi con le maiuscole, arriverebbe qui com'è: si normalizza in
     * lettura invece di fidarsi di com'era stato scritto.
     */
    fun ripulisci(salvato: Set<String>, limite: Int? = null): Set<String> {
        val puliti = salvato.map { it.lowercase() }.filter { it.isNotBlank() }.distinct()
        return (if (limite != null) puliti.take(limite) else puliti).toSet()
    }
}
