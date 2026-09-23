# CyOps TD — Architecture

This document explains how the game is put together and, where a decision could
reasonably have gone another way, why it went the way it did.

---

## 1. The central decision: hybrid rendering

Compose is excellent at menus and poor at drawing two hundred moving glyphs.

A Compose `Text` node is not free: it measures, lays out, participates in
recomposition, and carries a slot in the composition tree. Drawing every packet,
projectile, server LED and death flourish as its own `Text` would mean several
hundred of them being created and destroyed continuously — the exact workload
Compose is worst at.

So the game is split:

| Layer | Owner | Why |
| --- | --- | --- |
| Menus, HUD, dialogs, panels | **Compose** | Layout, theming and state binding are genuine assets here. Recomposes a few times a second. |
| The battlefield | **Native `android.graphics.Canvas`** | One draw pass, a handful of reused `Paint` objects, no allocation, no layout. |

`BattlefieldRenderer` is a plain class holding five `Paint` instances and a
one-character scratch buffer. It draws the entire battlefield — backdrop, lanes,
server rack, nodes, range rings, agents, packets, projectiles and effects — in a
single `draw()` call. It allocates nothing per frame.

The Canvas subscribes to the simulation by reading `viewModel.frameTick` inside
its draw lambda. Compose's snapshot system sees that read and invalidates the
draw when the tick changes — and at no other time.

---

## 2. The game loop

```
Compose frame clock (withFrameNanos, in GameScreen)
        │  real delta seconds
        ▼
GameViewModel.onFrame(delta)
        │  delta × speed multiplier
        ▼
GameEngine.update(delta, speed)
        │  split into ≤20 ms sub-steps
        ▼
GameEngine.step(dt) ──┬── wave phase machine + spawn scheduling
                      ├── CombatSystem.refreshBuffs()
                      ├── EnemySystem.update(dt)      move, boss behaviour, arrivals
                      ├── CombatSystem.update(dt)     acquire targets, fire
                      ├── ProjectileSystem.update(dt) fly, impact, resolve damage
                      └── EffectSystem.update(dt)     age out flourishes
        │
        ▼
frameTick++ ; HudSnapshot rebuilt and assigned only if changed
        │
        ├──► Canvas redraws (reads frameTick)
        └──► HUD recomposes (only when a number actually changed)
```

### Why sub-steps

`update()` splits its budget into slices of at most 20 ms. At 3× speed a single
60 Hz frame represents 50 ms of simulated time, and a DDoS packet moving at
~220 world units per second would jump far enough to cross a narrow agent's
range ring entirely between two samples — it would be *inside* range at no point
the game ever looked. Sub-stepping makes that impossible.

The input delta is also clamped (`Balance.MAX_FRAME_DELTA`), so a garbage
collection pause or a backgrounded app cannot teleport the whole wave forward.

### Why the clock lives in the UI

`GameScreen` owns the `withFrameNanos` loop. When the screen leaves the
composition — the player opens the Codex, or the app is backgrounded — the
`LaunchedEffect` is cancelled and the simulation simply stops. There is no
separate thread to remember to pause, and no way for a match to keep running
invisibly.

Battery saver changes only the tick *rate*: deltas are accumulated and flushed
at ~30 Hz instead of ~60 Hz. The engine receives the same total elapsed time
either way, so difficulty, damage and timing are provably unaffected.

---

## 3. State management

### Three tiers

1. **Simulation state** — inside `GameEngine`. Plain mutable Kotlin. No Compose,
   no `StateFlow`, no observation overhead in the hot loop.
2. **UI state** — `GameViewModel`, as Compose `mutableStateOf` properties.
   Selection, pause, speed, panels, banners, tutorial step.
3. **Persistent state** — DataStore, exposed as `Flow` and mirrored into
   `mutableStateOf` in the view model.

### The HudSnapshot pattern

The battlefield needs live data 60 times a second; the HUD needs it only when a
number changes. Every frame the view model builds a small immutable
`HudSnapshot` and assigns it **only if it differs** from the current one.

That one equality check is what separates 60 canvas redraws per second from 60
HUD recompositions per second. One small allocation per frame is a trivial price
for that.

### Why not Navigation Compose

