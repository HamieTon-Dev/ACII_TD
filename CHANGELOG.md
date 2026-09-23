# Changelog

All notable changes to CyOps TD.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/).

---

## [1.8.0]

### Fixed — paying more now buys more

Audited the whole roster against price. The finding was worse than "the
expensive units feel similar": **the curve was inverted.**

| | old dps/crypto | new |
| --- | ---: | ---: |
| FIREWALL (40 ◇) | 0.259 | 0.259 |
| CRYPTOGRAPHER (105 ◇) | 0.140 | 0.260 |
| ZERO-DAY HUNTER (150 ◇) | 0.199 | 0.322 |
| QUANTUM DEFENDER (240 ◇) | 0.149 | 0.262 |
| **ROOT ADMIN (320 ◇)** | **0.232** | **0.362** |

The 320-crypto ROOT ADMIN returned *less damage per crypto than the 40-crypto
starter*. The CRYPTOGRAPHER cost more than the ANALYST and hit for less than
half as much. Saving up was a worse plan than buying starters — the opposite of
what a tower defence should reward.

Reach had the same problem: 168 at the bottom to 255 at the top, +52% for eight
times the price, and the longest range in the game belonged to a 55-crypto unit.

**Nothing was cut.** Every change is a raise:

| Agent | Damage | Range |
| --- | --- | --- |
| TARPIT | 0.6 → **1.2** (restored) | 200 |
| IDS | 7 → **9** | 268 → **290** |
| IPS | 4.4 → **5.4** | 158 → **170** |
| SANDBOX | 3 → **5** | 186 → **196** |
| ANALYST | 30 → **38** | 200 → **212** |
| CRYPTOGRAPHER | 14 → **26** | 205 → **220** |
| ZERO-DAY HUNTER | 26 → **42** | 225 → **242** |
| AI SENTINEL | 15 → **22** | 235 → **256** |
| NETWORK ARCHITECT | 8 → **16** | 230 → **264** |
| QUANTUM DEFENDER | 34 → **60** | 245 → **278** |
| ROOT ADMIN | 78 → **122** | 255 → **285** |

Three tests now hold the shape: damage-per-crypto never falls below the
starter's, output rises with price across every damage dealer, and reach rises
with price (the IDS is the declared exception — a cheap unit whose whole
identity is seeing furthest).

### Changed — no agent's damage is a dice roll

The ZERO-DAY HUNTER rolled a **25% chance of a 3× critical**. It was the only
randomness anywhere in agent damage, and it meant the same tower against the
same threat could deal wildly different damage.

It is gone. The hunter's damage is flat and higher (26 → 42), which is worth
*more* than the old average (48.3 dps against 44.9), and every shot is now
identical. The visual emphasis is kept — its shots were the only ones drawn
heavy, and now they always are, because they always deserve it. The projectile
flag was renamed `critical` → `heavy` to stop the code claiming something that
is no longer true.

A test runs the same scenario under five different generators and asserts the
hunter deals exactly one distinct damage value, identical across all of them.

### Changed — TARPIT slows every threat in range

Confirmed as an aura, as intended: everything inside the radius, not just what
it shoots. Its damage is restored to 1.2, and the cheap-unit cheese did **not**
return — four tarpits still leak more than two FIREWALLs at the same price,
because the damage raise applies to the FIREWALL's tier too.

Node eligibility follows the longest range in the roster, so raising reach
opened 79 → 81 spots. A test ties the two together: no spot may exist that is
further from a route than the furthest-seeing agent can reach.

### Balance check

Scripted runs (a deliberately unsophisticated buyer: best affordable agent on
the best free spot, then upgrades):

| | waves reached |
| --- | --- |
| Starter agents only | 22–25 |
| Full roster | 22–28 |
| Full roster, ×1.5 firmware | 29–32 |
| Full roster, ×3 firmware | 37–50 |

**One issue remains and is deliberately not fixed here** — see `BALANCE.md`.
The SANDBOX (80 ◇) is outclassed by the TARPIT (20 ◇): it slows deeper in one
spot, but the tarpit's aura covers everything in range continuously for a
quarter of the price. It is the weakest buy on the board and wants a rethink
rather than a number tweak.

---

## [1.7.0]

### Added — TARPIT, a 20 ◇ support unit

