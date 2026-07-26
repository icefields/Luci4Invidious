import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// ── Read secrets from secrets.properties ─────────────────────────────
val secretsFile = rootProject.file("secrets.properties")
val secrets = Properties()
if (secretsFile.exists()) {
    secretsFile.inputStream().use { secrets.load(it) }
}

val invidiousHost = secrets.getProperty("INVIDIOUS_HOST") ?: "my.invidious.org"
val invidiousUser = secrets.getProperty("INVIDIOUS_USER") ?: "user"
val invidiousPass = secrets.getProperty("INVIDIOUS_PASS") ?: "pass"

android {
    namespace = "ca.devilplan.luci4invidious"
    compileSdk = 34

    defaultConfig {
        applicationId = "ca.devilplan.luci4invidious"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Inject secrets into BuildConfig so they're available at runtime
        buildConfigField("String", "INVIDIOUS_HOST", "\"$invidiousHost\"")
        buildConfigField("String", "INVIDIOUS_USER", "\"$invidiousUser\"")
        buildConfigField("String", "INVIDIOUS_PASS", "\"$invidiousPass\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.0")
    testImplementation("junit:junit:4.13.2")
}