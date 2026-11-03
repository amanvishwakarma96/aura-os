package com.aura.os.provider

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Host-testable encrypted session storage boundary.
 *
 * Production MUST inject AndroidKeyStore-backed key operations and an
 * atomic, private, credential-protected disk backend. All record operations
 * for the same account must be serialized across OS processes.
 *
 * No secret is stored in a filename or printed in exception messages.
 */
class AuraEncryptedSessionStore(
    private val records: AtomicRecords,
    private val keyStore: KeySource,
    private val random: SecureRandom = SecureRandom(),
) : AuraChatGptSessionManager.SessionStore {
    interface AtomicRecords {
        /** Lock remains held until block exits, including on exceptions. */
        fun <T> withExclusiveLock(
            account: AuraChatGptSessionManager.AccountKey,
            block: () -> T,
        ): T
        /** Must be called under the account lock; returns the FULL record. */
        fun read(account: AuraChatGptSessionManager.AccountKey): ByteArray?
        /** Crash-consistent all-or-nothing replacement. */
        fun writeAtomic(account: AuraChatGptSessionManager.AccountKey, data: ByteArray)
        /** Must not restore plaintext records from backups. */
        fun delete(account: AuraChatGptSessionManager.AccountKey)
    }

    interface KeySource {
        /** Must never create a new key if an encrypted record already exists. */
        fun existing(account: AuraChatGptSessionManager.AccountKey): SecretKey?
        fun getOrCreate(account: AuraChatGptSessionManager.AccountKey): SecretKey
        fun delete(account: AuraChatGptSessionManager.AccountKey)
    }

    override fun read(key: AuraChatGptSessionManager.AccountKey) =
        records.withExclusiveLock(key) { readUnderLock(key) }

    override fun replaceAtomic(
        key: AuraChatGptSessionManager.AccountKey,
        expectedGeneration: Long?,
        session: AuraChatGptSessionManager.Session,
    ): Boolean = records.withExclusiveLock(key) {
        require(key == session.key) { "Stored account key mismatch" }
        val previous = readUnderLock(key)
        if (previous?.generation != expectedGeneration) {
            false
        } else {
            val next = (previous?.generation ?: 0L) + 1L
            require(next > 0L && session.generation == next) {
                "Invalid session generation transition"
            }
            if (previous != null) {
                require(previous.subject == session.subject &&
                    previous.issuer == session.issuer) {
                    "Cannot silently replace ChatGPT account identity"
                }
            }
            // The Android implementation creates an AES key inside Keystore.
            val secret = keyStore.getOrCreate(key)
            val encoded = AuraSealedSessionCodec.encode(key, session, secret, random)
            records.writeAtomic(key, encoded)
            true
        }
    }

    override fun delete(key: AuraChatGptSessionManager.AccountKey) {
        records.withExclusiveLock(key) {
            // Delete the encryption key FIRST. A crash afterward leaves an
            // unreadable record instead of reviving a disconnected session.
            keyStore.delete(key)
            records.delete(key)
        }
    }

    private fun readUnderLock(key: AuraChatGptSessionManager.AccountKey):
        AuraChatGptSessionManager.Session? {
        val encrypted = records.read(key) ?: return null
        val secret = keyStore.existing(key)
            ?: error("Keystore credential key unavailable; sign in again")
        return AuraSealedSessionCodec.decode(key, encrypted, secret)
    }
}

/**
 * Binary AES-256-GCM envelope. The entire session is authenticated, including
 * version, Android user ID, issued OAuth client ID and generation.
 *
 * Outer header = magic (4), version (1), random IV (12); ciphertext + 128-bit
 * tag. AAD binds the exact account even if a file is moved to another profile.
 */
object AuraSealedSessionCodec {
    private val magic = byteArrayOf(0x41, 0x55, 0x52, 0x41) // AURA
    private const val VERSION = 1
    private const val IV_BYTES = 12
    private const val MAX_RECORD = 131_072
    private const val TAG_BITS = 128