The game has eight destinations, no deep links, no arguments to serialize, and a
match screen that must keep a live view model across every transition. A sealed
`Screen` hierarchy plus a `mutableStateOf` is smaller, faster to reason about,
and adds no dependency. `PacketBastionApp.kt` is the entire router.

---

## 4. Entity model and pooling

Runtime entities (`Enemy`, `Agent`, `Projectile`, `Effect`) are **mutable
classes, not data classes**, and live in fixed-capacity `ObjectPool`s allocated
once at engine construction.

```kotlin
val enemies     = ObjectPool(72)  { Enemy() }
val agents      = ObjectPool(32)  { Agent() }      // one per deployment node
val projectiles = ObjectPool(140) { Projectile() }
val effects     = ObjectPool(110) { Effect() }
```

`active == false` means "parked in the pool, ignore me". `obtain()` scans for
the first inactive slot; when the pool is full it returns `null` and the caller
**skips the spawn**. That is a deliberate failure mode: a phone is much happier
refusing a 73rd simultaneous packet than trying to allocate, simulate and draw
it. The caps double as a hard ceiling on worst-case frame cost.

The result is a simulation loop that allocates essentially nothing, so the
garbage collector never stutters the render.

### Enemy model

An enemy's authoritative position is `progress` — distance travelled along its
lane's waypoint path. `x` and `y` are *derived* every step by
`EnemySystem.placeOnPath()`. Writing coordinates directly does not work and is
not meant to; progress is the single source of truth, which is what keeps
`pathFraction()` (used by FIRST/LAST targeting) exact.

Boss modifiers are stored as a **bit set over `BossModifier.ordinal`** in a
single `Int`, so a boss carrying four modifiers costs four bits rather than a
collection allocation per boss.

### Agent model

An agent is bound to a deployment node id. Its stats are derived from
`AgentType.statsAtLevel(level)` — nothing is cached, because the computation is
three multiplications and caching would just be another thing to invalidate.

`damageBuff` / `rateBuff` are recomputed from scratch every step by
`CombatSystem.refreshBuffs()`. Network Architect auras **do not stack**: an
agent takes the strongest aura covering it. That keeps the effect legible and
closes an architect-stacking exploit.

---

## 5. Wave generation

`WaveGenerator` is the only thing that decides what spawns. There is **no
hand-written wave table anywhere in the game** — waves are composed from the
wave number.

```
generate(wave)
   ├── isBossWave(wave)?  →  generateBossWave()
   └── otherwise           →  generateStandardWave()
```

A `WavePlan` is a list of `SpawnOrder`s — time, type, lane, elite flag, boss
flag — pre-computed at wave start. The engine then simply walks that list
against `waveTimer`, which makes spawning trivially deterministic and cheap.

### Standard waves

Count, spawn interval and elite chance come from `Balance`. Archetypes come from
a **weighted pool that widens by wave band**:

| Waves | Pool |
| --- | --- |
| 1–2 | PACKET only |
| 3 | + BOT |
| 4 | + MALWARE |
| 5–7 | + EXPLOIT |
| 8–10 | + TROJAN |
| 11–14 | + ENCRYPTED |
| 15–20 | + DDoS |
| 21–30 | + ZERO-DAY |
| 31+ | full pool, reweighted toward the dangerous end |

The early bands are scripted deliberately: waves 1–4 are the game teaching
itself, and that is too important to leave to a random roll.

Swarm archetypes (BOT, DDoS) are emitted as a tight burst in a *single* lane
rather than scattered, because that is what makes a swarm read as a swarm.

### Boss waves

Escorts first, then the boss (or bosses) behind its own traffic. Modifiers are
rolled from `BossModifier.poolForCycle(cycle)`, which introduces exactly one new
mechanic at a time. **Wave 5's boss deliberately has no modifiers at all** — a
player's first boss should be a clean fight.

Boss count scales with cycle (1 → 2 at cycle 4 → 3 at cycle 9) in *distinct
lanes*, which raises difficulty without raising the entity count much.

### Why not more enemies

Enemy count grows sub-linearly and hard-caps at 46 per wave. Difficulty comes
from **stronger and more varied** packets, not more of them. A phone renders 40
interesting packets far better than 400 boring ones.

