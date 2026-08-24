package com.bellizia.mcmonitor.lgsm

import com.bellizia.mcmonitor.data.ServerConfig

/** Quanto pesa un rilievo. */
enum class Peso { ALTO, MEDIO, BASSO }

/** Com'è andato un singolo controllo. */
enum class Esito { BENE, ATTENZIONE, MALE, NON_SO }

/** Il giudizio complessivo. */
enum class Livello(val etichetta: String) {
    ALTO("alta"),
    MEDIO("media"),
    BASSO("bassa")
}

data class Controllo(
    val titolo: String,
    val esito: Esito,
    /** Cosa vuol dire, per chi non sa cosa sia una whitelist. */
    val spiegazione: String,
    /** Cosa fare per sistemarlo. Vuoto se non c'è niente da fare. */
    val rimedio: String = "",
    val peso: Peso = Peso.MEDIO,
    /** La chiave di server.properties da aprire, se il rimedio sta lì. */
    val impostazione: String = ""
)

data class Rapporto(
    val livello: Livello,
    val controlli: List<Controllo>
) {
    val problemi: List<Controllo> get() = controlli.filter { it.esito == Esito.MALE }
    val attenzioni: List<Controllo> get() = controlli.filter { it.esito == Esito.ATTENZIONE }
    val aPosto: List<Controllo> get() = controlli.filter { it.esito == Esito.BENE }

    /** Una riga che riassume, da mettere sotto il titolo. */
    fun riassunto(): String = when (livello) {
        Livello.ALTO -> "Nessun problema serio: il server è chiuso come dovrebbe."
        Livello.MEDIO -> "Niente di grave, ma ci sono ${attenzioni.size + problemi.size} cose da guardare."
        Livello.BASSO -> "Ci sono ${problemi.size} problemi seri: chiunque potrebbe entrare o comandare il server."
    }
}

/**
 * Un controllo veloce su quanto è chiuso il server.
 *
 * Non è un audit e non pretende di esserlo: guarda le poche cose che su un server
 * piccolo fanno davvero la differenza fra "ci entrano i tuoi amici" e "ci entra
 * chiunque abbia trovato l'indirizzo". Ognuna dice cosa vuol dire e come si
 * sistema, perché un semaforo rosso senza istruzioni serve solo a preoccupare.
 *
 * Il giudizio è severo di proposito: basta un problema serio per far scendere
 * tutto a "bassa". Su queste cose la media non ha senso — la porta aperta non si
 * compensa con la finestra chiusa.
 */
object SecurityCheck {

    /** Password RCON troppo corta o troppo ovvia per reggere un tentativo. */
    private val OVVIE = setOf(
        "password", "minecraft", "changeme", "admin", "rcon", "123456", "server"
    )

