package com.bellizia.mcmonitor.ui

/**
 * Aiuto contestuale, una voce per schermata.
 *
 * Il testo è scritto per chi non ha mai amministrato un server: niente sigle non
 * spiegate, ogni campo detto con parole sue e con un esempio concreto.
 */
object Help {

    data class Page(val title: String, val body: String)

    val CONNECTION = Page(
        "Passo 1: il computer",
        """
        Prima di vedere i mondi bisogna entrare nel computer dove stanno.

        È un computer sempre acceso, da qualche parte: si chiama "server". Non è il tuo telefono e non è il computer di casa, a meno che non sia proprio quello ad essere acceso giorno e notte.

        Ti servono quattro cose, e le sa chi ha acceso quel computer:
        • Indirizzo — dove si trova. Può essere un nome tipo casa.miosito.it oppure una fila di numeri tipo 192.168.1.10
        • Porta — quasi sempre 22, lasciala com'è se non ti dicono altro
        • Nome utente — con che nome entri, per esempio mcserver
        • Password — la parola segreta di quel nome utente

        Poi tocca "Collegati". Se va tutto bene compare una scritta verde e l'app passa da sola a cercare i mondi.

        Se compare una scritta rossa, tocca "Non funziona": l'app prova un pezzo alla volta e ti dice a quale punto si ferma. Le tre cause più comuni sono l'indirizzo scritto male, la password sbagliata e il computer spento.

        La password resta solo dentro il telefono. Se fai vedere lo schermo a qualcuno, la modalità privacy nelle impostazioni la nasconde.

        Se avevi già l'app e l'hai reinstallata, in cima trovi "Riprendi una configurazione salvata": scegli il file che avevi salvato, scrivi la sua password e ritrovi tutto senza ricopiare niente.
        """.trimIndent()
    )

    val INSTALLED = Page(
        "Passo 2: i mondi",
        """
        Fatto il collegamento, l'app guarda dentro il computer e ti mostra i mondi di Minecraft che ci trova già installati. Non devi sapere in quale cartella stanno: li cerca lei.

        Per ognuno vedi:
        • se in questo momento è acceso (quadratino verde) o spento (quadratino grigio)
        • quale versione di Minecraft usa
        • la porta a cui si collegano i giocatori
        • una riga "mod" se il server è moddato: toccala e ti dice quali mod ha
        • la cartella in cui vive, in piccolo sotto

        Se una porta è scritta in ROSSO vuol dire che un altro server sta già usando quel numero. Due server sulla stessa porta non possono essere accesi insieme: il secondo si spegne da solo appena parte. Per sistemarlo bisogna cambiare la porta a uno dei due.

        Tocca "Apri" su quello che ti interessa: da lì in poi puoi accenderlo, spegnerlo, vedere chi sta giocando, la mappa, le mod.

        Il cestino a destra cancella quel server dal computer per sempre: il mondo, le costruzioni, le mod, tutto. Te lo chiede due volte, e la seconda volta devi riscrivere il nome. Se il mondo ti interessa, prima fatti fare una copia.

        Se compare un riquadro giallo "Manutenzione del computer", vuol dire che il programma che fa funzionare i server (si chiama LinuxGSM) è vecchio. Il pulsante lo aggiorna: non tocca il mondo né le mod, e serve perché con quello vecchio ogni tanto le cose smettono di funzionare senza dire perché.

        Se l'elenco è vuoto vuol dire che su quel computer non c'è ancora nessun mondo installato. Allora usa "Crea un nuovo server da zero": ci mette un po' e scarica parecchia roba, quindi fallo con calma e con una buona connessione.

        Trascina l'elenco verso il basso per farlo cercare di nuovo, per esempio dopo aver installato un mondo nuovo.
        """.trimIndent()
    )

