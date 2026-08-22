package com.bellizia.mcmonitor.data

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Blocco dell'app.
 *
 * Da qui si spegne un server, si cambiano le whitelist e si cancella un mondo
 * intero: un telefono lasciato sul tavolo basta a fare danni che nessun backup
 * automatico rimedia. La password non viene salvata da nessuna parte — si
 * conserva solo la sua impronta, e l'impronta digitale del telefono serve a non
 * doverla riscrivere venti volte al giorno.
 *
 * Le funzioni di calcolo stanno qui separate dallo stato, così si possono
 * provare senza un telefono acceso.
 */
object AppLock {

    /** Come per l'esportazione della configurazione: lento apposta. */
    private const val ITERATIONS = 210_000
    private const val KEY_BITS = 256

    /** Momento dell'ultimo passaggio in secondo piano; zero se non e' mai successo. */
    private var backgroundedAt = 0L

    /** Vero finché non si e' superato il blocco in questo avvio dell'app. */
    var locked = true
        private set

    fun newSalt(): ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }

    fun hash(password: String, salt: ByteArray): String {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return encode(bytes)
    }

    fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    fun decode(value: String): ByteArray = Base64.getDecoder().decode(value)

    /**
     * Confronto a tempo costante: su una password sbagliata non deve trapelare
     * nemmeno quante lettere iniziali erano giuste.
     */
    fun verify(password: String, saltBase64: String, hashBase64: String): Boolean {
        if (password.isEmpty() || saltBase64.isBlank() || hashBase64.isBlank()) return false
        val salt = runCatching { decode(saltBase64) }.getOrNull() ?: return false
        val calcolata = runCatching { hash(password, salt) }.getOrNull() ?: return false
        return MessageDigest.isEqual(
            calcolata.toByteArray(Charsets.UTF_8),
            hashBase64.toByteArray(Charsets.UTF_8)
        )
    }

    /**
     * Quanto tempo in secondo piano si perdona prima di richiedere di nuovo la
     * password. Zero: sempre. Negativo: mai, finché l'app resta viva.
     */
    fun expired(backgroundedAt: Long, now: Long, timeoutMinutes: Int): Boolean {
        if (backgroundedAt <= 0L) return false
        if (timeoutMinutes < 0) return false
        if (timeoutMinutes == 0) return true
        return now - backgroundedAt > timeoutMinutes * 60_000L
    }

    // ------------------------------------------------------------------ stato

    fun unlock() {
        locked = false
        backgroundedAt = 0L
    }

    fun lockNow() {
        locked = true
    }

    fun onBackground(now: Long) {
        if (!locked) backgroundedAt = now
    }

    /** Da chiamare quando l'app torna in primo piano. */
    fun onForeground(now: Long, timeoutMinutes: Int) {
        if (expired(backgroundedAt, now, timeoutMinutes)) locked = true
        backgroundedAt = 0L
    }
}
