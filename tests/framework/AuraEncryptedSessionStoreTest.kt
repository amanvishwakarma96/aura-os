package com.aura.os.provider

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.SecureRandom
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

private typealias Account = AuraChatGptSessionManager.AccountKey
private typealias Session = AuraChatGptSessionManager.Session

private fun fails(block: () -> Any?) {
    check(runCatching(block).isFailure) { "Expected invalid encrypted session operation to fail" }
}

private class MemoryKeys : AuraEncryptedSessionStore.KeySource {
    private val secrets = mutableMapOf<Account, SecretKey>()
    @Synchronized override fun existing(account: Account) = secrets[account]
    @Synchronized override fun getOrCreate(account: Account): SecretKey =
        secrets.getOrPut(account) {
            KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        }
    @Synchronized override fun delete(account: Account) {
        secrets.remove(account)
    }
}

/**
 * Uses real disk files and atomic rename, with cross-instance JVM locking.
 * This is a host test substitute, NOT an Android Keystore implementation.
 */
private class DiskRecords(private val root: Path) : AuraEncryptedSessionStore.AtomicRecords {
    companion object {
        private val processGuard = Any()
    }

    private fun name(key: Account): String =
        "user_" + key.androidUserId + "_" + key.issuedClientId

    fun file(key: Account): Path = root.resolve(name(key) + ".bin")
    private fun lockFile(key: Account): Path = root.resolve(name(key) + ".lock")

    override fun <T> withExclusiveLock(account: Account, block: () -> T): T =
        synchronized(processGuard) {
            java.io.RandomAccessFile(lockFile(account).toFile(), "rw").use { file ->
                file.channel.use { channel -> channel.lock().use { block() } }
            }
        }

    override fun read(account: Account): ByteArray? {
        val path = file(account)
        return if (Files.exists(path)) Files.readAllBytes(path) else null
    }

    var failBeforeCommit = false

