# MC Monitor

App Android per gestire un server Minecraft installato con **LinuxGSM**, via **SSH**.

APK pronto all'uso: **`MC-Monitor-1.18.apk`** (firmato, `minSdk 26` / Android 8+, `targetSdk 35`).

**[Manuale d'uso completo](MANUALE.md)** · [Release e APK](https://github.com/bdbais/mc-monitor/releases)

<p>
<img src="store/screenshots/collegamento.png" width="200">
<img src="store/screenshots/server-installati.png" width="200">
<img src="store/screenshots/07-stato.png" width="200">
<img src="store/screenshots/04-mappa.png" width="200">
</p>

## Installazione

1. Copia l'APK sul telefono.
2. Abilita "Installa app sconosciute" per il gestore file / browser che usi.
3. Apri l'APK e installa.

## I due passi

L'app si apre su un menu laterale diviso in due sole voci, nell'ordine in cui servono.

**1. Collegamento al server Linux** — indirizzo, porta, utente e password (o chiave) del
computer. Nient'altro: dove stiano i mondi non lo chiede. "Collegati" prova davvero la
connessione; "Non funziona" avvia la diagnostica a stadi e dice dove si ferma.

**2. Server installati** — l'app cerca le istanze LinuxGSM nella home dell'utente
(`lgsm/config-lgsm/<script>`) e le elenca con stato acceso/spento, versione di Minecraft,
porta di gioco, mod installate e cartella. "Apri" entra in quella scelta; il profilo completo
viene creato al primo accesso, e se esiste già viene riusato con le sue impostazioni di RCON,
notifiche e mappa. Trascinando l'elenco verso il basso si rifà la ricerca.

In questa schermata:

- le **porte** vanno in rosso dal secondo server in poi che le usa — sia quella di gioco sia
  quella di RCON, che pescano dallo stesso spazio di numeri: due istanze sulla stessa porta
  non possono stare accese insieme, e la seconda muore appena parte;
- la riga **mod** dice quante sono e con quale loader, e toccandola mostra l'elenco;
- il **cestino** cancella l'istanza dal computer — spegnimento, due conferme di cui una con
  il nome da riscrivere, poi `rm -rf` della cartella dopo che il server ha ricontrollato che
  sia davvero un'istanza LinuxGSM;
- un riquadro di **manutenzione** compare quando gli script LinuxGSM sono più vecchi
  dell'ultima release pubblicata, con un pulsante che lancia `update-lgsm`.

Su un telefono appena installato il passo 1 offre **Riprendi una configurazione salvata**:
si sceglie il file esportato (o l'ultimo backup automatico), si dà la password e tornano
server e impostazioni. Aggiornando da una versione precedente non serve fare niente: le
credenziali del server in uso diventano da sole l'utenza del passo 1.

Sotto "Altro" restano le voci per chi sa cosa sta facendo: **Crea un nuovo server**
(installazione da zero), **Profili salvati** (l'elenco classico con Aggiungi, Modifica,
Duplica, Rimuovi), **Manuale d'uso** e **Informazioni**.

Cambiare server chiude sessione SSH, connessione RCON e azzera le scie in memoria: nessun
dato di un server può finire mescolato con quelli di un altro.

## Configurare un server

Scheda **Impostazioni** (il campo **Nome** è quello che vedi nell'elenco):

| Campo | Esempio | Note |
|---|---|---|
| Host o IP | `mc.miodominio.it` | del server Linux |
| Porta | `22` | porta SSH |
| Utente SSH | `mcserver` | l'utente proprietario di LinuxGSM |
| Password | | lascia vuoto se usi la chiave |
| Chiave privata OpenSSH | `-----BEGIN OPENSSH PRIVATE KEY----- …` | incolla il file intero |
| Directory LinuxGSM | `/home/mcserver` | dove sta lo script |
| Script LinuxGSM | `mcserver` | il nome dello script LinuxGSM |
| serverfiles | *(vuoto)* | default `<dir>/serverfiles` |
| URL mappa web | `http://mc.miodominio.it:8123` | Dynmap / BlueMap / squaremap, opzionale |

Poi **Prova connessione** → **Salva**.

Se qualcosa non va, **Diagnostica connessione** prova gli stadi uno per uno — DNS, apertura
TCP della porta, handshake SSH, autenticazione — e poi verifica sul server tmux, le sessioni
attive, lo script LinuxGSM, il supporto al comando `send` e i file di log. Il report è
copiabile negli appunti.

Il campo host accetta anche `host:2222` o `ssh://host`: l'app separa da sola la porta.

Il fingerprint della chiave host viene memorizzato al primo collegamento (TOFU): se in
seguito cambia, l'app blocca la connessione e lo segnala. "Dimentica fingerprint host"
lo riazzera dopo una reinstallazione legittima del server.

## Cosa fa

- **Stato** — `lgsm details`: stato STARTED/STOPPED, IP, porte, versione, uptime, output completo. Pulsanti **Avvia / Ferma / Riavvia** (`lgsm start|stop|restart`), con conferma sulle azioni distruttive.
- **Console** — coda di `logs/latest.log` (fallback sul console log di LinuxGSM), aggiornamento automatico ogni 6 s, e invio comandi alla console del server. La riga di scorciatoie sopra il campo di testo compila il comando al posto tuo: quelle che finiscono con uno spazio (`tp `, `kick `…) aspettano l'argomento e aprono la tastiera, le altre sono complete e basta premere **Invia**.
- **Giocatori** — chi è online con coordinate X/Y/Z e dimensione; whitelist e lista ban lette da `whitelist.json` e `banned-players.json`, con aggiunta/rimozione. Toccando un nome (online, in whitelist o bannato) si apre il pannello con tutte le operazioni — vedi sotto.
- **Mod** — cosa è installato sul server, ricerca su Modrinth con installazione lato server e verifica sha1, importazione di modpack `.mrpack`, installazione di Fabric su un server vanilla. Vedi [Mod e modpack](MANUALE.md#8-mod-e-modpack).
- **Versione** — cambio della versione di Minecraft dall'elenco ufficiale Mojang, scrivendo `mcversion` nella configurazione LinuxGSM e lanciando `update`, con backup opzionale del mondo.
- **Mappa** — piano X/Z navigabile (trascina, pizzica, doppio tap) con griglia dei chunk, spawn, giocatori e **scia degli spostamenti** aggiornata in tempo reale; filtro Overworld / Nether / End. Il pulsante **Mappa web** apre Dynmap/BlueMap a tutto schermo, se configurata.

### Lista d'attesa

In cima alla scheda Giocatori compare, solo quando serve, la card **In attesa di
approvazione**: chi risulta nei log come tentativo di accesso e non è né in `whitelist.json`
né in `banned-players.json`. Per ognuno la data dell'ultimo tentativo, l'esito (respinto
dalla whitelist / entrato / tentativo) e l'UUID, con due pulsanti: **Ammetti (whitelist)**
e **Banna**. Toccando la riga si apre il pannello completo del giocatore.

Non c'è nessuna lista da mantenere: appena decidi, il nome finisce in uno dei due file del
server e sparisce da qui. Se sul server `white-list=false`, la card lo segnala — ammettere
qualcuno non impedirebbe comunque agli altri di entrare.

### Pannello del giocatore

Si apre toccando un nome nella scheda Giocatori:

- **Mostra sulla mappa e segui** — porta alla scheda Mappa agganciata a quel giocatore: a ogni aggiornamento la vista lo ricentra e traccia la scia. Se passa nel Nether o nell'End il filtro dimensione si adegua da solo. "Smetti di seguire" sblocca la vista.
- **Cronologia chat e comandi** — tutte le sue righe di chat e i comandi digitati, con data e ora, raggruppati per giorno.
- **Teletrasporta** — a coordinate (precompilate con la posizione attuale), verso un altro giocatore online, oppure portando un altro giocatore da lui.
- **Modalità di gioco** — sopravvivenza, creativa, avventura, spettatore.
- **Punto di rinascita** — imposta lo spawn del giocatore sulla sua posizione attuale.
- **Permessi** — whitelist aggiungi/rimuovi, op/deop.
- **Moderazione** — espelli e banna (entrambi con motivo facoltativo), rimuovi il ban.

Le voci che richiedono il giocatore in gioco (teletrasporto, modalità, espulsione) sono
disattivate quando è offline.

### Come vengono lette le chat

`zgrep` sui log in `serverfiles/logs`: `latest.log` più i `.log.gz` dei giorni precedenti,
filtrando le righe `<Nome>` e `Nome issued server command`. La data viene dal nome del file
compresso, l'ora dalla riga. Senza `zgrep` (pacchetto gzip) l'app legge solo `latest.log`.

## RCON (consigliato)

Senza RCON l'app pilota il server scrivendo nella console tmux e rileggendo il log: ogni
aggiornamento lascia righe di servizio in `latest.log`. Con RCON i comandi tornano la
risposta del server, subito e senza sporcare il log.

Nella sezione **RCON** delle Impostazioni:

1. **Genera password** — 20 caratteri casuali. Sono ammessi solo lettere, cifre, `.`, `-` e `_`: finisce in un file di configurazione sul server, e i simboli con significato per la shell restano fuori.
2. **Attiva RCON sul server e riavvia** — dopo una conferma esplicita l'app fa cinque passi, mostrandoli mentre procede:
   - copia di sicurezza di `server.properties` (`.mcmonitor.bak.<data>`) e scrittura di `enable-rcon=true`, `rcon.port`, `rcon.password`, `broadcast-rcon-to-ops=false`
   - riavvio LinuxGSM (**i giocatori online cadono**)
   - attesa fino a 60 s che la porta risulti in ascolto (`ss`/`netstat`)
   - salvataggio della configurazione nell'app
   - prova di autenticazione reale
3. **Prova RCON** — riverifica in qualsiasi momento, mostrando la risposta a `list`.

**Tunnel SSH (attivo di default).** La porta RCON viene inoltrata dentro la connessione SSH
già aperta (`127.0.0.1:25575` sul server → porta locale sul telefono). Non devi aprire
niente sul firewall e la password RCON, che il protocollo manda in chiaro, non esce mai
dal tunnel. Disattiva l'interruttore solo se vuoi collegarti direttamente alla porta.

Quando RCON è attivo lo usano la console, l'elenco giocatori e il tracciamento posizioni;
log e chat restano su SSH, perché RCON non legge file.

### Come vengono lette le posizioni

L'app invia alla console `list`, poi `data get entity <nome> Pos` e `Dimension` per ogni
giocatore online, e rilegge la risposta dal log. Serve quindi un server **vanilla/Paper/Spigot**
recente (il comando `data` esiste da 1.13). Le scie sono ricostruite lato app campionando
la posizione all'intervallo impostato (default 6 s).

I comandi vengono inviati di default con `tmux send-keys -t <sessione>`, che funziona con
qualsiasi versione di LinuxGSM (tmux è già un suo requisito: nessun pacchetto extra da
installare). La sessione tmux ha il nome dello script, ma puoi cambiarla in Impostazioni —
`tmux ls` nella diagnostica mostra quelle davvero attive. Se la tua versione di LinuxGSM ha
il comando `send`, l'interruttore in Impostazioni lo attiva; se poi risultasse assente,
l'app torna da sola a tmux.

Importante: `tmux ls` mostra solo le sessioni **dell'utente con cui ti colleghi**. Se
LinuxGSM gira come utente `mcserver`, l'SSH dell'app deve usare quell'utente.

## Ricompilare

Serve JDK 17+ e l'Android SDK (platform 35, build-tools 35).

```bash
./gradlew assembleRelease
```

I parser dei log (giocatori online, posizioni, chat) hanno test su righe reali di un
server vanilla 1.21.10, e il client RCON è testato contro un finto server che parla il
protocollo Source:

```bash
./gradlew testReleaseUnitTest
```

L'APK esce in `app/build/outputs/apk/release/`.

La firma richiede un keystore, **che non fa parte di questo repository**: `mcmonitor.jks` e
`keystore.properties` sono credenziali personali e restano fuori dal versionamento. Senza
di essi la build produce comunque un APK, ma non firmato. Per firmarne uno tuo:

```bash
keytool -genkeypair -v -keystore mcmonitor.jks -storetype JKS -keyalg RSA -keysize 2048 \
  -validity 10000 -alias mcmonitor
```

e affianca un `keystore.properties` con `storeFile`, `storePassword`, `keyAlias`, `keyPassword`.

## Pubblicazione automatica

Il workflow [`.github/workflows/release.yml`](.github/workflows/release.yml) compila,
firma e distribuisce. Si attiva con un tag `v*` oppure a mano da Actions, scegliendo la
traccia di Play.

Cosa fa, in ordine: verifica che il tag corrisponda a `versionName` (così non si pubblica
`v1.8` con dentro la 1.7), esegue i test, ricostruisce il keystore dai segreti, compila
`bundleRelease` e `assembleRelease`, allega l'APK alla release GitHub e carica l'AAB sulla
traccia scelta di Google Play.

Segreti da impostare in *Settings → Secrets and variables → Actions*:

| Segreto | Contenuto |
|---|---|
| `KEYSTORE_BASE64` | il file `.jks` codificato in base64 |
| `KEYSTORE_PASSWORD` | password del keystore |
| `KEY_ALIAS` | alias della chiave (`mcmonitor`) |
| `KEY_PASSWORD` | password della chiave |
| `PLAY_SERVICE_ACCOUNT_JSON` | JSON del service account con accesso a Play Console |

Per generare il base64 del keystore:

```bash
base64 -w0 mcmonitor.jks > keystore.base64
```

I passaggi sono indipendenti: senza `KEYSTORE_BASE64` il workflow compila comunque (non
firmato), senza `PLAY_SERVICE_ACCOUNT_JSON` salta solo il caricamento su Play. Le note di
rilascio mostrate su Play vengono da `store/whatsnew/whatsnew-it-IT.txt` (limite 500
caratteri per lingua).

Il service account si crea in Google Cloud, poi va invitato in Play Console
(*Utenti e autorizzazioni*) con il permesso di gestire le release. L'API può caricare
versioni su un'app **già esistente**: la prima creazione dell'app resta manuale.

## Contributori

- **bdbais** — ideazione, requisiti, prove sul campo
- **Flus**
- **Claude Opus 5** — implementazione

## Sostenere il progetto

MC Monitor è gratuito e open source, e lo resterà. Se ti è stato utile e ti va, puoi
offrire un caffè a chi lo mantiene — nessun obbligo, l'app funziona identica in ogni caso.

<img src="store/donazione-paypal.png" width="140" alt="Codice QR PayPal per una donazione">

## Licenza

**Apache License 2.0** — testo completo in [LICENSE](LICENSE). Puoi usare, modificare e
ridistribuire il codice, anche in progetti commerciali, mantenendo l'attribuzione e
indicando le modifiche apportate. Rispetto a MIT aggiunge una concessione esplicita di
brevetto, la stessa licenza di AndroidX e Material Components su cui l'app è costruita.

Componenti di terze parti:

| Componente | Licenza |
|---|---|
| AndroidX, Material Components, Kotlin Coroutines | Apache 2.0 |
| [mwiede/jsch](https://github.com/mwiede/jsch) (client SSH) | BSD 3-Clause |
| Font **Press Start 2P** (Google Fonts) | SIL Open Font License 1.1 — testo in `OFL-PressStart2P.txt` |

Il font originale di Minecraft non è ridistribuibile, quindi è stato scelto un font pixel
libero dallo stesso spirito. Questo progetto non è affiliato con Mojang o Microsoft.

## Sicurezza

Le credenziali SSH stanno in `SharedPreferences` privata dell'app (accessibile solo
all'app su un dispositivo non rootato, non cifrata da password). Consigliato: utente SSH
dedicato, autenticazione a chiave, permessi limitati alla sola directory LinuxGSM.
