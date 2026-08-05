# Informativa sulla privacy — MC Monitor

Ultimo aggiornamento: 5 agosto 2026

MC Monitor è un'app di amministrazione che si collega, tramite SSH e RCON, a un server
Minecraft **scelto e posseduto dall'utente**.

## Dati raccolti dallo sviluppatore

**Nessuno.** L'app non contiene sistemi di analisi, pubblicità, tracciamento o SDK di terze
parti. Lo sviluppatore non riceve, non conserva e non ha modo di consultare alcun dato
proveniente dai dispositivi su cui l'app è installata.

## Dati conservati sul dispositivo

Per funzionare, l'app salva nella memoria privata dell'applicazione (accessibile solo
all'app stessa, secondo il modello di sicurezza di Android) le informazioni che l'utente
inserisce:

- indirizzo, porta e nome utente dei server configurati;
- password SSH, chiave privata SSH ed eventuale passphrase;
- password RCON;
- percorsi di LinuxGSM e URL della mappa web;
- impronta digitale (fingerprint) della chiave host del server, per rilevarne il cambiamento;
- posizioni dei giocatori lette durante la sessione, tenute solo in memoria e perse alla chiusura.

Questi dati **non lasciano il dispositivo** se non verso il server che l'utente ha
configurato, e per il solo scopo di autenticarsi ed eseguire i comandi richiesti.

## Connessioni di rete

L'app si collega esclusivamente a:

1. l'indirizzo del server indicato dall'utente, sulla porta SSH indicata;
2. la porta RCON di quel server, se l'utente attiva RCON (di norma dentro il tunnel SSH);
3. l'URL della mappa web (Dynmap, BlueMap o simili) se l'utente ne configura uno, aperto
   in una WebView.

Non esistono altre destinazioni. Nessun server dello sviluppatore è coinvolto.

## Dati letti dal server

Su richiesta dell'utente l'app legge dal server i log di gioco per mostrare la console, i
messaggi di chat dei giocatori, i tentativi di accesso, la whitelist e la lista dei ban.
Queste informazioni vengono mostrate sullo schermo e non vengono salvate né trasmesse
altrove. Chi amministra un server è responsabile del trattamento dei dati dei propri
giocatori secondo la normativa applicabile.

## Permessi richiesti

- `INTERNET` e `ACCESS_NETWORK_STATE`: necessari per collegarsi al server.

L'app non richiede posizione, fotocamera, microfono, contatti, file dell'utente o altri
permessi sensibili.

## Cancellazione dei dati

- Il pulsante **Rimuovi** nell'elenco server cancella la configurazione di quel server,
  credenziali comprese.
- La disinstallazione dell'app elimina tutti i dati salvati.

Non essendoci raccolta lato server, non esiste alcun dato da richiedere o far cancellare
allo sviluppatore.

## Minori

L'app è uno strumento di amministrazione di sistema e non è rivolta a minori di 18 anni.

## Contatti

federico@bellizia.com

## Modifiche

Eventuali aggiornamenti a questa informativa saranno pubblicati in questa pagina, con la
data in cima aggiornata.
