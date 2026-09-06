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

    val AVVIO = Page(
        "Perché non è partito",
        """
        Quando il server non riparte, questa è la schermata da aprire.

        La scheda Console non serve, in quel momento: mostra il log del gioco, che è quello dell'ultimo avvio riuscito. Il motivo vero sta in altri due file, che l'app fino alla 1.25 non guardava mai.

        In cima c'è LA RIGA DI AVVIO: il comando esatto con cui LinuxGSM lancia il server. Nove volte su dieci il guasto si vede lì, e prima non era visibile da nessuna parte. Se contiene due volte "-jar", o delle opzioni di memoria dopo il nome del programma, è quella la causa.

        Sotto, il motivo probabile in italiano e il pulsante che porta dove si ripara. I motivi riconosciuti sono quelli che capitano davvero: programma non trovato, riga di avvio sbagliata, Java troppo vecchio, memoria finita, mod incompatibili, condizioni d'uso non accettate, porta occupata.

        Se non riconosce niente lo dice, invece di inventare: le ultime righe dei log sono lì sotto, e quasi sempre la risposta è nelle ultime venti.

        I QUATTRO PEZZI

        • Cosa ha deciso LinuxGSM — il suo registro: se ha rinunciato ad avviare, qui c'è il perché
        • Cosa ha detto il server — quello che ha stampato Java prima di morire
        • Log di gioco — l'ultimo avvio riuscito; se il server non parte, è vecchio
        • Segnali di stato — i file che dicono se era partito, se è stato fermato apposta o se è caduto

        I primi due vengono riscritti a ogni avvio, quindi contengono sempre l'ultimo tentativo.

        "Copia tutto" mette il quadro completo negli appunti, da incollare a chi ti sta aiutando. Ci sono i percorsi del tuo server, non le password.
        """.trimIndent()
    )

    val SICUREZZA = Page(
        "Il controllo di sicurezza",
        """
        Un giudizio in una parola — alta, media o bassa — su quanto è chiuso il tuo server, con l'elenco di cosa lo abbassa e come si sistema.

        Non è un esame completo e non pretende di esserlo: guarda le poche cose che su un server piccolo fanno la differenza fra "ci entrano i tuoi amici" e "ci entra chiunque abbia trovato l'indirizzo". Legge solo la configurazione: non prova a entrare nel server e non manda niente fuori dal telefono.

        Il voto è severo di proposito. Basta una cosa grave per farlo scendere in fondo, e non è severità gratuita: su queste cose non si fa la media. Un server con la whitelist spenta non è "abbastanza sicuro" perché il resto è a posto — è aperto.

        LE DUE CHE CONTANO PIÙ DI TUTTE

        • Il controllo degli account Minecraft. Spento, chiunque può entrare con il nome di un altro, anche il tuo. Si spegne solo su una rete di casa isolata
        • La whitelist. Spenta, entra chiunque conosca l'indirizzo — e l'indirizzo gira più di quanto si pensi: ci sono motori di ricerca che scandagliano internet in cerca di server Minecraft aperti

        Poi guarda la password di RCON (chi ce l'ha comanda il server), come l'app ci parla, i blocchi comando, la zona protetta allo spawn, con quale utente ti colleghi al computer, e se l'app sul telefono ha una password.

        Toccando una voce si arriva dove si sistema. Tornando indietro il voto si rifà da solo.
        """.trimIndent()
    )

    val PROVVEDIMENTI = Page(
        "Su più server insieme",
        """
        Chi ha due mondi sulla stessa macchina non ha due comunità: ha una comunità e due mondi. Bannare un vandalo di là e non di qua vuol dire che fra dieci minuti è di qua.

        Per questo ammetti, banna, sbanna e togli dalla whitelist chiedono prima su quali altri server ripeterlo. Quelli sullo stesso computer arrivano già spuntati; gli altri no.

        Alla fine l'app dice dove è andata e dove no, con il motivo. Riuscire a metà è normale: un server è spento, un altro non risponde. Quelli non fatti restano come prima.

        Se hai sbagliato bersaglio, "Annulla tutto" disfa il provvedimento esattamente dove era riuscito.

        "Fatto" vuol dire quanto l'app sa. Senza RCON parla alla console e non sente la risposta: sa che il comando è arrivato, non che il server l'abbia accettato. Con RCON acceso la risposta si legge, e un rifiuto finisce fra i non fatti.

        NON SI PROPAGANO

        • Il ban di un indirizzo (ban-ip): colpisce un indirizzo, che su una rete di casa è di tutta la famiglia
        • Espelli, op e deop: riguardano quel momento e quel mondo

        Il server di destinazione dev'essere acceso: il comando passa dalla console, e per mettere qualcuno in whitelist il server deve chiedere a Mojang chi è.
        """.trimIndent()
    )

    val ALLINEAMENTO = Page(
        "Portare le liste altrove",
        """
        Ammettere e bannare su più server vale per i provvedimenti nuovi. Ma se il secondo mondo è arrivato dopo, la sua whitelist è vuota e la sua lista ban pure, e tutto quello che hai deciso negli anni sta solo di qua.

        Il pulsante "Porta le liste su un altro server" confronta le due liste e ti dice chi manca di là, con i nomi, prima di toccare qualcosa.

        Le liste si leggono dai file e non dalla console, quindi puoi guardare cosa c'è di là anche a server spento. Per scriverle il server dev'essere acceso.

        DUE REGOLE

        • Si aggiunge soltanto: da quel server non viene tolto nessuno. Chi è ammesso di qua e non di là può esserlo per una ragione
        • Chi è ammesso su un server e bannato sull'altro non viene toccato: l'app te lo fa vedere e si ferma lì

        Tutte e due per lo stesso motivo: non disfare decisioni che ha preso qualcuno.
        """.trimIndent()
    )

    val INVENTARIO = Page(
        "L'inventario di un giocatore",
        """
        Cosa ha addosso e nello zaino, disposto come nel gioco: armatura, mano secondaria, cintura, zaino, baule dell'End. Più vita, fame, livello, dimensione e posizione.

        DA DOVE ARRIVA

        Da `world/playerdata/<identificativo>.dat`, il file che Minecraft scrive per ogni giocatore. Lo riscrive quando quel giocatore esce e a ogni salvataggio del mondo.

        Quindi per chi è collegato in quel momento il file è vecchio: l'app chiede prima al server di salvare, aspetta, e poi legge. Se il server non risponde te lo dice, invece di mostrarti l'inventario di ieri come se fosse di adesso.

        Il nome viene tradotto in identificativo leggendo `usercache.json` sul server, dove finisce chi è entrato almeno una volta. Non viene chiesto niente a Mojang: il nome di una persona non esce dal tuo server.

        PERCHÉ NON CI SONO LE ICONE

        Le texture di Minecraft sono di Mojang e non si possono mettere dentro un'app. C'è il nome dell'oggetto, la quantità, un simbolo se è incantato, e la resistenza che resta quando l'oggetto si consuma.

        Si guarda e basta: da qui non si tocca niente.
        """.trimIndent()
    )

    val POSTA = Page(
        "La posta",
        """
        Un messaggio lasciato a un giocatore che in quel momento non c'è. Gli arriva in chat quando rientra, con scritto che gliel'ha lasciato l'amministratore e di che giorno era.

        Serve per le cose che non vale la pena rincorrere: "ho spostato il tuo baule", "domani il server è fermo un'ora", "ho sistemato la casa che ti avevano bruciato". Prima bisognava aspettare di beccarlo online.

        DOVE STA

        I messaggi stanno sul computer del server, non sul telefono. È l'unica scelta che funziona davvero: nel momento in cui il giocatore entra il tuo telefono è in tasca o spento, e un messaggio che parte solo se hai l'app aperta non è una posta, è una coincidenza.

        LA CONSEGNA

        Va accesa una volta sola, con l'interruttore in questa schermata. L'app mette sul computer un piccolo script e una riga di cron che lo lancia ogni minuto. Quando non c'è niente in attesa lo script non dice niente al server: legge le righe di registro nuove per tenere il conto di chi c'è, e si ferma lì.

        Quando invece c'è posta, guarda nel registro del server chi è entrato e chi è uscito, e consegna a chi c'è. Non chiede niente alla console: la risposta a una domanda finirebbe nello stesso registro dove finisce la chat, e un giocatore potrebbe scriverci quello che vuole.

        "Consegnato" vuol dire che il server l'ha confermato, non che il comando sia stato accettato. Se il server risponde che quel giocatore non c'è, il messaggio torna in coda. Se non dice né l'una né l'altra cosa — server lento o non standard — il messaggio si considera partito ma nel registro resta scritto "non confermato", con il testo per intero.

        Con il server fermo, o se la consegna non parte, il messaggio resta in coda e ci si riprova. Il caso "non confermato" è l'unico in cui potrebbe non essere arrivato a nessuno: è il prezzo per non risussurrarlo a ogni ingresso per sempre. Il testo però resta nel registro, e si riscrive da lì.

        Del crontab l'app tiene una copia di com'era prima, e quello che c'è dentro di altri non lo tocca. Spegnendo la consegna i messaggi in attesa restano dove sono.

        BUONO A SAPERSI

        • Se scrivi a qualcuno che è collegato in quel momento, l'app te lo dice e ti offre di scrivergli subito
        • "Cosa è già partito" apre il registro delle consegne, con il testo per intero: serve per le righe segnate "non confermato"
        • Un messaggio si può togliere finché non è partito: toccalo nell'elenco
        • Oltre 50 messaggi in attesa l'app si ferma: vuol dire che non li sta consegnando nessuno, e allungare la fila non serve
        • Il testo viene tagliato a 200 caratteri, che è quanto la chat del gioco mostra comunque
        """.trimIndent()
    )

    val RESTORE = Page(
        "Rimettere a posto",
        """
        Ogni volta che l'app modifica un file del tuo server ne lascia prima una copia, con la data nel nome. Questa schermata è il posto da cui quelle copie si rimettono.

        Serve quando qualcosa smette di funzionare dopo una modifica fatta da qui: una riga sbagliata nelle impostazioni tecniche, un'installazione di Fabric andata storta, un valore cambiato che non era quello giusto. Prima bisognava collegarsi al computer e sapere cosa cercare.

        Toccando una copia vedi PRIMA cosa cambierebbe: le righe con il meno spariscono, quelle con il più tornano. Solo dopo decidi.

        Rimettere una copia lascia a sua volta una copia di com'era: se torni indietro dalla cosa sbagliata, puoi tornare avanti.

        Le copie riguardano solo i file che l'app tocca: la configurazione di LinuxGSM e server.properties. Il mondo, i mod e i mondi salvati non c'entrano — per quelli c'è il backup.

        Dopo aver rimesso un file, riavvia il server perché lo rilegga.

        Le copie restano sul computer e non si cancellano da qui. Occupano pochissimo: sono file di testo.
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
        
        RIMETTERE UNA COPIA

        Tocca una copia nell'elenco. È l'unica cosa che l'app fa che butta via il lavoro di qualcuno, quindi prima ti fa vedere cosa c'è dentro l'archivio e poi chiede conferma una seconda volta.

        • Il server deve essere fermo: se è acceso non fa niente
        • Il mondo di adesso non viene cancellato, viene spostato di fianco con la data nel nome. Lo togli tu quando sei sicuro
        • Torna indietro tutta la cartella del gioco: mondo, mod, server.properties. Restano di adesso soltanto le impostazioni di LinuxGSM, così una riparazione della riga di avvio non se ne va
        • Se l'estrazione fallisce a metà, tutto torna com'era prima
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

    val RCON_DIRETTO = Page(
        "Collegamento diretto a RCON",
        """
        Serve per i server a cui non hai accesso al computer: uno su un pannello di hosting, quello di un amico, o un Minecraft su Windows dove SSH non c'è proprio.

        Ti bastano tre cose: l'indirizzo, la porta di RCON (quasi sempre 25575) e la password di RCON. Se il server è nella tua rete di casa e non sai l'indirizzo, "Cerca nella rete" lo trova da solo: bussa a ogni indirizzo e chiede la stessa cosa che chiede il gioco per riempire l'elenco dei multigiocatore.

        COSA FUNZIONA
        Comandi con la risposta vera del server, chi è online, whitelist e ban, op e kick, teletrasporto, modalità di gioco, ora e meteo, il seed del mondo, la mappa con le posizioni dei giocatori, e le macro.

        COSA NO
        Accendere e spegnere il server, il registro, i backup e il ripristino, le mod, server.properties, i parametri di LinuxGSM, l'inventario, la cronologia della chat. Vogliono tutte i file di quel computer, e RCON i file non li dà.

        La scheda Console qui non mostra il registro — quello è un file — ma quello che chiedi e quello che il server risponde. Per molte cose è meglio: la risposta arriva subito invece di doverla cercare fra le righe.

        UNA COSA DA SAPERE
        RCON manda la password in chiaro, senza cifratura. Nella tua rete di casa è un rischio piccolo. Su internet no: chi sta nel mezzo la legge, e con quella password possiede il server — può fermarlo, darsi i permessi, cancellare.

        Se devi comandare un server via internet, la strada giusta è il collegamento SSH: lì RCON viaggia dentro il tunnel e la password non esce mai allo scoperto. Se puoi farlo solo così, usa una password RCON lunga e usata soltanto lì.
        """.trimIndent()
    )

    val RCON_WINDOWS = Page(
        "Attivare RCON su un Minecraft per Windows",
        """
        Sono cinque minuti, e si fa una volta sola.

        1. FERMA IL SERVER
        Nella finestra del server scrivi stop e premi Invio, oppure chiudila dal pulsante. Non farlo mentre gira: il server riscriverebbe il file salvandoci sopra le tue modifiche.

        2. APRI server.properties
        Sta nella stessa cartella del file .jar del server. Se non c'è, il server non è mai partito: fallo partire una volta, accetta la EULA (eula.txt, metti eula=true) e fermalo di nuovo.
        Si apre col Blocco note: tasto destro, Apri con, Blocco note. È un file di testo normale.

        3. CAMBIA TRE RIGHE
        Cerca queste voci e mettile così — se una non c'è, aggiungila in fondo:

        enable-rcon=true
        rcon.port=25575
        rcon.password=

        Dopo rcon.password= scrivi una password lunga, senza spazi. Non lasciarla vuota: con la password vuota il server non accende RCON e basta, senza dirti perché.
        Facoltativa ma consigliata: broadcast-rcon-to-ops=false, così i comandi che mandi dall'app non compaiono nella chat degli operatori.

        4. SALVA E RIAVVIA
        Salva col Blocco note (Ctrl+S) e riavvia il server. Nella finestra deve comparire una riga tipo "RCON running on 0.0.0.0:25575". Se non compare, la password era vuota o c'è uno spazio di troppo.

        5. IL FIREWALL DI WINDOWS
        Serve solo se il telefono non è sullo stesso computer, cioè quasi sempre.
        Pannello di controllo, Windows Defender Firewall, Impostazioni avanzate, Regole connessioni in entrata, Nuova regola, Porta, TCP, porta specifica 25575, Consenti connessione.
        Spunta solo Privato. NON spuntare Pubblico: quella è la regola che apre la porta anche quando il portatile è attaccato al wifi di un bar.

        6. L'INDIRIZZO
        Nella finestra del prompt dei comandi (tasto Windows, scrivi cmd) dai ipconfig e leggi "Indirizzo IPv4": è quello da scrivere nell'app, tipo 192.168.1.20.

        SE NON SI COLLEGA
        • "Connessione rifiutata": il server non ha acceso RCON — ricontrolla il punto 4.
        • Non risponde e basta: è il firewall, punto 5.
        • "Password errata": nel file c'è uno spazio prima o dopo la password.
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

    val MODO = Page(
        "Semplice o esperto",
        """
        Decide quanta app vedi. Si cambia quando vuoi, dall'alto delle impostazioni del server: non è una scelta definitiva e non tocca niente sul server.

        SEMPLICE

        Resta quello che serve per far girare un server: accendere e spegnere, vedere chi c'è, ammettere e bannare, backup, mod, mappa, il controllo di sicurezza e la posta. È il modo giusto se il server ce l'hai per giocarci.

        Spariscono la console grezza del server, i comandi di LinuxGSM, i parametri tecnici e RCON. Non spariscono dal server: semplicemente l'app non te li mette davanti.

        ESPERTO

        Vedi tutto, comprese le schermate da cui si può impedire al server di ripartire scrivendo la riga sbagliata.

        Chi usava l'app da prima la ritrova com'era: se avevi già dei server configurati parte da esperto, perché togliere di colpo delle funzioni a chi le usa sarebbe peggio che mostrarne troppe a chi comincia.
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

}
