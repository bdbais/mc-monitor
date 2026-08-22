# MC Monitor — manuale d'uso

App Android per amministrare un server Minecraft installato con **LinuxGSM**, via SSH.
Versione 1.23.

- [1. Installazione](#1-installazione)
- [2. Il nome e la password](#2-il-nome-e-la-password)
- [3. I due passi: il computer e i mondi](#3-i-due-passi-il-computer-e-i-mondi)
- [4. Configurare un server](#4-configurare-un-server)
- [5. Stato e controllo](#5-stato-e-controllo)
- [6. Le impostazioni del server](#6-le-impostazioni-del-server)
- [6bis. Le impostazioni tecniche di LinuxGSM](#6bis-le-impostazioni-tecniche-di-linuxgsm)
- [7. Il progetto: rifare questo server altrove](#7-il-progetto-rifare-questo-server-altrove)
- [8. Console](#8-console)
- [9. Le macro](#9-le-macro)
- [10. Giocatori](#10-giocatori)
- [11. Mappa](#11-mappa)
- [12. Mod e modpack](#12-mod-e-modpack)
- [13. Cambiare versione di Minecraft](#13-cambiare-versione-di-minecraft)
- [14. RCON](#14-rcon)
- [15. Aggiornamenti dell'app](#15-aggiornamenti-dellapp)
- [16. Se qualcosa non funziona](#16-se-qualcosa-non-funziona)

---

## 1. Installazione

Scarica l'APK dalla pagina delle release, abilita "Installa app sconosciute" per il
gestore file che usi e apri il file. Serve Android 8 o successivo.

L'app non richiede account, non usa servizi intermedi e parla solo con il tuo server.

## 2. Il nome e la password

Al primo avvio l'app chiede due cose e poi non le chiede più.

**Come ti chiami** — il nome dell'amministratore. Compare nel menu accanto al computer
collegato, e firma i messaggi nella chat fra amministratori.

**Una password per aprire l'app** — perché da qui si spegne il server, si decide chi può
entrare e si può cancellare un mondo intero: un telefono lasciato sul tavolo non deve
avere tutto questo a portata di dito.

Se il telefono ha impronta o riconoscimento del volto, l'app propone di usarli: aprendo
compare il lettore, e la password resta come alternativa quando l'impronta non va.

Si può anche scegliere **"Per ora senza password"**: l'app lo chiede una volta sola e poi
si apre liberamente. La protezione si accende quando si vuole dal menu, voce **Blocco e
amministratore**, dove si cambia anche il nome, si toglie la password (serve conoscerla) e
si decide dopo quanto tempo richiuderla: subito, due minuti, un quarto d'ora, oppure mai
finché l'app resta aperta.

> **La password non si recupera.** L'app non la conserva: ne tiene solo un'impronta, che
> non si può riportare indietro. Se la dimentichi l'unica strada è cancellare i dati
> dell'app da Android e ricominciare — e allora conviene aver esportato la configurazione
> o aver acceso il backup automatico, così i server si ritrovano tutti reimportando il file.

## 3. I due passi: il computer e i mondi

L'app si apre con un menu a sinistra (l'icona con le tre righe, in alto) diviso nei due
passi che servono davvero, nell'ordine in cui si fanno.

### Passo 1 — Collegamento al server Linux

<img src="store/screenshots/collegamento.png" width="320" alt="Collegamento al server Linux">

Qui si inseriscono solo i dati per entrare nel computer dove vivono i mondi:

| Campo | Esempio | Note |
|---|---|---|
| Indirizzo del computer | `mc.miodominio.it` | oppure un IP, tipo `192.168.1.10` |
| Porta | `22` | lasciala così se non ti dicono altro |
| Nome utente | `mcserver` | **deve essere l'utente che esegue LinuxGSM** |
| Password | | oppure una chiave, con "Uso una chiave al posto della password" |

**Collegati** prova davvero il collegamento e, se riesce, passa da solo al secondo passo.
**Non funziona** avvia la diagnostica: prova un pezzo alla volta (rete, porta, utente,
password, LinuxGSM) e dice a quale punto si ferma, con un rapporto da copiare.

**Cambia la password di questa utenza** esegue `passwd` sul computer; la password salvata
nell'app viene aggiornata subito e il nuovo accesso verificato riaprendo la connessione.

**Dopo una reinstallazione** — su un telefono senza niente dentro compare in cima
**Riprendi una configurazione salvata**: si sceglie il file esportato (o l'ultimo backup
automatico), si dà la sua password e tornano server, impostazioni e credenziali. Chi invece
aggiorna l'app da una versione precedente non deve fare nulla: i dati del server che stava
usando compaiono già compilati nel passo 1.

### Passo 2 — Server installati

<img src="store/screenshots/server-installati.png" width="320" alt="Server installati">

L'app cerca da sola le istanze LinuxGSM dentro la home dell'utente: non serve sapere in
quale cartella stanno né come si chiama lo script. Per ognuna mostra

- il quadratino verde se è accesa in questo momento, grigio se è spenta,
- la versione di Minecraft letta da `mcserver.cfg`,
- la porta a cui si collegano i giocatori e, se attiva, quella di RCON — **in rosso** dal
  secondo server in poi che usa lo stesso numero: due server sulla stessa porta non possono
  stare accesi insieme, e il secondo muore appena parte senza spiegare perché. Vale anche
  fra porta di gioco e RCON, perché il numero è lo stesso spazio,
- la riga **mod**, se il server è moddato: quante sono e con quale loader (Fabric o Forge).
  Toccandola si apre l'elenco completo delle mod installate,
- la cartella, in piccolo, scritta come `~/nome`.

**Apri** entra nel mondo scelto: da lì si accende, si spegne, si vedono giocatori, mappa
e mod. Trascina l'elenco verso il basso per rifare la ricerca.

Il **cestino** a destra cancella quel server dal computer: cartella, mondo, mod e backup.
Prima lo spegne, e chiede due conferme — la seconda riscrivendo il nome a mano. Non si
torna indietro, quindi se il mondo ti interessa fai prima una copia.

Se l'elenco è vuoto, su quel computer non c'è ancora niente di installato: **Crea un
nuovo server da zero** avvia l'installazione guidata (ci vuole tempo e banda).

### Manutenzione: LinuxGSM da aggiornare

Dopo la ricerca l'app confronta la versione degli script LinuxGSM installati con l'ultima
pubblicata. Se qualcuno è rimasto indietro compare un riquadro giallo con **Aggiorna
LinuxGSM**: un tocco lancia `update-lgsm` sulle istanze interessate e mostra cosa risponde.

Riguarda solo gli script che gestiscono il server, non Minecraft: il mondo, le mod e la
configurazione non vengono toccati. Vale la pena farlo, perché con LinuxGSM vecchio comandi
come `update` o `details` iniziano a fallire con errori che non dicono qual è la causa.

### Le altre voci del menu

<img src="store/screenshots/menu.png" width="320" alt="Il menu laterale">

| Voce | A cosa serve |
|---|---|
| **Crea un nuovo server** | installa una nuova istanza LinuxGSM, staccata dai due passi |
| **Profili salvati** | l'elenco classico dei profili, con Aggiungi, Modifica, Duplica, Rimuovi; utile se gestisci più computer |
| **Amministratori e messaggi** | chi altro sta comandando questo server e la chat fra voi |
| **Blocco e amministratore** | il tuo nome, la password dell'app e dopo quanto richiuderla |
| **Manuale d'uso** | apre questa pagina |
| **Informazioni** | versione, licenza e i progetti su cui l'app si appoggia |

### Amministratori e messaggi

Se il server lo gestite in più persone, ognuno con la sua copia dell'app, questa pagina
dice **chi c'è adesso** e permette di **scrivervi**.

Ogni telefono lascia un segnale sul computer una volta al minuto; l'app li rilegge e mostra
chi si è fatto vivo di recente, con un pallino verde. Se qualcuno sta facendo qualcosa di
delicato — avvio, arresto, riavvio — accanto al nome compare cosa, con il pallino giallo.
Sul pulsante degli amministratori, nella barra in alto dentro un server, compare un numero:
quanti **altri** ci sono in questo momento.

Nella stessa pagina c'è la chat. I messaggi restano sul computer del server, in
`~/.mcmonitor/chat.log`, e li legge chiunque abbia accesso a quell'utenza — cioè gli
amministratori, che è esattamente il punto. Non ci sono account: accanto a ogni messaggio
c'è il nome che ognuno si è dato al primo avvio.

L'interruttore **avvisa tutti** sotto la chat serve per i messaggi che devono trovare gli
altri anche quando non stanno guardando: sul telefono di chi non l'ha ancora letto compare
un **numero rosso sull'icona dell'app**, che sparisce appena apre la chat. Senza, il
messaggio resta lì ad aspettare che qualcuno passi di là.

Anche le note lasciate bannando o ammettendo un giocatore finiscono qui, segnate con il
nome di quel giocatore.

**Il semaforo.** Prima di avviare, fermare o riavviare il server, se c'è qualcun altro
collegato l'app lo dice e chiede conferma, con un pulsante per scrivergli invece di
procedere. Non blocca niente — decidi tu — ma toglie il caso peggiore: due persone che si
spengono il server a vicenda senza sapere l'una dell'altra.

Cambiare server chiude sessione SSH, RCON e azzera le scie sulla mappa: i dati di un
server non possono mescolarsi con quelli di un altro.

## 4. Configurare un server

<img src="store/screenshots/02-impostazioni.png" width="320" alt="Impostazioni">

Le impostazioni sono divise in sette passi numerati, dal nome del server fino alla mappa:
i primi tre servono sempre, gli altri sono facoltativi e si possono lasciare come sono.

| Campo | Esempio | Note |
|---|---|---|
| Nome | `Server di casa` | come lo vedi nell'elenco |
| Nome tecnico | `server1` | dà il nome alla sottocartella |
| Host o IP | `mc.miodominio.it` | accetta anche `host:2222` |
| Porta | `22` | porta SSH |
| Utente SSH | `mcserver` | **deve essere l'utente che esegue LinuxGSM** |
| Password | | vuota se usi la chiave |
| Chiave privata | `-----BEGIN OPENSSH PRIVATE KEY-----…` | incolla il file intero |
| Directory LinuxGSM | `~/server1` | sottocartella nella home dell'utente SSH |
| Script LinuxGSM | `mcserver` | nome dello script |
| serverfiles | *(vuoto)* | default `<dir>/serverfiles` |
| Sessione tmux | *(vuoto)* | default: nome dello script |
| URL mappa web | `http://host:8123` | Dynmap/BlueMap, opzionale |

Poi **Prova connessione** e **Salva**.

**Cambia la password dell'utente SSH** esegue passwd sul server. Se il profilo si
collega con la password, quella salvata viene aggiornata subito e il nuovo accesso
verificato riaprendo davvero la connessione: se la verifica fallisce l'app te lo dice
prima che tu chiuda l'applicazione.

L'utente SSH è il punto critico: LinuxGSM gira dentro una sessione `tmux` che appartiene
a un utente preciso, e i comandi alla console funzionano solo collegandosi con quello.

Al primo collegamento l'app memorizza l'impronta della chiave host e la verifica ogni
volta: se cambia, blocca la connessione e lo segnala.

## 5. Stato e controllo

<img src="store/screenshots/07-stato.png" width="320" alt="Stato del server">

Stato STARTED/STOPPED con pallino colorato, IP, porte, versione, uptime e l'output
completo di `lgsm details`. I pulsanti **Avvia**, **Ferma** e **Riavvia** chiedono
conferma quando l'azione disconnette i giocatori, e avvisano se in quel momento c'è un
altro amministratore collegato.

Più in basso: la versione di Minecraft configurata e l'elenco dei mod installati, che
porta alla scheda Mod.

L'indirizzo da dare a chi gioca ha sempre la porta scritta, anche quando è la 25565 di
default: senza, le app di messaggistica lo scambiano per l'indirizzo di un sito e lo
trasformano in un link che non porta da nessuna parte.

Sulla riga **Impostazioni del server** ci sono due pulsanti: la **cassa** apre il progetto
(sezione 7), la **matita** apre le impostazioni del gioco (sezione 6), da cui si arriva
anche a quelle tecniche di LinuxGSM.

Trascina verso il basso per aggiornare.

## 6. Le impostazioni del server

Il pulsante con la matita, nella scheda Stato.

Sono le impostazioni del **gioco**: difficoltà, messaggio di benvenuto, quanti giocatori
entrano, quanto lontano si vede, chi può collegarsi. Vivono in
`serverfiles/server.properties`, e fino alla 1.22 dall'app non si potevano toccare in
nessun modo.

Ogni voce ha il suo tipo, non è un campo di testo qualsiasi: la difficoltà è una scelta fra
quattro parole, la distanza di visuale un numero fra 3 e 32, il resto sono interruttori. Il
motivo è che il server **non protesta** per un valore sbagliato: scrivendo
`difficulty=medio` non succede niente, il server usa il valore di fabbrica, e chi l'ha
scritto resta convinto di aver cambiato qualcosa.

**Niente parte finché non premi Salva.** Le modifiche si accumulano — la riga cambiata si
segna con un puntino — e partono tutte insieme: una connessione, una copia di sicurezza del
file, un messaggio. Prima di scrivere ti viene mostrato l'elenco esatto, da cosa a cosa.

Difficoltà e whitelist l'app le scrive nel file **e** le manda al server, così cambiano
anche per chi sta giocando in quel momento. Se il server è spento la scrittura vale lo
stesso, e te lo dice invece di far credere che non sia successo niente.

Le quattro che contano di più su un server piccolo:

- **Difficoltà** — in pacifica i mostri non compaiono affatto
- **Solo chi è in whitelist** — la vera difesa di un server aperto su internet
- **Quanto lontano si vede** — il primo numero da abbassare quando il server singhiozza
- **Metti in pausa quando non c'è nessuno** — smette di consumare quando il mondo è vuoto

Le impostazioni delicate avvisano nella riga stessa: spegnendo il controllo degli account
Minecraft, per esempio, chiunque può entrare con il nome di un altro.

Dal 1.21.9 quattro impostazioni (fra cui il PVP) sono uscite da `server.properties` e sono
diventate regole di gioco. L'app se ne accorge dalla versione del server e le manda per la
strada giusta, invece di scrivere una riga che non farebbe niente.

Le voci che qui non ci sono — porte, indirizzi, messa a punto fine — restano nel file come
sono: questa schermata non le tocca, e lo dice.

## 6bis. Le impostazioni tecniche di LinuxGSM

In fondo alla schermata delle impostazioni.

Sono gli interruttori del programma che accende e spegne il server: memoria per Java,
versione da scaricare, quanti backup tenere, dove mandare gli avvisi. Vivono in
`lgsm/config-lgsm/<script>/<script>.cfg`.

**Se questa schermata è vuota è normale, e non vuol dire che il server non abbia
impostazioni.** LinuxGSM ha cinque file di configurazione in fila e questo è quello delle
tue modifiche: nasce vuoto. I valori veri — memoria 1024, quattro backup, log tenuti sette
giorni — stanno nel file di fabbrica, che non si tocca perché LinuxGSM lo riscrive a ogni
suo aggiornamento. Qui si scrivono soltanto le differenze.

Sotto ogni parametro c'è scritto **quando avrà effetto**. Quasi tutti — backup, log,
avvisi — LinuxGSM li rilegge da solo e non serve riavviare niente: il pulsante di riavvio
compare solo per la memoria e la riga di avvio, che sono le due che finiscono nel comando
con cui il server viene lanciato. La versione di Minecraft non si applica con un riavvio ma
con un aggiornamento, dalla scheda Stato.

**Togliere** un parametro non lo cancella: lo commenta. LinuxGSM torna al valore di
fabbrica e la riga resta lì a ricordare cosa c'era. Un valore **vuoto** invece non è un
valore di fabbrica: è una riga che il server esegue lo stesso, e `javaram=""` diventa
`java -XmxM -jar`, cioè un server che non parte più. Per questo l'app non lo lascia
salvare.

Prima di ogni modifica il file viene copiato con la data nel nome
(`mcserver.cfg.mcmonitor.bak.20260823...`).

L'elenco completo con le spiegazioni ufficiali è nella
[documentazione di LinuxGSM](https://docs.linuxgsm.com/configuration/game-server-config),
richiamata anche dall'aiuto della pagina.

## 7. Il progetto: rifare questo server altrove

Il pulsante con la cassa, nella scheda Stato.

Il progetto è la ricetta di questo server in un file solo: le impostazioni di LinuxGSM,
quelle di Minecraft (`server.properties`), l'elenco dei mod e, se lo chiedi, chi è in
whitelist e chi è operatore. Serve a chi vuole rifare il tuo stesso server sul proprio
computer senza copiare niente a mano.

**Cosa non c'è dentro:**

- il mondo — sono gigabyte, si copia con un backup
- i file dei mod — c'è la loro impronta sha1, che su Modrinth ritrova il file esatto:
  stessa versione, stesso pacchetto, non uno che si chiama allo stesso modo
- le password — né quella SSH, né quella di RCON, né i token di Telegram o il webhook di
  Discord. Sarebbero utilissimi al clone, e sarebbero anche il modo di regalare a un altro
  la chiave del proprio server senza accorgersene

Il file è compresso e cifrato con AES-GCM e una password che scegli tu (PBKDF2, 210.000
iterazioni). Mandala per un'altra via rispetto al file: se viaggiano insieme, non serve a
niente.

**In importazione** scegli cosa applicare, riquadro per riquadro, e vedi quanti valori
sono. Porte, indirizzi e nome del server non vengono toccati: sul tuo computer sono
diversi, e sovrascriverli spegnerebbe il server o lo farebbe accavallare a un altro.
I mod che su Modrinth non ci sono vengono elencati per nome, da copiare a mano.

Whitelist e operatori passano dalla console, quindi in quel momento il server deve essere
acceso: gli UUID dei giocatori li cerca lui.

Di ogni file toccato resta una copia con la data. Le impostazioni valgono dal riavvio.

> Il file dei **collegamenti** — host, utente, password SSH — è un'altra cosa, e si esporta
> dalle Impostazioni. Se sbagli file, l'app te lo dice invece di aprirlo vuoto.

## 8. Console

<img src="store/screenshots/06-console.png" width="320" alt="Console">

La coda di `logs/latest.log`, aggiornata ogni 6 secondi (l'interruttore in alto la ferma).

Accanto all'interruttore quattro pulsanti:

- **macro** — le file di comandi già pronte (sezione 9).
- **cerca** — apre una riga di ricerca che filtra le righe già scaricate, senza chiedere
  niente al server: scrivi `ERROR`, o il nome di un giocatore, e restano solo quelle. Di
  fianco è scritto quante righe sono rimaste. Si chiude con lo stesso pulsante.
- **a capo automatico** — le righe lunghe continuano sotto invece di uscire a destra. Utile
  per leggere, meno per confrontare le colonne del log: si accende e si spegne a piacere.
- **tutto schermo** — restano solo le righe del log: spariscono barra del titolo, schede,
  scorciatoie e campo dei comandi, e le righe vanno a capo da sole. Si esce con il pulsante
  in alto a destra o con il tasto indietro, e tutto torna com'era.

**In orizzontale** l'app si stringe da sola: la fila di scorciatoie diventa il pulsante
**Comandi** accanto a Invia, l'interruttore perde l'etichetta e le righe vanno a capo. Con
il telefono girato il log passa da due righe a sei, e a tutto schermo riempie lo schermo.

Sotto, il campo per inviare comandi e una riga di scorciatoie: `list`, `say`, `tp`,
`gamemode`, `time set day`, `weather clear`, `whitelist`, `kick`, `ban`, `save-all`…
Toccarne una **compila il campo senza inviare**: quelle che finiscono con uno spazio
aspettano l'argomento, le altre sono complete e basta premere Invia. È voluto: evita
un `ban` partito per sbaglio.

**Due tocchi su una riga** del log la selezionano e la copiano intera, anche la parte
che esce dallo schermo: serve per incollare un errore in una ricerca o in un messaggio.

I comandi viaggiano su `tmux send-keys`, oppure via RCON se l'hai attivato.

## 9. Le macro

Il pulsante con il fulmine, nella Console.

Una macro è una fila di comandi con un nome: si tocca una volta e partono tutti,
nell'ordine giusto. Serve perché quasi niente di quello che fa un amministratore è un
comando solo — mettere il server in manutenzione vuol dire avvisare, aspettare, avvisare
ancora, salvare — e a mano si sbaglia l'ordine o si salta un pezzo proprio quando si ha
fretta.

Prima di partire l'app mostra la lista esatta di cosa sta per succedere. Le macro che
cambiano il mondo o disturbano chi gioca lo dicono nella conferma.

**Già pronte**, una ventina, fra cui:

- **Manutenzione fra 5 minuti** — avvisa, conta alla rovescia, salva
- **Backup a caldo: prima / dopo** — `save-off` e `save-all flush`, poi `save-on`
- **Pulisci gli oggetti a terra** — la prima cosa da provare quando il server arranca
- **Chiudi il server ai nuovi** — whitelist accesa e avviso in chat
- **Kit di sopravvivenza**, **Rimetti in piedi qualcuno**, **Dai il benvenuto**
- e qualcuna per far divertire: fuochi d'artificio, pioggia di polli, invisibilità,
  super salto, notte di caccia

**Scriverne una tua**: "Scrivi una macro nuova", poi un comando per riga, senza la barra
iniziale. Due cose che nei comandi normali non esistono:

- `<giocatore>`, `<x>`, `<messaggio>` — un buco fra parentesi angolari diventa una domanda
  quando lanci la macro. Puoi chiamarli come vuoi
- `!attendi 30` — non è un comando di Minecraft: è una pausa di 30 secondi, per i conti
  alla rovescia

Le macro già pronte non si rovinano: se ne apri una e la cambi, quello che salvi diventa
una macro tua e l'originale resta dov'è.

**Ferma la macro** interrompe subito, anche durante una pausa. I comandi già partiti sono
già partiti: non tornano indietro. Se un comando fallisce la macro si ferma lì.

Le macro stanno sul telefono, non sul server.

### Fattela scrivere

**"Fattela scrivere: descrivi cosa vuoi"** chiede la macro a un servizio di intelligenza
artificiale: scrivi *"prepara il server per una gara di costruzione"* e ti torna indietro la
sequenza di comandi, con un nome e una descrizione.

Serve una chiave del servizio, gratuita, che si prende in due minuti:

- [Google AI Studio](https://aistudio.google.com/apikey) (Gemini)
- [Groq](https://console.groq.com/keys)

La chiave è tua e resta su questo telefono: **nell'app non ce n'è nessuna**, e senza tutto il
resto funziona lo stesso. Al servizio arrivano la frase che scrivi tu, la versione di
Minecraft e il mod loader — non indirizzi, non password, non log, non nomi di giocatori.

Quello che torna indietro non viene creduto sulla parola. Un modello può scrivere `stop`
mentre stanno giocando in venti, o `op` a un nome che non hai mai sentito: comandi
legittimi, che però non hai chiesto tu. Passano solo i verbi di una lista — parlare in chat,
cambiare tempo e meteo, dare oggetti ed effetti, teletrasportare, salvare — e quelli tolti
te li elenca invece di farli sparire in silenzio.

Poi la macro te la fa **leggere prima di salvarla**, con il pulsante per correggerla. Nasce
segnata come delicata, così la conferma prima di lanciarla lo ricorda.

Non è una regola per te: dalla Console `stop` e `ban` li scrivi quando vuoi. È una regola
per quello che scrive qualcun altro al posto tuo, e che nessuno ha riletto prima che
diventasse un pulsante da premere.

## 10. Giocatori

<img src="store/screenshots/03-giocatori.png" width="320" alt="Giocatori">

Tre sezioni: **lista d'attesa**, **online**, **whitelist e ban**.

**Lista d'attesa** — compare solo se serve: chi ha provato a entrare e non è né in
whitelist né bannato, con data dell'ultimo tentativo ed esito. Due pulsanti: **Ammetti**
o **Banna**. Appena decidi, il nome finisce in uno dei due file del server e sparisce.
Se sul server `white-list=false`, la card avvisa che ammettere qualcuno non impedisce
comunque agli altri di entrare.

**Online** — nome, coordinate X/Y/Z e dimensione di ciascun giocatore.

**Whitelist e ban** — accanto al titolo della whitelist c'è l'interruttore che la accende
e la spegne (`whitelist on|off`): spegnendola l'app chiede conferma, perché da quel momento
entra chiunque conosca l'indirizzo.

Quando **banni o ammetti** qualcuno puoi lasciare una nota. La ritrovano gli altri
amministratori aprendo quel giocatore, insieme al nome di chi l'ha scritta: prima ogni
volta si ricominciava da capo a chiedersi perché quel nome fosse in quella lista.

Accanto al nome di chi non è collegato compare **l'ultima volta che si è visto**, presa da
`latest.log` e dagli archivi compressi dei giorni scorsi.

**Toccando un nome** si apre il pannello completo:

- **Mostra sulla mappa e segui**
- **Cronologia chat e comandi** con data e ora, anche dai log dei giorni scorsi
- **Teletrasporta** a coordinate, verso un altro giocatore, o portando qualcuno da lui
- **Modalità di gioco**, **punto di rinascita**
- **Whitelist**, **op/deop**
- **Espelli** e **banna** con motivo, **rimuovi ban**

Le voci che richiedono il giocatore in gioco sono disattivate quando è offline.

## 11. Mappa

<img src="store/screenshots/04-mappa.png" width="320" alt="Mappa">

Piano X/Z navigabile: trascina per spostarti, pizzica o doppio tap per lo zoom. Griglia
dei chunk con passo automatico, spawn evidenziato, giocatori come punti colorati con la
**scia dei loro spostamenti**.

Filtro Overworld / Nether / End in alto. **Tracciamento live** interroga le posizioni
all'intervallo impostato nelle Impostazioni (default 6 s). **Inquadra** riporta tutti in
vista, **Azzera scie** ripulisce i percorsi.

Toccando un giocatore la mappa si aggancia e lo segue; se cambia dimensione il filtro si
adegua da solo. **Mappa web** apre Dynmap/BlueMap a tutto schermo, se configurata.

Le posizioni arrivano da `data get entity <nome> Pos`: serve un server vanilla, Paper o
Spigot dalla 1.13 in poi.

Quando la mappa è vuota dice **perché** lo è: nessuno collegato, nessuna posizione ricevuta
(succede sui server che non rispondono a quel comando, per esempio con certi mod), oppure
tutti in un'altra dimensione rispetto al filtro scelto. Erano tre casi diversi che
sembravano lo stesso guasto.

## 12. Mod e modpack

<img src="store/screenshots/05-mod.png" width="320" alt="Scheda Mod">

**Ambiente del server** — versione di Minecraft e mod loader, che l'app ricava da sola: la
versione dalla riga che il server scrive nel log all'avvio e, se il server non è mai
partito, da `mcversion` nella configurazione di LinuxGSM; il loader dai file presenti.
Accanto alla versione è scritto da dove viene, "in esecuzione" o "da configurazione".

**Se il server è vanilla** compare **Installa Fabric per Minecraft &lt;versione&gt;**: la
versione non viene chiesta, è quella del server. Il jar di avvio lo scarica il server da
meta.fabricmc.net (con curl) e `startparameters` di LinuxGSM viene modificato per usarlo,
tenendo una copia del file. Poi va riavviato. Solo se la versione è davvero indeterminabile
— server mai avviato e `mcversion="latest"` — l'app la chiede una volta.

**Cerca su Modrinth** — nessun account necessario: l'API pubblica non richiede login. Non
c'è niente da impostare: i mod vengono cercati per la versione e il loader del server, e
sotto il campo di ricerca è scritto quali sono. Se per quella combinazione non esce niente,
un pulsante propone di **cercare senza filtri**.

Il pulsante con la **stella** apre l'elenco dei **mod consigliati**: quelli che su un
server piccolo risolvono i problemi che si presentano per primi — il server che arranca, i
mostri che cancellano le costruzioni, i backup dimenticati — con scritto in una riga a cosa
serve ognuno. Toccandone uno parte la ricerca già compilata.

<img src="store/screenshots/05-mod-ricerca.png" width="320" alt="Ricerca su Modrinth">

Toccando un risultato scegli la versione; l'app mostra le dipendenze obbligatorie e può
installarle insieme al mod. Il file viene scaricato **dal server** e verificato con lo
sha1 dichiarato da Modrinth: se non coincide viene cancellato.

**Modpack** — scegli un `.mrpack` dal telefono. L'app lo carica sul server via SFTP, ne
legge `modrinth.index.json`, ti mostra versione, loader e quanti file verranno scaricati,
e segnala se il pacchetto non è compatibile con il server. Poi il server scarica i mod
elencati e copia le configurazioni contenute in `overrides/`. I file marcati come solo
client vengono esclusi.

**Mod installati** — ogni mod si può **disattivare** (rinominato in `.disabled`, si
recupera in un tocco) o **rimuovere**.

Dopo ogni modifica compare il pulsante per riavviare il server.

## 13. Cambiare versione di Minecraft

Nella scheda Stato, **Cambia versione** mostra l'elenco ufficiale delle release preso dal
manifesto di Mojang. Scegliendone una, l'app scrive `mcversion` nella configurazione
LinuxGSM (con copia di sicurezza) e lancia `./mcserver update`, che ferma il server,
scarica il jar e riparte.

Prima di procedere ti viene proposto **Backup e cambio**: usalo. Un mondo salvato con una
versione recente spesso non si riapre con una precedente, e l'app te lo segnala quando
stai tornando indietro.

## 14. RCON

Senza RCON l'app scrive nella console tmux e rilegge il log: ogni aggiornamento lascia
righe di servizio in `latest.log`. Con RCON le risposte arrivano subito e il log resta
pulito.

Nelle Impostazioni: **Genera password**, poi **Attiva RCON sul server e riavvia**. L'app
salva una copia di `server.properties`, scrive le impostazioni, riavvia, attende che la
porta risponda e prova l'autenticazione, mostrando ogni passo.

Il **tunnel SSH** è attivo di default: la porta RCON viaggia dentro la connessione SSH,
quindi non devi aprire porte sul firewall e la password — che il protocollo trasmette in
chiaro — non esce dal tunnel.

## 15. Aggiornamenti dell'app

All'avvio l'app controlla se sul repository c'è una release più recente. In tal caso
compare un banner con versione, dimensione e le note: **Aggiorna** scarica l'APK e apre
l'installer di sistema, **Più tardi** lo nasconde fino al prossimo avvio.

La prima volta Android chiede di autorizzare MC Monitor a installare app: è una conferma
di sistema, l'app non installa nulla da sola.

## 16. Se qualcosa non funziona

**Diagnostica connessione** (in Impostazioni) prova gli stadi separatamente — DNS, porta
TCP, handshake SSH, autenticazione — e poi verifica sul server tmux, le sessioni attive,
lo script LinuxGSM, RCON e i file di log. Il report è copiabile.

| Sintomo | Causa tipica |
|---|---|
| Timeout in connessione | telefono su rete mobile e server su IP privato, o porta non inoltrata |
| Autenticazione fallita | utente o chiave errati; la chiave va incollata intera |
| I comandi non arrivano alla console | SSH con un utente diverso da quello di LinuxGSM: `tmux ls` non vede la sessione |
| Nessuna posizione dei giocatori | server precedente alla 1.13, oppure server in pausa perché vuoto (`pause-when-empty-seconds`) |
| Chat vuota | manca `zgrep` sul server: si legge solo `latest.log` |
| Mod non caricati | server vanilla senza mod loader |

---

Progetto open source, licenza Apache 2.0 — github.com/bdbais/mc-monitor
Non affiliato a Mojang Studios o Microsoft.