    val BACKUP = Page(
        "Il backup",
        """
        Il backup è una copia compressa di tutto il server — mondo, mod, configurazioni — che LinuxGSM mette in lgsm/backup dentro la cartella del server.

        In cima vedi quando è stato fatto l'ultimo, quanti ce ne sono e quanto spazio resta. Se lo spazio libero è poco l'app lo dice: un backup che finisce a metà per il disco pieno lascia un archivio rotto che poi sembra un backup buono.

        FARLO ADESSO

        Il pulsante fa la copia subito. Di fabbrica LinuxGSM ferma il server per tutta la durata: chi sta giocando viene disconnesso e rientra quando è finita. Su un mondo grande sono diversi minuti.

        FARLO FARE DA SOLO

        Scegli ogni quanto — ogni giorno, ogni settimana, ogni mese — e a che ora. L'ora è quella del computer dove vive il server, non quella del telefono. Di notte è meglio, visto che il server si ferma.

        LinuxGSM non ha un suo modo di programmare i backup: lo fa il cron del computer, che è il pezzo che manda avanti le cose a orario. L'app scrive una riga lì dentro, e il backup parte anche a telefono spento.

        Quel file può contenere righe scritte da qualcun altro, magari anni fa, che non c'entrano niente con Minecraft. Per questo l'app: legge prima, e se non capisce cosa c'è si ferma senza toccare niente; tiene una copia di com'era sul telefono, che si rimette con "Rimetti il crontab com'era"; e dopo aver scritto rilegge per controllare che ci sia davvero.

        Se il computer non ha cron, o se cron non sta girando, l'app lo dice invece di lasciarti credere che sia tutto a posto.

        DUE COSE DA SAPERE

        Dentro l'archivio ci finisce tutto il server, quindi anche i file di configurazione con le password e i token degli avvisi. Se lo copi da qualche parte, tienine conto.

        Quante copie tenere e per quanti giorni si decidono da "Impostazioni tecniche": sono maxbackups e maxbackupdays. La pulizia gira dopo aver creato la copia nuova, quindi per un momento ce n'è una in più.
        """.trimIndent()
    )

    val COMANDI = Page(
        "I comandi di LinuxGSM",
        """
        Sono i comandi che si darebbero da terminale scrivendo ./mcserver seguito da una parola. Qui ci sono quelli che si possono lanciare da un telefono, ognuno con scritto cosa fa e cosa succede al server.

        Quelli che chiedono conferma la chiedono per un motivo: fermano il server, scaricano roba o cambiano file. Nella conferma c'è scritto esattamente cosa succede a chi sta giocando.

        NON CI SONO TUTTI, E NON È PRUDENZA

        console, debug e install aspettano una risposta dalla tastiera. Senza un terminale vero quella risposta non arriva mai, e LinuxGSM non si ferma: entra in un ciclo che ripete "Please answer yes or no." all'infinito finché non si stacca la connessione. debug in più ferma il server prima di partire, quindi lanciarlo e chiudere l'app lascerebbe il mondo spento.

        COME SI CAPISCE SE È ANDATA BENE

        Non dal codice di uscita: LinuxGSM lo usa per dire la gravità dell'ultima riga che ha scritto nel suo registro, non se il comando è riuscito. Un comando che non esiste esce con "tutto bene". Per questo l'app guarda cosa ha scritto, e ti fa vedere il testo intero.

        Un comando che ci mette troppo viene interrotto sul computer, non solo staccando la connessione: altrimenti resterebbe a girare là senza che nessuno lo sappia.
        """.trimIndent()
    )