    fun encode(
        key: AuraChatGptSessionManager.AccountKey,
        session: AuraChatGptSessionManager.Session,
        secret: SecretKey,
        random: SecureRandom = SecureRandom(),
    ): ByteArray {
        validate(session, key)
        val plaintext = ByteArrayOutputStream()
        DataOutputStream(plaintext).use { out ->
            out.writeInt(key.androidUserId)
            out.writeUTF(key.issuedClientId)
            out.writeLong(session.generation)
            out.writeUTF(session.issuer)
            out.writeUTF(session.subject)
            out.writeUTF(session.accessToken)
            out.writeUTF(session.refreshToken)
            out.writeUTF(session.idToken)
            out.writeLong(session.expiresAtEpochSeconds)
            out.writeBoolean(session.earliestRefreshAtEpochSeconds != null)
            session.earliestRefreshAtEpochSeconds?.let { out.writeLong(it) }
            out.writeInt(session.grantedScopes.size)
            for (scope in session.grantedScopes.sorted()) out.writeUTF(scope)
        }
        val iv = ByteArray(IV_BYTES)
        random.nextBytes(iv)
        val aad = aad(key)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secret, GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(aad)
        val sealed = cipher.doFinal(plaintext.toByteArray())
        val result = magic + byteArrayOf(VERSION.toByte()) + iv + sealed
        require(result.size <= MAX_RECORD) { "Encrypted session record exceeds size limit" }
        return result
    }

    fun decode(
        key: AuraChatGptSessionManager.AccountKey,
        bytes: ByteArray,
        secret: SecretKey,
    ): AuraChatGptSessionManager.Session {
        require(bytes.size in (4 + 1 + IV_BYTES + 16 + 1)..MAX_RECORD) {
            "Encrypted session record size invalid"
        }
        require(bytes.sliceArray(0..3).contentEquals(magic) &&
            bytes[4].toInt() == VERSION) {
            "Unknown encrypted session envelope"
        }
        val iv = bytes.copyOfRange(5, 5 + IV_BYTES)
        val ciphertext = bytes.copyOfRange(5 + IV_BYTES, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secret, GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(aad(key))
        val plaintext = cipher.doFinal(ciphertext) // Reject tampered/wrong-account content.
        val input = DataInputStream(ByteArrayInputStream(plaintext))
        val androidUser = input.readInt()
        val clientId = input.readUTF()
        require(androidUser == key.androidUserId && clientId == key.issuedClientId) {
            "Stored account identity mismatch"
        }
        val generation = input.readLong()
        val issuer = input.readUTF()
        val subject = input.readUTF()
        val access = input.readUTF()
        val refresh = input.readUTF()
        val idToken = input.readUTF()
        val expiresAt = input.readLong()
        val earliest = if (input.readBoolean()) input.readLong() else null
        val count = input.readInt()
        require(count in 1..64) { "Invalid number of granted scopes" }
        val scopes = (0 until count).map { input.readUTF() }.toSet()
        require(scopes.size == count && input.available() == 0) {
            "Invalid session scope list or trailing data"
        }
        val session = AuraChatGptSessionManager.Session(
            key, issuer, subject, access, refresh, idToken,
            scopes, expiresAt, earliest, generation
        )
        validate(session, key)
        return session
    }

    private fun aad(key: AuraChatGptSessionManager.AccountKey): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use {
            it.writeUTF("AURA_CHATGPT_PLAN_SESSION_V1")
            it.writeInt(key.androidUserId)
            it.writeUTF(key.issuedClientId)
        }
        return output.toByteArray()
    }

    private fun validate(
        session: AuraChatGptSessionManager.Session,
        key: AuraChatGptSessionManager.AccountKey,
    ) {
        require(session.key == key && session.generation > 0) {
            "Invalid session identity or generation"
        }
        require(session.issuer == "https://auth.openai.com" &&
            session.subject.length in 1..512) { "Invalid trusted issuer/subject" }
        require(session.accessToken.length in 1..16_384 &&
            session.refreshToken.length in 1..16_384 &&
            session.idToken.length in 1..32_768) {
            "Invalid session credentials"
        }
        require(session.expiresAtEpochSeconds > 0 &&
            (session.earliestRefreshAtEpochSeconds == null ||
             session.earliestRefreshAtEpochSeconds > 0)) {
            "Invalid session expiration/refresh schedule"
        }
        require(session.grantedScopes.size in 1..64 &&
            session.grantedScopes.all { scope ->
                scope.length in 1..128 &&
                    scope.all { c -> c.isLetterOrDigit() || c in "._:-" }
            }) { "Invalid granted scopes" }
    }
}
