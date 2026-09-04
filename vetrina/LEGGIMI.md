# La vetrina

Il sito che racconta MC Monitor: <https://mcmonitor.bais.info>

## Cosa c'è

    public/     il sito: una pagina sola, il suo foglio di stile, un file di script
    src/        il Worker: due rotte che un file statico non saprebbe servire
    prove/      il banco di prova del Worker (node prove/prova-worker.mjs)

Il sito è statico. Al Worker restano solo le due cose che un file fermo non sa
fare, e che servono perché la pagina non invecchi a ogni release:

- `/apk` e `/scarica` chiedono a GitHub qual è l'ultima versione e mandano lì.
  **Il QR punta a `/apk`, non a un file**: un QR stampato su un foglio vive più a
  lungo di qualsiasi versione, e deve continuare a portare all'APK di oggi.
- `/api/versione` restituisce versione, peso e impronta; la pagina li usa per
  aggiornare i tre numeri che invecchiano. Se non arriva, restano quelli scritti
  al deploy: giusti, solo più vecchi.

Se GitHub non risponde, entrambe ripiegano sui valori costanti in cima a
`src/worker.js` e lo dichiarano (`riserva: true`).

## Da dove vengono le immagini

Gli screenshot sono quelli di `store/screenshots/`, ridotti a 460 px e in WebP.
Le due texture (`muro.png`, `pannello.png`) e il carattere sono le risorse vere
dell'app, copiate da `app/src/main/res/`: il sito deve somigliare a ciò che si
installa. Il carattere è Press Start 2P, licenza SIL Open Font, ridotto ai soli
caratteri usati; la licenza viaggia accanto, come richiede.

**Due screenshot sono stati ritoccati** per non pubblicare dati della macchina
di sviluppo: in `collegamento.png` l'indirizzo d'esempio è ora `mc.example.com`
(dominio riservato agli esempi dalla RFC 2606), e in `server-installati.png` la
riga di riepilogo mostra lo stesso indirizzo accorciato. Il testo è ridisegnato
con Roboto alla stessa dimensione e sulla stessa linea di base dell'originale.

## Provare in locale

    python -m http.server 8787 --directory public

`/apk` e `/api/versione` non esistono in locale — è il Worker a servirle — e la
pagina è fatta per reggerlo: i numeri restano quelli scritti dentro.

    node prove/prova-worker.mjs

Prova il Worker con GitHub che risponde, che non risponde, che risponde male, e
senza il binding degli asset. Sono 30 verifiche, e ognuna è stata provata
rimettendo il difetto che deve intercettare.

## Pubblicare

Il deploy passa dalle API di Cloudflare in tre tempi: si registra il manifest
degli asset, si caricano i file col JWT che la sessione restituisce, e si carica
il Worker col token di completamento. Il Worker gira **prima** degli asset
(`run_worker_first`): se girasse dopo, le pagine uscirebbero dallo strato statico
senza le intestazioni di sicurezza, che è l'unico motivo per cui ci passano.