    val GAME_SETTINGS = Page(
        "Le impostazioni del server",
        """
        Queste sono le impostazioni del gioco: difficoltà, messaggio di benvenuto, quanti giocatori entrano, quanto lontano si vede, chi può collegarsi.

        Vivono in un file che si chiama server.properties, dentro la cartella del mondo. È un file diverso da quello di LinuxGSM, e ha una vita diversa: quello di LinuxGSM si tocca il primo giorno e poi resta fermo, questo si cambia ogni volta che cambia qualcosa fra chi gioca.

        Niente parte finché non premi Salva. Le modifiche si accumulano — la riga cambiata si segna con un puntino — e partono tutte insieme: una sola connessione, una sola copia di sicurezza del file. Prima di scrivere ti viene mostrato l'elenco esatto di cosa cambia, da cosa a cosa.

        Alcune valgono subito e altre dal prossimo avvio, ed è scritto sotto ognuna. Difficoltà e whitelist, per esempio, l'app le scrive nel file E le manda al server, così cambiano anche per chi sta giocando in quel momento. Se il server è spento la scrittura vale lo stesso, e te lo dice.

        Le quattro che contano di più su un server piccolo:
        • Difficoltà — in pacifica i mostri non compaiono proprio
        • Solo chi è in whitelist — la vera difesa di un server aperto su internet
        • Quanto lontano si vede — il primo numero da abbassare quando il server singhiozza
        • Metti in pausa quando non c'è nessuno — smette di consumare quando il mondo è vuoto

        Di ogni file toccato resta una copia con la data nel nome.

        Le voci che non ci sono qui — porte, indirizzi, messa a punto fine — restano nel file come sono: questa schermata non le tocca. Per la memoria, i backup e gli avvisi c'è "Impostazioni tecniche di LinuxGSM", in fondo alla pagina.

        L'elenco completo con le spiegazioni ufficiali:
        https://minecraft.wiki/w/Server.properties
        """.trimIndent()
    )

    val PARAMS = Page(
        "Le impostazioni tecniche",
        """
        Questi sono gli interruttori di LinuxGSM, cioè del programma che accende e spegne il server: quanta memoria dare a Java, quale versione di Minecraft scaricare, quanti backup tenere, dove mandare gli avvisi.

        Non è qui che si cambiano difficoltà, messaggio di benvenuto o whitelist: quelle stanno in "Impostazioni del server", che è la schermata da cui sei arrivato.

        UNA COSA DA SAPERE SUBITO

        LinuxGSM ha cinque file di configurazione in fila, e questa schermata ne legge uno solo: mcserver.cfg, quello delle tue modifiche. Su un server appena installato quel file è VUOTO, e va benissimo così: i valori veri (memoria 1024, quattro backup, log tenuti sette giorni) stanno nel file di fabbrica, che non si tocca perché LinuxGSM lo riscrive a ogni aggiornamento.

        Quindi: se qui non vedi niente, non vuol dire che il server non abbia impostazioni. Vuol dire che stai usando quelle di fabbrica, e che qui si scrivono solo le differenze.

        Sotto, in "Da aggiungere", ci sono i parametri che LinuxGSM conosce ma che nel tuo file non ci sono ancora.

        QUANDO HANNO EFFETTO

        Sotto ogni parametro c'è scritto. Quasi tutti — backup, log, avvisi — LinuxGSM li rilegge da solo e non serve riavviare niente. Solo la memoria e la riga di avvio chiedono un riavvio, e solo per quelli compare il pulsante. La versione di Minecraft non si applica con un riavvio ma con un aggiornamento, dalla scheda Stato.

        Ogni modifica tiene una copia del file con la data, perché una riga sbagliata qui impedisce al server di partire. "Togli" non cancella la riga: la commenta, così LinuxGSM torna al valore di fabbrica e si può sempre rimetterla.

        Un valore vuoto non è un valore di fabbrica: è una riga che il server esegue lo stesso. Per questo l'app non lo lascia salvare.

        L'elenco completo con le spiegazioni ufficiali:
        https://docs.linuxgsm.com/configuration/game-server-config
        """.trimIndent()
    )

