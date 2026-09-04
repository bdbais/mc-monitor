/*
 * L'unica riga di codice che gira in questa pagina: chiede al Worker qual e'
 * l'ultima release e aggiorna i tre numeri che invecchiano.
 *
 * La pagina esce dal server gia' scritta con i valori dell'ultimo deploy, quindi
 * se questa richiesta non arriva — rete lenta, JavaScript spento, GitHub giu' —
 * quello che si legge resta giusto, solo piu' vecchio. Per la stessa ragione qui
 * non si costruisce niente: si sostituisce del testo.
 */
(function () {
    "use strict";

    /** 5881516 -> "5,6 MB", con la virgola che si usa in italiano. */
    function inMega(byte) {
        if (!Number.isFinite(byte) || byte <= 0) return null;
        return (byte / 1048576).toFixed(1).replace(".", ",") + " MB";
    }

    function scrivi(selettore, testo) {
        if (!testo) return;
        document.querySelectorAll(selettore).forEach(function (el) {
            el.textContent = testo;
        });
    }

    fetch("/api/versione", { headers: { Accept: "application/json" } })
        .then(function (r) {
            if (!r.ok) throw new Error("stato " + r.status);
            return r.json();
        })
        .then(function (v) {
            // Se il Worker ha dovuto ripiegare sui valori di riserva, la pagina
            // e' gia' quella: riscriverla non aggiungerebbe niente.
            if (!v || v.riserva) return;

            scrivi("[data-versione]", v.versione);
            scrivi("[data-peso]", inMega(v.byte));
            scrivi("[data-sha]", v.sha256);

            if (v.file) {
                document.querySelectorAll("[data-scarica]").forEach(function (a) {
                    a.setAttribute("download", v.file);
                    a.setAttribute("title", v.file);
                });
            }
        })
        .catch(function () {
            /* Niente da fare e niente da dire: i valori scritti nella pagina restano. */
        });
})();
