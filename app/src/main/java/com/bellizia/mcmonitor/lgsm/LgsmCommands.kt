package com.bellizia.mcmonitor.lgsm

import com.bellizia.mcmonitor.data.ServerConfig

/**
 * Un comando dello script di LinuxGSM, con quello che serve per lanciarlo senza
 * sorprese: cosa fa, cosa succede al server, e quanto si è disposti ad aspettare.
 */
data class ComandoLgsm(
    val nome: String,
    val titolo: String,
    val cosaFa: String,
    /** Cosa succede a chi sta giocando. Se c'è, finisce nella conferma. */
    val conseguenza: String? = null,
    val secondi: Int = 120,
    /** Ferma il server, cambia file o scarica roba: si chiede conferma. */
    val pesante: Boolean = false,
    /** Fa uscire qualcosa dal computer: si chiede conferma due volte. */
    val pubblica: Boolean = false
) {
    val chiedeConferma: Boolean get() = pesante || pubblica
}

/**
 * I comandi di `./<script>` che si possono lanciare dall'app.
 *
 * È una lista chiusa, e non per prudenza generica: `console`, `debug`, `install`
 * e `send` senza argomento aspettano una risposta dalla tastiera. Senza un
 * terminale vero quella risposta non arriva mai, e LinuxGSM non si ferma — entra
 * in un ciclo che stampa "Please answer yes or no." all'infinito, riempiendo il
 * canale finché qualcuno non stacca. Redirigere l'ingresso dal nulla non salva:
 * è proprio quello che fa partire il ciclo. L'unica difesa è non lanciarli.
 *
 * `debug` in più ferma il server prima di partire: lanciato e abbandonato,
 * lascia il mondo spento.
 */
object LgsmCommands {

    val catalogo: List<ComandoLgsm> = listOf(
        ComandoLgsm(
            nome = "details",
            titolo = "Dettagli",
            cosaFa = "Stampa tutto quello che LinuxGSM sa di questo server: indirizzi, porte, percorsi, versione, spazio.",
            secondi = 120
        ),
        ComandoLgsm(
            nome = "backup",
            titolo = "Backup",
            cosaFa = "Fa una copia compressa di tutto il server, mondo compreso.",
            conseguenza = "Di fabbrica LinuxGSM ferma il server per tutta la copia: chi sta giocando viene disconnesso e rientra quando è finita. Può volerci parecchio su un mondo grande.",
            secondi = 3600,
            pesante = true
        ),
        ComandoLgsm(
            nome = "update",
            titolo = "Aggiorna Minecraft",
            cosaFa = "Controlla se c'è una versione nuova del server Minecraft e, se c'è, la scarica.",
            conseguenza = "Se trova un aggiornamento ferma il server, sostituisce il programma e lo riavvia. Non fa una copia del mondo prima: il backup fallo tu.",
            secondi = 1800,
            pesante = true
        ),
        ComandoLgsm(
            nome = "check-update",
            titolo = "Guarda se c'è un aggiornamento",
            cosaFa = "Controlla la versione senza scaricare niente.",
            conseguenza = "Se trova una versione nuova, LinuxGSM mette un segnale che per un quarto d'ora sospende il riavvio automatico. Non fa danni, ma è bene saperlo.",
            secondi = 300,
            pesante = true
        ),
        ComandoLgsm(
            nome = "update-lgsm",
            titolo = "Aggiorna LinuxGSM",
            cosaFa = "Aggiorna il programma che gestisce il server. Non tocca il mondo né i mod.",
            conseguenza = "Riscrive lo script e i suoi moduli mentre sta girando, e rimette a nuovo i valori di fabbrica.",
            secondi = 600,
            pesante = true
        ),
        ComandoLgsm(
            nome = "monitor",
            titolo = "Controlla che risponda",
            cosaFa = "Interroga il server e verifica che stia rispondendo.",
            conseguenza = "Se non risponde per un minuto, LinuxGSM lo riavvia da solo. Non lanciarlo mentre stai lavorando a mano sul server.",
            secondi = 180,
            pesante = true
        ),
        ComandoLgsm(
            nome = "test-alert",
            titolo = "Prova gli avvisi",
            cosaFa = "Manda un avviso di prova su Discord, Telegram o per posta, per vedere se arrivano.",
            secondi = 120
        ),
        ComandoLgsm(
            nome = "postdetails",
            titolo = "Pubblica i dettagli",
            cosaFa = "Carica i dettagli del server su termbin.com e restituisce un indirizzo da mandare a chi ti sta aiutando.",
            conseguenza = "La pagina è PUBBLICA e resta in rete un mese. Le password vengono nascoste, ma indirizzo, porte, percorsi e distribuzione no. Usalo solo se qualcuno te lo sta chiedendo per aiutarti.",
            secondi = 180,
            pubblica = true
        )
    )

    fun byName(nome: String): ComandoLgsm? = catalogo.firstOrNull { it.nome == nome }

    /**
     * Il comando da eseguire, con un tetto di tempo messo SUL server.
     *
     * Un tetto messo dal telefono chiuderebbe solo la connessione: il comando
     * resterebbe a girare là, e a un backup interrotto a metà resta il blocco che
     * per un'ora impedisce di rifarne un altro.
     */
    fun run(cfg: ServerConfig, comando: ComandoLgsm): String {
        require(byName(comando.nome) != null) { "comando non in elenco: ${comando.nome}" }
        val azione = Lgsm.action(cfg, comando.nome)
        return "if command -v timeout >/dev/null 2>&1; then " +
                "timeout ${comando.secondi} sh -c ${Lgsm.sq(azione)}; " +
                "esito=\$?; [ \$esito -eq 124 ] && echo '@@TEMPOSCADUTO'; " +
                "else $azione; fi"
    }

    /**
     * Come è andata.
     *
     * Il codice di uscita di LinuxGSM non è un verdetto: è la gravità dell'ultima
     * riga che ha scritto nel suo registro. Un comando che non esiste esce con
     * zero, `start` su un server già acceso esce con due, e quando nessun modulo
     * scrive niente esce con zero comunque. Quindi si guarda cosa ha detto.
     */
    fun esito(raw: String): String {
        val testo = Lgsm.clean(raw)
        return when {
            testo.contains("@@TEMPOSCADUTO") ->
                "Ci ha messo troppo e l'ho interrotto. Non vuol dire che sia fallito: " +
                        "guarda lo stato del server prima di rilanciarlo."
            Regex("(?i)\\bFAIL\\b").containsMatchIn(testo) -> "Qualcosa non è andato: leggi qui sotto."
            Regex("(?i)\\bERROR\\b").containsMatchIn(testo) -> "C'è un errore: leggi qui sotto."
            testo.isBlank() -> "Fatto, senza niente da dire."
            else -> "Fatto."
        }
    }
}
