package com.bellizia.mcmonitor.lgsm

/**
 * La password RCON quando gli amministratori sono più di uno.
 *
 * C'era un difetto, e non era un caso limite: due persone aprono le
 * impostazioni, premono «genera» ciascuna, e ognuna scrive la propria password
 * nel file del server. Da quel momento l'ultima che ha premuto funziona e
 * l'altra no — e appena l'altra riprova a sistemare, si invertono. Si rompono a
 * vicenda per sempre, e ognuna delle due vede solo «password rifiutata» senza
 * capire da dove arrivi.
 *
 * La regola giusta è una sola, e ribalta quella di prima: **il file del server
 * comanda**. Se là dentro c'è già una password, l'app prende quella invece di
 * imporre la sua. Si scrive soltanto quando non c'è niente da prendere.
 *
 * Detta in un altro modo: la password RCON non appartiene a un telefono,
 * appartiene al server. Il telefono se la ricorda, non la decide.
 */
object RconCondivisa {

    sealed interface Scelta {
        /** Sul server non c'è niente: la nostra va scritta. */
        data class Scrivi(val password: String) : Scelta

        /** Sul server ce n'è già una diversa: si prende quella. */
        data class Adotta(val password: String) : Scelta

        /** Sono già la stessa: non si tocca niente. */
        data object Uguali : Scelta

        /** Non c'è né la nostra né quella del server: bisogna generarne una. */
        data object DaGenerare : Scelta
    }

    /**
     * Cosa fare, viste le due password.
     *
     * Non si guarda chi è arrivato prima né quale sia «più bella»: si guarda
     * soltanto se il server ne ha già una. Qualunque altra regola fa litigare
     * due telefoni.
     */
    fun decidi(nostra: String, sulServer: String): Scelta = when {
        sulServer.isNotBlank() && sulServer == nostra -> Scelta.Uguali
        sulServer.isNotBlank() -> Scelta.Adotta(sulServer)
        nostra.isNotBlank() -> Scelta.Scrivi(nostra)
        else -> Scelta.DaGenerare
    }

    /**
     * Un valore letto dal file è utilizzabile?
     *
     * Non si applica [Lgsm.PASSWORD_CHARSET]: quella regola serve a decidere
     * cosa **scrivere** nel file, e una password messa a mano da qualcun altro
     * può contenere altro senza per questo essere sbagliata. Qui basta che ci
     * sia e che non sia una riga di errore finita dentro per sbaglio.
     */
    fun utilizzabile(letta: String): Boolean =
        letta.isNotBlank() && letta.length in 1..200 && !letta.contains(' ')

    /**
     * Cosa dire agli altri amministratori quando la password cambia davvero.
     *
     * Non basta annunciare il fatto: chi lo legge deve sapere **cosa fare**.
     * Un avviso che dice solo «ho cambiato una cosa» lascia l'altro con l'app
     * rotta e nessuna istruzione, che è quasi peggio del silenzio.
     */
    fun avvisoCambioPassword(): String =
        "Ho cambiato la password RCON. Se la tua app dice che è sbagliata, apri " +
                "Impostazioni e premi «RCON non va: cerca il guasto»: la prende da sola " +
                "dal server, non serve che te la mandi."

    /** Le altre cose che, fatte da uno, sorprendono gli altri. */
    fun avvisoRiavvio(): String =
        "Ho riavviato il server dall'app: se eravate dentro siete stati sbattuti fuori."

    fun avvisoMappa(nome: String): String =
        "Ho installato $nome per la mappa del mondo. Il server è stato riavviato."

    fun avvisoMod(quante: Int): String =
        "Ho installato ${if (quante == 1) "una mod" else "$quante mod"}. " +
                "Serve un riavvio perché il server le carichi."

    fun avvisoBackup(): String =
        "Ho fatto un backup adesso: se il server è andato a scatti per qualche minuto, era quello."

    /**
     * Un'impostazione del gioco cambiata.
     *
     * Si dice **quale** e **come**, non «ho cambiato le impostazioni»: chi
     * legge deve poter capire se lo riguarda senza andare a controllare. Il
     * valore vecchio conta quanto quello nuovo — è l'unico modo per accorgersi
     * che è stato toccato qualcosa che si era messo apposta.
     */
    fun avvisoImpostazione(chiave: String, prima: String, dopo: String): String =
        "Ho cambiato «$chiave»: da $prima a $dopo."

    fun avvisoImpostazioni(quante: Int): String =
        if (quante == 1) "Ho cambiato un'impostazione del server."
        else "Ho cambiato $quante impostazioni del server."

    fun avvisoModRimossa(nome: String): String =
        "Ho tolto la mod «$nome». Serve un riavvio, e chi ha quella mod nel suo " +
                "gioco potrebbe non entrare più."

    fun avvisoModSpenta(nome: String, accesa: Boolean): String =
        if (accesa) "Ho riacceso la mod «$nome»." else "Ho spento la mod «$nome»."

    fun avvisoVersione(prima: String?, dopo: String): String =
        "Ho cambiato la versione di Minecraft" +
                (if (prima.isNullOrBlank()) "" else " da $prima") +
                " a $dopo. Chi ha il gioco su un'altra versione non entrerà più."

    fun avvisoRipristino(quando: String): String =
        "Ho rimesso un backup del $quando: quello che è stato costruito dopo non c'è più."

    fun avvisoPreparato(): String =
        "Ho preparato il server da zero: configurazione e impostazioni di sicurezza rifatte."
}