    override fun writeAtomic(account: Account, data: ByteArray) {
        val temp = Files.createTempFile(root, "aura-staging-", ".bin")
        try {
            Files.write(temp, data, StandardOpenOption.TRUNCATE_EXISTING)
            if (failBeforeCommit) throw IllegalStateException("Simulated crash before commit")
            Files.move(temp, file(account),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    override fun delete(account: Account) {
        Files.deleteIfExists(file(account))
    }
}

private fun session(
    key: Account,
    version: Long,
    access: String,
    refresh: String = "refresh-plan-$version",
    subject: String = "chatgpt-account-A",
    scopes: Set<String> = setOf("openid", "chatgpt.tokens.use.direct"),
) = Session(
    key, "https://auth.openai.com", subject, access, refresh,
    "signed.id.token-$version", scopes, 1_900_000_000L, null, version
)

fun main() {
    val dir = Files.createTempDirectory("aura-session-test-")
    try {
        val key = Account(0, "oaiapp_planA")
        val other = Account(0, "oaiapp_planB")
        val otherUser = Account(10, "oaiapp_planA")
        val disk = DiskRecords(dir)
        val keys = MemoryKeys()
        val store = AuraEncryptedSessionStore(disk, keys)

        check(store.read(key) == null)
        val original = session(key, 1, "top-secret-access", "top-secret-refresh")
        check(store.replaceAtomic(key, null, original))
        val ciphertext = Files.readAllBytes(disk.file(key))
        check(ciphertext.size > 16)
        check(!ciphertext.toString(Charsets.ISO_8859_1).contains("top-secret-access"))
        check(!ciphertext.toString(Charsets.ISO_8859_1).contains("top-secret-refresh"))
        check(!ciphertext.toString(Charsets.ISO_8859_1).contains("signed.id.token"))

        val loaded = store.read(key) ?: error("Expected encrypted record")
        check(loaded.generation == 1L)
        check(loaded.subject == "chatgpt-account-A")
        check(loaded.accessToken == original.accessToken)
        check(loaded.refreshToken == original.refreshToken)
        check(loaded.grantedScopes == original.grantedScopes)
        check("top-secret-access" !in loaded.toString())

        // Persisted ciphertext survives a new store object and supports CAS.
        val restarted = AuraEncryptedSessionStore(DiskRecords(dir), keys)
        check(restarted.read(key)?.refreshToken == "top-secret-refresh")
        check(!restarted.replaceAtomic(key, null, session(key, 2, "stale")))
        fails { restarted.replaceAtomic(key, 1, session(key, 3, "wrong-generation")) }
        fails { restarted.replaceAtomic(key, 1, session(other, 2, "wrong-account")) }
        fails { restarted.replaceAtomic(key, 1,
            session(key, 2, "different-subject", subject = "chatgpt-account-B")) }

        val rotated = session(key, 2, "rotated-access", "rotated-refresh")
        check(restarted.replaceAtomic(key, 1, rotated))
        check(store.read(key)?.refreshToken == "rotated-refresh")
        check(!store.replaceAtomic(key, 1, session(key, 2, "replay")))
        val nextCiphertext = Files.readAllBytes(disk.file(key))
        check(!ciphertext.contentEquals(nextCiphertext))

        // Simulate write interruption: original ciphertext remains usable.
        disk.failBeforeCommit = true
        fails { store.replaceAtomic(key, 2, session(key, 3, "uncommitted")) }
        disk.failBeforeCommit = false
        check(restarted.read(key)?.refreshToken == "rotated-refresh")
        check(restarted.read(key)?.generation == 2L)

        // Identically named OAuth clients on other Android profiles are isolated.
        check(store.read(otherUser) == null)
        check(store.replaceAtomic(otherUser, null, session(otherUser, 1, "user10-access")))
        check(store.replaceAtomic(other, null, session(other, 1, "clientB-access")))
        check(store.read(otherUser)?.accessToken == "user10-access")
        check(store.read(other)?.accessToken == "clientB-access")

        // Replay a ciphertext from a different account into another file:
        // GCM account-associated authenticated data must reject it.
        val originalB = Files.readAllBytes(disk.file(other))
        Files.write(disk.file(other), Files.readAllBytes(disk.file(key)))
        fails { store.read(other) }
        Files.write(disk.file(other), originalB)
        check(store.read(other)?.accessToken == "clientB-access")

        // Tamper/truncate/wrong key/swap header must never become valid tokens.
        val valid = Files.readAllBytes(disk.file(key))
        val tampered = valid.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        Files.write(disk.file(key), tampered)
        fails { store.read(key) }
        Files.write(disk.file(key), valid.copyOfRange(0, valid.size - 3))
        fails { store.read(key) }
        Files.write(disk.file(key), valid.copyOf().apply { this[4] = 3 })
        fails { store.read(key) }
        Files.write(disk.file(key), valid)
        fails { AuraEncryptedSessionStore(DiskRecords(dir), MemoryKeys()).read(key) }

        // Distinct encryption IVs even when the same plaintext is sealed.
        val secret = keys.existing(key) ?: error("AES key missing")
        val ivA = AuraSealedSessionCodec.encode(key, rotated, secret, SecureRandom())
        val ivB = AuraSealedSessionCodec.encode(key, rotated, secret, SecureRandom())
        check(!ivA.contentEquals(ivB))
        check(AuraSealedSessionCodec.decode(key, ivA, secret).generation == 2L)
        fails { AuraSealedSessionCodec.decode(otherUser, ivA, secret) }

        // Concurrent refreshes: exactly one compare-and-swap can commit.
        val ready = CountDownLatch(2)
        val go = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val attempts = (1..2).map { i ->
                pool.submit<Boolean> {
                    ready.countDown()
                    check(go.await(5, TimeUnit.SECONDS))
                    AuraEncryptedSessionStore(DiskRecords(dir), keys)
                        .replaceAtomic(key, 2, session(key, 3, "concurrent-$i"))
                }
            }
            check(ready.await(5, TimeUnit.SECONDS))
            go.countDown()
            check(attempts.count { it.get(10, TimeUnit.SECONDS) } == 1)
            check(store.read(key)?.generation == 3L)
        } finally {
            pool.shutdownNow()
        }

        store.delete(key)
        check(store.read(key) == null)
        check(keys.existing(key) == null)
        check(store.read(otherUser)?.accessToken == "user10-access")
        check(store.read(other)?.accessToken == "clientB-access")

        // An existing disk record with a missing Keystore key MUST error
        // rather than generating a new key or silently trusting the content.
        Files.write(disk.file(key), valid)
        fails { store.read(key) }
        Files.deleteIfExists(disk.file(key))

        val identityOnly = session(key, 1, "identity-only",
            scopes = setOf("openid", "profile"))
        check(store.replaceAtomic(key, null, identityOnly))
        check(store.read(key)?.planUsageGranted == false)
        println("PASS: encrypted session isolation, disk restart, atomic CAS, tamper and logout")
    } finally {
        Files.walk(dir).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }
}
