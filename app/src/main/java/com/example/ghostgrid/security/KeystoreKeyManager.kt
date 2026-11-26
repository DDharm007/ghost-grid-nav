package com.example.ghostgrid.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Hardware-backed Android Keystore key manager for GhostGrid.
 *
 * - Stores an AES-256-GCM master wrapping key exclusively inside AndroidKeyStore.
 * - Generates a cryptographically random 256-bit SQLCipher passphrase, wraps it
 *   with the Keystore master key, and persists only the ciphertext inside
 *   `context.noBackupFilesDir`.
 * - Also encrypts/decrypts optional user-configured Hybrid API keys at rest.
 */
class KeystoreKeyManager(private val context: Context) {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val MASTER_KEY_ALIAS = "ghostgrid_sqlcipher_master_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val PASSPHRASE_LENGTH_BYTES = 32
        private const val VAULT_FILE_NAME = "gg_sqlcipher_key.enc"
    }

    private val vaultFile: File
        get() = File(context.noBackupFilesDir, VAULT_FILE_NAME)

    @Synchronized
    fun getOrCreateSqlCipherPassphrase(): ByteArray {
        val secretKey = getOrCreateMasterKey()
        if (vaultFile.exists()) {
            val wrapped = vaultFile.readBytes()
            if (wrapped.size > GCM_IV_LENGTH_BYTES) {
                return decryptBytes(secretKey, wrapped)
            }
        }
        val rawPassphrase = ByteArray(PASSPHRASE_LENGTH_BYTES).also {
            SecureRandom().nextBytes(it)
        }
        val encrypted = encryptBytes(secretKey, rawPassphrase)
        val tmpFile = File(context.noBackupFilesDir, "$VAULT_FILE_NAME.tmp")
        tmpFile.writeBytes(encrypted)
        if (!tmpFile.renameTo(vaultFile)) {
            vaultFile.writeBytes(encrypted)
            tmpFile.delete()
        }
        return rawPassphrase
    }

    fun encryptSecretString(plaintext: String): String {
        if (plaintext.isEmpty()) return ""
        val key = getOrCreateMasterKey()
        val encrypted = encryptBytes(key, plaintext.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    fun decryptSecretString(encodedCiphertext: String): String {
        if (encodedCiphertext.isBlank()) return ""
        return runCatching {
            val key = getOrCreateMasterKey()
            val raw = Base64.decode(encodedCiphertext, Base64.NO_WRAP)
            String(decryptBytes(key, raw), Charsets.UTF_8)
        }.getOrDefault("")
    }

    fun isHardwareKeystoreAvailable(): Boolean {
        return runCatching {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE)
            ks.load(null)
            ks.containsAlias(MASTER_KEY_ALIAS) || getOrCreateMasterKey() != null
        }.getOrDefault(false)
    }

    private fun getOrCreateMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(MASTER_KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun encryptBytes(key: SecretKey, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        return ByteArray(iv.size + ciphertext.size).apply {
            System.arraycopy(iv, 0, this, 0, iv.size)
            System.arraycopy(ciphertext, 0, this, iv.size, ciphertext.size)
        }
    }

    private fun decryptBytes(key: SecretKey, payload: ByteArray): ByteArray {
        val iv = payload.copyOfRange(0, GCM_IV_LENGTH_BYTES)
        val ciphertext = payload.copyOfRange(GCM_IV_LENGTH_BYTES, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return cipher.doFinal(ciphertext)
    }
}
