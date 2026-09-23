# PROGRESS — save state and handoff

**Purpose:** if a session runs out of usage, this file is what the next one
reads first. It records where the project actually stands, what is proven and
what is not, and what comes next.

_Last updated: v1.8.0 — see `CHANGELOG.md` for the full per-version history._

---

## 1. Where to start

```bash
cd /home/user/ACII_TD
./gradlew :app:testDebugUnitTest      # 169 tests, all passing
./gradlew :app:assembleRelease        # -> app/build/outputs/apk/release/CyOpsTD-v<ver>.apk
```

- Branch: `claude/packet-bastion-ascii-defense-peoygp`
- Package: `com.cyopstd.game`
- Version: set in `app/build.gradle.kts` lines 16–17 (`versionCode`, `versionName`)

**The GitHub push is blocked** and has been for every version so far:

```
remote: Claude doesn't have GitHub access to HamieTon-Dev/ACII_TD for your organization.
fatal: ... 403
```

An org admin must install the Claude GitHub App at
<https://github.com/apps/claude/installations/select_target>. Until then every
commit lives only in the container, and each version is delivered to the user
as an APK + `git archive` tarball. **Commit early and often** — the container
is ephemeral and nothing survives it except what is handed to the user.

---

## 2. What the project is

A complete, offline, landscape Android tower-defence game in Kotlin + Compose.
Hostile traffic walks a serpentine route toward the CORE-SERVER; the player
deploys cyber agents on fixed spots to stop it.

Key architecture (full detail in `ARCHITECTURE.md`):

- **Hybrid rendering.** One native `android.graphics.Canvas` pass for the
  battlefield (`ui/game/BattlefieldRenderer.kt`); Compose for menus, HUD and
  dialogs. This split is the core rendering decision.
- **Fixed 1600×760 world**, letterboxed by `WorldTransform`. No game logic
  knows about pixels.
- **Waypoint pathing.** `progress` along the route is authoritative; x/y and
  heading are derived every step. *Setting x/y directly in a test does nothing* —
  this has caused bugs three times.
- **Object pools** for every entity; the simulation allocates nothing per frame.
- **The map is derived, not drawn.** `core/WorldGeometry.kt` builds routes and
  deployment spots from named constants. Moving a waypoint moves the spots.
- **All audio is synthesized at runtime.** The APK ships no audio files.
- **All balance constants live in `core/Balance.kt`.**

---

## 3. Testing approach that has actually caught bugs

This matters more than any individual feature. Three techniques have each
caught real defects that reading the code did not:

1. **Rasterize the renderer and look at it.** Robolectric with
   `@GraphicsMode(NATIVE)` draws the real `BattlefieldRenderer` into a `Bitmap`.
   `FieldStatusRenderTest.kt` shows the pattern. This caught the enemy-label
   smearing, a 92%-opaque chip that still bled through, and label collisions.
2. **Diff two frames that differ in one thing.** That locates exactly which
   pixels an element owns, and proves it touches nothing else.
3. **Seed the engine and average.** `GameEngine(random = Random(seed))` makes
   simulations reproducible. An earlier balance test read a *single* wave and
   drew the opposite conclusion from the truth. Always average over seeds and
   waves.

**Several of my own tests have been wrong.** When a test fails, first work out
whether the claim or the code is wrong. Three invariants have been corrected
rather than loosened.

---

## 4. Shipped so far

