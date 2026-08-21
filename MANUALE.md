# MC Monitor — manuale d'uso

App Android per amministrare un server Minecraft installato con **LinuxGSM**, via SSH.
Versione 1.17.

- [1. Installazione](#1-installazione)
- [2. I due passi: il computer e i mondi](#2-i-due-passi-il-computer-e-i-mondi)
- [3. Configurare un server](#3-configurare-un-server)
- [4. Stato e controllo](#4-stato-e-controllo)
- [5. Console](#5-console)
- [6. Giocatori](#6-giocatori)
- [7. Mappa](#7-mappa)
- [8. Mod e modpack](#8-mod-e-modpack)
- [9. Cambiare versione di Minecraft](#9-cambiare-versione-di-minecraft)
- [10. RCON](#10-rcon)
- [11. Aggiornamenti dell'app](#11-aggiornamenti-dellapp)
- [12. Se qualcosa non funziona](#12-se-qualcosa-non-funziona)

---

## 1. Installazione

Scarica l'APK dalla pagina delle release, abilita "Installa app sconosciute" per il
gestore file che usi e apri il file. Serve Android 8 o successivo.

L'app non richiede account, non usa servizi intermedi e parla solo con il tuo server.

## 2. I due passi: il computer e i mondi

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
| **Manuale d'uso** | apre questa pagina |
| **Informazioni** | versione, licenza e i progetti su cui l'app si appoggia |

Cambiare server chiude sessione SSH, RCON e azzera le scie sulla mappa: i dati di un
server non possono mescolarsi con quelli di un altro.

## 3. Configurare un server

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

## 4. Stato e controllo

<img src="store/screenshots/07-stato.png" width="320" alt="Stato del server">

Stato STARTED/STOPPED con pallino colorato, IP, porte, versione, uptime e l'output
completo di `lgsm details`. I pulsanti **Avvia**, **Ferma** e **Riavvia** chiedono
conferma quando l'azione disconnette i giocatori.

Più in basso: la versione di Minecraft configurata e l'elenco dei mod installati.

Trascina verso il basso per aggiornare.

## 5. Console

<img src="store/screenshots/06-console.png" width="320" alt="Console">

La coda di `logs/latest.log`, aggiornata ogni 6 secondi (l'interruttore in alto la ferma).

Sotto, il campo per inviare comandi e una riga di scorciatoie: `list`, `say`, `tp`,
`gamemode`, `time set day`, `weather clear`, `whitelist`, `kick`, `ban`, `save-all`…
Toccarne una **compila il campo senza inviare**: quelle che finiscono con uno spazio
aspettano l'argomento, le altre sono complete e basta premere Invia. È voluto: evita
un `ban` partito per sbaglio.

I comandi viaggiano su `tmux send-keys`, oppure via RCON se l'hai attivato.

## 6. Giocatori

<img src="store/screenshots/03-giocatori.png" width="320" alt="Giocatori">

Tre sezioni: **lista d'attesa**, **online**, **whitelist e ban**.

**Lista d'attesa** — compare solo se serve: chi ha provato a entrare e non è né in
whitelist né bannato, con data dell'ultimo tentativo ed esito. Due pulsanti: **Ammetti**
o **Banna**. Appena decidi, il nome finisce in uno dei due file del server e sparisce.
Se sul server `white-list=false`, la card avvisa che ammettere qualcuno non impedisce
comunque agli altri di entrare.

**Online** — nome, coordinate X/Y/Z e dimensione di ciascun giocatore.

**Toccando un nome** si apre il pannello completo:

- **Mostra sulla mappa e segui**
- **Cronologia chat e comandi** con data e ora, anche dai log dei giorni scorsi
- **Teletrasporta** a coordinate, verso un altro giocatore, o portando qualcuno da lui
- **Modalità di gioco**, **punto di rinascita**
- **Whitelist**, **op/deop**
- **Espelli** e **banna** con motivo, **rimuovi ban**

Le voci che richiedono il giocatore in gioco sono disattivate quando è offline.

## 7. Mappa

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

## 8. Mod e modpack

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

## 9. Cambiare versione di Minecraft

Nella scheda Stato, **Cambia versione** mostra l'elenco ufficiale delle release preso dal
manifesto di Mojang. Scegliendone una, l'app scrive `mcversion` nella configurazione
LinuxGSM (con copia di sicurezza) e lancia `./mcserver update`, che ferma il server,
scarica il jar e riparte.

Prima di procedere ti viene proposto **Backup e cambio**: usalo. Un mondo salvato con una
versione recente spesso non si riapre con una precedente, e l'app te lo segnala quando
stai tornando indietro.

## 10. RCON

Senza RCON l'app scrive nella console tmux e rilegge il log: ogni aggiornamento lascia
righe di servizio in `latest.log`. Con RCON le risposte arrivano subito e il log resta
pulito.

Nelle Impostazioni: **Genera password**, poi **Attiva RCON sul server e riavvia**. L'app
salva una copia di `server.properties`, scrive le impostazioni, riavvia, attende che la
porta risponda e prova l'autenticazione, mostrando ogni passo.

Il **tunnel SSH** è attivo di default: la porta RCON viaggia dentro la connessione SSH,
quindi non devi aprire porte sul firewall e la password — che il protocollo trasmette in
chiaro — non esce dal tunnel.

## 11. Aggiornamenti dell'app

All'avvio l'app controlla se sul repository c'è una release più recente. In tal caso
compare un banner con versione, dimensione e le note: **Aggiorna** scarica l'APK e apre
l'installer di sistema, **Più tardi** lo nasconde fino al prossimo avvio.

La prima volta Android chiede di autorizzare MC Monitor a installare app: è una conferma
di sistema, l'app non installa nulla da sola.

## 12. Se qualcosa non funziona

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
