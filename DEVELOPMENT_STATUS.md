# Packet Bastion: ASCII Defense — Development Status

Last updated: build of version 1.0.0.

---

## COMPLETED

### Stage 1 — Project structure
- Gradle 8.11.1 / AGP 8.7.3 / Kotlin 2.0.21 project, single `:app` module.
- Package `com.packetbastion.asciidefense`, min SDK 24, target/compile SDK 35.
- Version `1.0.0`, versionCode 1.
- Version catalog (`gradle/libs.versions.toml`); no engine, no Unity.
- Manifest declares **VIBRATE only** — deliberately **no INTERNET permission**.
- Landscape locked (`sensorLandscape`), configuration changes handled in-process.

### Stage 2 — Menus and navigation
- Splash → Main Menu → Play / Continue / Agents / Codex / Statistics /
  Settings / About / Exit.
- `CONTINUE` is disabled unless a resumable save exists.
- Hand-rolled screen graph (`ui/PacketBastionApp.kt`); back button handled on
  every screen.

### Stage 3 — Battlefield rendering
- Single native-Canvas pass (`ui/game/BattlefieldRenderer.kt`) for the whole
  battlefield; Compose owns menus, HUD and dialogs.
- Fixed 1600×760 world letterboxed by `WorldTransform` — correct on any screen.
- CORE-SERVER rack with ASCII chassis, blinking activity LEDs that react to
  traffic load, red flash / shake / alarm banner on damage.
- Lanes with flowing `>>>` markers, deployment-node brackets, animated backdrop.

### Stage 4 — Enemy movement
- Waypoint-following per lane (no general pathfinder); bends are supported by
  adding waypoints.
- Nine archetypes: PACKET, MALWARE, BOT, TROJAN, EXPLOIT, ENCRYPTED, DDoS,
  ZERO-DAY, BOSS. Elite variants of ordinary archetypes.

### Stage 5 — Tower placement
- 32 deployment nodes (4 rows × 8 columns) flanking the three lanes.
- Tap AGENTS → tap agent → valid nodes illuminate → tap node → deployed.
- Generous tap radius (2× node radius); invalid placement is refused with a
  reason (`INSUFFICIENT CRYPTO`, `NODE OCCUPIED`, `AGENT LOCKED`).

### Stage 6 — Targeting and combat
- `CombatSystem` targeting with FIRST / LAST / STRONGEST / WEAKEST; advanced
  agents expose the selector, simple agents stay on FIRST.
- `ProjectileSystem` flies shots, resolves the counter-play table
  (encryption, armour, swarm, elite, boss modifiers) and applies damage.
- Special abilities implemented: Sandbox slow, Cryptographer cipher break,
  IDS anti-fast, IPS anti-swarm, Analyst anti-elite, Hunter crits +
  armour-ignore, Sentinel multi-lock, Quantum chain, Root armour-ignore,
  Architect aura buff.

### Stage 7 — Wave generator
- Fully procedural (`engine/WaveGenerator.kt`); no hand-written wave table.
- Waves 1–2 plain packets, 3 adds swarms, 4 mixes types, 5 first boss, and the
  archetype pool widens by band from wave 6 onward.
- Swarm archetypes arrive as tight single-lane bursts.

### Stage 8 — Economy
- `EconomySystem` is the single place crypto moves.
- Kill rewards by tier with a gentle wave multiplier; wave-clear bonus;
  70% sell refund.

### Stage 9 — Upgrades
- Ten levels per agent, shared growth curve, rising cost.
- Management panel shows current → next for every stat before you commit.
- Glyph progression `[F]` → `[F+]` → `[F++]` → `[F#]` → `[F##]`.

### Stage 10 — Bosses
- Boss every fifth wave with `!!! INTRUSION ALERT !!!` warning, haptic and audio.
- Seven modifiers introduced gradually by boss cycle; multiple bosses per wave
  from cycle 4.

### Stage 11 — Saving and statistics
- DataStore persistence; run, unlocks, settings and lifetime statistics.
- Defensive decoding: corrupt data is logged and discarded, never fatal.
- Autosave on wave clear, on leaving a match, and in `onPause`.

### Stage 12 — Settings, audio, haptics
- All ten required settings implemented and persisted; reset requires
  confirmation.
- **All audio is synthesized at runtime** (`audio/ToneSynth.kt`) — the APK ships
  no sound files and carries no audio licensing surface.
