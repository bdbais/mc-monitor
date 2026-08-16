package com.bellizia.mcmonitor.data

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class TransferException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Esporta e importa la configurazione dei server in un file cifrato con una
 * password.
 *
 * Il file contiene credenziali SSH e RCON: viaggia su chat e email, quindi è
 * cifrato con AES-GCM e una chiave derivata dalla password con PBKDF2. GCM
 * autentica il contenuto: se il file viene manomesso o la password è sbagliata,
 * la decifratura fallisce invece di produrre dati falsi.
 */
object ConfigTransfer {

    private const val MAGIC = "MCMONITOR1"
    private const val ITERATIONS = 210_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    fun export(servers: List<ServerConfig>, password: String): String {
        require(password.length >= 8) { "la password deve avere almeno 8 caratteri" }
        if (servers.isEmpty()) throw TransferException("Nessun server da esportare.")

        val payload = JSONObject().apply {
            put("versione", 1)
            put("creato", System.currentTimeMillis())
            put("servers", JSONArray().also { array -> servers.forEach { array.put(it.toJson()) } })
        }.toString().toByteArray(Charsets.UTF_8)

        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(TAG_BITS, iv))
        }
        val sealed = cipher.doFinal(payload)

        val envelope = JSONObject().apply {
            put("formato", MAGIC)
            put("kdf", "PBKDF2WithHmacSHA256")
            put("iterazioni", ITERATIONS)
            put("salt", base64(salt))
            put("iv", base64(iv))
            put("dati", base64(sealed))
        }
        return envelope.toString(2)
    }

    /** Restituisce i server contenuti nel file, senza inserirli: decide il chiamante. */
    fun import(content: String, password: String): List<ServerConfig> {
        val envelope = runCatching { JSONObject(content) }
            .getOrElse { throw TransferException("Il file non è una configurazione di MC Monitor.") }

        if (envelope.optString("formato") != MAGIC) {
            throw TransferException("Formato non riconosciuto: manca l'intestazione di MC Monitor.")
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

        val payload = runCatching { JSONObject(String(plain, Charsets.UTF_8)) }
            .getOrElse { throw TransferException("Contenuto illeggibile dopo la decifratura.") }
        val array = payload.optJSONArray("servers") ?: JSONArray()
        val servers = (0 until array.length()).mapNotNull { i ->
            array.optJSONObject(i)?.let { ServerConfig.fromJson(it) }
        }
        if (servers.isEmpty()) throw TransferException("Il file non contiene nessun server.")
        return servers
    }

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
