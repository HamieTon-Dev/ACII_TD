# FEATURE BACKLOG — requested add-ons

Everything the owner has asked for that is **not built yet**, written down so a
session that picks this up can start without re-deriving the spec. Shipped work
lives in `CHANGELOG.md`; the state of the project lives in `PROGRESS.md`. This
file is the queue.

_Opened: 2026-09-24, against v1.17.0._

**Status:** ⬜ not started · 🟨 in progress · ✅ done (then move it to the
changelog) · ❓ needs a decision from the owner first.

---

## A. HUD and readability

### A1 ⬜ Halve the height of the speed bar

**Asked:** *"let's make the in game AGENTS 1X 2X 3X 5X bar skinnier like half the
height. it blocks the level of lower towers."*

The control bar is a Compose strip under the battlefield
(`ui/game/GameScreen.kt`, `ControlBar`), and `CompactButton` has a
`defaultMinSize(minHeight = 46.dp)` floor that sets its height
(`ui/common/Widgets.kt`). Halving it means a shorter variant rather than
changing `CompactButton` everywhere — the menus rely on that 46dp for touch
targets.

Watch the floor: Android's touch-target guidance is 48dp, and the project has
`MIN_TOUCH_HEIGHT_DP = 52` written down on purpose. A 24dp-tall speed button is
under it. Options, cheapest first:

1. Keep the buttons' *tap* height and shrink only the **padding and text**, so
   the bar loses most of its visual weight without losing its target size.
2. Let the speed row be genuinely short (~28dp) and accept the smaller target
   for those four buttons only — they are low-risk taps, and the field being
   blocked is the worse problem.

Recommendation: (1) first, measure how much height it actually recovers, and
only go to (2) if it is not enough.

### A2 ⬜ Stack WAVE above ◇ CRYPTO, both slightly larger

**Asked:** *"Put the wave number above the money count. make the money and wave
number slightly larger."*

These are the in-field corner readouts drawn by
`BattlefieldRenderer.drawFieldStatus` — `WAVE n` top-left, `◇ n` top-right,
both raised to 31pt in 1.17.0. Stacking them means one corner with two lines.

❓ **Which corner.** Top-left keeps the eye where the threats enter; top-right
keeps the money where it has always been. The run name currently sits centred
between them and would gain the freed corner.

Both plates are sized from a template (`WAVE_PLATE_TEMPLATE`,
`CRYPTO_PLATE_TEMPLATE`) so they do not resize as the numbers change — keep
that. `FieldStatusRenderTest` asserts where each readout may paint and will need
its expectations moved with the layout; it is written against the ATTACK ORIGIN
label's real position, so it should follow without becoming a magic number.

---

## B. The boss panel

### B1 ⬜ A button that opens the current boss's dossier

**Asked:** *"Add a small button to show current boss click and it has a pop up
window to show its health, stats, debuff, and modifiers and name of the type of
attack."*

Everything it needs already exists and is not surfaced anywhere: `Enemy` carries
`health`, `armor`, `speed`, and its rolled `BossModifier` set (each with a
`displayName`, `tag` and `description`), and `HudSnapshot` already knows
`bossOnField`.

Shape: a compact button in the control bar or the field's top strip, shown
**only while a boss is on the field**, opening a panel modelled on the rebuilt
`AgentManagementPanel` — two columns, actions clear of the scroll, capped
height. Live-updating, since the numbers move while it is open.

Content: name and glyph, health bar with current/max, armour, speed, the
modifiers it rolled with their descriptions, and — once B2 lands — which
variant it is and what that variant does.

---

## C. Boss identity

### C1 ⬜ Unique boss glyphs and identities

**Asked:** *"make text for some bosses different like unique boss identities ie
[GG] [ZZ] or similar."*

Today there is exactly one boss: `EnemyType.BOSS`, glyph `[!!!]`, and every
boss in the game is that type with different modifiers rolled on. Giving bosses
identities means **a boss family** — a set of variants with their own glyph,
stat weighting and signature behaviour — which is also the prerequisite for C2,
D2 and E1 below. This is the first real piece of engine work in the list.

Sketch: keep `EnemyType.BOSS` as the shared shape, add a `BossVariant` enum
(glyph, name, health/armour/speed multipliers, signature ability, which cycles
it can appear on), roll the variant alongside the modifiers in `EnemySystem`,
and let the renderer take its glyph from the variant.

Owner's examples: `[GG]`, `[ZZ]`. See §F for more to choose from.

### C2 ❓ Agent-versus-variant damage bonuses

**Asked:** *"[RH] deals more damage to GG boss and [BH] deals more bonus damage
to [!!!] boss or similar depending on bosses added."*

