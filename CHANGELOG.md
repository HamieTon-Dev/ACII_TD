# Changelog

All notable changes to CyOps TD.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/).

---

## [1.2.0]

Identity pass. No gameplay changes.

### Changed

- **Renamed to CyOps TD** — Cyber Operations Tower Defense. The launcher name,
  splash, main menu, tutorial and About screen all follow. The in-game subtitle
  is now "ASCII CYBER DEFENSE", keeping the ASCII identity in the name.
- **New launcher icon.** Same shield silhouette, redrawn in PCB solder-mask
  green with copper traces and via pads, and the terminal face is now `>_<`
  rather than `>_`. The inbound red packet stays red — it is the one thing on
  the icon that should not read as "yours".
- Release APK is now `CyOpsTD-v<version>.apk`; the Gradle project is `CyOpsTD`.

### Not changed, on purpose

- **The package ID stays `com.packetbastion.asciidefense`.** Changing it would
  make this a different app to Android: it would install alongside the old one
  instead of upgrading it, and every existing save, unlock, statistic and €
  balance would be stranded. A cosmetic rename is not worth a player's save
  file. The DataStore filename and internal class names stay for the same
  reason.

### Fixed while redrawing the icon

- The `>` and `<` strokes were first drawn as filled outlines, which rendered
  spindly: a chevron's perpendicular thickness is far smaller than its
  horizontal offset. They are stroked polylines now.
- The red packet previously sat at the very edge of the viewport, so the
  circular launcher mask clipped it away entirely on round-icon launchers. It
  now sits against the shield edge, inside the safe zone, where it reads as a
  packet being stopped.

---

## [1.1.0]

A post-playtest revision. A real session reached wave 9 with 24 integrity and
every agent still at level 1 — which said the problem was the economy, not the
enemies. Upgrades cost more than a second agent, so the correct play was always
"buy another level-1 tower", and the board only ever got wider, never stronger.

### Changed — progression

- **Agents now upgrade from level 1 to level 100**, up from 10. Each level is
  small, frequent and cheap rather than large, rare and expensive. Damage
  reaches ×20.8 at level 100; range is the one capped stat, because unbounded
  range would make node placement stop mattering.
- **Bulk upgrading.** `+1`, `+10` and `MAX` in the management panel. `MAX`
  spends down to the last affordable level and stops. A hundred individual taps
  is not a design.
- The agent glyph now has eight tiers across the climb:
  `[F]` → `[F+]` → `[F++]` → `[F#]` → `[F##]` → `[F*]` → `[F**]` → `[F***]`.

### Changed — difficulty

- Enemy health scaling reduced (`0.08` → `0.06` linear, `0.010` → `0.008`
  power). Wave 9 is ×1.66 instead of ×1.88.
- Fewer packets per wave, a slower spawn floor, and a lower cap (46 → 42).
- Elites start at wave 7 instead of 5, and cap at 28% instead of 32%.
- Speed scaling and its ceiling both lowered; wave-number armour arrives every
  15 waves instead of 12.
- Starting crypto 90 → 120: a real opening, not a single tower.
- Ordinary wave clear bonus raised from `8 + wave×2` to `12 + wave×3`.

### Added — boss payout

- **Boss waves now pay a completion bonus**, starting at ◇20 on wave 5 and
  stepping up ◇15 per boss cycle before the usual wave multiplier. Boss waves
  are where a run either stabilises or dies, so they are now also where it gets
  the capital to rebuild.

### Added — € BUDGET and CORE FIRMWARE

- **A second currency that outlives the run.** € BUDGET is banked at every tenth
  wave, and the award grows with the *square* of the milestone — wave 50 pays
  €125 where wave 10 pays €5, so one deep run beats five shallow ones.
- **A FIRMWARE screen**, reachable from the main menu, spends € on CORE FIRMWARE
  levels. Each level is +0.5% damage to every agent in every match from then on,
  nominally to level 10,000 — a cost curve that makes the scaling indefinite in
  practice.