    fun valuta(
        properties: Map<String, String>,
        cfg: ServerConfig,
        lockConfigurato: Boolean,
        /** Gli altri profili configurati: servono a vedere se due si pestano i piedi. */
        altriServer: List<ServerConfig> = emptyList()
    ): Rapporto {
        val c = mutableListOf<Controllo>()

        // --- due server che parlano nella stessa console
        //
        // LinuxGSM chiama la sessione tmux come lo script. Due istanze in
        // cartelle diverse ma con lo stesso nome di script finiscono sulla
        // stessa sessione: un comando mandato a una puo' arrivare all'altra, e
        // non c'e' niente a schermo che lo faccia sospettare.
        val gemelli = altriServer.filter {
            it.id != cfg.id &&
                    it.host.equals(cfg.host, ignoreCase = true) &&
                    it.user == cfg.user &&
                    it.session == cfg.session
        }
        if (gemelli.isNotEmpty()) {
            c += Controllo(
                titolo = "Due server nella stessa console",
                esito = Esito.MALE,
                spiegazione = "Questo server e ${gemelli.joinToString(", ") { it.displayName }} " +
                        "usano la stessa sessione tmux (\"${cfg.session}\"), perche' hanno lo " +
                        "stesso nome di script LinuxGSM. Un comando mandato a uno puo' finire " +
                        "all'altro, e a schermo non si vede.",
                rimedio = "Nelle impostazioni di uno dei due scrivi un nome diverso nel campo " +
                        "\"Sessione tmux\", oppure rinomina lo script LinuxGSM di quell'istanza.",
                peso = Peso.ALTO
            )
        } else if (altriServer.any { it.id != cfg.id && it.host.equals(cfg.host, ignoreCase = true) }) {
            c += Controllo(
                titolo = "Due server nella stessa console",
                esito = Esito.BENE,
                spiegazione = "Sullo stesso computer ci sono altri server, e ognuno ha la sua " +
                        "sessione tmux: i comandi non si mescolano.",
                peso = Peso.ALTO
            )
        }

        fun prop(chiave: String) = properties[chiave]?.trim()
        fun bool(chiave: String, diFabbrica: Boolean): Boolean? =
            when (prop(chiave)?.lowercase()) {
                "true" -> true
                "false" -> false
                null -> if (properties.isEmpty()) null else diFabbrica
                else -> null
            }

        // --- chi può entrare
        when (bool("online-mode", true)) {
            false -> c += Controllo(
                titolo = "Controllo degli account Minecraft",
                esito = Esito.MALE,
                spiegazione = "È spento. Chiunque può collegarsi dicendo di essere chi vuole, " +
                        "anche uno dei tuoi operatori: gli basta scriverne il nome.",
                rimedio = "Riaccendilo nelle impostazioni del server. Si spegne solo su una " +
                        "rete di casa isolata, mai su un server raggiungibile da internet.",
                peso = Peso.ALTO,
                impostazione = "online-mode"
            )
            true -> c += Controllo(
                titolo = "Controllo degli account Minecraft",
                esito = Esito.BENE,
                spiegazione = "Acceso: chi entra è davvero chi dice di essere.",
                peso = Peso.ALTO
            )
            null -> c += nonSo("Controllo degli account Minecraft")
        }

        val whitelist = bool("white-list", false)
        when (whitelist) {
            true -> c += Controllo(
                titolo = "Whitelist",
                esito = Esito.BENE,
                spiegazione = "Accesa: entra solo chi hai messo in elenco.",
                peso = Peso.ALTO
            )
            false -> c += Controllo(
                titolo = "Whitelist",
                esito = Esito.MALE,
                spiegazione = "Spenta. Entra chiunque conosca l'indirizzo, e l'indirizzo gira " +
                        "più di quanto si pensi: i motori di ricerca dei server Minecraft " +
                        "scandagliano internet in continuazione.",
                rimedio = "Accendila dalla scheda Giocatori o dalle impostazioni del server, " +
                        "dopo aver messo in elenco chi deve entrare.",
                peso = Peso.ALTO,
                impostazione = "white-list"
            )
            null -> c += nonSo("Whitelist")
        }

        if (whitelist == true && bool("enforce-whitelist", false) == false) {
            c += Controllo(
                titolo = "Chi togli dalla whitelist",
                esito = Esito.ATTENZIONE,
                spiegazione = "Chi è già dentro ci resta anche dopo che l'hai tolto " +
                        "dall'elenco, finché non esce da solo.",
                rimedio = "Accendi \"Butta fuori subito chi togli dalla whitelist\".",
                peso = Peso.MEDIO,
                impostazione = "enforce-whitelist"
            )
        }

        // --- RCON, che è la chiave del server
        val rconAcceso = bool("enable-rcon", false) == true || cfg.rconEnabled
        if (rconAcceso) {
            val passwordFile = prop("rcon.password").orEmpty()
            val password = passwordFile.ifBlank { cfg.rconPassword }
            when {
                password.isBlank() -> c += Controllo(
                    titolo = "Password di RCON",
                    esito = Esito.MALE,
                    spiegazione = "RCON è acceso ma la password è vuota. Chi arriva a quella " +
                            "porta comanda il server come un operatore.",
                    rimedio = "Metti una password lunga dalle impostazioni, o spegni RCON.",
                    peso = Peso.ALTO,
                    impostazione = "rcon.password"
                )
                password.length < 12 || password.lowercase() in OVVIE -> c += Controllo(
                    titolo = "Password di RCON",
                    esito = Esito.MALE,
                    spiegazione = "È corta o troppo facile da indovinare. RCON non ha nessun " +
                            "limite ai tentativi: si prova finché non si trova.",
                    rimedio = "Mettine una di almeno dodici caratteri, senza parole comuni.",
                    peso = Peso.ALTO,
                    impostazione = "rcon.password"
                )
                else -> c += Controllo(
                    titolo = "Password di RCON",
                    esito = Esito.BENE,
                    spiegazione = "Abbastanza lunga da non essere indovinata a tentativi.",
                    peso = Peso.ALTO
                )
            }

            c += if (cfg.rconTunnel) {
                Controllo(
                    titolo = "Come l'app parla a RCON",
                    esito = Esito.BENE,
                    spiegazione = "Dentro il collegamento SSH: la porta di RCON non deve " +
                            "essere aperta su internet.",
                    peso = Peso.MEDIO
                )
            } else {
                Controllo(
                    titolo = "Come l'app parla a RCON",
                    esito = Esito.ATTENZIONE,
                    spiegazione = "L'app si collega alla porta di RCON direttamente. RCON " +
                            "manda la password in chiaro: chi è in mezzo alla rete la legge.",
                    rimedio = "Accendi \"RCON dentro il tunnel SSH\" nelle impostazioni.",
                    peso = Peso.MEDIO
                )
            }

            if (bool("broadcast-rcon-to-ops", true) == true) {
                c += Controllo(
                    titolo = "Comandi RCON in chat",
                    esito = Esito.ATTENZIONE,
                    spiegazione = "Ogni comando che l'app manda compare nella chat degli " +
                            "operatori. Non è un pericolo, ma è rumore e fa vedere a tutti " +
                            "cosa stai facendo.",
                    rimedio = "Spegni broadcast-rcon-to-ops.",
                    peso = Peso.BASSO,
                    impostazione = "broadcast-rcon-to-ops"
                )
            }
        }

        // --- dentro il gioco
        if (bool("enable-command-block", false) == true) {
            c += Controllo(
                titolo = "Blocchi comando",
                esito = Esito.ATTENZIONE,
                spiegazione = "Sono accesi. Chi può costruirli può far eseguire comandi al " +
                        "server, e alcuni comandi non si annullano.",
                rimedio = "Spegnili se non li usate apposta.",
                peso = Peso.MEDIO,
                impostazione = "enable-command-block"
            )
        }

        val spawn = prop("spawn-protection")?.toIntOrNull()
        if (spawn != null && spawn == 0) {
            c += Controllo(
                titolo = "Zona protetta allo spawn",
                esito = Esito.ATTENZIONE,
                spiegazione = "È a zero: chiunque può costruire e rompere dove si nasce.",
                rimedio = "Mettila almeno a 16 blocchi, se lo spawn è una zona comune.",
                peso = Peso.BASSO,
                impostazione = "spawn-protection"
            )
        }

        // --- il collegamento al computer
        if (cfg.user.equals("root", ignoreCase = true)) {
            c += Controllo(
                titolo = "Utente del collegamento",
                esito = Esito.MALE,
                spiegazione = "Ti colleghi come root. Un errore da qui tocca tutto il " +
                        "computer, non solo il server — e LinuxGSM si rifiuta comunque di " +
                        "girare come root.",
                rimedio = "Crea un utente dedicato al server e collegati con quello.",
                peso = Peso.ALTO
            )
        }

        if (cfg.privateKey.isBlank() && cfg.password.isNotBlank()) {
            c += Controllo(
                titolo = "Come entri nel computer",
                esito = Esito.ATTENZIONE,
                spiegazione = "Con una password. Una chiave è più difficile da indovinare e " +
                        "non si scrive da nessuna parte.",
                rimedio = "Se sai come si fa, passa a una chiave SSH nelle impostazioni.",
                peso = Peso.BASSO
            )
        }

        c += if (cfg.hostKeyFingerprint.isNotBlank()) {
            Controllo(
                titolo = "Identità del computer",
                esito = Esito.BENE,
                spiegazione = "L'impronta del server è memorizzata: se cambiasse, l'app se ne " +
                        "accorgerebbe invece di collegarsi a un altro computer.",
                peso = Peso.MEDIO
            )
        } else {
            Controllo(
                titolo = "Identità del computer",
                esito = Esito.ATTENZIONE,
                spiegazione = "Non è ancora memorizzata l'impronta del server: al prossimo " +
                        "collegamento riuscito viene salvata.",
                peso = Peso.BASSO
            )
        }

        // --- il telefono
        c += if (lockConfigurato) {
            Controllo(
                titolo = "Password dell'app",
                esito = Esito.BENE,
                spiegazione = "L'app è protetta: chi prende il telefono in mano non comanda " +
                        "il server.",
                peso = Peso.MEDIO
            )
        } else {
            Controllo(
                titolo = "Password dell'app",
                esito = Esito.ATTENZIONE,
                spiegazione = "L'app si apre senza password. Da qui si spegne il server e si " +
                        "può cancellare un mondo intero: chiunque prenda il telefono può farlo.",
                rimedio = "Mettila dalle impostazioni dell'app.",
                peso = Peso.MEDIO
            )
        }

        return Rapporto(livello = livello(c), controlli = c)
    }

    private fun nonSo(titolo: String) = Controllo(
        titolo = titolo,
        esito = Esito.NON_SO,
        spiegazione = "Non sono riuscito a leggere questa impostazione.",
        peso = Peso.BASSO
    )

    /**
     * Il voto complessivo.
     *
     * Basta un problema di peso alto per scendere in fondo, e non è severità
     * gratuita: su queste cose non si fa la media. Un server con la whitelist
     * spenta non è "abbastanza sicuro" perché il resto è a posto — è aperto.
     */
    fun livello(controlli: List<Controllo>): Livello {
        val gravi = controlli.any { it.esito == Esito.MALE && it.peso == Peso.ALTO }
        if (gravi) return Livello.BASSO
        val medi = controlli.any {
            (it.esito == Esito.MALE) || (it.esito == Esito.ATTENZIONE && it.peso != Peso.BASSO)
        }
        return if (medi) Livello.MEDIO else Livello.ALTO
    }
}
