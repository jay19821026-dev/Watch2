plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.kapt")
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
val legacyMigrationBridge = providers.gradleProperty("LEGACY_MIGRATION_BRIDGE").orNull == "true"
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
        applicationId = if (legacyMigrationBridge) "com.jaysato.xiaomihealthbridge" else "net.ailisa.healthbridge"
        minSdk = 28
        targetSdk = 36
        versionCode = playMobileVersionCode ?: 181
        versionName = "4.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

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
    implementation("net.zetetic:sqlcipher-android:4.19.1@aar")
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    kapt("androidx.room:room-compiler:2.7.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    implementation(project(":health-engine"))
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.fragment:fragment-ktx:1.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.health.connect:connect-client:1.1.0")
    implementation("com.google.android.gms:play-services-wearable:20.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.health.connect:connect-testing:1.0.0-alpha04")
}
