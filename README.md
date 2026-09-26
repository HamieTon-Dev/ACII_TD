# CyOps TD

**Cyber Operations Tower Defense.** A lightweight, offline-first, ASCII
cyber-defence game for Android.

Cyberattacks advance along two serpentine network routes toward **CORE-SERVER**.
You deploy Cyber Agents beside the routes; they detect and stop what comes past.
Every attack you stop pays out **◇ Crypto**, which buys more agents and upgrades.
Every fifth wave is a boss. Every tenth wave banks **€ Budget**, which outlives
the run and buys permanent damage upgrades for every match after it. There is no
final wave — the only question is how far you get.

```
        >_<        CyOps TD

 ATTACK ORIGIN
      |
  A   +--->---->----+                    +---->---->----+
                    v                    ^              v
      +----<----<---+                    |              |
      v                                  |              |
      +--->---->---->---->---->---->-----+        +-----+------+
                                                  |  CORE-     |
      +--->---->---->---->---->---->-----+        |  SERVER    |
      ^                                  |        +-----+------+
      +----<----<---+                    |              ^
                    ^                    v              |
  B   +--->---->----+                    +---->---->----+

  Two routes. They run side by side across the middle, then merge
  for the final approach. A tower in either pocket covers both.
```

It is built for cybersecurity, IT and networking students, programmers, and
anyone who likes terminals — but it needs **no technical knowledge to play**.
The security terminology is personality and education, not a prerequisite.

---

## What this game does not do

- **No analytics, no telemetry, no crash reporter.** Nothing about how you play
  is measured or sent anywhere. The one thing that can leave the device is the
  save itself, and only if you ask for it (see *Keeping your progress*).
- **No account and no login.** There is no CyOps account and no password.
  Purchases belong to the Google account already signed into the Play Store on
  the device. Progress can optionally be linked to a Google account too — see
  below — but that is Google's sign-in, not one this game invented.
- **No real cryptocurrency.** No blockchain, wallet, mining, NFTs or gambling.
  `◇ Crypto` and `€ Budget` are fictional in-game resources, exactly like gold
  in any other tower defence game. Neither has value and neither can leave the
  device.
- **Nothing that costs money is required.** Every agent, wave, boss and mode is
  reachable by playing. The store sells cosmetics, convenience and € shortcuts.

## Keeping your progress

Progress is stored on the device by default and nothing is uploaded.

Two things can carry it to a new phone:

- **Android Auto Backup**, which needs nothing from you. Reinstall on a device
  signed into the same Google account and Android restores the save.
- **Cloud save**, under MAIN MENU → GOOGLE PLAY → CLOUD SAVE. Linking a Google
  account keeps waves, agents, € and firmware in that account, so two devices
  can share one set of progress. Google asks for permission to manage this
  game's saved data — that is Play Games' saved-game storage in the private
  part of your Drive that only this app can read. It is optional and the game is
  complete without it.

When two devices disagree, the merge rules are fixed and written down: lifetime
records take the better of the two, unlocks are never removed, and the wallet —
unspent € together with the firmware it bought — comes from whichever save is
newer, as one piece. Purchases deliberately do *not* travel inside a save;
Google Play carries those, which is both safer and more accurate.

## What it does ask for

Earlier releases of this README said the app requested no `INTERNET`
permission at all. That stopped being true in 1.13.0, when Google Play Billing
and AdMob were wired in, and a README that keeps the old claim is worse than no
README. The release build declares:

| Permission | Why |
| --- | --- |
| `VIBRATE` | Optional haptic feedback |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Google Play Billing and, when a build is configured for them, ads |
| `com.google.android.gms.permission.AD_ID` | Required by the ads SDK |
| `com.android.vending.BILLING` | Added by the billing library |

