# PROGRESS — save state and handoff

**Purpose:** if a session runs out of usage, this file is what the next one
reads first. It records where the project actually stands, what is proven and
what is not, and what comes next.

_Last updated: v1.9.1 + cosmetics_ — see `CHANGELOG.md` for the full per-version history._

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

- ~~SANDBOX outclassed by TARPIT~~ — **resolved in 1.9.1: the unit was removed**
  at the owner's instruction. Save compatibility is covered by tests in
  `SaveSerializationTest`.
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
2. **Ads** — 🟨 **the policy and wiring are built and tested; the ad network
   is not connected.** `ads/AdGateway.kt` holds the interface, `NoAdGateway`
   (what ships today — never has an ad, always calls back, so the game plays
   exactly as it does now) and `AdPolicy`, which is where the rules that
   matter live: paying to remove ads removes them with no exceptions, only a
   *lost* run carries an ad (never quitting to the menu), and a 180 s cooldown
   so a player losing repeatedly on an early wave is not shown one every
   thirty seconds. 6 tests, one of which caught a `Long.MIN_VALUE` overflow
   that stopped the first ad of every session from ever showing.
   Still needed from the owner: an AdMob account and unit ids. Note that
   **"skip after 30 seconds" is not ours to set** — skip timing belongs to the
   ad format and the network.
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
3. ✅ **Menu music.** `ChiptuneComposer.Track` now has GAME and MENU, which are
   deliberately opposite: GAME is slow lo-fi that must sit under an hour of
   play without asking for attention; MENU gets a few seconds, so it is 132 BPM
   against 72, a 7.2 kHz cutoff against 2.6 kHz, almost no tape wow, and 58
   seconds long instead of 213. Measured: 25.8% of its energy above 3 kHz
   against the game track's 12.6%. Two `MusicEngine` instances rather than one
   that reloads — switching screens is frequent and re-preparing a
   multi-megabyte file each time would stutter the transition.
   `AudioEngine.setInMatch()` swaps them, pausing rather than stopping so
   returning to the menu resumes instead of restarting.
4. ✅ **Store model**: `store/Sku.kt` (full catalog), `store/Entitlements.kt`,
   `store/BillingGateway.kt` (interface + `NoBillingGateway`),
   `store/StoreRepository.kt` (interface). Persistence added to
   `save/GameRepository.kt` — `storeState`, `applyPurchase`, cosmetic choice,
   `identity`, `setUsername`, `recordDamage`. `save/SaveModels.kt` gained
   `StoreState` and `PlayerIdentity`. 9 tests in `StoreTest.kt`.
5. ⬜ Store screen UI, driven by the catalog. Add `Screen.Store` to
   `ui/CyOpsApp.kt` and a MAIN MENU entry.
6. 🟨 Skins — core-server skins chosen; **living backgrounds and the SPECTRUM
   agent skin are implemented and wired**, backgrounds also tint the lane
   corridors. Remaining: showing the menu backdrop the same treatment.
   `ui/theme/CoreSkin.kt` holds six looks as pure data (four colours plus a
   signature flourish), so a new one is a table entry rather than a branch.
   `BattlefieldRenderer.coreSkin` applies it; `drawCoreFlourish` draws the
   mark *under* the rack's text and LEDs, so a skin can never make the
   identity, integrity or load readouts harder to read. Damage and critical
   integrity still override the skin's accent, for the same reason.
   Options rendered: TERMINAL (free), OBSIDIAN, REACTOR, MERIDIAN, GLACIER,
   VOID. **Sent to the owner to choose from.**
   Still to do: agent spectrum cycle, living backgrounds.
7. ✅ **5× speed** exists and is gated: `Balance.GAME_SPEEDS` is now
   `[1,2,3,5]` with `Balance.speedCount(fifthUnlocked)`, and
   `GameViewModel.fifthSpeedUnlocked` gates cycling. Still needs wiring from
   the store state into the view model.
8. ✅ **Leaderboard + callsign registration.** `LEADERBOARD` on the main menu:
   rankings, your record, and the registration field. Behind a
   `LeaderboardGateway` interface with `LocalLeaderboard`, so swapping in Play
   Games or a backend later touches one file and no screen. Rank is by deepest
   wave with damage as the tiebreak, and that ordering lives on
   `LeaderboardEntry` so nothing can re-implement it differently. The board is
   a history of runs, not a table of players — beating your own record keeps
   both. Capped at 25. 6 tests.
9. ✅ **Play Billing and AdMob are wired** (v1.13.0). See `RELEASING.md` for
   the exact product ids to create and what is still account setup.
   - `store/PlayBillingGateway.kt` — grants on what Play reports as *owned*,
     not on the buy flow returning, so purchases made on another device,
     restores, and pending payments that settle hours later all land.
     Acknowledges permanents and consumes consumables (Play auto-refunds
     anything unacknowledged after three days). Order ids flow into
     `applyPurchase` so a re-report cannot pay out twice.
   - `ads/AdMobGateway.kt` — every path calls the continuation exactly once,
     guarded against the SDK delivering both a dismissal and a failure. The
     game can never be stuck waiting on an ad.
   - `ads/PlayServices.kt` — selects real gateways only when both AdMob ids are
     set *and* neither is Google's sample id. Empty by default, so a checkout
     with no AdMob account builds and plays.
   - Both gateways hold the Activity **weakly**; `MainActivity` re-attaches on
     resume so a recreated Activity does not leave them holding a dead
     reference.
   - APK 1.18 MB → 3.02 MB.

   ~~blocked on the owner~~ — what remains is account setup only:
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