Needs C1. Sits naturally as a table on the variant (`bonusDamageFrom:
Map<AgentType, Float>`) rather than a branch in the damage path, so a new
variant is a table row.

❓ **How large the bonus is.** A counter that matters without making the rest of
the roster pointless is roughly ×1.5–×2 on top of the ×2 the new agents already
get against bosses. Wants a number from the owner, or a recommendation once the
variants exist.

---

## D. Agents

### D1 ⬜ Range buffs and identities for the weaker agents

**Asked:** *"add range buffs to IPS and lower level agents that range isn't long
enough and make them more unique ie immune to JAM or other."*

Current base ranges, for reference:

| Agent | Cost | Range |
| --- | ---: | ---: |
| TARPIT | 20 | 200 |
| FIREWALL | 40 | 168 |
| IDS | 55 | 290 |
| **IPS** | **70** | **170** |
| ANALYST | 95 | 212 |
| CRYPTOGRAPHER | 105 | 220 |
| ZERO-DAY HUNTER | 150 | 242 |
| AI SENTINEL | 185 | 256 |
| QUANTUM DEFENDER | 240 | 278 |
| ROOT ADMIN | 320 | 285 |
| NETWORK ARCHITECT | 280 | 264 |

IPS is the clear outlier: it costs more than IDS and reaches 120 units less.
FIREWALL's 168 is the shortest in the game but it is also the cheapest, which is
defensible.

The "make them more unique" half is the more interesting one. JAM immunity is a
real lever because jamming already exists — `BossModifier.AGENT_DISRUPTION` sets
`agent.disruptedFor`, which halves fire rate (`Entities.kt`). An agent that
ignores it has a clear, legible identity.

Keep the rule the owner set earlier: **never cut a number to fix balance if
raising another will do.** Raise IPS's range; do not lower IDS's.

### D2 ❓ [REDHAT] and [BLUEHAT]

**Asked:** *"Add two AGENTS [REDHAT] AND [BLUEHAT] which the unit on board can
be [RH] and [BH] these are very high damage immune to Jam and very long range
like 500+ range, ignore armor and 2X damage to bosses."*

Spec as given: very high damage · immune to JAM · range 500+ · ignores armour ·
×2 versus bosses · plus the per-variant bonuses in C2.