| Version | What |
| --- | --- |
| 1.0 | Full game: 11 agents, 10 threat types, bosses, tutorial, codex, settings, save |
| 1.1 | 100 agent levels, € meta-currency, boss clear bonus, `[P]`→`[SQL]` |
| 1.2 | Renamed CyOps TD, PCB-green `>_<` icon |
| 1.3 | Package rename, serpentine 2-lane map, killable bosses, attack-first language |
| 1.4 | Re-spaced pockets so marked tower spots are actually buildable |
| 1.5 | 3m33s lo-fi chiptune (replaced a 2s drone), backdrop colour shift every 5 waves |
| 1.5.1 | Firmware readout fix (`×1.00` → `×1.005`; `+0%` → `+0.5%`) |
| 1.5.2 | Wave + crypto readouts in the battlefield corners |
| 1.6 | 48→79 deployment spots, threat chips, swarm spacing, health bar tracks |
| 1.7 | TARPIT support unit (20 ◇ slow field), spawn interval 1.0–1.5s |
| 1.8 | Roster rebalanced upward; removed the last random damage roll |

---

## 5. Known issues, open questions

- **SANDBOX (80 ◇) is outclassed by TARPIT (20 ◇).** Recorded in `BALANCE.md`.
  Needs a design decision — stronger effect, different effect, or lower price —
  not another number nudge. **Waiting on the user.**
- **Release APK has never been seen drawing a frame.** The container emulator
  has no KVM and its own `system_server` died under render load. The debug APK
  was observed rendering the main menu. No crash was ever logged against the
  game. Documented honestly in `DEVELOPMENT_STATUS.md`.
- **Audio has never been heard.** The emulator ran `-no-audio`. The music is
  verified numerically (length, peak, per-section levels, loop-seam continuity,
  FFT showing the intended chords) but nobody has listened through the app.
- **Whether the top Compose HUD strip is visible on the user's device** is
  still unconfirmed; the in-field corner readouts in 1.5.2 were added partly to
  hedge against it.

---

## 6. CURRENT REQUEST — Play Store release + monetization

Requested at v1.8.0. **Large, and partly blocked.** Status of each piece:

### 6a. Note on the original spec

The v1.0 spec required: no INTERNET permission, no accounts, no ads, no IAP.
This request reverses all four. That is the owner's call and the work proceeds
on the new instruction — but the reversal is recorded here so nobody later
mistakes it for drift, and `README.md`/`DEVELOPMENT_STATUS.md` must be updated
to stop claiming the old guarantees.

### 6b. What can be built and verified in this container

| Item | Notes |
| --- | --- |
| Splash screen with ASCII `HAMIETON-DEV` logo | Pure Compose/Canvas. Verifiable by rasterizing. |
| Menu music (classic 8-bit, distinct from the in-game lo-fi track) | Extend `ChiptuneComposer` with a second arrangement. Verifiable numerically. |
| Store UI, entitlement model, restore-purchases plumbing | Behind a billing facade (below). |
| Agent skins — slow spectrum colour cycle, per-unit colours | Renderer + palette work. Rasterizable. |
| Core-server skins (3 premium looks) | Renderer work. Rasterizable. |
| Living animated background (menu + board) | Renderer work. Rasterizable. |
| 5× speed unlock | `Balance`/HUD speed list already has 1×/2×/3×. |
| € packs as entitlements | Credits the existing `budget` in `GameRepository`. |
| Username registration + **local** leaderboard | DataStore. Verifiable. |

### 6c. What is BLOCKED and cannot be completed here

These need things only the owner can provide. Write the integration behind an
interface, ship a no-op implementation, and **do not claim they work**:

1. **Google Play Billing** — needs a Play Console app, a package name
   registered, product IDs created, and a signed upload. The library
   (`com.android.billingclient:billing`) is a Google Maven dependency; check it
   resolves through the proxy before promising anything. Nothing about billing
   can be functionally tested in this container.
2. **Ads (interstitial on run loss)** — needs an AdMob account and app/unit
   IDs. Note: **the "skip after 30 seconds" rule is not ours to set** — skip
   timing is controlled by the ad format and network. An interstitial on loss
   is implementable; the skip behaviour is AdMob's.