---

## 6. Combat and the damage pipeline

```
CombatSystem.update
   └── for each agent off cooldown:
         selectTarget(agent)        ← FIRST / LAST / STRONGEST / WEAKEST
         ProjectileSystem.launch()  ← rolls crits, sets slow/chain/pierce flags
                                      damage = stats × auraBuff × crit

ProjectileSystem.update
   └── on impact:
         applyDamage(enemy, raw, sourceType, ignoresArmor, critical, sourceNodeId)
               ├── × damageMultiplier(enemy, sourceType)   ← the counter-play table
               ├── − armour (floored at 18% of raw)
               └── health ≤ 0 → credit the kill → EnemySystem.onEnemyDestroyed
```

Targeting is an O(agents × enemies) sweep using squared distances with no
allocation: at the pool caps that is at most 32 × 72 comparisons per sub-step,
which is nothing.

### The counter-play table

Every "who beats what" rule lives in `ProjectileSystem.damageMultiplier()` —
one function, readable in one screen:

| Condition | Multiplier |
| --- | --- |
| Encrypted target, Cryptographer source | ×3.0 |
| Encrypted target, anything else | ×0.45 |
| Elite/boss target, Analyst source | ×1.8 |
| Fast archetype, IDS source | ×1.45 |
| Swarm archetype, IPS source | ×1.35 |
| Boss with FIREWALL RESISTANCE, Firewall source | ×0.65 |

Armour is applied after the multiplier and is **floored at 18% of the raw hit**.
No combination of packets can make an agent completely dead weight — a wrong
pick is punished, never bricked.

---

## 7. World geometry and screen scaling

The battlefield is simulated in a fixed **1600 × 760 world**, and
`WorldTransform` letterboxes it onto the actual view:

```kotlin
scale   = min(viewWidth / 1600, viewHeight / 760)
offsetX = (viewWidth  - 1600 × scale) / 2
offsetY = (viewHeight -  760 × scale) / 2
```

Nothing in the game logic or the renderer knows about pixels or device density.
The same code is correct on a 16:9 budget phone and a 21:9 flagship, and the
conversion happens in exactly two places: once when drawing, once when
converting a tap back to world space.

Lanes are **waypoint lists**, not straight-line special cases:

```kotlin
laneWaypoints[lane] = [ Waypoint(SPAWN_X, laneY), Waypoint(SERVER_X, laneY) ]
```

They are straight today. Adding a bend to a future map is a matter of inserting
waypoints — `placeOnPath()` already walks arbitrary segment chains, and no
movement, targeting or rendering code would change.

Deployment nodes are a 4 × 8 grid flanking the lanes: row 0 above lane 1, rows
1–2 between lanes, row 3 below lane 3. Fixed nodes rather than free placement is
what makes this comfortable on a touchscreen — the tap radius is twice the node
radius, so nothing demands precision.

---

## 8. Saving

Jetpack **DataStore (Preferences)** holds everything. Scalars are stored as
typed preferences; the in-progress run and the per-agent deployment tallies are
stored as JSON strings via `kotlinx.serialization`.

That mix was chosen over Room deliberately: the data is a handful of scalars and
one small object graph, DataStore already gives atomic, corruption-tolerant,
transactional writes, and it costs a fraction of Room's size and build time.

### Defensive by construction

Every read path catches, logs and falls back:

- `Flow` reads use `.catch { emit(emptyPreferences()) }` — a corrupt store reads
  as defaults rather than throwing into the UI.
- Deserializing a run is wrapped: malformed JSON logs a warning and yields
  `null`, which simply means "no save to continue".
- `restore()` validates every placement — unknown agent name, out-of-range node
  id, duplicate node, absurd level — and skips or clamps rather than trusting
  the file.
- Writes swallow `IOException`. Losing a save is survivable; crashing is not.

Every persisted field has a default, so a save written by an older build
deserializes cleanly against a newer one. Adding a field is a non-breaking
change.

### When it saves

- After every cleared wave.
- When leaving a match from the pause menu.
- In `Activity.onPause` — not `onStop`, which is not guaranteed to run if the
  process is killed aggressively.

