package com.bellizia.mcmonitor.lgsm

import com.bellizia.mcmonitor.data.ServerConfig

/** Com'è andata su un server: uno solo dell'elenco. */
data class EsitoSuServer(
    val server: ServerConfig,
    val riuscito: Boolean,
    val dettaglio: String
)

/**
 * Ammettere e bannare su più server insieme.
 *
 * Chi ha due mondi sulla stessa macchina non ha due comunità: ha una comunità e
 * due mondi. Bannare un vandalo di là e non di qua vuol dire che fra dieci
 * minuti è di qua, e nessuno se lo ricorda finché non ricomincia.
 *
 * Il provvedimento passa dalla console, non dai file: `whitelist add` e `ban`
 * chiedono a Mojang lo UUID del giocatore, e senza quello una riga scritta a
 * mano dentro `whitelist.json` viene semplicemente ignorata da un server in
 * online-mode. Per questo un server spento non si può servire, e lo si dice
 * invece di far finta.
 */
object Provvedimenti {

    /** I quattro gesti che ha senso ripetere altrove. */
    enum class Tipo(val etichetta: String, val fatto: String, val opposto: String) {
        BAN("Ban", "bannato", "pardon"),
        PARDON("Rimozione del ban", "sbannato", "ban"),
        AMMETTI("Ammissione", "ammesso", "whitelist remove"),
        TOGLI("Rimozione dalla whitelist", "tolto dalla whitelist", "whitelist add")
    }

    /**
     * Riconosce il gesto da come è fatto il comando.
     *
     * Si guarda il comando e non chi lo ha premuto perché la stessa strada la
     * percorrono le macro e la console: se un giorno un ban arriverà da lì,
     * arriverà con lo stesso trattamento.
     */
    fun tipoDi(comando: String): Tipo? {
        val c = comando.trim().trimStart('/').lowercase()
        return when {
            c.startsWith("whitelist add ") -> Tipo.AMMETTI
            c.startsWith("whitelist remove ") -> Tipo.TOGLI
            c.startsWith("pardon ") -> Tipo.PARDON
            // "ban-ip" è un'altra cosa e non si propaga: colpisce un indirizzo,
            // che su una rete di casa è di tutta la famiglia.
            c.startsWith("ban ") -> Tipo.BAN
            else -> null
        }
    }

    /**
     * Chi e' il giocatore, dentro un comando di console.
     *
     * Non l'ultima parola: un ban puo' portarsi dietro il motivo
     * ("ban Pippo ha bruciato la casa di Anna"), e l'ultima parola sarebbe
     * "Anna". Da li' il riassunto avrebbe detto il nome sbagliato e "Annulla
     * tutto" avrebbe sbannato qualcun altro.
     */
    fun giocatoreDi(comando: String): String? {
        val p = comando.trim().trimStart('/').split(Regex("""\s+""")).filter { it.isNotBlank() }
        return when {
            p.size >= 3 && p[0].equals("whitelist", true) &&
                    (p[1].equals("add", true) || p[1].equals("remove", true)) -> p[2]
            p.size >= 2 && (p[0].equals("ban", true) || p[0].equals("pardon", true)) -> p[1]
            else -> null
        }
    }

    /**
     * Il comando che disfa quello appena dato, per lo stesso giocatore.
     *
     * Serve quando si sbaglia bersaglio su cinque server insieme: senza, si
     * dovrebbe rifare il giro a mano ricordandosi quali erano andati a buon
     * fine.
     */
    fun perDisfare(tipo: Tipo, giocatore: String): String = when (tipo) {
        Tipo.BAN -> "pardon $giocatore"
        Tipo.PARDON -> "ban $giocatore"
        Tipo.AMMETTI -> "whitelist remove $giocatore"
        Tipo.TOGLI -> "whitelist add $giocatore"
    }

    /**
     * Gli altri server a cui ha senso proporre lo stesso provvedimento.
     *
     * Quelli senza credenziali non ci sono: chiederebbero una password che
     * l'app non ha, e comparirebbero solo per fallire.
     */
    fun altriServer(tutti: List<ServerConfig>, corrente: ServerConfig): List<ServerConfig> =
        tutti.filter { it.id != corrente.id && it.isComplete }
            .sortedBy { it.displayName.lowercase() }

    /**
     * Quali proporre già spuntati.
     *
     * Quelli sullo stesso computer: sono quasi sempre lo stesso gruppo di
     * persone, e chi ha un secondo mondo altrove di solito ha un motivo per
     * tenerlo separato.
     */
    fun daSpuntare(altri: List<ServerConfig>, corrente: ServerConfig): BooleanArray =
        BooleanArray(altri.size) {
            altri[it].host.equals(corrente.host, ignoreCase = true) &&
                    altri[it].user == corrente.user
        }

    /**
     * Il server ha rifiutato il comando?
     *
     * Con RCON la risposta del server torna indietro davvero, e allora si puo'
     * sapere. Con tmux no: si sa solo che i tasti sono arrivati alla console, e
     * "riuscito" non puo' voler dire di piu'.
     *
     * "gia' in whitelist" NON e' un rifiuto: lo stato finale e' quello che si
     * voleva, ed e' il caso normale quando si ripete un provvedimento su un
     * server dove era gia' stato dato.
     */
    fun rifiutato(risposta: String): Boolean {
        val r = risposta.lowercase()
        return listOf(
            "that player does not exist",
            "unknown command",
            "incorrect argument",
            "expected whitespace",
            "no player was found"
        ).any { r.contains(it) }
    }

    /**
     * Cosa si puo' onestamente dire di un esito.
     *
     * Senza RCON non si sa se il server ha accettato: si sa che il comando e'
     * arrivato in console. Dirlo e' meno soddisfacente che dire "bannato", ma
     * e' quello che si sa.
     */
    fun certezza(conRcon: Boolean): String =
        if (conRcon) ""
        else "\n\nSu questi server l'app parla alla console senza sentire la risposta: " +
                "sa che il comando è arrivato, non che il server l'abbia accettato. " +
                "Con RCON acceso la risposta si legge."

    /** Il riassunto da mostrare quando il giro è finito. */
    fun riassunto(tipo: Tipo, giocatore: String, esiti: List<EsitoSuServer>): String {
        val fatti = esiti.filter { it.riuscito }
        val no = esiti.filterNot { it.riuscito }
        return buildString {
            if (no.isEmpty()) {
                append("$giocatore ${tipo.fatto} su tutti e ${esiti.size}.")
            } else if (fatti.isEmpty()) {
                append("Non ci sono riuscito da nessuna parte.")
            } else {
                append("$giocatore ${tipo.fatto} su ${fatti.size} server su ${esiti.size}.")
            }
            if (fatti.isNotEmpty()) {
                append("\n\nFatto:")
                fatti.forEach { append("\n· ").append(it.server.displayName) }
            }
            if (no.isNotEmpty()) {
                append("\n\nNon fatto:")
                no.forEach {
                    append("\n· ").append(it.server.displayName).append(" — ").append(it.dettaglio)
                }
                append("\n\nQuesti restano come prima. Riprova quando il server è acceso.")
            }
        }
    }
}
