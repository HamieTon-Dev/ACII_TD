# Releasing to Google Play

Everything the app needs is built. What remains is account setup that only the
account owner can do. This is the exact list.

---

## 1. Build configuration

The AdMob ids are **build configuration, empty by default**. An unconfigured
build selects the no-op gateways and plays exactly as the offline build did —
that is deliberate, so the project builds and runs for anyone without an AdMob
account.

Put the real ids in `gradle.properties` (or pass them on the command line).
**Do not commit them:**

```properties
cyops.admob.appId=ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY
cyops.admob.interstitialId=ca-app-pub-XXXXXXXXXXXXXXXX/ZZZZZZZZZZ
```

```bash
./gradlew :app:assembleRelease \
  -Pcyops.admob.appId=ca-app-pub-...~... \
  -Pcyops.admob.interstitialId=ca-app-pub-.../...
```

`PlayServices.adsConfigured` requires both ids, both starting `ca-app-pub-`,
and **neither equal to Google's sample ids**. That last check matters: the
manifest falls back to the sample application id so the SDK can initialise at
all, and without the check a build that forgot the real ids would serve test
ads to real players — a policy violation, not a cosmetic bug.

---

## 2. Play Console

**App identity** — these cannot change after the first publish:

| | |
| --- | --- |
| Package / applicationId | `com.cyopstd.game` |
| App name | CyOps TD |

**Signing.** `keystore/cyopstd.jks` signs release builds (`tools/generate-keystore.sh`
made it). Back it up somewhere permanent — losing it means never updating this
listing again. Prefer enrolling in Play App Signing.

**In-app products.** Create each of these as a *one-time product* with exactly
these ids. A typo is a product that can never be bought, so copy them:

| Product ID | Type | Suggested price | Grants |
| --- | --- | ---: | --- |
| `no_ads` | Permanent | $4.99 | No interstitial, ever |
| `speed_5x` | Permanent | $4.99 | Fifth simulation speed |
| `budget_small` | **Consumable** | $0.99 | €150 |
| `budget_medium` | **Consumable** | $2.99 | €500 |
| `budget_large` | **Consumable** | $4.99 | €900 |
| `skin_agents_spectrum` | Permanent | $2.99 | SPECTRUM agent colours |
| `core_skin_reactor` | Permanent | $1.00 | CORE: REACTOR |
| `core_skin_meridian` | Permanent | $1.00 | CORE: MERIDIAN |
| `core_skin_glacier` | Permanent | $1.00 | CORE: GLACIER |
| `core_skin_void` | Permanent | $1.00 | CORE: VOID |
| `core_skin_mainframe` | Permanent | $1.00 | CORE: MAINFRAME |
| `core_skin_cascade` | Permanent | $1.00 | CORE: CASCADE |
| `core_skin_neongrid` | Permanent | $1.00 | CORE: NEONGRID (premium) |
| `core_skin_pack` | Permanent | **$2.50** | Six core skins + €200 |
| `bg_drift` | Permanent | $1.99 | LIVING: DRIFT |
| `bg_lattice` | Permanent | $2.99 | LIVING: LATTICE |
| `bg_aurora` | Permanent | $4.99 | LIVING: AURORA |
| `bg_rainfall` | Permanent | $1.99 | LIVING: RAINFALL |
| `bg_pulse` | Permanent | $2.99 | LIVING: PULSE |
| `bg_pack` | Permanent | $4.99 | All five backgrounds + €200 |
| `starter_pack` | Permanent | $4.99 | No-ads + SPECTRUM + DRIFT + €200 |

The three **consumables must be created as consumable** in the console. The app
consumes them so they can be bought again; a € pack created as a
non-consumable could only ever be bought once.

`core_skin_pack` deliberately **excludes** `core_skin_neongrid` — NEONGRID is
the premium skin and is sold on its own. Note also that the pack is still at
the originally specified $2.50 while now covering six skins rather than three;
that price is worth revisiting.

**Licence testing.** Add your own account under *Setup → License testing* so
purchases can be exercised end to end without being charged.

---

## 3. AdMob

1. Create the app in AdMob and link it to the Play listing.
2. Create one **Interstitial** ad unit.
3. Put the application id and the interstitial unit id into the build (§1).

The app shows at most one interstitial, only after a **lost** run, never after
quitting to the menu, and never within 180 seconds of the last one. Those rules
are in `ads/AdPolicy.kt` and are covered by tests.

**"Skip after 30 seconds" is not something the app controls.** Skip timing
belongs to the ad format and the network; the app decides *whether* an ad may
show, not how it behaves once AdMob has it on screen.

---

## 4. Store listing obligations

Adding billing, ads and a network permission changes what must be declared:

- **Privacy policy** — required. The AdMob SDK collects an advertising id.
- **Data safety form** — declare the advertising id, and that the app contains
  ads and in-app purchases.
- **Ads declaration** — yes.
- **Target audience** — if the listing ever targets children, the AdMob setup
  and the data safety answers both change. Decide before the first publish.

Nothing else leaves the device: there is no analytics SDK, no crash reporter,
no account system, and the leaderboard is local. The in-game **GOOGLE PLAY**
screen states this to the player directly, including that purchases follow
their Google account while run progress does not — worth reading before
writing the store listing, so the two say the same thing.

---

## 5. What has and has not been proven

**Verified here:** the app builds and signs; 219 tests pass; the Play SDKs
resolve, link and survive R8 (billing and ads classes are present in the
release DEX); the merged manifest carries INTERNET, `AD_ID` and the
`com.android.vending.BILLING` permission the billing library adds.

**Not verified here, and cannot be:** no purchase has been made, no ad has been
shown, and no money has moved. There is no Play Console behind this build and
no emulator with Play Services in this container. Billing and ads must be
exercised on a real device against a real Play Console before release —
start with an internal testing track and a licence-test account.

**Also still unproven from earlier:** the release APK has never been observed
drawing a frame (the container emulator has no KVM), and the audio has never
been heard. Both are recorded in `DEVELOPMENT_STATUS.md`.