    val MACRO = Page(
        "Le macro",
        """
        Una macro è una fila di comandi con un nome. Si tocca una volta e partono tutti, nell'ordine giusto.

        Serve perché quasi niente di quello che fa un amministratore è un comando solo: mettere il server in manutenzione vuol dire avvisare, aspettare, avvisare ancora, salvare. A mano si sbaglia l'ordine o si salta un pezzo, e capita sempre quando si ha fretta.

        Prima di partire ti mostra la lista esatta di cosa sta per succedere. Le macro che cambiano il mondo o disturbano chi sta giocando lo dicono nella conferma.

        Scriverne una tua: "Scrivi una macro nuova", poi un comando per riga, senza la barra iniziale.

        Due cose che nei comandi normali non esistono:
        • <giocatore>, <x>, <messaggio> — un buco fra parentesi angolari diventa una domanda quando lanci la macro. Puoi chiamarli come vuoi
        • !attendi 30 — non è un comando di Minecraft: è una pausa di 30 secondi. Serve per i conti alla rovescia

        Le macro già pronte non si rovinano: se ne apri una e la cambi, quello che salvi diventa una macro tua e l'originale resta dov'è.

        "Ferma la macro" interrompe subito, anche durante una pausa. I comandi già partiti sono già partiti: non tornano indietro.

        Le macro stanno sul telefono, non sul server: sono un modo tuo di comandare, non una cosa del mondo di Minecraft. Se cambi telefono si riscrivono.

        FATTELA SCRIVERE

        "Fattela scrivere: descrivi cosa vuoi" chiede la macro a un servizio di intelligenza artificiale. Serve una chiave del servizio, che si prende gratis in due minuti da Google AI Studio o da Groq: è tua, resta su questo telefono, e nell'app non ce n'è nessuna. Senza, tutto il resto funziona lo stesso.

        Al servizio arrivano la frase che scrivi tu, la versione di Minecraft e il mod loader. Non ci vanno indirizzi, password, log né nomi di giocatori.

        Quello che torna indietro non viene creduto sulla parola: i comandi che spengono il server o decidono chi può entrare (stop, op, ban, kick, whitelist…) vengono tolti, e ti viene detto quali. Poi la macro te la fa leggere prima di salvarla, e la puoi correggere. Non è una regola per te — dalla Console quei comandi li scrivi quando vuoi — è una regola per quello che scrive qualcun altro al posto tuo.

        L'elenco dei comandi di Minecraft, se ne cerchi uno:
        https://minecraft.wiki/w/Commands
        """.trimIndent()
    )

    val BLUEPRINT = Page(
        "Il progetto del server",
        """
        Il progetto è la ricetta di questo server, in un file solo: le impostazioni di LinuxGSM, quelle di Minecraft (server.properties), l'elenco dei mod e, se lo chiedi, chi è in whitelist e chi è operatore.

        Serve quando qualcuno vuole rifare il tuo stesso server sul proprio computer: gli mandi il file, lui lo importa e si ritrova tutto configurato come da te, senza copiare niente a mano.

        Cosa NON c'è dentro:
        • il mondo — sono gigabyte, si copia con un backup
        • i file dei mod — c'è la loro impronta, che su Modrinth ritrova il file esatto: stessa versione, stesso pacchetto
        • le password — né quella SSH, né quella di RCON, né i token di Discord o Telegram. Restano qui

        Il file è cifrato con una password che scegli tu e compresso. Mandala per un'altra via rispetto al file: se viaggiano insieme, la password non serve a niente.

        In importazione scegli cosa applicare, riquadro per riquadro. Porte, indirizzi e nome del server non vengono toccati: sul tuo computer sono diversi, e sovrascriverli spegnerebbe il server o lo farebbe accavallare a un altro.

        Whitelist e operatori si scrivono passando dalla console, quindi in quel momento il server deve essere acceso: gli UUID dei giocatori li cerca lui.

        Le impostazioni valgono dal riavvio successivo. Di ogni file toccato resta una copia con la data nel nome.

        Il file dei collegamenti — quello con host, utente e password — è un'altra cosa, e si esporta dalle Impostazioni.
        """.trimIndent()
    )

    val SERVERS = Page(
        "Profili salvati",
        """
        Questa è la lista dei profili salvati sul telefono: uno per ogni mondo che hai aperto almeno una volta.

        Di solito non serve venire qui: si usano i due passi del menu, "Collegamento al server Linux" e "Server installati". Questa pagina serve quando gestisci più computer o vuoi mettere le mani nei dettagli.

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
