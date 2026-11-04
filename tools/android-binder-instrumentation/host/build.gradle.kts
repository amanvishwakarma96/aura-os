plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.aura.os.binderprobe"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aura.os.binderprobe"
        minSdk = 35
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets.getByName("main").java.srcDirs(
        file("../../../platform/aura/framework/src"),
        file("../../../platform/aura/android-adapter/src/com/aura/os/binder"),
        file("../../../platform/aura/android-adapter/probe/host/src")
    )
    sourceSets.getByName("main").manifest.srcFile(
        file("../../../platform/aura/android-adapter/probe/host/AndroidManifest.xml")
    )
    sourceSets.getByName("androidTest").java.srcDirs(
        file("../../../platform/aura/android-adapter/probe/tests/src")
    )
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("junit:junit:4.13.2")
}