- Firmware applies before armour and before the counter table, so it helps a
  Cryptographer against encryption exactly as much as a Firewall against plain
  traffic. It never touches enemy health, rewards or wave composition.
- € is surfaced on the main menu status panel and on STATISTICS, and purchases
  re-read the balance inside the write transaction so two rapid taps cannot
  spend the same € twice.

### Changed — threat roster

- **`[P] PACKET` is gone.** A packet is ordinary traffic; naming the baseline
  enemy after it taught the player something untrue. A test now asserts it
  cannot come back and that `[P]` belongs solely to the IPS agent.
- **`[SQL] SQL INJECTION`** is the new baseline threat — the most common real
  attack there is.
- **`[SQL2] BLIND SQLi`** added as the tougher, armoured later-game variant,
  entering the pool around wave 11.
- Codex gains full entries for both, and the PACKET glossary entry now explains
  that packets are normal traffic.

### Fixed

- **The between-waves banner covered the top lane's deployment nodes**, so you
  could not see where to place an agent. It is now one line tall, pinned to the
  very top, dismissible by tapping, and hides itself after 3.5 seconds. The same
  information lives permanently in the control bar anyway.

### Testing

- 114 JVM tests, all passing. New `ProgressionTest` covers the hundred-level
  curve, bulk-upgrade affordability, boss payouts, budget milestones and the
  firmware multiplier end to end; `GameRepositoryTest` gains six tests for €
  persistence and firmware purchase, including that a purchase can never drive
  the balance negative.
- Three test-harness flaws surfaced and were fixed while validating the
  rebalance: a maxed 32-agent board takes damage from nothing, integrity-lost
  saturates at 100, and `nodes.take(n)` had been building boards crammed against
  lane 1 with lanes 2 and 3 undefended.

---

## [1.0.0]

First complete, playable release. Every system in the original specification is
implemented and verified.

### Added — Gameplay

- **Endless three-lane tower defence.** Hostile packets enter from the left and
  advance toward CORE-SERVER on the right. No final wave; the goal is survival.
- **Procedural wave generation.** No hand-written wave table anywhere. Waves 1–4
  are shaped by explicit early-game rules so the opening teaches; from wave 6 the
  archetype pool widens by band.
- **Nine threat archetypes** — PACKET, MALWARE, BOT, TROJAN, EXPLOIT, ENCRYPTED,
  DDoS, ZERO-DAY and BOSS — each with distinct health, speed, armour and impact.
- **Elite variants** of ordinary archetypes from wave 5 onward.
- **Boss wave every fifth wave**, with an `!!! INTRUSION ALERT !!!` warning,
  escalating health, multiple bosses in distinct lanes from cycle 4, and seven
  modifiers introduced one at a time. Wave 5's boss is deliberately unmodified.
- **Eleven cyber agents** with genuinely different roles, ten upgrade levels
  each, and visible glyph progression `[F]` → `[F+]` → `[F++]` → `[F#]` → `[F##]`.
- **Counter-play table** — encryption, armour, swarms, elites and boss modifiers
  all interact with specific agents, with an armour floor so no agent is ever
  rendered completely useless.
- **Special abilities** — Sandbox slow, Cryptographer cipher break, Hunter
  criticals, Sentinel multi-lock, Quantum chain lightning, Root armour-ignore,
  Architect aura buffs, IDS anti-fast, IPS anti-swarm, Analyst anti-elite.
- **Targeting modes** — FIRST / LAST / STRONGEST / WEAKEST, exposed on advanced
  agents.
- **Crypto economy** with tier-based kill rewards, wave-clear bonuses, rising
  upgrade costs and a 70% sell refund.
- **Permanent agent unlocks** at wave milestones, surviving a lost run.
- **Game speed controls** — 1×, 2× and 3×, with sub-stepped simulation so fast
  packets cannot tunnel past agents at high speed.
- **Preparation phase** between waves, with optional auto-start.

### Added — Presentation

- **Hybrid rendering**: a single native Canvas pass draws the entire battlefield
  while Compose owns menus, HUD and dialogs.
