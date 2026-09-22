# Packet Bastion: ASCII Defense

A lightweight, offline-first, cyber-themed tower defence game for Android.

Hostile packets stream down three network lanes toward **CORE-SERVER**. You
deploy Cyber Agents beside the lanes; they detect and destroy what comes past.
Destroyed packets pay out **◇ Crypto**, which buys more agents and upgrades.
Every fifth wave is a boss. There is no final wave — the only question is how
far you get.

```
PACKET ENTRY

[!] ----- NETWORK LANE 1 ------------>  +================+
                                        | CORE-SERVER    |
[M] ----- NETWORK LANE 2 ------------>  | [::::SYSTEM:::]|
                                        | DATA CORE      |
[T] ----- NETWORK LANE 3 ------------>  | . .  . .   . . |
                                        | [==========]   |
        [F]      [I]      [A]           +================+
```

It is built for cybersecurity, IT and networking students, programmers, and
anyone who likes terminals — but it needs **no technical knowledge to play**.
The security terminology is personality and education, not a prerequisite.

---

## What this game does not do

- **No internet connection.** The app does not request the `INTERNET`
  permission. It cannot talk to a network even if it wanted to.
- No account, no login, no cloud save.
- No advertisements, in-app purchases or subscriptions.
- No analytics or telemetry.
- **No real cryptocurrency.** No blockchain, wallet, mining, NFTs or gambling.
  `◇ Crypto` is a fictional in-game resource, exactly like gold in any other
  tower defence game. It has no value and cannot leave the device.

The only permission requested is `VIBRATE`, for optional haptic feedback.

---

## Screenshots

*(Capture these from a device or an accelerated emulator and drop them in
`docs/screenshots/`.)*

| Screen | File |
| --- | --- |
| Main menu | `docs/screenshots/menu.png` |
| Battlefield mid-wave | `docs/screenshots/battlefield.png` |
| Boss warning | `docs/screenshots/boss.png` |
| Agent management panel | `docs/screenshots/upgrade.png` |
| Codex | `docs/screenshots/codex.png` |

> The verification environment used to build this project has no KVM
> acceleration, so its emulator renders in software at a few frames per second —
> unusable for representative screenshots. Capture them on real hardware with
> `adb exec-out screencap -p > shot.png`.

---

## Requirements

| | |
| --- | --- |
| Minimum Android | 7.0 (API 24) |
| Target / compile SDK | 35 |
| Orientation | Landscape |
| JDK to build | 17 or newer |
| Android SDK | Build tools 35, platform 35 |
| Installed size | ~10 MB debug, smaller release |

---

## Building

The Gradle wrapper is checked in, so no local Gradle install is needed.

Point the build at your SDK by creating `local.properties`:

```properties
sdk.dir=/path/to/Android/sdk
```

(or set the `ANDROID_HOME` environment variable).

### Debug APK

```bash
./gradlew assembleDebug
```

Windows:

```bat
gradlew.bat assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

### Release APK

A release build needs a signing key. Generate a local one:

```bash
./tools/generate-keystore.sh
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/PacketBastion-v1.0.0.apk`

If no keystore is present the release variant still builds, but unsigned —
Gradle simply skips the signing config. Replace the generated key with your own
before publishing anywhere.

### Tests

```bash
./gradlew test
```

88 JVM tests run in about fifteen seconds, in three layers:

- **Simulation tests** drive the real engine headlessly — balance curve shape,
  wave generation, a fully played match (placement, economy, targeting modes,
  upgrades, selling, boss waves on 5 and 10, difficulty scaling, unlocks, pool
  limits), save-format round-tripping, and endless-mode runs played to their
  death to confirm that a considered agent mix genuinely out-lasts stacking a
  single type.
- **Persistence tests** exercise the real DataStore path — settings, saved runs,
  CONTINUE gating, unlock accumulation, statistics folding and a full reset.
- **UI tests** run the real Compose screens under Robolectric and assert on what
  a player actually sees and taps — menu actions, the deploy flow, upgrade
  panel, settings toggles, and the reset confirmation.

All three run on the JVM, so the whole suite is fast enough to run on every
change.

### Install on a connected device

```bash
./gradlew installDebug
# or
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Project structure

```
app/src/main/java/com/packetbastion/asciidefense/
├── MainActivity.kt              Single activity, immersive landscape
├── core/
│   ├── Balance.kt               Every tuning constant in the game
│   └── WorldGeometry.kt         Lanes, waypoints, deployment nodes, server box
├── model/
│   ├── AgentType.kt             The eleven cyber agents and their stats
│   ├── EnemyType.kt             Threat archetypes, traits, boss modifiers
│   └── Entities.kt              Mutable pooled runtime entities + ObjectPool
├── engine/                      Pure Kotlin. No Android, no Compose.
│   ├── GameEngine.kt            Owns all state; orchestrates the systems
│   ├── WaveGenerator.kt         Procedural wave composition
│   ├── EnemySystem.kt           Spawning, lane traversal, boss behaviour
│   ├── CombatSystem.kt          Targeting, fire timing, aura buffs
│   ├── ProjectileSystem.kt      Flight and the damage counter-play table
│   ├── EffectSystem.kt          Short-lived ASCII flourishes
│   ├── EconomySystem.kt         The only place crypto moves
│   ├── GameEvents.kt            Sound / haptic cues and placement results
│   └── RunPhase.kt              Preparation → boss warning → in-wave → over
├── state/
│   ├── GameViewModel.kt         Engine ↔ UI ↔ persistence glue
│   └── HudSnapshot.kt           Cheap immutable HUD view
├── save/
│   ├── GameRepository.kt        DataStore persistence, defensively decoded
│   ├── SaveModels.kt            Serializable save shapes
│   └── GameSettings.kt          Settings model
├── audio/
│   ├── ToneSynth.kt             PCM/WAV synthesis — the game ships no audio
│   ├── SoundBank.kt             Synthesis recipe per effect
│   ├── AudioEngine.kt           SoundPool playback
│   └── HapticEngine.kt          Rate-limited vibration
└── ui/
    ├── PacketBastionApp.kt      Screen graph
    ├── theme/                   Palette and monospace typography
    ├── common/                  Buttons, panels, backdrop, scaffold
    ├── game/                    Canvas renderer, HUD, panels, overlays
    ├── menu/                    Main menu, agents roster, about
    ├── codex/                   Codex content and screen
    ├── stats/                   Statistics
    ├── settings/                Settings
    └── splash/                  Boot screen
```