3. **Online leaderboard with unique usernames** — a *unique* username registry
   needs a server. Two honest options, and the user must pick:
   - **Play Games Services leaderboards** (no backend; identity and display
     name come from the player's Play Games profile, so custom usernames are
     not possible), or
   - a **custom backend** (Firebase or similar) — which is real hosting,
     ongoing cost, and a privacy policy.
   Until then: implement a **local** leaderboard + username, stored in
   DataStore, with the sync layer behind an interface.
4. **"Borrow the living background from OmniByte"** — that project is **not
   accessible from this session** (`list_repos` returns empty). Either the user
   adds the repo to the session, or the background is written from scratch.

### 6d. Order of work — STATUS

1. ✅ This file.
2. ✅ **Splash**: ASCII HAMIETON-DEV shield mark above the title
   (`ui/splash/SplashScreen.kt`).
3. ⬜ Menu music — a second `ChiptuneComposer` arrangement, classic 8-bit,
   distinct from the in-game lo-fi track. Follow the existing composer's shape:
   `ARRANGEMENT` + `writeWav`, and add a `MusicEngine` track selector.
4. ✅ **Store model**: `store/Sku.kt` (full catalog), `store/Entitlements.kt`,
   `store/BillingGateway.kt` (interface + `NoBillingGateway`),
   `store/StoreRepository.kt` (interface). Persistence added to
   `save/GameRepository.kt` — `storeState`, `applyPurchase`, cosmetic choice,
   `identity`, `setUsername`, `recordDamage`. `save/SaveModels.kt` gained
   `StoreState` and `PlayerIdentity`. 9 tests in `StoreTest.kt`.
5. ⬜ Store screen UI, driven by the catalog. Add `Screen.Store` to
   `ui/CyOpsApp.kt` and a MAIN MENU entry.
6. ⬜ Skins: agent spectrum cycle, 3 core-server skins, 3 living backgrounds.
   All renderer work in `ui/game/BattlefieldRenderer.kt` — rasterize and look.
7. ✅ **5× speed** exists and is gated: `Balance.GAME_SPEEDS` is now
   `[1,2,3,5]` with `Balance.speedCount(fifthUnlocked)`, and
   `GameViewModel.fifthSpeedUnlocked` gates cycling. Still needs wiring from
   the store state into the view model.
8. ⬜ Username + local leaderboard screen. The model and persistence exist;
   the UI does not.
9. ⬜ Real Play Billing / AdMob / Play Games wiring — **blocked on the owner.**
   All three SDKs were confirmed to resolve through the proxy:
   `com.android.billingclient:billing-ktx:7.1.1`,
   `com.google.android.gms:play-services-ads:23.6.0`,
   `com.google.android.gms:play-services-games-v2:20.1.2`.
   They are deliberately **not** in `app/build.gradle.kts` yet — adding them
   pulls in the INTERNET permission and roughly 5× the APK size, and none of it
   can be tested here.
10. ⬜ Update `README.md` / `DEVELOPMENT_STATUS.md` to stop advertising
    "no ads, no IAP, no accounts, no INTERNET permission".

**Implementation note for whoever picks this up:** `NoBillingGateway` is the
shipped gateway. The game must stay fully playable with it — that is the
property that matters, because billing fails on devices without Play Services,
without a network, and for every player who never opens the store.

### 6e. Decisions needed from the user

- Leaderboard: Play Games (no custom usernames, no backend) **or** a custom
  backend (real hosting + privacy policy)?
- OmniByte living background: add the repo to the session, or write fresh?
- Package name / applicationId for the Play Console listing — `com.cyopstd.game`
  is the current one and cannot change after first publish.
- SANDBOX rebalance (from §5).

---

## 7. Conventions to keep

- Commit messages: what changed and **why**, including defects found while
  verifying. Attribution footer as configured.
- Every behavioural claim in a doc must be one a test or a measurement backs.
  If something is unverified, say so — `DEVELOPMENT_STATUS.md` exists for this.
- Never cut a number to fix balance if raising another will do; the owner has
  said so explicitly.
- No randomness in agent damage.
