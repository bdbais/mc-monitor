package com.bellizia.mcmonitor.rete

/**
 * Quali indirizzi provare quando si cerca un server nella rete di casa.
 *
 * Cercare vuol dire bussare a ogni indirizzo della propria rete. È una cosa che
 * si fa a casa propria e da nessun'altra parte: fuori è una scansione di rete
 * altrui, che a seconda di dove ci si trova è maleducazione o è un reato. Per
 * questo qui si rifiuta tutto quello che non è un indirizzo privato, e non lo si
 * lascia decidere all'interfaccia.
 */
object Sottorete {

    /** Oltre questa soglia non è più la rete di casa di nessuno. */
    const val MASSIMO_INDIRIZZI = 512

    /**
     * Se un indirizzo è in una delle reti riservate all'uso privato.
     *
     * RFC 1918 (10/8, 172.16/12, 192.168/16) più il link-local 169.254/16, che è
     * quello che si prende un telefono quando il DHCP non risponde.
     */
    fun privato(ip: String): Boolean {
        val p = ottetti(ip) ?: return false
        return when {
            p[0] == 10 -> true
            p[0] == 172 && p[1] in 16..31 -> true
            p[0] == 192 && p[1] == 168 -> true
            p[0] == 169 && p[1] == 254 -> true
            else -> false
        }
    }

    private fun ottetti(ip: String): List<Int>? {
        val parti = ip.trim().split('.')
        if (parti.size != 4) return null
        val numeri = parti.map { it.toIntOrNull() ?: return null }
        return if (numeri.all { it in 0..255 }) numeri else null
    }

    /**
     * Gli indirizzi da provare, dato l'indirizzo del telefono e la lunghezza del
     * prefisso di rete.
     *
     * Restano fuori l'indirizzo di rete e quello di broadcast, che non sono di
     * nessuno, e quello del telefono stesso. Se la rete è troppo grande — capita
     * con certi hotspot e con le VPN aziendali, che dichiarano /16 — non si prova
     * lo stesso a tappeto: si restituisce niente, e chi chiama lo dice.
     */
    fun indirizzi(ipLocale: String, prefisso: Int): List<String> {
        if (!privato(ipLocale)) return emptyList()
        if (prefisso !in 8..30) return emptyList()

        val quanti = 1L shl (32 - prefisso)
        if (quanti - 2 > MASSIMO_INDIRIZZI) return emptyList()

        val mio = numero(ipLocale) ?: return emptyList()
        val maschera = (0xFFFFFFFFL shl (32 - prefisso)) and 0xFFFFFFFFL
        val rete = mio and maschera
        val broadcast = rete or (quanti - 1)

        return ((rete + 1) until broadcast)
            .filter { it != mio }
            .map { testo(it) }
    }

    /** Se la rete è troppo grande per essere setacciata a mano a mano. */
    fun troppoGrande(prefisso: Int): Boolean =
        prefisso < 8 || prefisso > 30 || (1L shl (32 - prefisso)) - 2 > MASSIMO_INDIRIZZI

    private fun numero(ip: String): Long? {
        val p = ottetti(ip) ?: return null
        return (p[0].toLong() shl 24) or (p[1].toLong() shl 16) or
                (p[2].toLong() shl 8) or p[3].toLong()
    }

    private fun testo(n: Long): String =
        "${(n shr 24) and 0xFF}.${(n shr 16) and 0xFF}.${(n shr 8) and 0xFF}.${n and 0xFF}"
}
