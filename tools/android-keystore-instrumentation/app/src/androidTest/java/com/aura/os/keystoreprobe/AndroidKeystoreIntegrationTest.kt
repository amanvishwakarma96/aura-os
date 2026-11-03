package com.aura.os.keystoreprobe

import android.content.Context
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aura.os.provider.AndroidKeystoreChatGptSessionStore
import com.aura.os.provider.AuraChatGptSessionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Real Android Keystore instrumentation using ONLY synthetic credentials. */
@RunWith(AndroidJUnit4::class)
class AndroidKeystoreIntegrationTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val userId: Int
        get() = Process.myUid() / 100_000

    private fun freshAccount(): AuraChatGptSessionManager.AccountKey =
        AuraChatGptSessionManager.AccountKey(userId, "oaiapp_probe_${UUID.randomUUID()}")

    private fun newSession(
        account: AuraChatGptSessionManager.AccountKey,
        generation: Long,
        subject: String = "test-only-subject",
    ) = AuraChatGptSessionManager.Session(
        account,
        "https://auth.openai.com",
        subject,
        "test-access-$generation",
        "test-refresh-$generation",
        "test.signed.id.$generation",
        setOf("openid", "chatgpt.tokens.use.direct"),
        System.currentTimeMillis() / 1000L + 3600L,
        null,
        generation,
    )

    private fun storedFile(account: AuraChatGptSessionManager.AccountKey): File {
        val original = "AURA_PLAN_V1:${account.androidUserId}:${account.issuedClientId}"
        val digest = MessageDigest.getInstance("SHA-256").digest(original.toByteArray())
        val name = digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return File(context.noBackupFilesDir, "aura_chatgpt_v1/$name.bin")
    }

    @Test
    fun genuineKeystoreEncryptionPersistsAndRefreshRotates() {
        val key = freshAccount()
        val store = AndroidKeystoreChatGptSessionStore(context)
        try {
            assertNull(store.read(key))
            assertTrue(store.replaceAtomic(key, null, newSession(key, 1)))
            assertTrue(storedFile(key).isFile)
            val ciphertext = storedFile(key).readBytes().toString(Charsets.ISO_8859_1)
            assertFalse(ciphertext.contains("test-access-1"))
            assertFalse(ciphertext.contains("test-refresh-1"))

            // Fresh adapter instance must load the same Android Keystore key.
            val reopened = AndroidKeystoreChatGptSessionStore(context)
            val previous = reopened.read(key)
            assertNotNull(previous)
            assertEquals("test-refresh-1", previous!!.refreshToken)
            assertEquals(1L, previous.generation)
            assertFalse(reopened.replaceAtomic(key, null, newSession(key, 2)))
            assertTrue(reopened.replaceAtomic(key, 1, newSession(key, 2)))
            assertEquals(2L, store.read(key)!!.generation)
            assertEquals("test-refresh-2", store.read(key)!!.refreshToken)
            assertFalse(store.replaceAtomic(key, 1, newSession(key, 2)))
        } finally {
            store.delete(key)
        }
        assertNull(AndroidKeystoreChatGptSessionStore(context).read(key))
        assertFalse(storedFile(key).exists())
    }

    @Test
    fun crossProfileAccountAndCorruptedCiphertextAreRejected() {
        val key = freshAccount()
        val otherProfile = AuraChatGptSessionManager.AccountKey(userId + 1, key.issuedClientId)
        val store = AndroidKeystoreChatGptSessionStore(context)
        try {
            assertTrue(store.replaceAtomic(key, null, newSession(key, 1)))
            assertTrue(runCatching { store.read(otherProfile) }.isFailure)
            assertTrue(runCatching {
                store.replaceAtomic(otherProfile, null, newSession(otherProfile, 1))
            }.isFailure)

            val record = storedFile(key)
            val bytes = record.readBytes()
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
            record.writeBytes(bytes)
            assertTrue(runCatching { store.read(key) }.isFailure)
        } finally {
            store.delete(key)
        }
    }

    @Test
    fun subjectSwitchingIsDeniedAndLogoutDisablesAccess() {
        val key = freshAccount()
        val store = AndroidKeystoreChatGptSessionStore(context)
        try {
            assertTrue(store.replaceAtomic(key, null, newSession(key, 1)))
            assertTrue(runCatching {
                store.replaceAtomic(key, 1, newSession(key, 2, "different-subject"))
            }.isFailure)
            assertEquals("test-only-subject", store.read(key)!!.subject)
        } finally {
            store.delete(key)
        }
        assertNull(AndroidKeystoreChatGptSessionStore(context).read(key))
    }
}