- Haptics rate-limited and disableable.

### Stage 13 — Tutorial and Codex
- Four-step skippable tutorial that advances on real player actions.
- Codex with four sections; agent and threat entries are generated from game
  data so they cannot drift out of sync.

### Stage 14 — Visual polish
- Button press glow, agent selection pulse, muzzle flash, level-up burst,
  packet death sequence `[*]` → `+` → `.`, boss starburst, crypto pop,
  occasional terminal chatter, boss alert border.

### Stage 15 — Performance
- Fixed-capacity object pools; the simulation loop allocates nothing per frame.
- Renderer reuses a handful of `Paint` objects across the whole draw pass.
- Battery saver halves the tick rate and drops decorative effects; gameplay
  maths is untouched.
- Sub-stepped simulation so 3× speed cannot let a fast packet tunnel.

### Stage 16 — Build and test
- **Debug APK builds and installs.** 10.5 MB.
- **Release APK builds**, minified and shrunk, signed with a generated
  local key. Audited with `apkanalyzer`: 1.15 MB on disk (~1.0 MB download),
  13,967 methods in a single dex, minSdk 24 / targetSdk 35, and exactly two
  declared permissions — `VIBRATE` plus AndroidX's own signature-level
  `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. No `INTERNET`.
- **R8 verified not to have stripped serialization.** Every generated
  `$$serializer` for the save models is present in the release dex, along with
  an unobfuscated `MainActivity` — the failure mode the ProGuard rules exist to
  prevent.
- **88 JVM tests, all passing** in about fifteen seconds, in three layers:
  - Simulation tests that drive the real engine headlessly — balance curve
    shape, wave generation, a fully played match, and save serialization.
  - Compose UI tests under Robolectric that compose the real screens and assert
    on what a player sees and taps, plus persistence tests that run against a
    real DataStore, all at JVM speed.

---

## IN PROGRESS

Nothing. Version 1.0.0 is feature-complete against the original specification.

---

## NOT STARTED

Deliberately out of scope for 1.0, and the code is structured to accept them:

- Additional maps (4/5 lanes, branching paths). `WorldGeometry` already models
  lanes as waypoint lists specifically so this is additive.
- Campaign missions, challenge modes, achievements.
- Leaderboards or any online feature. There is no INTERNET permission by design.
- Prestige / specialisation past agent level 10.
- Pinch-to-zoom on the battlefield (the fixed world fits the screen, so it has
  not been needed).

---

## KNOWN ISSUES

1. **Mid-wave saves resume at the start of that wave.** Packets in flight are
   not persisted. Leaving during wave 12 and continuing puts you at the
   beginning of wave 12 with your agents, crypto and integrity intact. This is
   a deliberate trade: reconstructing a half-finished assault would be both
   complex and confusing to drop back into.

2. **Emulator performance is not representative.** The verification emulator in
   the build environment has no KVM acceleration, so it renders in software at
   a few frames per second. Frame-rate figures must be taken from real hardware.

3. **`Typeface.MONOSPACE` is the system monospace font.** Glyph metrics vary
   slightly between OEM font stacks, so ASCII box art can differ by a hairline
   across devices. Bundling JetBrains Mono would fix this at a cost of a few
   hundred KB; the platform font was chosen to keep the APK small.

4. **The release keystore is generated locally and is not a distribution key.**
   `keystore/` is git-ignored. Publishing to Play requires your own upload key.

5. **No landscape-left/right lock.** The activity uses `sensorLandscape`, so the
   device may flip between the two landscape orientations. This is intentional
   but means a flip mid-wave briefly re-lays-out the HUD.

---

## VERIFICATION LOG

How each item on the acceptance checklist was actually confirmed. "Simulation"
means a headless JVM test driving the real engine; "UI" means a Robolectric test
composing the real screens; "Persistence" means a test against a real DataStore;
"Device" means the debug or release APK on an emulator.

| Item | How it was verified |
| --- | --- |
| Debug APK installs and launches | Device — installed, launched, main menu rendered correctly, no app crash in logcat |
| Release APK installs | Device — installed and dex-optimised successfully |
| Release APK survives R8 | `apkanalyzer` — every generated `$$serializer` and an unobfuscated `MainActivity` present in the release dex |
| Release APK is signed | `apksigner verify` — valid signature, v1/v2 |
| Main menu works | Device screenshot + UI test asserting every action fires |
| New game starts | UI test — `matchActive`, wave 0, tutorial armed |
| Tutorial works | UI test — advances on each real action; cannot be stranded |
| Agents can be placed | Simulation + UI test — node occupied, agent present |
| Crypto decreases after deployment | Simulation + UI test — exact cost deducted |
| Enemies spawn | Simulation — packets on the field within 2s of wave start |
| Enemies follow lanes | Simulation — x increases, lane and centre-line y hold |
| Agents target enemies | Simulation — all four targeting modes pick the right packet |
| Agents fire | Simulation — projectiles observed, out-of-range agent never fires |
| Enemies take damage | Simulation — health drops; kills credited to the firing agent |
| Destroyed enemies award Crypto | Simulation — crypto rises above post-deployment balance |
| Server takes damage | Simulation — undefended wave reduces integrity |
| Server can reach zero | Simulation — undefended run ends in GAME_OVER at 0 HP |
| Game Over screen works | Simulation raises the callback; summary rendered by the overlay |
| Upgrades work | Simulation — level, damage, rate and range all rise; cost deducted |
| Upgrade cap | Simulation — stops at level 10, further upgrades refused |
| Selling works | Simulation — node freed, partial refund, never above investment |
| Boss wave on Wave 5 | Simulation — BOSS_WARNING phase, then a boss on the field |
| Boss wave on Wave 10 | Simulation — boss spawns and out-scales the wave 5 boss |
| Difficulty increases | Simulation — wave 12 out-scales wave 2; wave 30 out-scales wave 10 |
| Endless mode is sound | Simulation — a maxed board played to its death: every wave resolves, no pool overflows, economy stays bounded |
| Counters matter | Simulation — a mixed board out-lasts a mono-agent board on the same seed |
| Advanced agents unlock | Simulation — IPS/ANALYST/SANDBOX fire once each at their milestone |
| Save works | Persistence + simulation — full round-trip through DataStore and engine |
| Continue works | Persistence — CONTINUE gating, including that a dead run is never offered |
| Corrupt saves survive | Persistence + serialization — malformed, older and newer payloads |
| Settings persist | Persistence — all ten settings, with volume clamping |
| Statistics persist | Persistence — totals fold across runs; best wave only ever rises |
| Reset progress | Persistence + UI — confirmation required, then everything cleared |
| Pause works | UI test — the simulation clock freezes |
| Game speed works | UI + simulation — 2x covers ~2x the ground, sub-stepped |
| Sound setting works | Volumes persist and clamp; `AudioEngine` is muted at zero |
| Landscape UI scales | Fixed 1600x760 world letterboxed by `WorldTransform`; UI tests run at a landscape qualifier |

### Not verified on real hardware

The build environment's emulator has no KVM acceleration. It renders in
software, and under the load of a 60 FPS canvas game its own `system_server`
repeatedly ANR'd and then died outright, taking SystemUI and the telephony and
Bluetooth processes with it (`DeadSystemException`). No crash was ever logged
against `com.packetbastion.asciidefense` — the app was not even running when the
system fell over. What the emulator could be pushed to do, it did: both APKs
installed, the debug build launched and drew the main menu correctly.

These remain unverified on hardware and should be checked on a real phone:

- **The release build actually rendering.** It installs and dex-optimises, is
  correctly signed, `apkanalyzer` confirms R8 kept the serialization machinery
  the ProGuard rules exist to protect, and `am start` against it returned
  success with no crash ever recorded against the package in either the main or
  the crash log buffer. What could not be done is capture it drawing: the
  emulator's system process died again before a screenshot completed. Nothing
  observed suggests a problem, but "the release build draws its first frame" is
  an unverified claim, not a confirmed one.
- **Frame pacing and sustained frame rate.** No meaningful number can come from
  a software renderer.
- **Touch latency and gesture feel.**
- **Haptics** — no vibrator to feel.
- **Audible sound output** — the emulator ran with `-no-audio`. The synthesis
  itself is deterministic and the WAV container is built by hand, but nobody has
  heard it.

Interactive gameplay itself is covered by the test suite rather than by the
emulator: the Robolectric UI tests drive the real deploy flow, pause, speed and
settings against the real view model, and the simulation tests play entire
matches through the shipping game loop.
