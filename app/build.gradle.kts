import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// --- Google Play ids, supplied from outside source control -------------------
//
// Two different kinds of AdMob id, and confusing them is the classic mistake,
// so they are named apart everywhere:
//
//     APPLICATION id   ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY   (tilde)
//     AD UNIT id       ca-app-pub-XXXXXXXXXXXXXXXX/ZZZZZZZZZZ   (slash)
//
// The application id identifies the whole app to the SDK and goes in the
// manifest. An ad unit id identifies one placement of one format. There are
// two units: the rewarded ad behind the voluntary revive, and the interstitial
// after a lost run (removed in 1.35.0, restored in 1.37.0). The interstitial
// is optional -- a release with only the rewarded id offers revives and shows
// no lost-run ad.
//
// Empty by default, on purpose: an unset id selects the no-op gateway, so a
// checkout with no AdMob account behind it builds and plays exactly as the
// offline build does. Supply them in an untracked `secrets.properties` at the
// repository root, in `~/.gradle/gradle.properties`, or on the command line —
// NOT in this repository's own `gradle.properties`, which is tracked:
//
//     cyops.admob.appId=ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY
//     cyops.admob.rewardedId=ca-app-pub-XXXXXXXXXXXXXXXX/WWWWWWWWWW
//     cyops.admob.interstitialId=ca-app-pub-XXXXXXXXXXXXXXXX/ZZZZZZZZZZ
//
// These reach RELEASE builds only. Debug builds ignore them and use Google's
// published test units instead — see the buildTypes block. ADMOB_SETUP.md has
// the walkthrough.
val admobAppId = secret("cyops.admob.appId").orEmpty()
val admobRewardedId = secret("cyops.admob.rewardedId").orEmpty()
val admobInterstitialId = secret("cyops.admob.interstitialId").orEmpty()

// Google's published test units. Safe to commit — they are documented sample
// ids that serve test creatives to anyone, which is exactly why a release
// build must never carry one.
//
// https://developers.google.com/admob/android/test-ads
val testAdmobAppId = "ca-app-pub-3940256099942544~3347511713"
val testRewardedId = "ca-app-pub-3940256099942544/5224354917"
val testInterstitialId = "ca-app-pub-3940256099942544/1033173712"

// What an unconfigured RELEASE puts in the manifest. Not a real id, not one of
// Google's test ids, and shaped so that anyone reading it in a built artifact
// can see at a glance that the build was never given one.
val unconfiguredAdmobAppId = "ca-app-pub-0000000000000000~0000000000"

fun quoted(value: String) = "\"" + value + "\""


/**
 * One configuration value that must not enter source control.
 *
 * Looked for, in order, in an untracked `secrets.properties` at the repository
 * root, then in Gradle properties (the command line, or
 * `~/.gradle/gradle.properties`), then in the environment. Returns null for
 * anything missing or blank so a caller can treat "not configured" as one case
 * rather than four. The environment variant upper-cases and underscores the
 * name — `cyops.keystore.path` becomes `CYOPS_KEYSTORE_PATH` — which is what
 * CI secrets look like.
 *
 * Note which file is *not* in that list: the repository's own
 * `gradle.properties` is **tracked**, and it holds build settings rather than
 * anything private. Putting an AdMob id or a keystore password there commits
 * it. `secrets.properties` exists so there is an obvious place that is not
 * that file, and it is git-ignored.
 */
fun Project.secret(name: String): String? {
    val local = rootProject.file("secrets.properties")
    if (local.exists()) {
        val properties = Properties()
        local.inputStream().use(properties::load)
        val fromFile = properties.getProperty(name)?.trim()
        if (!fromFile.isNullOrEmpty()) return fromFile
    }
    val fromProperty = (findProperty(name) as String?)?.trim()
    if (!fromProperty.isNullOrEmpty()) return fromProperty
    val fromEnvironment = System.getenv(name.uppercase().replace('.', '_'))?.trim()
    return if (fromEnvironment.isNullOrEmpty()) null else fromEnvironment
}

