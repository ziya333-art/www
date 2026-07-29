package com.jegly.www.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.CompletableDeferred
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for the SQLCipher passphrase.
 *
 * Two modes:
 *  - Plain mode: passphrase lives in EncryptedSharedPreferences. Available immediately on app start.
 *  - Biometric mode: passphrase is encrypted under an Android-Keystore key marked
 *    `setUserAuthenticationRequired(true)`. Decryption requires a Cipher unlocked by
 *    BiometricPrompt's CryptoObject — there is no software path to the passphrase.
 *
 * AppModule's database provider blocks on [await] until the appropriate flow has supplied
 * the passphrase via [supply].
 */
@Singleton
class PassphraseGate @Inject constructor(
    private val encryptionManager: EncryptionManager
) {
    private val deferred = CompletableDeferred<ByteArray>()

    /** Block until the passphrase is available. Called by AppModule's provideDatabase. */
    suspend fun await(): ByteArray = deferred.await()

    /** Mark the gate as open with the given passphrase. Idempotent — second call is ignored. */
    fun supply(passphrase: ByteArray) {
        deferred.complete(passphrase)
    }

    /** True if the gate has already been opened. */
    fun isOpen(): Boolean = deferred.isCompleted

    // ─── Plain mode ──────────────────────────────────────────────────────────────────────────

    /**
     * Reads (or creates on first use) the passphrase stored in EncryptedSharedPreferences.
     *
     * DANGER: the create-on-absent behaviour is why [removePlain] exists as a separate, guarded
     * operation. Once the plain copy has been removed in favour of a passcode-wrapped one, calling
     * this again would mint a *brand new* random passphrase and the existing encrypted database
     * would become permanently unopenable. Callers on any path that can run while a passcode is
     * configured must use [loadPlainOrNull] instead and route to the unlock screen when it is null.
     */
    fun loadOrCreatePlainPassphrase(): ByteArray {
        val hex = encryptionManager.getString(PREF_DB_KEY_HEX) ?: run {
            val bytes = ByteArray(32)
            SecureRandom().nextBytes(bytes)
            val key = bytes.joinToString("") { "%02x".format(it) }
            encryptionManager.saveString(PREF_DB_KEY_HEX, key)
            key
        }
        return hex.toByteArray(StandardCharsets.UTF_8)
    }

    /** Non-creating read. Null once the plain copy has been removed in favour of a passcode. */
    fun loadPlainOrNull(): ByteArray? =
        encryptionManager.getString(PREF_DB_KEY_HEX)?.toByteArray(StandardCharsets.UTF_8)

    fun hasPlainPassphrase(): Boolean = encryptionManager.getString(PREF_DB_KEY_HEX) != null

    /**
     * Deletes the unwrapped passphrase, leaving the passcode-wrapped copy as the only way in.
     *
     * Until this is called, a passcode is only a UI gate: the database key still sits in
     * EncryptedSharedPreferences, so anyone who can read the app's data directory (root, a device
     * backup, forensic extraction) can open the database without ever knowing the passcode. Callers
     * must have verified that a working wrapped copy exists first — this is deliberately not
     * something the gate checks for them, because it cannot verify someone else's wrap.
     */
    fun removePlain() {
        encryptionManager.securePrefs.edit().remove(PREF_DB_KEY_HEX).apply()
    }

    /** Restores the plain copy, used when the last knowledge/biometric factor is turned off. */
    fun restorePlain(passphrase: ByteArray) {
        encryptionManager.saveString(PREF_DB_KEY_HEX, passphrase.toString(StandardCharsets.UTF_8))
    }

    // ─── Biometric mode ──────────────────────────────────────────────────────────────────────

    /** True if a biometric-wrapped passphrase blob exists. */
    fun hasBiometricWrappedPassphrase(): Boolean =
        encryptionManager.getString(PREF_DB_KEY_WRAPPED_CIPHERTEXT) != null

    /**
     * Builds an init'd encryption Cipher for the biometric-protected Keystore key.
     * Pass to BiometricPrompt.authenticate(CryptoObject(cipher)). On success the same
     * cipher object is usable for [wrapPlainPassphraseUnderBiometric].
     */
    fun makeEncryptionCipher(): Cipher {
        val key = getOrCreateBiometricKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher
    }

    /**
     * Builds a decryption Cipher pre-loaded with the stored IV. Pass to BiometricPrompt;
     * on success use [unwrapPlainPassphraseWithCipher] to retrieve the passphrase.
     */
    fun makeDecryptionCipher(): Cipher? {
        val ivB64 = encryptionManager.getString(PREF_DB_KEY_WRAPPED_IV) ?: return null
        val iv = Base64.decode(ivB64, Base64.NO_WRAP)
        val key = getOrCreateBiometricKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher
    }

    /** Encrypts a plaintext passphrase with a biometric-unlocked Cipher and persists the result. */
    fun wrapPlainPassphraseUnderBiometric(plaintext: ByteArray, biometricUnlockedCipher: Cipher) {
        val ciphertext = biometricUnlockedCipher.doFinal(plaintext)
        encryptionManager.saveString(
            PREF_DB_KEY_WRAPPED_CIPHERTEXT,
            Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        )
        encryptionManager.saveString(
            PREF_DB_KEY_WRAPPED_IV,
            Base64.encodeToString(biometricUnlockedCipher.iv, Base64.NO_WRAP)
        )
    }

    /** Decrypts the stored wrapped passphrase using a biometric-unlocked Cipher. */
    fun unwrapPlainPassphraseWithCipher(biometricUnlockedCipher: Cipher): ByteArray? {
        val ctB64 = encryptionManager.getString(PREF_DB_KEY_WRAPPED_CIPHERTEXT) ?: return null
        val ciphertext = Base64.decode(ctB64, Base64.NO_WRAP)
        return biometricUnlockedCipher.doFinal(ciphertext)
    }

    /** Forgets the biometric-wrapped passphrase. Plain-mode passphrase remains intact. */
    fun clearBiometricWrap() {
        encryptionManager.securePrefs.edit()
            .remove(PREF_DB_KEY_WRAPPED_CIPHERTEXT)
            .remove(PREF_DB_KEY_WRAPPED_IV)
            .apply()
        runCatching {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                .deleteEntry(BIOMETRIC_KEY_ALIAS)
        }
    }

    private fun getOrCreateBiometricKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(BIOMETRIC_KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(
            BIOMETRIC_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            // Invalidate the key if biometrics are added/removed — forces re-enrolment.
            .setInvalidatedByBiometricEnrollment(true)
            .build()
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        kg.init(spec)
        return kg.generateKey()
    }

    companion object {
        private const val PREF_DB_KEY_HEX = "db_key"                            // plain-mode passphrase
        private const val PREF_DB_KEY_WRAPPED_CIPHERTEXT = "db_key_wrapped"     // biometric-wrapped ciphertext
        private const val PREF_DB_KEY_WRAPPED_IV = "db_key_wrapped_iv"          // GCM IV (12 bytes)
        private const val BIOMETRIC_KEY_ALIAS = "www_db_passphrase_biometric"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
    }
}
