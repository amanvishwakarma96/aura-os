"""Offline Android credential-adapter wiring checks — NOT Android instrumentation."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "platform/aura/android-adapter/src/com/aura/os/provider/AndroidKeystoreChatGptSessionStore.kt"
FRAMEWORK = ROOT / "platform/aura/framework/src/com/aura/os/provider/AuraEncryptedSessionStore.kt"


class CredentialStoreLayoutTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.android = SRC.read_text(encoding="utf-8")
        cls.core = FRAMEWORK.read_text(encoding="utf-8")

    def test_platform_keystore_isolated_from_framework(self):
        self.assertIn('KeyStore.getInstance("AndroidKeyStore")', self.android)
        self.assertNotIn("AndroidKeyStore", self.core)
        self.assertIn('KeyProperties.BLOCK_MODE_GCM', self.android)
        self.assertIn('setUnlockedDeviceRequired(true)', self.android)
        self.assertIn('setRandomizedEncryptionRequired(true)', self.android)

    def test_private_atomic_storage(self):
        self.assertIn("context.noBackupFilesDir", self.android)
        self.assertIn("!context.isDeviceProtectedStorage", self.android)
        self.assertIn("AtomicFile", self.android)
        self.assertIn("channel.lock()", self.android)
        self.assertIn("file.finishWrite(stream)", self.android)
        self.assertIn("file.failWrite(stream)", self.android)

    def test_account_isolation_and_disconnect(self):
        self.assertIn("UserHandle.getUserId(Process.myUid())", self.android)
        self.assertIn('MessageDigest.getInstance("SHA-256")', self.android)
        self.assertIn("keyStore.delete(key)", self.core)
        self.assertIn("records.delete(key)", self.core)
        self.assertIn("GCMParameterSpec", self.core)
        self.assertIn("cipher.updateAAD(aad(key))", self.core)

    def test_no_production_privileged_wiring(self):
        self.assertNotIn("SystemServer", self.core)
        self.assertNotIn("Binder.getCallingUid", self.android)
        self.assertNotIn("getExternalFilesDir", self.android)
        self.assertNotIn("SharedPreferences", self.android)


if __name__ == "__main__":
    unittest.main()
