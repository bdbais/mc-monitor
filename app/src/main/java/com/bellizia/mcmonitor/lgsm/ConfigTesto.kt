package com.bellizia.mcmonitor.lgsm

/**
 * Cambiare una riga dentro un file di configurazione, lasciando stare il resto.
 *
 * Le tre mappe scrivono in tre formati diversi — HOCON, YAML, e un file che si
 * chiama `.txt` ed è YAML lo stesso — e questo era il motivo per cui l'app non
 * li aveva mai toccati. Ma per cambiare *una riga* i tre formati non servono
 * tutti e tre: quello che hanno in comune è che una riga è `chiave: valore`, e
 * che quanto è rientrata dice di chi è figlia. Tanto basta, e non si finisce a
 * dover capire tre grammatiche per sempre.
 *
 * Quello che **non** si fa è riscrivere il file. Si sostituisce la riga e tutto
 * il resto resta identico, commenti compresi: un file riscritto da un programma
 * che lo capisce a metà è un file che perde quello che non ha capito.
 *
 * La chiave si indica per intero — `settings` → `internal-webserver` → `port` —
 * e non per nome. In squaremap `port:` compare anche sotto altro, e in Dynmap
 * c'è un `#port: 3306` che è la porta di un database MySQL: chi cerca «port» e
 * si ferma alla prima trovata cambia la cosa sbagliata.
 */
object ConfigTesto {

    private data class Riga(
        val numero: Int,
        val rientro: Int,
        val commentata: Boolean,
        val chiave: String?,
        /** Tutto quello che sta prima del valore, separatore compreso: `port:`, `data =`. */
        val prefisso: String,
        val valore: String,
    )