`[~]` **TARPIT** — 20 crypto, 200 range, available from wave 1. It is the
cheapest thing on the board and deals almost no damage. Everything inside its
radius simply moves at **0.72× speed**, deepening to a 0.55× floor as it levels.

It is an **area field**, not an on-hit slow, and that decision was made by
measurement rather than taste. Applied on hit, a tarpit firing 1.6 shots a
second with a 1.4 second slow holds about two threats at a time; against a wave
of twenty that measured as **0.6 hp of integrity saved for 20 crypto** — worse
value than simply upgrading a tower you already own, i.e. a unit nobody would
ever buy. As a field it does what its name means.

Balanced against two failure modes, both measured over 12 seeded runs each
(the engine's generator is injectable, so every figure is reproducible):

- **It must not be spammable.** Slows do not stack — only the strongest
  applies — so a wall of tarpits buys area, never a deeper slow, and the unit
  has the damage output of a rounding error. Four tarpits (80 ◇) leak more
  integrity than two FIREWALLs at the same price.
- **It must be worth buying.** Placed beside damage, 20 ◇ of tarpit saves
  ~2 hp of integrity where 17 ◇ of upgrade saves ~1.2.

It also must not delete the **SANDBOX**, which costs four times as much and is
the slow specialist: a test asserts the sandbox slows strictly deeper at all
100 levels.

The field is drawn as a faint permanent ring. Range rings are otherwise shown
only for a selected agent, because a board of overlapping circles is
unreadable — but for this unit the field *is* the unit, and an area effect
whose edge you cannot see is guesswork.

An earlier pass had the tarpit at 1.2 base damage. Four of them then cleared
wave 12 *better* than two FIREWALLs for the same money, making the cheapest
unit also the most efficient one. Damage is now 0.6.

### Changed — threats arrive far enough apart to be read

Spawn interval moved from **1.15 s decaying to a 0.38 s floor** to **1.50 s
decaying to a 1.00 s floor**. At the old floor a late wave put a threat on the
board every third of a second — faster than one clears its own chip width — so
deep waves read as one continuous smear of traffic rather than a stream of
separate attacks you could pick off and respond to.

The trade is honest and worth stating: **late waves take about twice as long to
spawn** (wave 90 goes from 16 s of spawning to 42 s), and because threats arrive
spread out rather than bunched, **the game is easier**. Late-game pressure now
comes from health scaling rather than from spawn rate, since wave size is capped
at 42 regardless. The 2× and 3× speed controls absorb the extra wall-clock time.

---

## [1.6.0]

### Added — deployment spots where the map was empty

A playtest screenshot marked seventeen places that looked like they should be
buildable. Checking each against the geometry, seven already were; the other
ten were being dropped, and for two distinct reasons — both of which turned
out to be real gaps in the derivation rather than deliberate design.

- **No candidate row existed across the middle of the map.** The A3/B1
  convergence is deliberately too tight to build inside, and that had been
  generalised into "no row anywhere in that band of y". But A3 and B1 are only
  a route apart across the *middle* of the board; further right both routes
  have turned inward towards the core and the same band is a wide-open
  corridor 130 units clear of either one. A row at `CORE_Y` now runs through
  it. The spots it opens cover **both routes at once** and are among the
  strongest on the board.
- **Eligibility was judged at the shortest agent's range.** Down the right
  side of the map both routes have turned in, so everything in the outer band
  is 170–200 units from anything and every spot out there was silently
  dropped — even though an ANALYST posted at one covers 400 units of route. A
  spot is now offered if *some* agent can work from it.
- Two inner margin rows were added, closer in than the outer ones, which exist
  only where the routes have moved away; the clearance filter rejects them
  down the left of the map, which is exactly the wanted behaviour.

Deployment spots went from 48 to 79, and **every one of the seventeen marked
places now has a spot within 36 units of it**, most within 15.

Because spots now exist that a short-ranged agent cannot use, the deploy
overlay had to earn them: while placing, a spot the agent in hand cannot reach
is dimmed and marked `-` instead of `+`. Nobody pays for a tower that would
shoot at nothing.

Two map invariants were tightened rather than loosened to accommodate this.
"No node is useless" became "no node is useless *to every agent*", checked at
the longest range any agent has, and is now backed by a second test that the
out-of-reach ones are knowable before they are paid for. The convergence test
gained the x bound it always needed — checking y alone would have forbidden
the best ground on the map.

### Changed — the battlefield reads like a game now

- **Threats are drawn as chips**: an opaque plate with a coloured border and
  the ASCII tag inside, drawn back to front by progress along the route. This
  is the headline fix. A tag is up to sixty units wide and a fast archetype
  constantly catches a slow one, so bare text over bare text composited into
  unreadable mush — four bots genuinely rendering as `[BBBIB]`. The nearer
  threat now simply covers the one behind, which reads as depth.
- **Swarm bursts no longer stack.** Members were spawned 0.16 s apart, and at
  89 units/second a BOT covers 14 units in that time — less than a quarter of
  its own chip. The gap is now expressed as a *distance* (`SWARM_BURST_SPACING`)
  and divided by the archetype's speed, so it holds for quick and slow types
  alike. Threats also take rotating lateral slots across the corridor, so a
  burst arrives as a staggered column instead of a single smear.
- **Health bars are bars again.** The empty track was drawn in the sunken
  surface colour, a shade off the backdrop and therefore invisible, so all you
  saw was the coloured fill — a short stub floating beside its threat, reading
  as a rendering fault. The track is now drawn in the divider colour.
- **Threats entering the field fade in** over 70 units, measured from the
  chip's leading edge, so one is never drawn clipped against the frame.
- **`ATTACK ORIGIN` and `ROUTE A` no longer overlap.** The route names moved
  inside their own corridors, painted like road markings — the only place they
  fit, since the gap between a lane edge and the first deployment bracket is
  eight units.
- Elites are marked with a second outline rather than a fixed-radius circle,
  which used to cut through the wider tags.
- A vignette settles the backdrop so 1600 units of near-black reads as a place
  rather than as an empty document.

All of it was verified by rasterizing the real renderer under Robolectric's
native graphics and looking at the output. That is also what caught the last
defect: the chip plate was 92% opaque, so 7% of the chip behind still bled
through — the same smearing, only fainter. Chips are now fully opaque, tinted
slightly towards the threat's colour so the plate reads as a unit rather than
a hole cut in the lane. A test renders one threat alone and then with a second
overlapping it, and asserts the leading chip's interior is **pixel-identical**
between the two.

---

## [1.5.2]

### Added — wave and crypto on the field itself

Small, dim readouts in the top corners of the battlefield: `WAVE 17` at the
left, `◇ 1480` at the right. The same two numbers are already in the strip
above the field; they are repeated here because those are the two you check
constantly while placing agents, and looking away from the lanes to read them
costs you the thing you were watching.

Everything about the placement keeps them out of the way. They sit in the dead
band above the top lane (which starts at y=73 in world units), they are drawn
*before* the lanes and everything on them so gameplay always paints over them,
and they run at two-thirds alpha so they read as a watermark rather than as
another panel.

Verified by rasterizing the real renderer under Robolectric's native graphics
and diffing frames that differ only in the number being drawn. That locates the
ink exactly: the wave readout occupies `(85,12)-(96,28)` and the crypto readout
`(1471,12)-(1583,28)` — both comfortably clear of the `ATTACK ORIGIN` label
below them and of lane 1, and **nothing else in the 1,216,000-pixel frame
changes** when either number does. Tests assert all of it.

---

## [1.5.1]

### Fixed — buying firmware now visibly does something

The € firmware system was working correctly; its **readout** was not, in two
ways that between them made a working upgrade look broken.

- The multiplier printed with two decimals, so buying level 1 moved `×1.00` to
  `×1.00` and level 2 to `×1.01`. It now prints with **three decimals**. The
  step is 0.005, so every single level changes the last digit and no purchase
  is ever invisible. A test walks 400 consecutive levels and asserts no two of
  them print the same string.
- `PER LEVEL` was computed as `(FIRMWARE_DAMAGE_PER_LEVEL * 100).toInt()`,
  which truncates 0.5 to zero — the screen literally read **`+0%`**. It now
  reads `+0.5%`.

Both screens now go through one `FirmwareFormat` helper so they cannot drift
apart again, and the install panel gained two things worth knowing *before*
spending: which multiplier the next level takes you to, and the total gain a
bulk purchase buys (`12 levels  +6%`).

The **tuning is unchanged** — +0.5% a level is the intended curve, and the cost
table assumes it is bought in dozens.

---

## [1.5.0]

### Added — a real soundtrack instead of a two-second loop

The background "music" was a two-second drone looped through the sound pool.
It is now a **3 minute 33 second lo-fi chiptune**, composed and synthesized at
runtime by `audio/ChiptuneComposer.kt`. The APK still ships no audio files.

- Eight eight-bar sections, each with its own chord progression, melodic
  density and drum intensity: the intro is pad and bass alone, the melody
  arrives in section two, section five drops to a breakdown, and the outro
  thins back down so the track folds into its own beginning.
- Pulse lead, detuned pulse pad, triangle bass and a soft kit — the classic
  8-bit voices. The melody is confined to the A-minor pentatonic and steps at
  most two scale degrees at a time, snapping to a chord tone on strong beats,
  so it sounds composed rather than generated.
- The lo-fi half: a 16 kHz sample rate, a two-pole low-pass at 2.6 kHz that
  takes the edge off every square wave, a slow tape-wow pitch drift, soft
  saturation instead of clipping, and a noise floor quiet enough to be felt
  rather than heard.
- Playback moved from `SoundPool` to `MediaPlayer` (`audio/MusicEngine.kt`).
  `SoundPool` decodes a clip fully into memory and is meant for one-shots; a
  three-minute track wants to stream from disk and loop natively without a gap.
  The music pauses and resumes where it left off rather than restarting.
- Rendered one section at a time straight to disk, so peak memory is ~2 MB
  rather than the ~20 MB the whole track would need at once, and cached in
  `cacheDir/music` keyed by `TRACK_VERSION` — a second or two on first launch,
  free afterwards.
- Both ends fade through silence, so the loop seam cannot click.

Verified numerically rather than by assertion: the rendered track is 213.3 s,
peaks at 0.84 with no sample at the rail, carries 8% of its energy above 4 kHz
(the low-pass is doing its job), and an FFT of the busy sections shows exactly
the intended chords — a G bass at 98 Hz under B/D/G partials, with the detuned
pad visible as a 1 Hz beat against itself.

Two bugs were found and fixed during that verification:

- **The render was not deterministic.** The noise generator is object state, so
  a second render continued where the first left off and produced a different
  track. It is now reset per render, and a test compares two renders byte for
  byte.
- **The bass was written an octave too low** (55 Hz), where a phone speaker
  reproduces nothing at all. It now sits between 82 and 147 Hz.

### Added — the backdrop shifts every five waves

A long run no longer spends an hour on one shade of navy.

- Eight backdrop colours in `Palette.backdropBands`, one per five waves,
  cycling after forty. Every one is a near-black cool tone — navy, pine,
  indigo, slate, ocean, steel, moss, twilight.
- **None of them can be mistaken for an enemy.** A test asserts every band is
  more than 0.6 away in RGB from red, deep red, magenta, orange and purple, and
  that no band is ever warm (red channel never exceeds blue). Another asserts
  every band stays dark enough to read ASCII on.
- The change cross-fades over 2.5 seconds on a smoothstep curve rather than
  snapping, so it reads as the room's light changing. Consecutive bands are
  held between 0.015 and 0.14 apart in RGB by a test: far enough to notice over
  a couple of waves, close enough that it never draws attention to itself.
- The grid lines and the letterbox pick up a fraction of the current tint so
  nothing fights the backdrop it sits on.

### Notes

The € **CORE FIRMWARE** system was reviewed and deliberately left unchanged —
see the discussion in `BALANCE.md`. It is wired correctly and does exactly what
it claims; one firmware level is simply a 0.5% damage increase, which is below
what anyone can perceive in a single test.

---

## [1.4.0]

### Fixed — pockets that looked buildable but were not

A playtest screenshot marked six obvious tower spots inside the serpentine
bends. Checking them against the geometry showed five were being silently
rejected, and the reason was structural rather than a bad filter.

A deployment node needs `LANE_HEIGHT / 2 + NODE_RADIUS + margin` — 58 units — of
clearance from a route's centreline, so a pocket needs **116 units** between
levels before a tower can stand in it at all. Half the pockets were 90. They
read as prime real estate and could never be built on.

- Route levels re-spaced so **every pocket is 122 units**, comfortably over the
  threshold. The levels are now named constants (`A1`..`B3`) that the waypoints
  are built from, so the rule is visible where the numbers live.
- The A3/B1 convergence stays deliberately tight at 72 units — the two routes
  are meant to run close there and be covered from the pockets above and below,
  not built inside. A test asserts nothing ever lands in it.
- Candidate node rows are now **derived from the pockets** (one row down the
  centre of each, plus the outer margins) instead of a uniform grid that put
  rows wherever the arithmetic landed. One pocket was previously missed by two
  units.
- Node columns 10 → 12.

**48 deployment nodes, up from 31**, and best route coverage rises from 732 to
899 units.

### Added — tests that guard the derivation

New `MapGeometryTest`: every pocket is wide enough for a tower, every pocket
actually received nodes, no node sits on a route, no node is useless to build
on, nodes clear the server rack, both routes reach the core, and route progress
always resolves to a point on the path.

Also fixed a test that assumed route progress is proportional to x — true of the
old straight lanes, not of a serpentine.

---

## [1.3.0]

The map, the boss, and the language. Driven by a play report: bosses could not
be killed "no matter what I tried", and straight lanes were the suspected cause.
Both halves were right.

### Fixed — bosses were genuinely unkillable

Two causes compounded, and only one was about numbers.

- **Targeting.** Bosses move slower than the trash escorting them, so under
  FIRST targeting — "closest to the server" — every escort permanently outranked
  the boss. The whole board shot escorts while the boss walked the route
  untouched. Upgrading could never fix it, because upgrading does not change
  what gets shot. Bosses now take priority under FIRST and STRONGEST; LAST and
  WEAKEST keep ignoring them, which is what makes those modes useful.
- **Health tuned for coverage that does not exist.** Instrumenting a wave-5
  fight showed a five-agent board keeps the boss under fire for 11 seconds of
  its 50-second journey. Boss base health 520 → 250, speed 30 → 44, armour
  3 → 2, and the first boss cycle is now explicitly softened (×0.65) while the
  per-cycle climb steepens (+0.26 → +0.45). Wave 5 is 221 HP; wave 100 is
  19,619.

Three level-5 FIREWALLs now clear the wave-5 boss with the server untouched, and
a regression test asserts exactly that.

### Changed — two serpentine routes replace three straight lanes

| | Before | After |
| --- | ---: | ---: |
| Routes | 3 straight | **2 serpentine** |
| Route length | 1,378 | **2,353** |
| Best node coverage | 295 units | **732 units** |

A straight lane gives a tower one pass at each target; a route that doubles back
past the same pocket gives it three or four. The routes come close twice on
purpose — running parallel across the middle, then merging for the final
approach — so a tower in either convergence pocket covers both at once.

Boss routes rotate by cycle, so consecutive boss waves never arrive down the
same route.

Deployment nodes are now **derived from the routes** rather than hand-placed: a
candidate grid filtered to positions that clear every route and actually cover
some of it. Move a waypoint and the nodes follow, and no useless node exists.

Enemy speeds lifted ~20% so the longer routes do not slow the game down.

### Changed — it is a cyberattack, not a packet

A packet is ordinary network traffic, so calling every enemy one was inaccurate
and confusing. Throughout: `CYBERATTACK INCOMING`, `MAJOR BREACH DETECTED`,
`ATTACK ORIGIN`, `ATTACKS BLOCKED`, `threats remaining`, `ATTACKS STOPPED`.
`DDoS PACKET` → `DDoS FLOOD`, `ENCRYPTED PACKET` → `ENCRYPTED PAYLOAD`,
`INTRUSION` → `BREACH`, `PACKET REPLICATION` → `ATTACK REPLICATION`. Internal
identifiers followed. The Codex glossary keeps its *packet* entry, because there
the word is being taught correctly.

### Changed — the package matches the name

Now that this is pre-release, `com.packetbastion.asciidefense` →
**`com.cyopstd.game`**, along with the source tree, class names, DataStore file
and keystore. This installs as a new app rather than upgrading v1.2.0.

### Changed — an honest composition test

The old test asserted a mixed board beats stacking one agent type. Measured with
**equal crypto** rather than equal agent count, that is simply not true: focused
ANALYST, focused ROOT ADMIN and a counter-led mix all land within a few waves of
each other. The suite now asserts what is true — the same budget spent on
specialists goes far further than spent on the cheap all-rounder.

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
