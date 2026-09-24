# PROGRESS — save state and handoff

**Purpose:** if a session runs out of usage, this file is what the next one
reads first. It records where the project actually stands, what is proven and
what is not, and what comes next.

_Last updated: v1.28.0 — splash fix, boot sound, menu music restored. See `CHANGELOG.md` for the full per-version history._

---

## 0. What is queued

**When the owner says "continue", take the first unfinished item from
`FEATURE_BACKLOG.md` §K and work it.** That is a standing instruction, not a
one-off; it does not need re-asking each time. The owner has also asked to keep
working the whole list until usage runs out, logging position as it goes — so
this section is the position marker. Update it when an item ships.

### Where the list stands

| §K | Item | State |
| --- | --- | --- |
| 1 | A1, A2 — HUD sizing | ✅ 1.18.0 |
| 2 | D1 + all of §G — balance | ✅ 1.19.0 |
| 3 | C1 — boss identities | ✅ 1.21.0 |
| 4 | B1 — the boss dossier | ✅ 1.22.0 |
| 5 | F1 — revive on a rewarded ad | ✅ 1.23.0 |
| 6 | F2 — revive pack + ×10 € rescale | ✅ 1.24.0 |
| 7 | H1 — the tutorial overhaul | ✅ 1.25.0 |
| 8 | D2 — `[REDHAT]` / `[BLUEHAT]` | ⛔ **blocked on the owner** — the four guard rails in §D2 need a pick |
| 9 | M1 — main-menu boot sequence | ❌ withdrawn 1.28.0 — read §M1, the failure is instructive |
| 9b | M2 — boot sound over the ident | ✅ 1.28.0 |
| 10 | E1 — the "Hugging-Face" map | ⬜ **next workable item** — largest; its own version |
| 11 | E2 — AI bosses `[₩₩₩]` / `[¥¥¥]` | ⬜ needs C1 + D2 + E1 |
| 12 | F3 — two-device cloud-save check | ⛔ needs a real Play Console |

**On the music:** the owner has set the generated mode tracks aside and will
source music another way. The work stays in place and passing — HACK:AI has
its own track — and `trackForMode()` in `audio/AudioEngine.kt` is the single
place to redirect when real audio arrives. Do not spend more effort tuning
the generated tracks unless asked.

**Start-up budget.** The menu power-on is gone, so it is back to two timed
screens: the studio ident (1.9s, now carrying the boot sound) and the boot
splash (1.9s) — about 3.8 seconds. The boot splash and the ident are
arguably still one beat too many; worth raising before the store listing
goes live, but no longer urgent.

**Two things the test renderer cannot check.** Both cost a shipped bug.
Robolectric substitutes its own **fonts**, so anything whose correctness
depends on font metrics is unverifiable there — the 1.26.0 splash banner
looked perfect in a captured preview and was garbage on hardware. And a
preview that looks right is worse than no preview, because it stops you
asking. Second: **staggered opacity is not "hardware powering on"**, however
carefully the beats are timed. 1.27.0's menu boot passed every test it had
and was cut on sight.

**Pick up at E1**, skipping D2 until the owner chooses. E1 is the second map,
"Hugging-Face", the largest remaining item and worth its own version. The
thing to understand before starting: the map is *derived* from named constants
in `WorldGeometry`, and a great deal assumes there is exactly one of it. §E1
has the detail; E2 (the AI bosses `[₩₩₩]` / `[¥¥¥]`) needs E1 and D2 first.

Useful anchors from 1.25.0 if the second map moves the readouts:
`FieldStatusAnchors` is the single source for where WAVE and ◇ are drawn, and
the tutorial's arrows follow it automatically.

**A note for whoever picks this up:** `revive_pack` is a new Play Console
product id that does not exist yet, and `cyops.admob.rewardedId` is a new
AdMob unit that does not exist yet. Both are in `RELEASING.md` and both are
the owner's to create. The build degrades correctly without either — no
revive button, no revive pack purchase — so neither blocks development.