    /** `port: 8100`, `  #bind: 0.0.0.0`, `data = "x"`, o niente di tutto questo. */
    private fun leggiRiga(numero: Int, testo: String): Riga {
        val rientro = testo.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) testo.length else it }
        var resto = testo.substring(rientro)
        val commentata = resto.startsWith("#")
        if (commentata) resto = resto.removePrefix("#").trimStart()

        // Una chiave è un nome semplice: virgolette e spazi vogliono dire che è
        // un'altra cosa -- una riga di `additional-headers`, un valore su più
        // righe -- e lì non ci si mette a indovinare.
        val duePunti = resto.indexOfFirst { it == ':' || it == '=' }
        if (duePunti <= 0) return Riga(numero, rientro, commentata, null, "", "")
        val nome = resto.substring(0, duePunti).trim()
        if (nome.isEmpty() || !nome.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }) {
            return Riga(numero, rientro, commentata, null, "", "")
        }
        return Riga(
            numero = numero,
            rientro = rientro,
            commentata = commentata,
            chiave = nome,
            prefisso = resto.substring(0, duePunti + 1),
            valore = resto.substring(duePunti + 1).trim(),
        )
    }

    private fun righe(testo: String): List<Riga> =
        testo.split("\n").mapIndexed { i, t -> leggiRiga(i, t) }

    /**
     * Dove sta la riga di questa chiave, seguendo il percorso un pezzo per volta.
     *
     * Ogni pezzo si cerca **dentro** il blocco di quello prima: più rientrato di
     * lui, e non oltre la riga in cui quel blocco finisce. È così che
     * `settings → internal-webserver → port` non becca un `port` che sta da
     * un'altra parte del file.
     */
    private fun trova(righe: List<Riga>, percorso: List<String>): Riga? {
        var da = 0
        var fino = righe.size
        var rientroPadre = -1
        var trovata: Riga? = null

        percorso.forEach { pezzo ->
            trovata = null
            var i = da
            while (i < fino) {
                val r = righe[i]
                // Non serve fermarsi qui quando il blocco del padre finisce:
                // `fino` e' gia' la sua ultima riga. Una guardia in piu' che non
                // guarda niente si legge come se reggesse qualcosa.
                if (r.chiave != null) {
                    if (r.chiave == pezzo && (rientroPadre >= 0 || r.rientro == 0)) {
                        trovata = r
                        break
                    }
                }
                i++
            }
            val t = trovata ?: return null
            rientroPadre = t.rientro
            da = t.numero + 1
            // Il blocco di questa chiave finisce dove ricompare il suo stesso
            // rientro, o uno minore.
            fino = (t.numero + 1 until righe.size).firstOrNull { n ->
                righe[n].chiave != null && righe[n].rientro <= t.rientro
            } ?: righe.size
        }
        return trovata
    }

    /** Il valore scritto, senza virgolette e senza il commento che lo segue. */
    private fun pulisci(grezzo: String): String {
        val v = grezzo.trim()
        if (v.startsWith("\"")) {
            val chiusura = v.indexOf('"', 1)
            return if (chiusura > 0) v.substring(1, chiusura) else v.removePrefix("\"")
        }
        val commento = v.indexOf(" #")
        return (if (commento >= 0) v.substring(0, commento) else v).trim()
    }

    /**
     * Il valore che c'è adesso, o null se quella riga non c'è o è commentata.
     *
     * Commentata vale come assente perché è quello che fa il programma che legge
     * il file: `#webserver-bindaddress: 0.0.0.0` non è l'indirizzo scelto, è
     * l'esempio stampato di fabbrica.
     */
    fun leggi(testo: String, percorso: List<String>): String? {
        val r = trova(righe(testo), percorso) ?: return null
        return if (r.commentata) null else pulisci(r.valore)
    }

    /** Se la riga c'è ma è spenta da un cancelletto. */
    fun commentata(testo: String, percorso: List<String>): Boolean {
        val r = trova(righe(testo), percorso) ?: return false
        return r.commentata
    }

    /**
     * Scrive il valore e torna il file intero.
     *
     * Tre casi, in ordine:
     *
     * 1. la riga c'è: si sostituisce, tenendo il suo rientro e il suo separatore
     *    -- HOCON accetta sia `:` che `=`, e cambiarlo di nascosto sarebbe una
     *    modifica in più che nessuno ha chiesto;
     * 2. la riga c'è ma commentata: si sostituisce **togliendo il cancelletto**.
     *    È il caso di `#webserver-bindaddress`, dove cambiare il valore lasciando
     *    il cancelletto non cambierebbe niente e sembrerebbe fatto;
     * 3. la riga non c'è: si aggiunge in fondo al blocco che la deve contenere.
     *    È il caso di `ip` in BlueMap, che esiste nel programma ma non nel file
     *    scritto di fabbrica -- e senza questo terzo caso sarebbe l'unica
     *    impostazione impossibile da mettere.
     */
    fun scrivi(testo: String, percorso: List<String>, valore: String): String {
        val linee = testo.split("\n").toMutableList()
        val righe = righe(testo)
        val nome = percorso.last()

        val esistente = trova(righe, percorso)
        if (esistente != null) {
            // Si tiene il prefisso originale, spaziatura compresa: HOCON accetta
            // sia `:` che `=`, e riscriverlo a modo proprio sarebbe una modifica
            // in piu' che nessuno ha chiesto.
            linee[esistente.numero] = " ".repeat(esistente.rientro) + esistente.prefisso + " " + valore
            return linee.joinToString("\n")
        }

        // Non c'è: la si aggiunge.
        //
        // Se è una chiave di primo livello si mette **in fondo al file** e non
        // dopo l'ultima chiave che si vede: l'ultima chiave di un file HOCON è
        // spesso l'apertura di un blocco, e infilarcisi dentro vorrebbe dire
        // scrivere l'impostazione in un posto dove non conta niente.
        if (percorso.size == 1) {
            return testo.trimEnd('\n') + "\n$nome: $valore\n"
        }

        val padre = trova(righe, percorso.dropLast(1)) ?: return testo
        val fine = (padre.numero + 1 until righe.size).firstOrNull { n ->
            righe[n].chiave != null && righe[n].rientro <= padre.rientro
        } ?: righe.size
        val figli = righe.filter {
            it.chiave != null && it.numero in (padre.numero + 1) until fine && it.rientro > padre.rientro
        }
        val rientro = figli.firstOrNull()?.rientro ?: (padre.rientro + 2)
        val dopo = figli.lastOrNull()?.numero ?: padre.numero
        linee.add(dopo + 1, " ".repeat(rientro) + "$nome: $valore")
        return linee.joinToString("\n")
    }
}
