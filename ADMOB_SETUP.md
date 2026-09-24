# AdMob setup

Exactly where the two ids go, what each one is, and how to tell that it worked.

Nothing in this repository contains a real AdMob id. Until you paste yours in,
release builds ship with no ads and no ad-revive offer — the game plays, it
simply does not monetise. That is deliberate: the alternative failure, a
release quietly serving Google's *test* creatives to real players, earns
nothing and breaks AdMob policy.

---

## 1. The two ids, and why mixing them up is the classic mistake

They look almost identical and they are not interchangeable.

| | Looks like | What it identifies | Where it goes |
| --- | --- | --- | --- |
| **Application ID** | `ca-app-pub-1234567890123456~1234567890` | the whole app, to the SDK | the Android manifest |
| **Ad unit ID** | `ca-app-pub-1234567890123456/1234567890` | one placement, one format | the code that requests that ad |

The separator is the tell: **`~` is an app, `/` is a unit.** Paste an
application id where a unit belongs and the SDK fails at runtime with a message
that does not say that is what happened.

This game uses **two different ad units**:

- an **interstitial**, shown at most once after a lost run, on a cooldown;
- a **rewarded** unit, which is the only thing that can grant a revive.

They must be separate units. A rewarded ad has a reward callback that fires
only on completion; an interstitial calls back on *dismissal*. Granting a
revive on a dismissal means granting it for closing the ad after two seconds.

---

## 2. Create them in the AdMob console

1. Sign in at <https://apps.admob.com>.
2. **Apps → Add app.** Choose Android. If the game is already on Play, say yes
   and search for `com.cyopstd.game`; if not, choose "not listed yet" and
   link it later.
3. Copy the **App ID** shown under *App settings*. It has a `~`.
4. **Ad units → Add ad unit → Interstitial.** Name it something you will
   recognise in reports, e.g. `CyOpsTD run-lost interstitial`. Copy the id.
5. **Ad units → Add ad unit → Rewarded.** Name it e.g. `CyOpsTD revive`.
   Set the reward to anything — the game ignores the reward's declared amount
   and type and only cares that the callback fired. Copy the id.

---

## 3. Where you paste them

**One file, three lines, and it is not in source control.**

Copy `secrets.properties.example` to **`secrets.properties`** in the repository
root and fill in the three AdMob lines:

```bash
cp secrets.properties.example secrets.properties
```

```properties
# secrets.properties
cyops.admob.appId=ca-app-pub-1234567890123456~1234567890
cyops.admob.interstitialId=ca-app-pub-1234567890123456/2222222222
cyops.admob.rewardedId=ca-app-pub-1234567890123456/3333333333
```

Replace all three with the ids from step 2. Keep the property names exactly as
written.

**Not** the repository's own `gradle.properties`. That file is *tracked* — it
holds build settings — so anything put there is committed. `secrets.properties`
is git-ignored and exists precisely so there is an obvious place that is not
that file. Confirm with `git check-ignore secrets.properties` before you paste
anything in.

`~/.gradle/gradle.properties` works too, if you would rather keep them out of
the checkout entirely.

They can also be passed on the command line, which is what CI does:

```bash
./gradlew bundleRelease \
  -Pcyops.admob.appId=ca-app-pub-…~… \
  -Pcyops.admob.interstitialId=ca-app-pub-…/… \
  -Pcyops.admob.rewardedId=ca-app-pub-…/…
```

### What reads them

`app/build.gradle.kts` reads the three values through its `secret()` helper —
`secrets.properties`, then Gradle properties, then the environment — and applies
them **per build type**:

- `release` gets exactly what you supplied. With nothing supplied, the manifest
  carries an explicit placeholder (`ca-app-pub-0000000000000000~0000000000`)
  rather than Google's test application id, so an unconfigured release contains
  no test ad id at all.
- `debug` **ignores them entirely** and uses Google's published test units.

That second rule is not a setting and there is no flag to change it. A debug
build requesting a production ad is invalid traffic against your own AdMob
account, and the way that rule gets broken is by making it optional.

The values reach the app as `BuildConfig.ADMOB_APP_ID`,
`ADMOB_INTERSTITIAL_ID`, `ADMOB_REWARDED_ID` and `USING_TEST_ADS`, read through
`app/src/main/java/com/cyopstd/game/ads/PlayServices.kt`. The application id
also reaches `AndroidManifest.xml` as the `${admobAppId}` manifest placeholder.

---

## 4. The test ids a debug build uses

These are Google's own, published at
<https://developers.google.com/admob/android/test-ads>, and are safe in source
control because they serve test creatives to anybody:

```
application   ca-app-pub-3940256099942544~3347511713
interstitial  ca-app-pub-3940256099942544/1033173712
rewarded      ca-app-pub-3940256099942544/5224354917
```

So `./gradlew installDebug` gives you a build where the full revive path —
preload, offer, show, reward, revive — can be exercised on a real phone with no
AdMob account and without a single real impression.

---

## 5. Checking it worked

### Before you upload

```bash
./gradlew bundleRelease -Pcyops.admob.appId=… -Pcyops.admob.interstitialId=… -Pcyops.admob.rewardedId=…
tools/verify-release.sh
```

The script reads the ids back out of the built `.aab` and fails if a test id is
in it. The build file claiming a release carries no test ids and the bundle
actually carrying none are different claims, and only the second one is
submitted to Google.

### On a device

Filter logcat on `CyOpsAds`. The revive path logs a fixed vocabulary:

```
AD_LOAD_STARTED format=rewarded unit=ca-app-pub-…
AD_LOADED       format=rewarded
AD_SHOW_STARTED format=rewarded
REWARD_EARNED   format=rewarded
AD_DISMISSED    format=rewarded earned=true
REVIVE_GRANTED  wave=14 paidWithAd=true
```

`REVIVE_GRANTED` without a preceding `REWARD_EARNED` would mean the reward rule
had been broken — that combination should be impossible, and it is what to look
for if a revive ever appears to be free.

Nothing identifying a player is logged. The lines carry a format, an ad unit id
and an SDK error code, and that is all.

---

## 6. If no ads appear

In order of likelihood:

1. **A brand-new ad unit takes a few hours to start filling.** This is normal
   and looks exactly like a bug.
2. **`AD_LOAD_FAILED ... code=3`** is "no fill". Not an error; there was simply
   no ad to serve. The game carries on and does not offer the revive.
3. **`code=1`** is an invalid request — usually a malformed id, or an
   application id pasted where a unit belongs.
4. **`code=0`** is an internal error, and `code=2` is a network error.
5. **Consent was declined, or never settled.** The SDK is not initialised until
   Google's consent flow says ads may be requested, so a player who declines
   sees no ads at all, by design. `CyOpsConsent` logs `CONSENT_SETTLED
   canRequestAds=false` in that case.
6. **The app is not yet linked in AdMob.** An app created as "not listed yet"
   serves limited or no ads until it is linked to the published listing.

---

## 7. What you still have to do in the AdMob console

Neither of these can be done from this repository:

- **App-ads.txt**, if you run one. AdMob will prompt.
- **Privacy & messaging → GDPR and US states.** The consent form the app shows
  is created *in the AdMob console*, not in this code. The app calls Google's
  User Messaging Platform, which fetches whatever message you have published.
  If you never publish one, UMP has nothing to show, and players in regions
  requiring consent will not be able to consent — so ads will not serve there.