Also still open and owner-facing: §J's boss, level and background options, and
the remaining `BossVariant` rows.

**Rendering ASCII art as UI** (learned in 1.26.0, and it will come up again for
any big lettering): the theme's body styles carry `letterSpacing`, which smears
a character grid; Compose's default leading has to be turned off with
`includeFontPadding = false` and a trimmed `LineHeightStyle` before
`lineHeight` means anything; and the right leading is the glyph's *ink* height,
measured with `Paint.getTextBounds` — 0.71 of the point size for `#` in the
platform monospace face.

Requested but **not built**: `FEATURE_BACKLOG.md`. It holds the owner's
add-on list, what each item touches in this codebase, and the decisions still
open. Read it before starting new work, and move an item into `CHANGELOG.md`
when it ships rather than leaving it in both.

---

## 1. Where to start

```bash
cd /home/user/ACII_TD
./gradlew :app:testDebugUnitTest      # 400 tests, all passing
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
2b. **Fetch a composable's laid-out bounds, do not just assert its text.**
   `onNodeWithText(...).fetchSemanticsNode().boundsInRoot` and `.size`. In
   1.22.0 two dossier tests failed with a bare *"the component is not
   displayed"*, naming no line; the bounds named it in one run — the signature
   measured `78 x 0` and a stat value `6 x 0`, both laid out and both off the
   bottom of a fixed-height panel. A semantics assertion proves a composable
   exists; only its measured bounds prove it is on screen.
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
| 1.9 | SANDBOX removed, splash mark, Hack:AI behind wave 100, identity strip |
| 1.10 | Store catalog, entitlements, persistence; `NoBillingGateway` default |
| 1.11 | Menu music, core-server skins, living backgrounds, SPECTRUM agents |
| 1.12 | Local leaderboard + callsign; one interstitial after a lost run |
| 1.13 | Real Play Billing + AdMob, selected only when configured. 3.02 MB |
| 1.14 | GOOGLE PLAY account screen; living backgrounds reach the menus |
| 1.15 | Cloud save: progress follows a linked Google account across devices |
| 1.16 | LOADOUT (equip skins), RUN MODE (pick HACK:AI), locked 5× speed fixed |
| 1.17 | Rack animation glitch fixed; legibility pass; two-column agent panel |
| 1.18 | Half-height control bar, stacked corner readouts, HUD strip unblocked |
| 1.19 | Range scales ×1.25/5 levels; TARPIT aura ×1.5; IPS splash; FIREWALL jam-proof |
| 1.20 | Boss and elite death blasts: 420 derived pixel shards, shockwave, core |
| 1.21 | Boss identities: `[!!!]` BREACH, `[GG]` GOOD GAME, `[ZZ]` ZOMBIE |
| 1.22 | BOSS dossier: live stats and every modifier's description, mid-fight |
| 1.23 | Revive on a rewarded ad: same wave, half integrity, one per run |
| 1.24 | REVIVE PACK ($4.99, 3/run, no revive ads); whole € economy ×10 |
| 1.25 | Tutorial: forced placements, arrows onto the Canvas readouts, briefing |
| 1.26 | HamieTon.dev studio ident, block ASCII, fades in and out before the menu |
| 1.27 | Main menu powers on like a server; MENU INITIALIZATION toggle |
| 1.28 | Splash banner fixed on device; menu power-on removed; boot sound; menu music restored |

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
- ~~**Whether the top Compose HUD strip is visible on the user's device**~~ —
  **answered in 1.18.0, and the answer was no.** The battlefield opens every
  frame with `canvas.drawColor`, which fills the whole *clip* rather than the
  composable's box, and Compose does not clip a draw to its bounds unless asked
  — so the battlefield painted over the strip above it every frame. One
  `clipToBounds()` fixes it. The in-field readouts added in 1.5.2 to hedge
  against this were, it turns out, the only reason the wave and crypto were
  readable at all.
  - The lesson is in `MatchScreenRenderTest`: a semantics assertion proves a
    composable *exists*; only pixels prove it is *seen*. The HUD laid out with
    perfectly correct bounds for twelve releases.

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
3. **Online leaderboard with unique usernames** — still needs a server for
   *global* ranking, and that has not changed. What did change in 1.15.0: the
   local board is now part of the cloud save, so a linked player's run history
   follows them between devices, with their own callsign, without a backend.
   What remains impossible without one is comparing against *other players* and
   guaranteeing a name is unique across them. `LeaderboardGateway` is still the
   seam if that is ever wanted.
4. **"Borrow the living background from OmniByte"** — that project is **not
   accessible from this session** (`add_repo` reports no access). The five
   living backgrounds were written from scratch instead, and shipped in 1.11.0.

5. **Cloud save** — ✅ built in 1.15.0 on Play Games Saved Games. What is
   blocked is only the Play Console side: a games project, *Saved Games*
   switched on, and a numeric project id passed at build time. See §6i and
   `RELEASING.md` §3.

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
5. ✅ Store screen UI, driven by the catalog — `ui/menu/StoreScreen.kt`,
   `Screen.Store` in `ui/CyOpsApp.kt`, MAIN MENU entry. Joined in v1.14.0 by
   `ui/menu/PlayAccountScreen.kt` (`Screen.PlayAccount`, MAIN MENU → GOOGLE
   PLAY): connection state, what the Google account owns, the callsign, and
   links into Play's own order history. It states plainly that purchases
   follow the Google account while run progress does not leave the device —
   the one thing a player is likely to get wrong, and expensive to get wrong.
   `store/PlayLinks.kt` builds the deep links (market:// first, https
   fallback) and reports failure rather than pretending a dead button worked.
   8 tests in `PlayAccountTest.kt`.
6. ✅ Skins — core-server skins chosen; **living backgrounds and the SPECTRUM
   agent skin are implemented and wired**, backgrounds also tint the lane
   corridors, and as of v1.14.0 they theme **the menu backdrop** too, through
   `LocalLivingBackground` in `ui/common/AsciiBackdrop.kt` — one
   CompositionLocal set in `CyOpsApp` rather than a parameter threaded through
   a dozen screens. Verified by rasterizing the real backdrop
   (`MenuBackdropRenderTest`): free backdrop 2,051 ink pixels against AURORA's
   4,270, LATTICE green where AURORA is blue, every theme ≈0.02 luminance
   against a 0.22 ceiling.
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
10. ✅ `README.md` no longer advertises "no ads, no IAP, no INTERNET
    permission" — it now carries the real permission table and says what the
    unconfigured checkout does instead. `DEVELOPMENT_STATUS.md` is a log of
    the 1.0 build, so its claim is marked superseded rather than rewritten.
    The **main menu** carried the same stale line (`NO ADS · NO PURCHASES`)
    and now reads `PLAYS OFFLINE · NO LOGIN REQUIRED`, plus `· AD-FREE` only
    for a player who bought it.

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

### 6i. CLOUD SAVE — how it works (v1.15.0)

**Mechanism:** Play Games Services *Saved Games* (`play-services-games-v2`).
The save lives in the player's own Google account, in the app-private Drive area
only this app can read — which is why linking shows Google's prompt about
managing this game's saved data. No server, no account system, no privacy policy
to write beyond declaring it, nothing to keep alive.

**Files:**
- `save/CloudSave.kt` — the payload *and* `CloudSaveMerge`, which holds every
  rule for what a player keeps when two devices disagree. Pure, and therefore
  the only part that can be tested here.
- `save/CloudSaveGateway.kt` — interface + `NoCloudSaveGateway`.
- `save/PlayGamesCloudSave.kt` — the real one. Moves bytes; decides nothing.
- `save/CloudSaveSync.kt` — pull, merge, apply, push, in that order.
- `GameRepository.exportCloudSave` / `importCloudSave` — one atomic write, so a
  restore cannot land half-applied.

**Merge rules** (all tested): lifetime counters take the max; unlocks are
unioned; the wallet (unspent € + the firmware it bought) moves as one piece from
whichever save is *further along*; the in-progress run comes from that same
save; a claimed callsign is never replaced by an empty one.

"Further along" is `CloudSave.progressValue`, the sum of the lifetime counters,
**not** the timestamp. This was a real bug, caught by testing the fresh-install
case: a save is stamped when it is exported, so a newly installed phone always
looked newer than the account it was about to read from, and linking it would
have replaced the account's € and its in-progress run with nothing. Clocks also
disagree between devices. The same value is given to Play as the snapshot's
progress value so Google's own conflict resolution agrees. The merge is order-independent and
idempotent, which is what stops two phones ping-ponging.

**Deliberately excluded:** entitlements (Play owns purchases; a save file that
could grant paid content would be a way to steal it) and settings (they belong
to a device).

**Configuration:** `-Pcyops.games.appId=<numeric project id>`. Unset selects the
no-op gateway. See `RELEASING.md` §3 for the Play Console steps, including that
*Saved Games* must be switched on in the games project or every snapshot call
fails.

**Unproven, and cannot be proven here:** no account has been linked and no
snapshot has ever been written. The container has no Play Services. The test
that matters on a real device is two devices, not one.

---

### 6j. THE LESSON FROM v1.16.0 — check every feature has a control

Three features were built, persisted, read by the renderer or the engine, and
**had no way for a player to reach them**:

- six core skins, five living backgrounds and the SPECTRUM palette could be
  bought but not equipped (no LOADOUT screen existed);
- `GameMode.HACK_AI` could not be selected, so every run since 1.9 was
  STANDARD;
- the paid fifth speed was drawn as a live button that silently selected 3×.

All three passed their own unit tests, because the tests exercised the layer
and not the path. The check that finds this class of bug is one question, asked
per feature: **where does a player press this?** It is now a step in §7.

---

### 6e. Decisions needed from the user

- ~~Leaderboard: Play Games or a custom backend?~~ **Resolved in 1.15.0 by
  accident, and worth stating.** The board stays local *and* now travels: it is
  part of the cloud save payload, so a linked player's run history follows them
  between devices without a backend and without giving up the custom callsigns
  the owner asked for. Play Games' own leaderboards would have forced gamer tags
  and would not work offline. `LeaderboardGateway` still exists, so a real
  cross-player board remains a one-file change if it is ever wanted — that is
  the only thing the local board cannot do.
- `core_skin_pack` is still priced at the originally specified $2.50 while now
  covering six skins rather than three. Worth revisiting before publish.
- OmniByte living background: the repo is unreachable from this session
  (`list_repos` returns it as inaccessible), so the five backgrounds were
  written fresh.
- Package name / applicationId for the Play Console listing — `com.cyopstd.game`
  is the current one and cannot change after first publish.

---

## 7. Conventions to keep

- Commit messages: what changed and **why**, including defects found while
  verifying. Attribution footer as configured.
- Every behavioural claim in a doc must be one a test or a measurement backs.
  If something is unverified, say so — `DEVELOPMENT_STATUS.md` exists for this.
- Never cut a number to fix balance if raising another will do; the owner has
  said so explicitly.
- For every feature: name the control a player presses to reach it. A feature
  whose state is stored and read but never set is not finished, however many
  tests its own layer has (see §6j).
- A control that cannot act must say so. Never let a button quietly do
  something *else* — that is worse than a dead button, because it also
  misreports the state it appears to have set.
- **Never derive an animation's position from `time × rate` when the rate can
  change.** Integrate instead (`phase += delta × rate`). A rate that changes
  mid-run moves the position by `elapsed × Δrate`, which after a minute of
  play is tens of cycles — this shipped in the core rack for six releases and
  read as a glitch every time a threat died. `RackAnimation` holds the fix and
  the tests.
- No randomness in agent damage.
