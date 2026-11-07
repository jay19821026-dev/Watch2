plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseStoreFile = providers.gradleProperty("RELEASE_STORE_FILE").orNull
    ?: System.getenv("RELEASE_STORE_FILE")
val releaseStorePassword = providers.gradleProperty("RELEASE_STORE_PASSWORD").orNull
    ?: System.getenv("RELEASE_STORE_PASSWORD")
val releaseKeyAlias = providers.gradleProperty("RELEASE_KEY_ALIAS").orNull
    ?: System.getenv("RELEASE_KEY_ALIAS")
val releaseKeyPassword = providers.gradleProperty("RELEASE_KEY_PASSWORD").orNull
    ?: System.getenv("RELEASE_KEY_PASSWORD")
val hasReleaseSigning = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
    .all { !it.isNullOrBlank() }
val playMobileCode = providers.gradleProperty("PLAY_MOBILE_VERSION_CODE").orNull
val playWearCode = providers.gradleProperty("PLAY_WEAR_VERSION_CODE").orNull
require((playMobileCode == null) == (playWearCode == null)) { "Provide both Play version codes together" }
val playMobileVersionCode = playMobileCode?.toIntOrNull()
val playWearVersionCode = playWearCode?.toIntOrNull()
if (playMobileCode != null) {
    require(playMobileVersionCode != null && playMobileVersionCode > 155 &&
        playWearVersionCode != null && playWearVersionCode > 155 && playWearVersionCode != playMobileVersionCode) {
        "Play phone and Wear version codes must be distinct integers greater than the public 4.0 test code 155"
    }
}

android {
    namespace = "com.jaysato.xiaomihealthbridge"
    compileSdk = 36

    defaultConfig {
        applicationId = "net.ailisa.healthbridge"
        minSdk = 30
        targetSdk = 36
        versionCode = playWearVersionCode ?: 175
        versionName = "4.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.create("release") {
                    storeFile = file(releaseStoreFile!!)
                    storePassword = releaseStorePassword
                    keyAlias = releaseKeyAlias
                    keyPassword = releaseKeyPassword
                }
            } else {
                null
            }
        }
    }
}

dependencies {
    implementation(project(":health-engine"))
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.wear.compose:compose-foundation:1.5.0")
    implementation("androidx.wear.compose:compose-material3:1.5.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.fragment:fragment-ktx:1.8.6")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.health:health-services-client:1.1.0-rc02")
    implementation("androidx.wear.tiles:tiles:1.4.1")
    implementation("androidx.wear.protolayout:protolayout:1.2.1")
    implementation("androidx.wear.watchface:watchface-complications-data-source-ktx:1.2.1")
    implementation("com.google.android.gms:play-services-wearable:20.0.1")
    implementation("com.google.guava:guava:33.4.0-android")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    testImplementation("junit:junit:4.13.2")
}