android {
    namespace = "com.cyopstd.game"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cyopstd.game"
        minSdk = 24
        targetSdk = 36
        versionCode = 47
        versionName = "1.39.1"

        // Stamped into the APK so the build identifier on screen is the real
        // one, not a string someone remembered to update. Reported by
        // BuildStamp and shown on every screen.
        buildConfigField("String", "BUILD_STAMP", "\"${'$'}{System.currentTimeMillis() / 1000}\"")

        // --- Google Play configuration --------------------------------------
        //
        // The AdMob ids are declared above this block and applied per build
        // type below, because debug and release must not use the same ones.
        //
        // Play Games Services, which is what carries a player's progress
        // between devices:
        //
        //     cyops.games.appId=1234567890
        //
        // The numeric project id from the Play Console's Play Games Services
        // setup. Empty selects the no-op cloud-save gateway, and the game then
        // keeps every save on the device exactly as it always has.
        //
        // Read through secret() like every other id, so it can live in the
        // untracked secrets.properties that secrets.properties.example points
        // at. It used to be read from Gradle properties only, which silently
        // ignored that file.
        val gamesAppId = project.secret("cyops.games.appId").orEmpty()
        buildConfigField("String", "GAMES_APP_ID", "\"$gamesAppId\"")

        // The global leaderboards, one per game mode. Each is the id Play
        // Console shows for a leaderboard ("CgkI..."). Either may be left
        // empty: that mode then keeps a local board only. They do nothing
        // without cyops.games.appId, which signs the player in.
        //
        //     cyops.games.leaderboard.standard=CgkIxxxxxxxxxxxxEAIQAQ
        //     cyops.games.leaderboard.hack_ai=CgkIxxxxxxxxxxxxEAIQAg
        val boardStandard = project.secret("cyops.games.leaderboard.standard").orEmpty()
        val boardHackAi = project.secret("cyops.games.leaderboard.hack_ai").orEmpty()
        buildConfigField("String", "LEADERBOARD_STANDARD_ID", "\"$boardStandard\"")
        buildConfigField("String", "LEADERBOARD_HACK_AI_ID", "\"$boardHackAi\"")
        // The Games SDK insists this be a string *resource*, and refuses to
        // initialise without one. "0" is a syntactically valid placeholder that
        // is never used: CloudSaveGateways.create() returns the no-op gateway
        // unless a real id is configured, and only the real gateway ever calls
        // PlayGamesSdk.initialize().
        resValue("string", "games_app_id", gamesAppId.ifEmpty { "0" })

        // Keep the APK small: the game ships no localized resources yet.
        resourceConfigurations += listOf("en")
    }

    signingConfigs {
        // Play upload key, supplied from OUTSIDE source control.
        //
        // Nothing here ever holds a path, a password or an alias literal: the
        // four values come from Gradle properties, which may live in
        // `~/.gradle/gradle.properties`, in an untracked `keystore.properties`
        // read below, or in CI secrets. An earlier version of this file
        // carried the local dev key's password as a string literal, which is
        // a password in source control however throwaway the key is.
        //
        // PLAY_STORE_RELEASE.md has the keytool command and the full setup.
        create("upload") {
            val path = secret("cyops.keystore.path")
            if (path != null) {
                storeFile = file(path)
                storePassword = secret("cyops.keystore.password")
                keyAlias = secret("cyops.key.alias")
                keyPassword = secret("cyops.key.password")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"

            // ALWAYS Google's test units, never the configured production
            // ones, and not conditionally: a debug build requesting a real ad
            // is invalid traffic against the owner's AdMob account, and the
            // way that rule gets broken is by making it a setting.
            buildConfigField("String", "ADMOB_APP_ID", quoted(testAdmobAppId))
            buildConfigField("String", "ADMOB_REWARDED_ID", quoted(testRewardedId))
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", quoted(testInterstitialId))
            buildConfigField("boolean", "USING_TEST_ADS", "true")
            manifestPlaceholders["admobAppId"] = testAdmobAppId
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // Whatever was configured, and nothing if nothing was. An empty
            // id selects the no-op gateway at runtime, so an un-configured
            // release still builds and still plays — it simply shows no ads
            // and never offers the ad revive. That is the safe failure: the
            // alternative, falling back to a test id, would serve Google's
            // test creatives to real players.
            buildConfigField("String", "ADMOB_APP_ID", quoted(admobAppId))
            buildConfigField("String", "ADMOB_REWARDED_ID", quoted(admobRewardedId))
            buildConfigField("String", "ADMOB_INTERSTITIAL_ID", quoted(admobInterstitialId))
            buildConfigField("boolean", "USING_TEST_ADS", "false")

            // The manifest needs *something* syntactically id-shaped here, but
            // it must not be Google's test application id: a release is not
            // allowed to carry a test advertising id at all, and "it is only
            // the app id, it cannot serve an ad by itself" is the kind of
            // reasoning that ends with a test creative in front of a paying
            // player.
            //
            // So an unconfigured release gets an explicit, obviously-fake
            // placeholder instead. Nothing ever initialises the SDK with it —
            // `PlayServices.adsConfigured` is false, the no-op gateway is
            // selected, and `MobileAds.initialize` is only reached once
            // consent has settled on a build that has real ids.
            manifestPlaceholders["admobAppId"] = admobAppId.ifEmpty { unconfiguredAdmobAppId }

            signingConfig = if (secret("cyops.keystore.path") != null) {
                signingConfigs.getByName("upload")
            } else {
                // Unsigned. Play App Signing can take an unsigned bundle only
                // if you have an upload key registered, so this is a local
                // build aid rather than a release path, and
                // PLAY_STORE_RELEASE.md says so.
                null
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            // Robolectric drives the real Compose UI on the JVM, which needs the
            // merged Android resources on the unit-test classpath.
            isIncludeAndroidResources = true
            isReturnDefaultValues = true

            all {
                it.systemProperty("robolectric.logging.enabled", "false")
                // Robolectric downloads its android-all runtime itself rather
                // than through Gradle's repositories, so it needs the mirror
                // pointed out to it separately (see settings.gradle.kts).
                it.systemProperty(
                    "robolectric.dependency.repo.url",
                    "https://maven-central.storage-download.googleapis.com/maven2"
                )
                it.systemProperty("robolectric.dependency.repo.id", "central-mirror")
                // Each fork re-resolves the runtime; one fork keeps that to a
                // single download and avoids racing on the shared cache.
                it.maxParallelForks = 1
            }
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "DebugProbesKt.bin"
            excludes += "kotlin-tooling-metadata.json"
        }
    }

    applicationVariants.all {
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            output.outputFileName = if (buildType.name == "release") {
                "CyOpsTD-v${versionName}.apk"
            } else {
                "app-${buildType.name}.apk"
            }
        }
    }
}

/**
 * JVM target, in the DSL Kotlin 2.3 requires.
 *
 * `kotlinOptions { jvmTarget = "17" }` inside `android { }` became a hard
 * error rather than a warning in Kotlin 2.3, which is the one migration the
 * Mobile Ads 25.x upgrade cost. Same target, current spelling.
 */
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)

    // Google Play. Both are optional at runtime: the app selects a no-op
    // gateway when the ids below are unset, so a build with no Play Console
    // behind it still runs and still plays.
    implementation(libs.billing.ktx)
    implementation(libs.play.services.ads)
    // Google's User Messaging Platform: the consent/privacy form Play requires
    // before an ad is requested in a region that needs one.
    implementation(libs.user.messaging.platform)
    implementation(libs.play.services.games)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