### 6f. EXPANDED REQUEST (restated at v1.9.1)

Additions on top of §6b–6d:

- **Show images, let the owner choose.** Render option sheets for agent skins,
  living backgrounds and core-server skins and hand them over *before*
  committing to a final set. Use the rasterization technique from §3 — this is
  exactly what it is for.
- **Simulate and show every function working**, with images, so the owner can
  say which need changing.
- ✅ **`Hack:AI` hard mode** — `core/GameMode.kt`. Built as multipliers over the
  shared curves, never a second balance table, so every rebalance of the normal
  game carries into the hard one and the two cannot drift. Health ×2.35, spawn
  gap ×0.72, starting integrity 70 instead of 100, rewards ×1.5 so a harder run
  is not also a poorer one. Unlocked only by reaching wave 100. Five tests.
- ✅ **Run names** drawn centred at the top of the field, smaller and dimmer
  than the corner readouts: `NETWORK DEFENCE` in muted grey, `HACK:AI` in
  orange.
- ✅ **Persistent identity strip** — `ui/common/IdentityStrip.kt`, drawn last in
  `CyOpsApp` over whatever screen is showing, so there is no screen it can be
  missing from. Player tag left, build id right. The build id is
  `version (code) · hash`, where the hash comes from a `BUILD_STAMP`
  `buildConfigField` set at compile time — so two builds of the same version
  string are still distinguishable, which is the point of having it during
  testing. It is set at 10sp Medium rather than scaled down to nothing:
  a build id exists to be read off a photo of a bug report.
- The owner has explicitly accepted that this makes the game **no longer
  lightweight**. The foundation stays lean; the additions need not.
- **Report progress after every step and keep this log current.**

### 6g. DECISIONS — resolved

**OmniByte living background: RESOLVED — writing fresh.** `list_repos` returns
empty and `add_repo HamieTon-Dev/OmniByte` fails with *"you don't have access"*.
That is the same org-level GitHub block that stops the push to this repo, so no
asset from that project is reachable from here. Backgrounds are original work.

**Leaderboard: RECOMMENDED — Play Games Services, with a local display name.**
A *unique* username needs a server to enforce uniqueness, which means hosting,
cost and a privacy policy. Play Games gives ranking and submission for free but
shows the player's Play profile name. The middle path, which is what is built:
a **local username** (already in `GameRepository.identity`) shown in-game on the
run and on the ID strip, and Play Games for the global board. Full custom
usernames stay possible later by swapping the sync layer behind an interface.
**Still the owner's call.**

**Play SDKs: RECOMMENDED — stage them.** Add billing first, since it unlocks
everything on the list; add ads afterwards. Ads carry the most size and privacy
cost for the least gain and nothing about them can be tested here.
**Still the owner's call.**

### 6h. COSMETIC OPTIONS — logged

**CORE-SERVER skins.** Rendered for selection in two rounds.

| Skin | Flourish | Status |
| --- | --- | --- |
| TERMINAL | none | free default |
| REACTOR | amber containment rings | **chosen** |
| MERIDIAN | gold traces on indigo | **chosen** |
| GLACIER | ice needles | **chosen** |
| VOID | violet starfield | **chosen** |
| MAINFRAME | CRT scanlines + refresh sweep | **chosen** |
| CASCADE | falling code inside the rack | **chosen** |
| NEONGRID | receding grid, bright cyan | **chosen** (brightened on request) |
| OBSIDIAN | glass facets | rejected — facets near-invisible |
| PCB | copper traces + solder pads | rejected |

Rack hardware, applied to every skin (v1.9.1+):

- **Indicator LEDs are shared across every skin**, not tinted per skin
  (`Palette.ServerLedGreen` / `ServerLedAmber`). A skin changes the chassis;
  the lights on a rack are green for link and power and amber for activity
  whatever the box is painted. Tinting them to match a skin made them read as
  decoration rather than hardware. Which light is which is fixed per position,
  never rolled per frame — an LED that changes colour is not an LED.
- **A racetrack chase that loops around the integrity bar and its numbers**:
  two comets running opposite sides of the circuit with fading tails, speeding
  up with board load, modelled on an addressable LED strip. Framing the
  readout rather than sitting beside it puts the motion where the number that
  matters already is.
- **NEONGRID is the premium skin** ($1, deliberately **excluded from the
  pack** — a test asserts it). Its chase is a closed **ring** rather than a
  circuit, and its lights skim slowly between blue and green like a
  holographic foil. (`CoreSkin.Chase.RING_HOLOGRAPHIC` keeps the standalone
  circle available if the owner prefers it.) That is the one place a skin colours a light, because it is a
  signature rather than an indicator; the status LEDs above it are untouched.

Seven paid skins. **Pricing needs a decision:** `CORE_SKIN_PACK` is still at the
originally specified $2.50, but it now unlocks seven skins rather than three.

**Living backgrounds.** Implemented, *not yet shown for selection*: DRIFT,
LATTICE, AURORA, RAINFALL, PULSE. All are built to one budget — cool
desaturated colours that cannot be mistaken for a threat, alpha in the low
tens, slow motion, drawn under the lanes so gameplay always paints over them.

**Locking is tested, not assumed.** `StoreTest` walks `CoreSkin.purchasable`
and `LivingBackground.purchasable` from the real tables — not a hand-written
list — and asserts none can be selected or reach the renderer without the
product that grants it, that owning one does let it through, and that every
purchasable cosmetic has a catalog entry. That last test immediately caught two
backgrounds with no product to sell them.

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
