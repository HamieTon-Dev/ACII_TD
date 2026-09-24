# Releasing CyOps TD to Google Play

Everything between "the code is ready" and "it is live", in the order you do
it.

Read `ADMOB_SETUP.md` first if you have not pasted your AdMob ids in yet, and
`RELEASE_CHECKLIST.md` last, immediately before you press publish.

---

## 0. What is already true

| | |
| --- | --- |
| Application ID | `com.cyopstd.game` |
| `compileSdk` / `targetSdk` | 36 |
| `minSdk` | 24 (Android 7.0) |
| Current `versionCode` | 37 |
| Current `versionName` | 1.33.0 |
| Bundle output | `app/build/outputs/bundle/release/app-release.aab` |
| APK output | `app/build/outputs/apk/release/CyOpsTD-v1.33.0.apk` |

---

## 1. Create the app in the Play Console

1. <https://play.google.com/console> → **Create app**.
2. Name, default language, **App**, **Free** (the game has in-app purchases but
   the download is free).
3. Accept the declarations.

### The package name is permanent

`com.cyopstd.game` is fixed the moment your **first upload** is accepted. It
cannot be renamed, and a different package name is a different app with a
different listing, different reviews and different install base — there is no
migration.

Two consequences worth stating plainly:

- Decide now if you ever wanted a different name. Once a build is uploaded, it
  is decided.
- Do not upload a build with `applicationIdSuffix` applied. The debug build
  uses `com.cyopstd.game.debug` for exactly that reason, and it is not the app
  you are publishing.

---

## 2. Signing

### How Play App Signing works

You keep an **upload key**. Google keeps the **app signing key**.

1. You sign the bundle with your upload key and upload it.
2. Google verifies the upload key, strips your signature, and re-signs with the
   app signing key that is actually installed on players' devices.

This matters because **losing the upload key is recoverable and losing the app
signing key is not** — and with Play App Signing you never hold the second one.
If you lose the upload key, you contact Google and register a new one.

Turn Play App Signing on when you create the app. It is the default and there
is no good reason to opt out.

### Creating your upload key

```bash
keytool -genkeypair -v \
  -keystore ~/keys/cyopstd-upload.jks \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -alias cyopstd-upload
```

It asks for a keystore password, a name and an organisation. The name fields
are not shown to players.

`-validity 10000` is about 27 years. A key that expires before your app does is
a problem you cannot fix later.

### Where the key and its passwords live: not here

**Nothing about the key goes in this repository.** Not the `.jks`, not the
password, not the alias. `.gitignore` already excludes `*.jks`, `*.keystore`
and `keystore/`, and the build reads all four values from Gradle properties.

Pick one of three places:

**A. Your own Gradle home** — simplest for a single machine.

```properties
# ~/.gradle/gradle.properties
cyops.keystore.path=/home/you/keys/cyopstd-upload.jks
cyops.keystore.password=…
cyops.key.alias=cyopstd-upload
cyops.key.password=…
```

**B. An untracked `secrets.properties`** at the repository root — copy
`secrets.properties.example` and fill in the four keys. It is git-ignored;
confirm with `git check-ignore secrets.properties` before you write anything
into it. This is the same file the AdMob ids go in.

Not the repository's own `gradle.properties`: that one is **tracked**, and
holds build settings.

**C. Environment variables**, which is what CI uses:

```bash
export CYOPS_KEYSTORE_PATH=…   CYOPS_KEYSTORE_PASSWORD=…
export CYOPS_KEY_ALIAS=…       CYOPS_KEY_PASSWORD=…
```

If none of the four is set, the release build still succeeds and produces an
**unsigned** bundle. That is intentional — an unconfigured checkout must build
— but an unsigned bundle cannot be uploaded. The build log says which case you
are in.

### Back it up

Two copies, neither on the machine that builds. A password manager attachment
and an encrypted archive elsewhere is enough. Write down the passwords in the
same place as the key.

---

## 3. Build the bundle

```bash
./gradlew clean bundleRelease \
  -Pcyops.admob.appId=ca-app-pub-…~… \
  -Pcyops.admob.interstitialId=ca-app-pub-…/… \
  -Pcyops.admob.rewardedId=ca-app-pub-…/…

tools/verify-release.sh
```

