plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// An independently generated, TEST-ONLY APK signing certificate is mandatory.
// The target host uses the Android debug certificate. Same-signature attacker
// tests are invalid because they cannot verify signature-permission rejection.
val attackerKeyFile = providers.environmentVariable("AURA_ATTACKER_TEST_KEYSTORE")
    .orNull ?: error("Generate an ephemeral AURA_ATTACKER_TEST_KEYSTORE first")
val attackerKeyPassword = providers.environmentVariable("AURA_ATTACKER_TEST_PASSWORD")
    .orNull ?: error("Missing TEST-ONLY keystore password")

android {
    namespace = "com.aura.os.binderprobe.attacker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aura.os.binderprobe.attacker"
        minSdk = 35
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("attackerFixture") {
            storeFile = file(attackerKeyFile)
            storePassword = attackerKeyPassword
            keyAlias = "auraattacker"
            keyPassword = attackerKeyPassword
        }
    }
    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("attackerFixture")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets.getByName("main").java.srcDirs(
        file("../../../platform/aura/android-adapter/probe/attacker/src")
    )
    sourceSets.getByName("main").manifest.srcFile(
        file("../../../platform/aura/android-adapter/probe/attacker/AndroidManifest.xml")
    )
}

kotlin {
    jvmToolchain(17)
}