**Flagging this honestly:** as specified, each of these is strictly better than
every agent in the game on every axis at once — longest range (500 against ROOT
ADMIN's 285), immune to the one debuff that exists, ignores the armour stat the
whole late game is built on, and doubles up on the fight that matters. Built as
written they do not join the roster, they replace it: the optimal board becomes
"as many RH/BH as you can afford".

That is fixable without losing any of what makes them exciting. Levers, in the
order I would reach for them:

1. **Price and gate.** 450–600 ◇ and an unlock deep in the run (wave 30+), so
   they are a late-game investment, not an opening.
2. **Slow fire rate.** Huge per-shot damage at a low rate makes them siege
   weapons: devastating against a boss, wasteful against a swarm — which is
   exactly the shape the owner described.
3. **A cap on the board.** Two of each, say. Makes each placement a decision.
4. **No splash / single target.** They do not solve crowds. TARPIT, IDS and the
   rest keep their jobs.

❓ Wants the owner's call on 1–4 before it is built. Recommendation: all four,
with the numbers tuned against the existing dps-per-crypto curve in
`BALANCE.md` so they sit at the top of it rather than off it.

---

## E. A second map

### E1 ❓ "Hugging-Face", unlocked at wave 100 of Hack:AI

**Asked:** *"Create another base level named Hugging-face with a different lane
layout. this level unlocks by reaching wave 100 of Hack AI level. this one will
include new AI bosses and elites with new debuffs and modifiers."*

**This is the largest item in the list, and it is architecture before content.**
`WorldGeometry` is a Kotlin `object` — a singleton of hard-coded constants from
which the routes and all 79 deployment nodes are *derived*. Every system reads
it statically: the engine, the renderer, the transform, the save.

A second map means turning it into an interface with two implementations, and
the derivation is the good news — a new layout is a new set of constants, and
the nodes come out of it automatically, the same way the current map's do.

The trap to write down now: **`SavedRun` stores placements by node id**, and
node ids are positions in a per-map array. A run saved on one map must never
restore onto the other. The save needs the map's id alongside the run, and
`CONTINUE` must refuse (or discard) a run whose map does not match — a test for
that belongs with the first line of this work.

Unlock: wave 100 **on Hack:AI specifically**, so the identity needs a per-mode
best wave rather than the single `highestWave` it keeps today. `GameMode`
already has `unlockAtWave`; this extends it to "on which mode".

The leaderboard already carries `modeId` per entry, so a third mode lands on the
board with no change.

### E2 ❓ AI bosses for it

**Asked:** *"give these bosses a new look like [₩₩₩] and [¥¥¥] for example. the
old bosses can also be present. [₩₩₩] can Jam Red hat agent once every 6 seconds
if within range of 100 for 2 seconds. [¥¥¥] can Jam Blue hat agent once every 6
seconds if within range of 100 for 2 seconds."*

Needs C1 (boss variants) and D2 (the agents they target). The jam is a variant
signature: every 6s, if a RH/BH is within 100 units, jam that one agent for 2s.

Worth noting the interaction: RH and BH are specified as **immune to JAM**, and
these bosses exist specifically to jam them. That is a good design — the
counter-counter — but it has to be written as an explicit exception ("immune to
JAM *except* from its counterpart boss") or the two rules silently cancel and
the boss does nothing. Whichever way it goes, it wants a test.

---

## F. Options for the owner to choose

Asked for: *"if you think of other boss options or level options let me know and
I'll choose."*

### Boss variants

| Glyph | Name | What it does | Why it is interesting |
| --- | --- | --- | --- |
| `[GG]` | GOOD GAME | Owner's. Suggest: heavy armour, slow, hits hard | The wall. RH counters it (C2) |
| `[ZZ]` | ZOMBIE | Owner's. Suggest: revives once at 40% health | Punishes celebrating early |
| `[SS]` | SYN-STORM | Splits into two half-health bosses at 50% | Turns one lane problem into two |
| `[RM]` | RANSOM | Locks a random agent's upgrades for 8s | Attacks the economy, not the wall |
| `[∑∑∑]` | GRADIENT | Speeds up as it takes damage, slows when untouched | Rewards burst over chip damage |
| `[©©©]` | LICENSE | Takes less damage from any agent type that already hit it this wave | Forces a varied board |
| `[∆∆∆]` | MODEL COLLAPSE | Heals while three or more agents hit it at once | Punishes blobbing; very "AI level" |
| `[XX]` | EXFIL | Steals in-run ◇ crypto on hit instead of integrity | A different kind of loss |

My picks for the Hugging-Face set, if you want a recommendation: `[₩₩₩]`,
`[¥¥¥]` (yours), plus `[∆∆∆]` and `[©©©]` — both punish a lazy board rather
than simply having more health, which is what makes a hard mode interesting
rather than merely slower.

### Level layouts

| Name | Layout | Hook |
| --- | --- | --- |
| **Hugging-Face** | Owner's. Suggest three lanes converging late | More board, less time per lane |
| **Air-Gap** | A break in the lane; threats cross it untouchable | Two defended halves, one dead zone |
| **Model-Zoo** | Four short lanes, two merge points | Crowd control over single target |
| **Edge-Node** | One short, very fast lane | A sprint. Good for daily-run scoring later |

### Living background shapes

**Asked:** *"Make the living themes (background shapes) more unique if possible.
Different shapes and things of that sort."*

The five today are all variations on drifting glyph columns with different
colours and speeds (`ui/theme/LivingBackground.kt`,
`BattlefieldRenderer.drawLivingBackground`). Genuinely different *shapes* are
the ask. Candidates that stay within the rule that a background must never be
mistakable for a threat — cool, desaturated, low alpha, drawn under the lanes:

- **LATTICE** → an actual hex or circuit lattice that breathes, not columns.
- **AURORA** → broad horizontal bands that bend, like a spectrum analyser.
- **RAINFALL** → keep, it is already a distinct shape.
- **PULSE** → concentric rings from the core, on the wave beat.
- **DRIFT** → diagonal flow lines with occasional long streaks.
- New: **ORBIT** — slow elliptical traces, like a scheduler visualiser.
- New: **HEATMAP** — a coarse grid whose cells warm and cool.

---

## G. Sequence

The dependencies decide most of this:

1. **A1, A2** — self-contained UI, no decisions needed beyond the corner in A2.
2. **D1** — numbers and one new trait; no dependencies.
3. **C1** — boss variants. Unblocks B1's dossier, C2, and E2.
4. **B1** — the dossier, once there is something worth showing in it.
5. **D2** — RH/BH, once the guard rails in §D2 are chosen.
6. **E1** — the map layer. Largest, and worth its own version.
7. **E2** — the AI bosses, last, because they need C1, D2 and E1.

Backgrounds (§F) can slot in anywhere; they touch nothing else.

Ship as a new version when a group lands, not per item — the owner asked for
this build to be pushed as a new version once the agents are in.
