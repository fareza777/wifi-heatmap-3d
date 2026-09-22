import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

fun monetizationSetting(name: String) = providers.gradleProperty(name)
    .orElse(providers.environmentVariable(name)).orElse("")

val admobAppId = monetizationSetting("ADMOB_APP_ID")
val admobBannerId = monetizationSetting("ADMOB_BANNER_ID")
val admobInterstitialId = monetizationSetting("ADMOB_INTERSTITIAL_ID")

abstract class ValidateReleaseAds : DefaultTask() {
    @get:Input abstract val identifiers: MapProperty<String, String>
    @TaskAction fun validate() {
        identifiers.get().forEach { (name, value) ->
                val separator = if (name == "ADMOB_APP_ID") '~' else '/'
                check(Regex("ca-app-pub-\\d{16}[$separator]\\d{10}").matches(value) &&
                    !value.startsWith("ca-app-pub-3940256099942544")) {
                    "Set $name to a real AdMob identifier in Gradle properties or the environment before release."
                }
            }
    }
}
val validateReleaseAds by tasks.registering(ValidateReleaseAds::class) {
    identifiers.put("ADMOB_APP_ID", admobAppId)
    identifiers.put("ADMOB_BANNER_ID", admobBannerId)
    identifiers.put("ADMOB_INTERSTITIAL_ID", admobInterstitialId)
}
tasks.configureEach {
    if (name == "preReleaseBuild") dependsOn(validateReleaseAds)
}

val signingFile = rootProject.file("release-signing.properties")
val signingValues = Properties().apply {
    if (signingFile.isFile) signingFile.inputStream().use(::load)
}
fun signingSetting(key: String, environment: String): String? =
    providers.environmentVariable(environment).orNull ?: signingValues.getProperty(key)
val releaseStoreFile = signingSetting("storeFile", "RELEASE_STORE_FILE")
val releaseStorePassword = signingSetting("storePassword", "RELEASE_STORE_PASSWORD")
val releaseKeyAlias = signingSetting("keyAlias", "RELEASE_KEY_ALIAS")
val releaseKeyPassword = signingSetting("keyPassword", "RELEASE_KEY_PASSWORD")
val releaseSigningPresent = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
    .all { !it.isNullOrBlank() }

android {
    namespace = "com.sinyal.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.f7developer.wifiheatmap3d"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "1.0.1"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        if (releaseSigningPresent) create("release") {
            storeFile = rootProject.file(releaseStoreFile!!)
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }
    buildTypes {
        debug {
            isMinifyEnabled = false
            manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
            buildConfigField("String", "ADMOB_BANNER_ID", "\"ca-app-pub-3940256099942544/6300978111\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"ca-app-pub-3940256099942544/1033173712\"")
        }
        release {
            if (releaseSigningPresent) signingConfig = signingConfigs.getByName("release")
            manifestPlaceholders["admobAppId"] = admobAppId.get()
            buildConfigField("String", "ADMOB_BANNER_ID", "\"${admobBannerId.get()}\"")
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"${admobInterstitialId.get()}\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // AdConfig switches ad units on BuildConfig.DEBUG.
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    // Settings changes locale locally, so both supported languages must be installed.
    bundle {
        language {
            enableSplit = false
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.google.arcore)
    implementation(libs.sceneview.ar)
    implementation(libs.play.services.ads)
    implementation(libs.google.ump)
    implementation(libs.android.billing)
    implementation(libs.play.review)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation("junit:junit:4.13.2")
}
