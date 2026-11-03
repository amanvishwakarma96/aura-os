package com.aura.os.provider

import android.content.Context
import android.os.Build
import android.os.Process
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.io.RandomAccessFile
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Platform-specific session backend; compile/test status is tracked by CI.
 * Runs inside AURA's eventual *unprivileged credential-protected* process,
 * not SystemServer, and never shares secrets through Binder IPC.
 *
 * Only usable on Android 15+ (AURA reference minSdk 35). Strictly avoid
 * device-protected direct-boot storage and Auto Backup.
 */
class AndroidKeystoreChatGptSessionStore(private val context: Context) :
    AuraChatGptSessionManager.SessionStore {
    init {
        require(Build.VERSION.SDK_INT >= 35) { "Android 15+ required" }
        require(!context.isDeviceProtectedStorage) {
            "ChatGPT credentials must use credential-encrypted storage"
        }
    }

    private val backing = AuraEncryptedSessionStore(
        AndroidRecords(context),
        AndroidKeys(context),
    )

    override fun read(key: AuraChatGptSessionManager.AccountKey) = backing.read(key)
    override fun replaceAtomic(
        key: AuraChatGptSessionManager.AccountKey,
        expectedGeneration: Long?,
        session: AuraChatGptSessionManager.Session,
    ) = backing.replaceAtomic(key, expectedGeneration, session)
    override fun delete(key: AuraChatGptSessionManager.AccountKey) = backing.delete(key)

    private companion object {
        // File lock ensures serialization between processes; JVM lock also
        // prevents OverlappingFileLockException between threads in one process.
        val processGuard = Any()

        fun accountId(key: AuraChatGptSessionManager.AccountKey): String {
            require(key.androidUserId == Process.myUserHandle().identifier) {
                "Android profile mismatch"
            }
            val canonical = "AURA_PLAN_V1:" + key.androidUserId + ":" + key.issuedClientId
            val hash = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray())
            return hash.joinToString("") { "%02x".format(it.toInt() and 0xff) }
        }

        fun baseDir(context: Context): File {
            require(!context.isDeviceProtectedStorage) {
                "Device-protected credential storage is forbidden"
            }
            val directory = File(context.noBackupFilesDir, "aura_chatgpt_v1")
            require(directory.isDirectory || directory.mkdirs()) {
                "Unable to create private credential directory"
            }
            return directory
        }
    }

    private class AndroidRecords(private val context: Context) :
        AuraEncryptedSessionStore.AtomicRecords {
        private val root = baseDir(context)

        private fun backing(key: AuraChatGptSessionManager.AccountKey) =
            AtomicFile(File(root, accountId(key) + ".bin"))

        override fun <T> withExclusiveLock(
            account: AuraChatGptSessionManager.AccountKey,
            block: () -> T,
        ): T = synchronized(processGuard) {
            val lock = File(root, accountId(account) + ".lock")
            RandomAccessFile(lock, "rw").use { file ->
                file.channel.use { channel ->
                    channel.lock().use { block() }
                }
            }
        }

        override fun read(account: AuraChatGptSessionManager.AccountKey): ByteArray? {
            try {
                val bytes = backing(account).readFully()
                require(bytes.size <= 131_072) { "Credential ciphertext too large" }
                return bytes
            } catch (_: FileNotFoundException) {
                return null
            }
            // Deliberately propagate corruption and Keystore errors; no
            // silent recreation/re-login that might conceal data substitution.
        }

        override fun writeAtomic(
            account: AuraChatGptSessionManager.AccountKey,
            data: ByteArray,
        ) {
            require(data.size in 32..131_072) { "Invalid ciphertext size" }
            val file = backing(account)
            val stream = file.startWrite()
            var committed = false
            try {
                stream.write(data)
                file.finishWrite(stream)
                committed = true
            } finally {
                if (!committed) file.failWrite(stream)
            }
        }

        override fun delete(account: AuraChatGptSessionManager.AccountKey) {
            backing(account).delete()
        }
    }

    private class AndroidKeys(private val context: Context) :
        AuraEncryptedSessionStore.KeySource {
        private fun alias(account: AuraChatGptSessionManager.AccountKey) =
            "aura_chatgpt_aes_" + accountId(account)

        private fun store(): KeyStore =
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

        override fun existing(account: AuraChatGptSessionManager.AccountKey): SecretKey? {
            val key = store().getKey(alias(account), null)
            return key as? SecretKey
        }

        override fun getOrCreate(
            account: AuraChatGptSessionManager.AccountKey,
        ): SecretKey {
            existing(account)?.let { return it }
            val builder = KeyGenParameterSpec.Builder(
                alias(account),
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                // Supported on Android 15+; disallow use while locked.
                .setUnlockedDeviceRequired(true)
                .setUserAuthenticationRequired(false)
            return KeyGenerator.getInstance("AES", "AndroidKeyStore").run {
                init(builder.build())
                generateKey()
            }
        }

        override fun delete(account: AuraChatGptSessionManager.AccountKey) {
            store().deleteEntry(alias(account))
        }
    }
}
