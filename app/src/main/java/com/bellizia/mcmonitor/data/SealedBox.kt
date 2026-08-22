package com.bellizia.mcmonitor.data

import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * La busta chiusa a chiave in cui l'app fa viaggiare le sue configurazioni.
 *
 * Chiusa con AES-GCM e una chiave derivata dalla password con PBKDF2. GCM
 * autentica il contenuto: se il file viene manomesso o la password è sbagliata,
 * l'apertura fallisce invece di restituire dati inventati.
 *
 * L'intestazione dice di che busta si tratta: le credenziali di collegamento e
 * il progetto di un server sono due cose diverse, e aprire l'una aspettandosi
 * l'altra deve dare un errore chiaro, non un elenco vuoto.
 */
object SealedBox {

    private const val ITERATIONS = 210_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    const val MIN_PASSWORD = 8

    fun seal(magic: String, payload: ByteArray, password: String, compress: Boolean = false): String {
        require(password.length >= MIN_PASSWORD) { "la password deve avere almeno $MIN_PASSWORD caratteri" }

        val dati = if (compress) gzip(payload) else payload
        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }

        val sealed = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(TAG_BITS, iv))
        }.doFinal(dati)

        return JSONObject().apply {
            put("formato", magic)
            put("kdf", "PBKDF2WithHmacSHA256")
            put("iterazioni", ITERATIONS)
            if (compress) put("compresso", true)
            put("salt", base64(salt))
            put("iv", base64(iv))
            put("dati", base64(sealed))
        }.toString(2)
    }

    fun open(magic: String, content: String, password: String, wrongKind: String): ByteArray {
        val envelope = runCatching { JSONObject(content) }
            .getOrElse { throw TransferException("Il file non è una configurazione di MC Monitor.") }

        val formato = envelope.optString("formato")
        if (formato != magic) {
            throw TransferException(
                if (formato.isNotBlank()) wrongKind
                else "Formato non riconosciuto: manca l'intestazione di MC Monitor."
            )
        }

        val salt = decode(envelope.optString("salt"))
        val iv = decode(envelope.optString("iv"))
        val sealed = decode(envelope.optString("dati"))
        val iterations = envelope.optInt("iterazioni", ITERATIONS)

        val plain = try {
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, deriveKey(password, salt, iterations), GCMParameterSpec(TAG_BITS, iv))
            }.doFinal(sealed)
        } catch (e: Exception) {
            // GCM non distingue fra password errata e file manomesso: entrambi
            // fanno fallire la verifica del tag, ed è giusto dirlo così.
            throw TransferException("Password errata, oppure il file è stato alterato.", e)
        }

        return if (envelope.optBoolean("compresso")) {
            runCatching { gunzip(plain) }
                .getOrElse { throw TransferException("Contenuto illeggibile dopo la decifratura.") }
        } else {
            plain
        }
    }

    private fun gzip(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(data) }
        return out.toByteArray()
    }

    private fun gunzip(data: ByteArray): ByteArray =
        GZIPInputStream(data.inputStream()).use { it.readBytes() }

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int = ITERATIONS): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(key, "AES")
    }

    private fun base64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decode(value: String): ByteArray =
        runCatching { Base64.decode(value, Base64.NO_WRAP) }
            .getOrElse { throw TransferException("File danneggiato: contenuto non leggibile.") }
}