### The one deliberate limitation

A mid-wave save records the wave number, and resuming replays that wave from the
start. Packets in flight are not persisted. Reconstructing a half-finished
assault would be both complex and disorienting to drop back into; restarting the
wave with your agents, crypto and integrity intact is simpler and kinder.

---

## 9. Audio

The game **ships no audio files**. `ToneSynth` generates every effect as 16-bit
mono PCM at 22.05 kHz, wraps it in a WAV container, writes it to the app cache
once, and hands it to a `SoundPool`.

Each effect is a recipe in `SoundBank`: a duration plus a list of voices (sine,
square, triangle or deterministic noise), each with a frequency sweep, amplitude
and exponential decay. A 4 ms fade-in removes the click an instant attack would
otherwise make.

This does three things at once: it keeps the APK tiny, it removes every audio
licensing question, and it gives the game a coherent "terminal beep" character
that a bag of sampled effects would not.

### Music

The music is generated too, but it does not go through any of the above.
`ChiptuneComposer` writes a **3 minute 33 second lo-fi chiptune** — eight
eight-bar sections, each with its own chord progression, melodic density and
drum intensity, so the track develops instead of repeating. The first version
of the game looped a two-second drone out of the sound pool, which is exactly
the thing that grates after ten minutes of play.

The composer works in two passes. First it *composes*: it walks the
arrangement, picks the chord for each bar, and emits note events for a pulse
lead, a detuned pulse pad, a triangle bass and a small drum kit. The melody is
constrained to the A-minor pentatonic and steps at most two scale degrees at a
time, snapping to a chord tone on strong beats — that constraint is what makes
a generated line sound like a tune rather than like random notes. Then it
*renders*: each note is synthesized into the mix with its own envelope, and the
mix goes through a master chain of a two-pole low-pass at 2.6 kHz, a high-pass
at 38 Hz, soft saturation and a very quiet noise floor.

Four things make it "lo-fi" rather than merely chiptune: the 16 kHz sample
rate, the low-pass that shaves the harsh square-wave harmonics, a slow two-rate
tape-wow pitch drift, and the detuned pad voice beating gently against itself.

Rendering is blocked one section at a time and streamed straight to disk, so
peak memory is about 2 MB rather than the 20 MB the whole track would need.
Playback is a `MediaPlayer` (`MusicEngine`), not the `SoundPool` — `SoundPool`
decodes fully into memory and is built for one-shots, while `MediaPlayer`
streams from disk and loops natively without a gap. The render is cached under
`cacheDir/music` and keyed by `TRACK_VERSION`, so the cost is a second or two
on first launch and nothing afterwards. Both ends of the track fade through
silence, so the loop seam cannot click.

The composer has no Android dependency and no unseeded randomness, so the track
is byte-identical everywhere and a JVM test renders and inspects it directly.

Threats are drawn as opaque *chips* rather than as bare text, and sorted back
to front by progress along the route before drawing. Both exist for the same
reason: a threat tag is up to sixty world units wide, and a fast archetype
constantly overtakes a slow one, so bare text drawn over bare text composited
into something unreadable. An opaque plate turns an overlap into occlusion.
The plate is fully opaque — at 92% the chip behind still bled through — and
tinted a little towards the threat's colour so it reads as a unit rather than
as a hole cut in the lane.

Wave and crypto appear twice on purpose: once in the Compose HUD strip, and
once as small dim readouts in the battlefield's top corners, drawn by the
renderer straight from `engine.currentWave` and `engine.crypto`. They are drawn
before the lanes so gameplay always paints over them, and they live in the band
above lane 1 so they can never cover a deployment node.

The engine never touches `SoundPool` or the `Vibrator`. It raises a `GameSound`
or `HapticCue`, and the view model decides whether the player's settings allow
it to be heard or felt.

---

## 10. Extension points

The structure anticipates the obvious follow-ups:

