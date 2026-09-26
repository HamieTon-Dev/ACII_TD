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
cyops.admob.rewardedId=ca-app-pub-XXXXXXXXXXXXXXXX/WWWWWWWWWW
```

```bash
./gradlew :app:assembleRelease \
  -Pcyops.admob.appId=ca-app-pub-...~... \
  -Pcyops.admob.interstitialId=ca-app-pub-.../... \
  -Pcyops.admob.rewardedId=ca-app-pub-.../...
```

The **rewarded** unit is a separate ad unit and a separate format, created in
AdMob as *Rewarded* rather than *Interstitial*. It is what the revive on the
game-over screen hangs off, and it is optional: a build with an interstitial id
and no rewarded id shows ads between runs and simply never offers a revive,
rather than offering one it cannot deliver. `PlayServices.rewardedConfigured`
is the check, and the button is absent — not disabled — when it is false.

Note for the store listing and for support: **REMOVE ADS does not cover the
revive ad.** REMOVE ADS buys freedom from ads the player did not ask for; the
revive is one they did, and it is opt-in. The GOOGLE PLAY account screen says
so on the ADVERTISING row, and the revive button says it too.

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
| `no_ads` | Permanent | $4.99 | No interstitial, ever, + €5,000 |
| `revive_pack` | Permanent | $4.99 | 3 revives per run, no revive ads, + €5,000 |
| `speed_5x` | Permanent | $4.99 | Fifth simulation speed |
| `budget_small` | **Consumable** | $0.99 | €1,500 |
| `budget_medium` | **Consumable** | $2.99 | €5,000 |
| `budget_large` | **Consumable** | $4.99 | €9,000 |
| `skin_agents_spectrum` | Permanent | $2.99 | SPECTRUM agent colours |
| `core_skin_reactor` | Permanent | $1.00 | CORE: REACTOR |
| `core_skin_meridian` | Permanent | $1.00 | CORE: MERIDIAN |
| `core_skin_glacier` | Permanent | $1.00 | CORE: GLACIER |
| `core_skin_void` | Permanent | $1.00 | CORE: VOID |
| `core_skin_mainframe` | Permanent | $1.00 | CORE: MAINFRAME |
| `core_skin_cascade` | Permanent | $1.00 | CORE: CASCADE |
| `core_skin_neongrid` | Permanent | $1.00 | CORE: NEONGRID (premium) |
| `core_skin_pack` | Permanent | **$2.50** | Six core skins + €2,000 |
| `bg_drift` | Permanent | $1.99 | LIVING: DRIFT |
| `bg_lattice` | Permanent | $2.99 | LIVING: LATTICE |
| `bg_aurora` | Permanent | $4.99 | LIVING: AURORA |
| `bg_rainfall` | Permanent | $1.99 | LIVING: RAINFALL |
| `bg_pulse` | Permanent | $2.99 | LIVING: PULSE |
| `bg_orbit` | Permanent | $1.99 | LIVING: ORBIT *(new in 1.37.0)* |
| `bg_heatmap` | Permanent | $1.99 | LIVING: HEATMAP *(new in 1.37.0)* |
| `bg_pack` | Permanent | $4.99 | All seven backgrounds + €2,000 |
| `starter_pack` | Permanent | $4.99 | No-ads + SPECTRUM + DRIFT + €2,000 |

The three **consumables** need no special setting: Play Console has no
consumable switch for one-time products. The app consumes them after each
purchase, which is what lets them be bought again.

**Before any product can be created:** a payments profile (merchant account)
must be set up in Play Console, and a build containing the billing library
must have been uploaded to any track. Each product must be **activated** after
it is saved; an inactive product cannot be bought. Product ids can never be
changed or reused, so copy them from this table.

**`no_ads` and `revive_pack` both say "no ads" and cover different ads.**
`no_ads` removes the interstitial between runs; `revive_pack` removes the
rewarded ad in front of a revive and raises the per-run revive count to three.
Neither implies the other — in code, in the store copy, or on the account
screen — because a player who assumes one covers the other files a refund. If
you edit the store listings, keep both descriptions explicit about it.

**The € figures above are the 1.24.0 scale**, ten times what they were. The
whole economy was multiplied by ten together — wave payouts and firmware costs
as well as packs — so a pack buys exactly what it bought before and only the
numbers are larger. `Balance.BUDGET_SCALE` is the factor and
`GameRepository.migrateBudgetScale()` carries a pre-1.24.0 save across, once.
Do not raise the pack figures alone.

`core_skin_pack` deliberately **excludes** `core_skin_neongrid` — NEONGRID is
the premium skin and is sold on its own.

**Pack pricing rule (owner, 2026-09-26).** `bg_pack` and `core_skin_pack` always
contain every item of their kind and **never change price** as items are added
($4.99 and $2.50). A new background or skin sells alone at the same price as
the other singles. `StoreTest` fails if a new item is left out of its pack or a
pack price drifts. When a pack gains items, **edit its description in Play
Console** too — the price stays, the text changes.

**Licence testing.** Add your own account under *Setup → License testing* so
purchases can be exercised end to end without being charged.

---

## 3. Play Games Services — this is what carries progress between devices

Cloud save is built on Play Games *Saved Games*, so a player's waves, agents,
€ and firmware live in their own Google account rather than on a server this
project would have to run. Setting it up is Play Console work:

1. **Play Console → Grow → Play Games Services → Setup and management →
   Configuration.** Create a new Play Games Services project and link it to
   this app (`com.cyopstd.game`).
2. **Turn on *Saved Games*** in the project's properties. Without it the
   snapshot API returns an error on every call and cloud save silently does
   nothing.
3. **Credentials.** Add an Android credential for the app, signed with the same
   key as the release build (or the Play App Signing key). This creates the
   OAuth client Google uses for the consent prompt.
4. **Testers.** Add your account under *Testers* while the project is
   unpublished, or sign-in fails with a generic error that looks like a bug in
   the game.
5. Copy the **numeric project id** from the configuration page and build with:

```bash
./gradlew :app:assembleRelease -Pcyops.games.appId=1234567890
```

An unset id selects `NoCloudSaveGateway`: the game keeps every save on the
device, the CLOUD SAVE panel says "NOT IN THIS BUILD", and nothing crashes. The
id must be numeric — `PlayServices.cloudSaveConfigured` checks that, because the
manifest has to carry a placeholder (`0`) for the SDK to parse at all, and
initialising against a placeholder throws.

**What the player is asked.** The first link shows Google's own consent prompt
for managing this game's saved data in their account. The game never sees their
password, their email, their Drive files or anything else; saved games are
stored in the app-private area of their Drive that only this app can read. The
CLOUD SAVE panel says so *before* the prompt appears, which is deliberate.

**What travels and what does not.** Progress, unlocks, the callsign and the run
history travel. **Entitlements do not** — Google Play is the source of truth for
purchases, and a save file that could grant paid content would be a way to steal
it. Device settings (volume, haptics, battery saver) do not travel either; they
belong to a device, not to a player.

**Conflicts** are resolved by `CloudSaveMerge`, whose rules are in
`save/CloudSave.kt` and covered by 17 tests: lifetime counters take the maximum,
unlocks are unioned, and the wallet (unspent € plus the firmware it was spent
on) moves as one piece from whichever save is *further along* — taking the max
of each independently would let a player mint currency by restoring an old save.
"Further along" is measured by lifetime counters rather than by a clock, because
a save is stamped when it is exported: ordering by time let a freshly installed
phone overwrite the account it was about to read from, and let a device with a
wrong clock win every merge.

**If you never set this up**, Android's own Auto Backup still restores the save
when a player reinstalls on a device signed into the same Google account
(`allowBackup` and the rules in `res/xml/`). That is the floor; Play Games is
the version that works across two devices at once.

---

## 4. AdMob

1. Create the app in AdMob and link it to the Play listing.
2. Create one **Rewarded** ad unit and one **Interstitial** ad unit.
3. Put the application id and both unit ids into the build (§1).

The interstitial plays only after the player answers NO to "revive?" (or loses with no revive on offer), only if the run lasted at least three minutes of real play, never after a revive paid for with an ad, and never to someone who owns REMOVE ADS. No cooldown; no ad at any other time. Those rules are in
`ads/AdPolicy.kt` and `GameViewModel.declineRevive`, and are covered by tests.

**"Skip after 30 seconds" is not something the app controls.** Skip timing
belongs to the ad format and the network; the app decides *whether* an ad may
show, not how it behaves once AdMob has it on screen.

---

## 5. Store listing obligations

Adding billing, ads and a network permission changes what must be declared:

- **Privacy policy** — required. The AdMob SDK collects an advertising id.
- **Data safety form** — declare the advertising id, that the app contains ads
  and in-app purchases, and — once a games project is configured — that the app
  transfers *App activity / other user-generated content* (the saved game) to
  the player's own Google account. It is optional, it is used only to restore
  progress, and it is not shared with anyone else, which is exactly what the
  form asks.
- **Ads declaration** — yes.
- **Target audience** — if the listing ever targets children, the AdMob setup
  and the data safety answers both change. Decide before the first publish.

Nothing else leaves the device: there is no analytics SDK, no crash reporter,
no account system, and the leaderboard is local. The in-game **GOOGLE PLAY**
screen states this to the player directly, including that purchases follow
their Google account while run progress does not — worth reading before
writing the store listing, so the two say the same thing.

---

## 6. What has and has not been proven

**Verified here:** the app builds and signs; 306 tests pass; the Play SDKs
resolve, link and survive R8 (billing and ads classes are present in the
release DEX); the merged manifest carries INTERNET, `AD_ID` and the
`com.android.vending.BILLING` permission the billing library adds.

**Not verified here, and cannot be:** no purchase has been made, no ad has been
shown, no account has been linked, no snapshot has been written, and no money
has moved. There is no Play Console behind this build and
no emulator with Play Services in this container. Billing, ads and cloud save must all be
exercised on a real device against a real Play Console before release — start
with an internal testing track and a licence-test account. For cloud save, the
test that matters is two devices: play on one, link both, and confirm the
second one shows the first one's progress.

**Also still unproven from earlier:** the release APK has never been observed
drawing a frame (the container emulator has no KVM), and the audio has never
been heard. Both are recorded in `DEVELOPMENT_STATUS.md`.
