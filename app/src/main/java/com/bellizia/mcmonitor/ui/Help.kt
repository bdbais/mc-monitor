package com.bellizia.mcmonitor.ui

/**
 * Aiuto contestuale, una voce per schermata.
 *
 * Il testo è scritto per chi non ha mai amministrato un server: niente sigle non
 * spiegate, ogni campo detto con parole sue e con un esempio concreto.
 */
object Help {

    data class Page(val title: String, val body: String)

    val SERVERS = Page(
        "I tuoi server",
        """
        Questa è la lista dei server Minecraft che puoi comandare da qui.

        Un "server" è il computer sempre acceso dove vive il tuo mondo di Minecraft: quando tu e i tuoi amici giocate insieme, il mondo sta lì, non sul telefono.

        • Aggiungi — crea una nuova scheda e ti chiede i dati per collegarti
        • Modifica — cambia i dati di quello selezionato
        • Duplica — ne fa una copia, utile se hai due mondi sullo stesso computer
        • Rimuovi — cancella la scheda dal telefono. Il server e il mondo NON vengono toccati
        • Apri — entra e ti mostra com'è messo il server

        Tocca una scheda per sceglierla: diventa con il bordo verde.

        Se qualcosa non ti torna, il Manuale d'uso in alto a destra spiega tutto con le immagini.
        """.trimIndent()
    )

    val STATUS = Page(
        "Stato del server",
        """
        Qui vedi se il server sta funzionando.

        • STARTED in verde = acceso, si può giocare
        • STOPPED in rosso = spento, nessuno può entrare

        L'indirizzo per i giocatori è quello che devi dare ai tuoi amici perché possano entrare nel mondo. Con l'icona di copia lo copi, con quella di condivisione lo mandi su WhatsApp o dove vuoi. In Minecraft si incolla in "Multiplayer", poi "Aggiungi server".

        I tre pulsanti:
        • Avvia — accende il server
        • Ferma — lo spegne. Chi sta giocando viene buttato fuori, quindi avvisa prima
        • Riavvia — lo spegne e lo riaccende, utile quando fa i capricci

        Più sotto trovi la versione di Minecraft del server (deve essere la stessa che usi nel gioco, altrimenti non ti fa entrare) e la lista delle modifiche installate.

        Tira la pagina verso il basso con il dito per aggiornare i dati.
        """.trimIndent()
    )

    val CONSOLE = Page(
        "Console",
        """
        La console è il diario del server: scrive da sola tutto quello che succede, tipo chi entra, chi esce e cosa si dicono in chat.

        Nel riquadro grande scorrono le ultime righe. Si aggiorna da solo ogni pochi secondi; se ti dà fastidio puoi spegnere l'interruttore in alto.

        In basso puoi scrivere un comando e mandarlo al server, come se fossi seduto davanti a lui. I bottoncini sopra la casella scrivono i comandi al posto tuo:

        • list — chi sta giocando adesso
        • say — manda un messaggio a tutti quelli collegati
        • time set day — fa tornare giorno
        • weather clear — ferma la pioggia
        • save-all — salva subito il mondo

        Quelli che finiscono con uno spazio (tipo "tp " o "kick ") aspettano che tu scriva un nome dopo. Toccare un bottoncino non manda niente: scrive solo, poi decidi tu se premere Invia.
        """.trimIndent()
    )

    val PLAYERS = Page(
        "Giocatori",
        """
        Chi sta giocando e chi vorrebbe giocare.

        In attesa di approvazione — compare quando qualcuno prova a entrare ma non è ancora nella lista degli invitati. Hai due bottoni: "Ammetti" lo fa entrare da quel momento in poi, "Banna" gli impedisce di tornare. Se non conosci il nome, meglio non ammetterlo.

        Online — chi è dentro adesso, con il punto del mondo in cui si trova. I numeri X, Y, Z sono le coordinate: X e Z dicono dove, Y quanto in alto.

        Whitelist — la lista degli invitati. Se il server la usa, entrano solo i nomi che stanno lì.

        Ban — chi hai bloccato. Puoi sempre togliergli il blocco.

        Toccando un nome si apre un pannello con tutto quello che puoi fare su quella persona: seguirla sulla mappa, leggere cosa ha scritto in chat, spostarla, cambiarle la modalità di gioco, o buttarla fuori.
        """.trimIndent()
    )