| To add | Touch |
| --- | --- |
| A new agent | One entry in `AgentType`; optionally one branch in `ProjectileSystem.launch` and one row in `damageMultiplier`. UI, Codex and roster pick it up automatically. |
| A new threat | One entry in `EnemyType`, one weight in the archetype pool. |
| A new boss modifier | One entry in `BossModifier`, one behaviour block in `EnemySystem.updateBoss`, one pool entry. |
| A new map | Extend `WorldGeometry` — lane count, waypoints (including bends), node grid. |
| Rebalancing | `core/Balance.kt`, and nowhere else. |
| Achievements | `GameRepository` already aggregates every statistic needed. |

Codex agent and threat entries are **generated from the game data**, so they can
never drift out of sync with what the game actually does.

The engine has no Android dependency at all, which is why a full match can be
simulated headlessly on the JVM — and why the test suite tests the real game
loop rather than a mock of it.

---

## 11. Testing

Everything runs on the JVM. Nothing in the suite needs a device or an emulator,
which is what keeps it fast enough to actually run on every change.

### Layer 1 — simulation

`GameEngineTest` constructs a real `GameEngine`, seeded deterministically, and
steps it in fixed 20 ms slices:

```kotlin
private fun GameEngine.runFor(seconds: Float, speed: Float = 1f) { ... }
private fun GameEngine.runWaveToCompletion(budgetSeconds: Float = 240f): Boolean
```

That is the same `update()` the frame clock calls in the app, so these tests
exercise the shipping game loop: packets spawn, walk their lanes, get shot,
pay out crypto, and reach the server. A full 12-wave run completes in
milliseconds.

Every helper that advances multiple waves carries an explicit **progress
assertion** rather than a bare `while` — if a run stalls or the server falls
early, the test fails with a message instead of hanging the build. A hanging
test is far worse than a failing one.

Two tests play a fully built board to its death. One asserts that nothing
degenerates on the way — every wave resolves, no pool overflows, the economy
stays bounded — so an endless run ends by being overwhelmed rather than by a
bug. The other builds the same board twice, once stacking a single maxed agent
and once mixing the types the counter-play table rewards, and asserts the mix
gets further. If it did not, every counter in the damage table would be
decoration.

`BalanceTest` asserts the *shape* of the difficulty curves — monotonicity,
caps, the wave-9-to-10 step, and that reward growth stays below health growth.
These are the assertions that catch a well-meaning constant tweak quietly
ruining the game.

`WaveGeneratorTest` samples across many seeds where the pool is weighted, so it
asserts what the generator can produce rather than what one lucky roll did.

`SaveSerializationTest` covers the format a player can lose progress to: exact
round-trips, payloads from older builds (missing keys), payloads from newer
builds (unknown keys), and malformed JSON.

### Layer 2 — persistence

`GameRepositoryTest` runs against a real DataStore under Robolectric: defaults,
settings persistence and clamping, save round-trips, CONTINUE gating (a run
saved with a dead server is never offered), unlock accumulation, statistics
folding across runs, and a full reset.

`GameRepository` takes a `DataStore<Preferences>` rather than building one from
a `Context`, and `GameViewModel` takes a `GameRepository` (defaulting to the
real one). That seam exists for a concrete reason: Robolectric keeps one process
across test classes and a view model's coroutines outlive the test that created
it, so tests sharing the app's single store could observe a previous test's
write landing after their own setup. Each test now gets its own store file,
which removes that class of ordering failure by construction rather than by
timing.

### Layer 3 — UI

`GameUiTest` runs under **Robolectric** with the Compose test rule, composing
the real screens with the real theme at a landscape qualifier:

```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1920dp-h1080dp-land-xhdpi")
```

It asserts on what a player would see and tap — that CONTINUE is inert without
a save, that the Codex switches sections, that a settings row toggles when you
tap anywhere on it, that RESET PROGRESS cannot fire without confirmation — and
drives `GameViewModel` through the full deploy flow, checking that crypto is
spent, the agent lands, and the tutorial advances.

Robolectric downloads its `android-all` runtime itself rather than through
Gradle's repositories, so `app/build.gradle.kts` points it at the same Maven
mirror and pins it to a single test fork to avoid racing on that cache.

### What the tests do not cover

Real frame pacing, GPU behaviour and touch latency need real hardware. The
build environment's emulator has no KVM acceleration, so it renders in software
far too slowly to produce meaningful performance numbers — it is useful for
confirming that the app installs, launches and renders, and nothing more.