---

## Controls

Everything is touch. There is no keyboard, mouse or hover requirement.

| Action | Control |
| --- | --- |
| Deploy an agent | Tap **AGENTS** → tap an agent → tap a highlighted node |
| Cancel a placement | Tap **CANCEL**, or tap empty space |
| Manage an agent | Tap the deployed agent |
| Upgrade / sell | Buttons in the management panel |
| Change targeting | **CHANGE** in the panel (advanced agents only) |
| Start the wave | **NEXT WAVE** |
| Pause | **PAUSE**, or the system back gesture |
| Game speed | **1X / 2X / 3X** |
| Close a panel | **CLOSE**, or tap empty battlefield |

Agent range rings appear only for a selected or about-to-be-placed agent —
never permanently, because a battlefield full of overlapping circles is
unreadable.

---

## Cyber Agents

| | Agent | Cost | Unlocks | Role |
| --- | --- | --- | --- | --- |
| `[F]` | **FIREWALL** | ◇40 | start | Balanced all-rounder. No weaknesses. |
| `[I]` | **IDS** | ◇55 | start | Very long range. +45% vs fast packets. |
| `[P]` | **IPS** | ◇70 | wave 3 | Extreme rate of fire. +35% vs swarms. |
| `[A]` | **ANALYST** | ◇95 | wave 5 | Slow, heavy hits. +80% vs elites and bosses. |
| `[S]` | **SANDBOX** | ◇80 | wave 8 | Slows packets by up to 45% while analysing. |
| `[C]` | **CRYPTOGRAPHER** | ◇105 | wave 10 | Ignores encryption; triple damage to it. |
| `[Z]` | **ZERO-DAY HUNTER** | ◇150 | wave 15 | 25% chance of a 3× crit. Ignores armour. |
| `[@]` | **AI SENTINEL** | ◇185 | wave 20 | Engages three packets per volley. |
| `[Q]` | **QUANTUM DEFENDER** | ◇240 | wave 30 | Each hit chains to two nearby packets. |
| `[#]` | **ROOT ADMIN** | ◇320 | wave 40 | Overwhelming damage. Ignores all armour. |
| `[N]` | **NETWORK ARCHITECT** | ◇210 | wave 50 | Buffs nearby agents: +30% dmg, +20% rate. |

Unlocks are permanent and survive a lost run.

Agents upgrade to level 10. The glyph changes as they climb:
`[F]` → `[F+]` → `[F++]` → `[F#]` → `[F##]`.

---

## Threat Packets

| | Threat | Behaviour |
| --- | --- | --- |
| `[P]` | **PACKET** | The baseline. Balanced, unremarkable. |
| `[M]` | **MALWARE** | Higher health, hits the server for 2. |
| `[B]` | **BOT** | Weak, fast, always arrives in groups. |
| `[T]` | **TROJAN** | Slow and armoured. Blunts small, fast hits. |
| `[X]` | **EXPLOIT** | Very fast and hits hard. Detection range matters. |
| `[E]` | **ENCRYPTED** | Most agents lose over half their damage against it. |
| `<<>>` | **DDoS** | Fastest thing in the game. Trivial alone, swarms. |
| `[0]` | **ZERO-DAY** | Rare elite. High health, heavy armour, big payout. |
| `[!!!]` | **BOSS** | Every fifth wave. Large, slow, devastating on arrival. |

Elite variants of ordinary archetypes appear from wave 5 onward: roughly double
health, extra armour, extra server damage, and a bigger payout.

### Boss modifiers

Introduced one at a time as the boss cycle climbs, so each can be learned in
isolation. Wave 5's boss deliberately has none.

`FIREWALL RESISTANCE` · `ENCRYPTION SHIELD` · `ARMOR PLATING` · `SPEED BURST` ·
`REGENERATION` · `PACKET REPLICATION` · `AGENT DISRUPTION`

The in-game **CODEX** explains every agent, threat and modifier — plus a short
plain-language glossary of the real networking and security terms the game
borrows.

---

## Documentation

- [`ARCHITECTURE.md`](ARCHITECTURE.md) — game loop, state, rendering, saving.
- [`BALANCE.md`](BALANCE.md) — every tuning number and what it does.
- [`DEVELOPMENT_STATUS.md`](DEVELOPMENT_STATUS.md) — what is done, what is not,
  and known issues.
- [`LICENSES.md`](LICENSES.md) — third-party dependency licenses.
- [`CHANGELOG.md`](CHANGELOG.md) — release history.

---

## License

The game's own source code, artwork and synthesized audio are original work.
See [`LICENSES.md`](LICENSES.md) for dependency licensing — everything the
project depends on is Apache-2.0.
