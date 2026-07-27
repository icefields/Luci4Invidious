import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
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
    namespace = "com.devilplan.luci4invidious"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.devilplan.luci4invidious"
        minSdk = 24
        targetSdk = 37
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
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation(platform("androidx.compose:compose-bom:2025.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    testImplementation("junit:junit:4.13.2")
}