The verifier reads the ids back out of the artifact and fails if a Google test
id is present. Run it every time; it costs a second and it is the only check
that looks at what actually ships.

An APK, for installing on your own phone without going through Play:

```bash
./gradlew assembleRelease
# app/build/outputs/apk/release/CyOpsTD-v1.33.0.apk
```

The APK is for local testing. Play takes the `.aab`.

---

## 4. versionCode and versionName

**`versionCode`** is an integer, and Play rejects any upload whose
`versionCode` it has already seen — including one from a build you later
deleted. It only goes up.

Bump it in `app/build.gradle.kts` for **every** upload, including a re-upload
of an otherwise identical build:

```kotlin
versionCode = 37      // → 38 → 39 …
versionName = "1.33.0"
```

**`versionName`** is the string players see and has no rules beyond being a
string.

> **A note on the brief.** The release spec asked to preserve `versionName`
> 1.23.0 "unless a version bump is required by the existing build history". It
> is: 1.23.0 was `versionCode` 27 and the build history has since reached 37.
> Going back would be rejected by Play, and the number players see would
> disagree with the changelog.

---

## 5. API level 36

Play requires new apps and updates to target a recent API level, and 36 is what
this build targets. Two things follow:

- **Do not lower `targetSdk` to make a warning go away.** The upload is
  rejected outright below the current floor.
- `targetSdk` is a promise about behaviour, not just a number. The app already
  handles edge-to-edge display and its own configuration changes, which is most
  of what recent levels change for a game.

`minSdk` stays at 24 (Android 7.0). That is above the Google Mobile Ads SDK's
own minimum, so the ads integration does not constrain it.

---

## 6. Store listing, before any test track

Play will not let you release anything until these are complete. None of them
are in this repository:

- App name, short description (80 chars), full description (4000).
- App icon, 512×512 PNG.
- Feature graphic, 1024×500.
- **At least 2 screenshots**, and this game is landscape — take them in
  landscape or the listing looks broken.
- Category, contact email, **privacy policy URL** (required: the app shows
  ads).
- Content rating questionnaire.
- **Data safety form** — `PRIVACY_AND_DATA_SAFETY.md` has the inventory to
  answer it from.
- Ads declaration: **yes, this app contains ads.**
- Target audience. Declaring a child audience changes the ad rules
  substantially; this game is not built for one.

---

## 7. Internal test

The fastest track: no review wait, up to 100 testers, available in minutes.

1. **Testing → Internal testing → Create new release.**
2. Upload the `.aab`.
3. Add yourself as a tester by email; testers must accept the opt-in link.
4. Release.

**This is where you verify the ads actually work**, because the release build
is the first one that uses your real ad units. Check:

- an interstitial after a lost run, at most one, on a cooldown;
- WATCH AD TO CONTINUE appearing only when an ad is loaded;
- a completed rewarded ad reviving the run at half integrity, same wave;
- closing the ad early granting nothing;
- a second revive not being offered in the same run;
- the consent form, if you are in a region that requires one.

A brand-new ad unit can take a few hours before it fills. An empty ad on day
one is usually not a bug.

---

## 8. Closed test

Play requires a **closed test with at least 12 testers running for 14
continuous days** before a personal developer account can go to production.
Organisation accounts have different rules.

Start it early. It is a calendar delay, not a work item, and it is the thing
most likely to be the reason a launch date slips.

---

## 9. Production

1. **Production → Create new release.**
2. Upload the bundle (a new `versionCode`).
3. Release notes.
4. **Staged rollout**, starting around 20%. If crash-free sessions drop, halt
   the rollout — you cannot un-release, but you can stop it spreading.
5. Review takes hours to days. First submissions take longest.

---

## 10. Every subsequent update

```bash
# bump versionCode in app/build.gradle.kts
./gradlew clean bundleRelease -Pcyops.admob.appId=… -Pcyops.admob.interstitialId=… -Pcyops.admob.rewardedId=…
tools/verify-release.sh
./gradlew testDebugUnitTest
```

Then internal test → production. Work `RELEASE_CHECKLIST.md` each time.