    val MAP = Page(
        "Mappa",
        """
        Una mappa del mondo vista dall'alto, con i giocatori come pallini colorati.

        • trascina con un dito per spostarti
        • allarga con due dita per ingrandire
        • due tocchi veloci per zoomare in fretta

        La crocetta gialla al centro è il punto di partenza del mondo (lo spawn), cioè dove si nasce.

        Dietro ogni giocatore resta una scia: è la strada che ha fatto mentre lo guardavi. "Azzera scie" la cancella.

        Minecraft ha tre mondi diversi: Overworld (quello normale), Nether (quello infernale) e End. I bottoncini in alto scelgono quale guardare, perché i giocatori che stanno nel Nether non si vedono nella mappa dell'Overworld.

        Toccando un giocatore la mappa lo insegue mentre cammina.
        """.trimIndent()
    )

    val MODS = Page(
        "Mod",
        """
        Le mod sono aggiunte che cambiano il gioco: nuovi blocchi, animali, macchine.

        Perché funzionino servono due cose:
        1. un "mod loader" sul server, cioè il programma che sa caricarle (il più usato si chiama Fabric). Se il tuo server non ce l'ha, qui trovi il pulsante per installarlo
        2. la mod giusta per la tua versione di Minecraft. Una mod per la 1.20 non funziona sulla 1.21

        Cerca su Modrinth — Modrinth è un sito pieno di mod gratuite. Scrivi un nome, scegli il risultato e l'app la scarica direttamente sul server. Non serve registrarsi.

        Modpack — un pacchetto già pronto con tante mod insieme, in un file che finisce per .mrpack. Se qualcuno te ne ha mandato uno, con quel pulsante lo installi tutto in una volta.

        ATTENZIONE: chi gioca deve avere le stesse mod sul proprio computer, altrimenti non riesce a entrare.

        Dopo ogni cambiamento il server va riavviato: il pulsante compare da solo.
        """.trimIndent()
    )

    val SETTINGS = Page(
        "Impostazioni",
        """
        Qui dici all'app come parlare con il computer dove vive il server. Se non hai questi dati, chiedili a chi ha creato il server.

        Nome — come vuoi chiamarlo tu, per esempio "Server di casa"
        Nome tecnico — il nome della cartella sul computer, senza spazi
        Host o IP — l'indirizzo del computer, tipo mioserver.it oppure 192.168.1.50
        Porta — quasi sempre 22, lascia com'è se non ti hanno detto altro
        Utente SSH — il nome con cui entrare nel computer. Deve essere lo stesso utente che ha acceso il server, altrimenti i comandi non arrivano
        Password — la parola d'ordine di quell'utente
        Chiave privata — un'alternativa alla password, più sicura. Se non sai cos'è, lascia vuoto e usa la password
        Directory LinuxGSM — la cartella dove sta il server, di solito ~/server1
        Script LinuxGSM — il nome del programma che accende il server, di solito mcserver

        Prova connessione ti dice subito se i dati sono giusti. Diagnostica, se qualcosa non va, ti spiega dove si blocca.

        Notifiche — se le accendi, il telefono ti avvisa quando il server si spegne o quando qualcuno entra ed esce. Puoi scegliere quali avvisi ricevere. Resta un avviso fisso nella barra: serve ad Android per lasciare l'app in ascolto, e consuma un po' di batteria.

        RCON — un modo più veloce di parlare col server. Non è obbligatorio: se lo attivi, i comandi rispondono subito.
        """.trimIndent()
    )

    /** Le schede dentro un server, nell'ordine in cui compaiono in alto. */
    fun forTab(index: Int): Page = when (index) {
        0 -> STATUS
        1 -> CONSOLE
        2 -> PLAYERS
        3 -> MAP
        4 -> MODS
        else -> SETTINGS
    }
}
