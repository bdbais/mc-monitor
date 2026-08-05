# Materiale per Google Play — MC Monitor

Tutto il testo qui sotto è pronto da incollare in Play Console. I conteggi dei caratteri
rispettano i limiti attuali dello store.

## Dati tecnici

| Campo | Valore |
|---|---|
| Nome pacchetto | `com.bellizia.mcmonitor` |
| Versione | 1.7 (versionCode 8) |
| File da caricare | `app-release.aab` (Play non accetta più APK per app nuove) |
| minSdk / targetSdk | 26 (Android 8) / 35 (Android 15) |
| Categoria | Strumenti |
| Tipo | App (non gioco), gratuita, senza acquisti in-app |
| Contiene annunci | No |
| Informativa privacy | https://github.com/bdbais/mc-monitor/blob/main/PRIVACY.md |

## Titolo (max 30 caratteri)

```
MC Monitor: server Minecraft
```
28 caratteri.

## Descrizione breve (max 80 caratteri)

```
Gestisci il tuo server Minecraft LinuxGSM via SSH: console, giocatori, mappa
```
75 caratteri.

## Descrizione completa (max 4000 caratteri)

```
MC Monitor è lo strumento di amministrazione per chi gestisce un server Minecraft installato con LinuxGSM su una macchina Linux. Si collega in SSH al tuo server e ti mette in mano la console, i giocatori e la mappa, senza aprire il portatile.

STATO DEL SERVER
Stato STARTED o STOPPED a colpo d'occhio, indirizzo IP, porte, versione e tutti i dettagli riportati da LinuxGSM. Avvio, arresto e riavvio con un tocco, protetti da una conferma per le azioni che disconnettono i giocatori.

CONSOLE
La coda del log di gioco aggiornata da sola, con l'invio dei comandi alla console del server. Una riga di scorciatoie compila per te i comandi più usati: list, say, tp, gamemode, time set day, weather clear, whitelist, kick, ban, save-all e altri.

GIOCATORI
Chi è collegato in questo momento, con le coordinate X/Y/Z e la dimensione in cui si trova. Whitelist e lista dei ban lette direttamente dai file del server, con aggiunta e rimozione immediate.

Toccando un giocatore si apre il pannello completo: teletrasporto a coordinate o verso un altro giocatore, modalità di gioco, punto di rinascita, permessi di operatore, espulsione e ban con motivo, e la cronologia delle sue chat e dei suoi comandi con data e ora, recuperata anche dai log dei giorni precedenti.

LISTA D'ATTESA
Quando qualcuno prova a entrare e non è né in whitelist né bannato, finisce in una lista d'attesa con due soli pulsanti: ammetti o banna. Nessuna richiesta va persa e non devi cercarla nei log.

MAPPA CON TRACCIAMENTO
Una mappa del mondo sul piano X/Z che si trascina e si zooma, con la griglia dei chunk, lo spawn e la posizione dei giocatori aggiornata in tempo reale. Ogni giocatore lascia la scia dei propri spostamenti, e puoi agganciare la vista a uno di loro per seguirlo mentre si muove, anche quando passa nel Nether o nell'End. Se sul server gira Dynmap, BlueMap o squaremap, l'app apre anche la mappa web a tutto schermo.

PIÙ SERVER
L'app si apre sull'elenco dei tuoi server: aggiungi, modifica, duplica e rimuovi le configurazioni. Con un solo server configurato entra direttamente.

RCON, ATTIVATO PER TE
Se il server non ha ancora RCON attivo, l'app lo configura: salva una copia di server.properties, scrive le impostazioni, riavvia il gioco, verifica che la porta risponda e prova l'autenticazione, mostrandoti ogni passaggio. La connessione RCON viaggia dentro il tunnel SSH, quindi non devi aprire porte sul firewall e la password non transita mai in chiaro.

FATTA PER LAVORARE SUL SERIO
Autenticazione SSH con password o chiave privata, con passphrase. L'impronta della chiave host viene memorizzata al primo collegamento e verificata a ogni accesso successivo: se cambia, l'app blocca la connessione e te lo dice. Una diagnostica prova la connessione a stadi separati, DNS, porta TCP, handshake, autenticazione, e ti mostra esattamente dove si ferma.

Le credenziali restano nella memoria privata dell'app sul tuo telefono. Nessun account da creare, nessun servizio intermedio, nessuna pubblicità, nessun tracciamento.

REQUISITI
Un server Minecraft (vanilla, Paper o Spigot dalla 1.13 in poi) installato con LinuxGSM su Linux, e un accesso SSH a quella macchina.

Progetto open source, licenza Apache 2.0: github.com/bdbais/mc-monitor

Non affiliato a Mojang Studios o Microsoft. Minecraft è un marchio di Mojang Studios.
```