A checkout with no AdMob ids configured — which is how this repository ships —
selects no-op gateways, shows no ads and sells nothing. **The game is fully
playable with no network at all**, and with ads configured there are exactly two:
a rewarded ad the player chooses in order to revive, and an interstitial after
a lost run that lasted at least three minutes, never at all once REMOVE ADS is
bought.

---

## Screenshots

*(No device captures yet — see [`docs/screenshots/README.md`](docs/screenshots/README.md).
Four screens are available as rendered previews, rasterized from the real
Compose code under Robolectric: [`menu-run-modes.png`](docs/screenshots/menu-run-modes.png),
[`loadout.png`](docs/screenshots/loadout.png),
[`cloud-save-unlinked.png`](docs/screenshots/cloud-save-unlinked.png) and
[`cloud-save-linked.png`](docs/screenshots/cloud-save-linked.png).)*

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
| Package ID | `com.packetbastion.asciidefense` (unchanged — see below) |
| Minimum Android | 7.0 (API 24) |
| Target / compile SDK | 35 |
| Orientation | Landscape |
| JDK to build | 17 or newer |
| Android SDK | Build tools 35, platform 35 |
| Release APK | 1.15 MB on disk, ~1.0 MB download |
| Debug APK | 10.7 MB (unminified, with tooling) |

> **On the package ID.** The app is named CyOps TD, but its Android package is
> still `com.packetbastion.asciidefense` from the project's first release. That
> is deliberate: the package ID is the app's identity to Android, and changing
> it makes the new build a *different app*. Installing it would not upgrade the
> old one, and every player's saved run, unlocked agents, statistics and € BUDGET
> would be orphaned behind an app they would then have to uninstall by hand.
> A cosmetic rename is not worth anyone's save file. The same reasoning keeps the
> DataStore filename and the internal class names as they are.

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

Output: `app/build/outputs/apk/release/CyOpsTD-v1.0.0.apk`

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
│   ├── ChiptuneComposer.kt      Generates the 3m33s lo-fi chiptune track
│   ├── MusicEngine.kt           MediaPlayer playback and render cache
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
| Upgrade in bulk | `+1` / `+10` / `MAX` in the management panel |
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
| `[SQL]` | **SQL INJECTION** | The baseline threat, and the most common real attack there is. |
| `[M]` | **MALWARE** | Higher health, hits the server for 2. |
| `[B]` | **BOT** | Weak, fast, always arrives in groups. |
| `[T]` | **TROJAN** | Slow and armoured. Blunts small, fast hits. |
| `[X]` | **EXPLOIT** | Very fast and hits hard. Detection range matters. |
| `[E]` | **ENCRYPTED PAYLOAD** | Most agents lose over half their damage against it. |
| `[SQL2]` | **BLIND SQLi** | Armoured and patient. Heavy hits beat it; rapid fire wastes itself. |
| `«««»»»` | **DDoS** | Fastest thing in the game. Trivial alone, swarms. |
| `[0]` | **ZERO-DAY** | Rare elite. High health, heavy armour, big payout. |
| `[!!!]` | **BREACH (boss)** | Every fifth wave, alternating routes. Large, slow, devastating on arrival. |

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

## Progression

Two currencies, on two timescales.

**◇ Crypto** lives and dies with the run. Destroyed packets pay it out; clearing
a wave pays a bonus; clearing a **boss** wave pays a much larger one, starting
at ◇20 and climbing every boss cycle. You spend it on agents and on their
hundred upgrade levels.

**€ Budget** outlives the run. It is banked at every tenth wave, and the award
grows with the *square* of the milestone — wave 50 pays €125 where wave 10 pays
€5, so one deep run is worth far more than five shallow ones. You spend it in
**FIRMWARE** on CORE FIRMWARE levels, each worth +0.5% damage to every agent in
every match from then on. The level cap is nominally 10,000; the cost curve
makes that effectively indefinite.

Firmware applies before armour and before the counter table, so it helps every
agent equally. It never touches enemy health, rewards or wave composition.

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
