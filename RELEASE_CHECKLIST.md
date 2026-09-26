# Release checklist

Work top to bottom immediately before pressing publish. Every box is something
that has to be *true*, not something to intend.

Anything marked **owner only** cannot be done from this repository — it needs
your Play Console, your AdMob account or your keystore.

---

## Build configuration

- [ ] `versionCode` in `app/build.gradle.kts` is **higher than any previously
      uploaded build**, including deleted ones. Play rejects a repeat.
- [ ] `versionName` updated and matches `CHANGELOG.md`.
- [ ] `compileSdk = 36` and `targetSdk = 36`.
- [ ] `minSdk = 24`, unchanged.
- [ ] `applicationId` is `com.cyopstd.game` with **no suffix**. The `.debug`
      suffix is a different app.
- [ ] Release build type: `isMinifyEnabled = true`, `isShrinkResources = true`,
      `isDebuggable = false`.

## Ads

- [ ] **Owner only** — AdMob **application** id (`~`) set via
      `cyops.admob.appId` in an untracked `secrets.properties`.
- [ ] **Owner only** — **rewarded** unit id (`/`) set via
      `cyops.admob.rewardedId`.
- [ ] The rewarded unit is created as a **Rewarded** unit, not an interstitial.
- [ ] **Owner only** — the AdMob app is marked **child-directed / Designed for
      Families**, and ad content rating is capped at **G**.
- [ ] **No mediation** is enabled in AdMob.
- [ ] `tools/verify-release.sh` passes against the built `.aab`: no Google test
      ids, **AD_ID absent**, cleartext refused, not debuggable. This is the only
      check that inspects what actually ships.
- [ ] **Owner only** — a consent message is published in AdMob under
      *Privacy & messaging*. Without one, UMP has nothing to show and ads will
      not serve in regions that require consent.

## Signing

- [ ] **Owner only** — upload keystore exists outside the repository.
- [ ] `git status` shows no `.jks`, `.keystore` or `secrets.properties`.
      (The tracked `gradle.properties` holds build settings only — no secret
      belongs in it.)
- [ ] `git log -p` for this release contains no password, alias or key path.
- [ ] The release build log reports a **signed** bundle, not an unsigned one.
- [ ] The keystore and its passwords are backed up in two places, neither of
      them the build machine.
- [ ] Play App Signing is enabled on the Console app.

## Tests and build

- [ ] `./gradlew testDebugUnitTest` — all green.
- [ ] `./gradlew clean bundleRelease` succeeds with the AdMob properties set.
- [ ] `./gradlew assembleRelease` produces an installable APK for local
      testing.

## On a real device — the debug build

Uses Google's test ad units, so the whole path can be exercised with no real
impressions.

- [ ] Lose a run that lasted **under three minutes**, answer **NO** → no ad.
- [ ] Lose a run that lasted **three minutes or more**, answer **NO** → one test
      interstitial, then RETRY / MAIN MENU.
- [ ] Revive with an ad, then lose again → **no** interstitial (the run's ad
      budget was already spent).
- [ ] Background the app while "WOULD YOU LIKE TO REVIVE?" is up → no ad.
- [ ] Lose a run. **WOULD YOU LIKE TO REVIVE? — YES / NO** appears, and RETRY /
      MAIN MENU are hidden until it is answered. **YES** plays the rewarded ad.
- [ ] Complete the ad → the run resumes, **same wave**, core at **50%**,
      agents still deployed, crypto unchanged.
- [ ] Lose again → **no second revive is offered**.
- [ ] Close an ad early → nothing is granted, nothing is spent, and the offer
      is still there.
- [ ] Turn airplane mode on, then lose a run → no offer, and the ordinary game
      over path works. **Nothing hangs.**
- [ ] Background the app during the ad and return → the game is not stuck.
- [ ] `adb logcat -s CyOpsAds` shows `REWARD_EARNED` immediately before every
      `REVIVE_GRANTED ... paidWithAd=true`, and never a `REVIVE_GRANTED`
      without one.

## On a real device — the release build

The first build that uses your real ad units.

- [ ] Installs and launches.
- [ ] No test ad ever appears. A test creative here means the ids did not
      reach the build.
- [ ] The consent form appears if you are in a region that requires it, and the
      game starts normally if you dismiss it.
- [ ] Settings shows **PRIVACY OPTIONS** where consent is required, and it
      reopens the form.
- [ ] The game is playable with the network off.

A brand-new ad unit can take hours to start filling. An empty ad on day one is
usually not a bug — check `AD_LOAD_FAILED ... code=3`, which is "no fill".

## Screen and device

- [ ] Launches in landscape and stays there.
- [ ] Readable on the smallest phone you have.
- [ ] Nothing important sits under a notch or the gesture bar.

## Play Console — owner only

- [ ] App created; **package name is permanent** once the first upload is
      accepted.
- [ ] Store listing: name, short and full description.
- [ ] App icon 512×512, feature graphic 1024×500.
- [ ] At least 2 screenshots, **in landscape**.
- [ ] Privacy policy URL — mandatory, the app shows ads.
- [ ] Content rating questionnaire completed.
- [ ] **Data safety form** completed from `PRIVACY_AND_DATA_SAFETY.md`.
      **Do not declare Advertising ID collection** — the permission is removed
      and Play cross-checks the declaration against the manifest.
- [ ] Ads declaration: **contains ads = yes**.
- [ ] Target audience set to **all ages, including children**, and the
      Designed for Families requirements reviewed.
- [ ] In-app products created with ids matching `store/Sku.kt` **exactly** — a
      mismatched id is a product that can never be bought.
- [ ] `revive_pack` created, if the revive pack is being sold.

## Testing tracks — owner only

- [ ] Internal test release uploaded and installed by at least one real tester.
- [ ] Ads verified on that internal build.
- [ ] Closed test running with **at least 12 testers for 14 continuous days**
      (personal developer accounts).

## Production — owner only

- [ ] Release notes written.
- [ ] **Staged rollout** starting at ~20%, not 100%.
- [ ] Someone is going to look at crash-free sessions within 24 hours.

---

## Audit result — v1.34.0 (versionCode 38)

Verified against the built artifacts, not against the build file.

| Checked | Result |
| --- | --- |
| compileSdk / targetSdk | **36** in the built APK's badging, not just in Gradle |
| minSdk | 24 |
| applicationId | `com.cyopstd.game` (release carries no suffix) |
| versionName / versionCode | 1.34.0 / 38 |
| Release debuggable | No |
| Release signing | **Unsigned** — correct (it is not debug-signed), but not uploadable |
| Signing pipeline | Proven end to end with a throwaway key, since destroyed |
| Test ad ids in release | None. Application id is the explicit `…0000~0000` placeholder |
| ABIs | arm64-v8a, armeabi-v7a, x86, x86_64 |
| Launcher icon | Adaptive + monochrome for API 26+, layer-list fallback for 24–25 |
| Tests | 632 passing |

## Known blockers carried into this release

These are true today and none of them can be closed from this repository.

- [ ] AdMob application id and rewarded unit id — not supplied.
- [ ] Upload keystore — not supplied. Release builds are currently **unsigned**.
- [ ] Play Console products, including `revive_pack` — not created.
- [ ] Two-device cloud-save verification (backlog §F3) — needs a real Play
      Console.
- [ ] Privacy policy URL — not written.