## Grafiche

| File | Uso | Formato richiesto |
|---|---|---|
| `store/icon-512.png` | Icona dell'app | 512x512 PNG — pronto |
| `store/feature-1024x500.png` | Immagine in evidenza | 1024x500 PNG — pronto |
| — | Screenshot telefono | **da acquisire**: minimo 2, da 320 a 3840 px di lato |

Gli screenshot devono ritrarre l'app in funzione: vanno catturati dal telefono con l'app
collegata al tuo server. Consigliati, in quest'ordine: Stato, Mappa con le scie,
Giocatori con la lista d'attesa, Console. Oscura host e nomi utente prima di caricarli.

## Accesso all'app (istruzioni per il team di revisione)

Sezione *Contenuti dell'app → Accesso all'app*. L'app non ha login né account, ma senza un
server SSH non mostra nulla: senza questa spiegazione un revisore vedrebbe solo una
schermata di configurazione e potrebbe respingere la pubblicazione.

Scegli "Tutte le funzionalità sono disponibili senza credenziali particolari" e incolla
nelle istruzioni:

```
L'app non ha account né login. È uno strumento di amministrazione che si collega, tramite SSH, a un server Minecraft posseduto dall'utente e installato con LinuxGSM.

Senza un server da amministrare l'app mostra solo la schermata di configurazione: non esistono credenziali di prova da fornire, perché ogni utente usa il proprio server e le proprie chiavi SSH.

Tutte le funzioni (stato, console, giocatori, mappa, RCON) diventano disponibili dopo aver inserito indirizzo, utente e password o chiave SSH di un server proprio, nella scheda Impostazioni.
```

## Sicurezza dei dati — risposte proposte

Il modulo va compilato e firmato da te; questa è la lettura tecnica di quello che l'app fa.

| Domanda | Risposta | Motivo |
|---|---|---|
| L'app raccoglie o condivide dati utente? | No | Per Google "raccolta" significa trasmissione dei dati **allo sviluppatore o a terze parti**. Qui nulla raggiunge lo sviluppatore: le credenziali viaggiano solo verso il server che l'utente stesso configura e possiede. |
| I dati sono cifrati in transito? | Sì | Tutto passa da SSH; RCON viaggia dentro il tunnel SSH. |
| L'utente può chiedere la cancellazione dei dati? | Sì, dal dispositivo | Pulsante "Rimuovi" nell'elenco server, o disinstallazione. |

Verifica tu stesso questa lettura prima di firmare: una dichiarazione errata nel modulo
Sicurezza dei dati è uno dei motivi più comuni di rimozione di un'app.

## Classificazione dei contenuti (questionario IARC)

Categoria "Utility, produttività, comunicazione". Nessuna violenza, nessun contenuto
sessuale, nessuna sostanza, nessun gioco d'azzardo, nessun acquisto. L'app mostra
all'amministratore i messaggi di chat del proprio server, ma non consente a utenti di
comunicare tra loro attraverso l'app.

## Pubblico di destinazione

18+. È uno strumento di amministrazione di sistema: dichiarare fasce d'età inferiori
farebbe scattare i requisiti aggiuntivi del programma per famiglie senza alcun vantaggio.

## Firma dell'app

Attiva **Play App Signing**: Google custodisce la chiave di distribuzione e
`mcmonitor.jks` diventa la chiave di caricamento. Conservane una copia al sicuro — se la
perdi non puoi più caricare aggiornamenti senza una procedura di reimpostazione presso
Google.