- **ASCII visual identity** throughout — packet glyphs, projectile trails
  (`--->`, `>>>>`, `{==>}`, `:::>`), the `[*]` → `+` → `.` death sequence, a
  terminal starburst for boss deaths, and ASCII meters everywhere.
- **CORE-SERVER rack** with ASCII chassis, activity LEDs that blink faster under
  traffic load, red flash, screen shake and an alarm banner on damage.
- **Animated backdrop** of drifting binary, toggleable.
- **Polish** — button press glow, agent selection pulse, muzzle flash, level-up
  burst, floating damage numbers, crypto pops, and occasional terminal chatter
  (`PACKET DROPPED`, `THREAT NEUTRALIZED`, `PORT SECURED`).
- **Fixed 1600×760 world** letterboxed to any screen, so the layout is correct
  across phone sizes and aspect ratios rather than tuned to one device.
- **Landscape-locked, immersive, edge-to-edge** presentation.

### Added — Content and UX

- **Main menu** with PLAY, CONTINUE, AGENTS, CODEX, STATISTICS, SETTINGS, ABOUT
  and EXIT. CONTINUE is disabled unless a resumable save exists.
- **Four-step skippable tutorial** that advances on real player actions rather
  than on reading, and never repeats once completed.
- **Codex** covering every agent, threat and boss modifier — generated from game
  data so it cannot drift — plus a plain-language networking glossary.
- **Agent roster screen** showing unlock progress toward every locked agent.
- **Statistics screen** — highest wave, packets blocked, bosses defeated, crypto
  earned, games played, server damage taken, agents deployed, upgrades bought,
  and a per-agent deployment breakdown that identifies a favourite agent.
- **Splash screen** that masks normal cold-start work without adding to it.
- **Pause menu** with resume, restart, settings and an auto-saving exit.
- **Game over screen** reporting wave reached, packets blocked, crypto earned,
  bosses defeated and best wave, with a new-record callout.

### Added — Platform

- **Offline-first.** No `INTERNET` permission is declared. `VIBRATE` is the only
  permission requested.
- **No accounts, ads, in-app purchases, subscriptions, analytics or telemetry.**
- **No real cryptocurrency** — no blockchain, wallet, mining, NFTs or gambling.
  `◇ Crypto` is a fictional in-game resource that cannot leave the device.
- **Runtime-synthesized audio.** The APK ships no audio files; every effect is
  generated as PCM at startup, eliminating the audio licensing surface entirely.
- **Rate-limited haptics** for boss alerts, server impacts, milestone upgrades
  and game over.
- **DataStore persistence** for the active run, unlocks, settings and lifetime
  statistics, with defensive decoding — corrupt data is discarded, never fatal.
- **Ten settings**: music volume, SFX volume, vibration, background animation,
  damage numbers, agent range, auto-start waves, screen shake, battery saver,
  and a confirmation-gated reset.
- **Battery saver** that halves the tick rate and drops decorative effects
  without altering any gameplay maths.
- **Original adaptive launcher icon** with a themed monochrome layer.

### Performance

- Fixed-capacity object pools for enemies, agents, projectiles and effects; the
  simulation loop allocates nothing per frame.
- Renderer reuses a handful of `Paint` objects across the whole draw pass.
- HUD recomposes only when a displayed value actually changes.
- Hard caps on entity counts bound worst-case frame cost.

### Verification

- 88 JVM tests, all passing, in three layers: simulation tests that drive the real
  engine headlessly (balance curve shape, wave generation, a fully played match,
  save-format round-tripping including corrupt and cross-version payloads),
  persistence tests against a real DataStore, and Compose UI tests under
  Robolectric that assert on what a player sees and taps.
- Debug APK builds, installs and launches cleanly (10.5 MB).
- Release APK builds minified, shrunk and signed.

### Known limitations

See `DEVELOPMENT_STATUS.md`. The notable one: a mid-wave save resumes at the
start of that wave rather than mid-assault.
