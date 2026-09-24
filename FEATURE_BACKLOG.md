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

## F. Revive on a rewarded ad

### F1 ⬜ Watch an ad to continue from the same wave at half integrity

**Asked:** *"if player loses add a button to choose to watch a 30 second ad to
revive player at same round with half health of core server and continue."*

The offer belongs on the game-over overlay (`ui/game/GameOverlays.kt`), beside
the existing actions: *CONTINUE — WATCH AD*, and whatever the player does, the
run either resumes or ends properly.

**This is a rewarded ad, not the interstitial the app already shows.** The
distinction matters and is the first piece of work: `AdGateway` today has one
method, `showInterstitial(onFinished)`, and it calls back on *dismissal*. A
revive granted on dismissal is a revive granted for closing the ad after two
seconds. A rewarded ad has a separate SDK path (`RewardedAd`) with a **reward
callback that fires only on completion**, and the revive must hang off that and
nothing else. The gateway grows a second method whose continuation carries
whether the reward was actually earned.

Keep the discipline the interstitial path already has: every path calls the
continuation exactly once, including when the SDK delivers both a dismissal and
a failure. A player who is owed a revive and gets a dead screen instead has lost
a run to a bug.

**"30 seconds" is not ours to set** — the same caveat as the loss interstitial,
now in `RELEASING.md`. Length belongs to the ad format and the network; rewarded
ads are typically 15–30s with the reward at the end. The app decides *whether*
to offer one and what it grants, not how long it runs.

#### What the revive restores

Owner's spec: same wave, **half** core integrity, continue. Concretely:

- `serverHp = ceil(serverMaxHp / 2)` — from the mode's max, so it is half of 70
  on Hack:AI and half of 100 on standard.
- **Clear the threats currently on the field.** Reviving into the swarm that
  just killed the player is not a revive; they would lose again inside a second
  and would rightly feel cheated of the ad they watched.
- Keep the wave number, the deployed agents, their levels and the ◇ crypto —
  the run continues, it does not restart.
- ❓ **Resume where, exactly.** Re-entering the *preparing* phase for the same
  wave gives a moment to spend crypto and re-place, which is what makes the
  revive feel like a second chance. Resuming mid-wave is harsher and closer to
  a literal reading of "continue". Recommendation: preparing phase.

#### The part that will bite

`GameViewModel.onRunEnded()` currently does everything at once: it records the
run result, submits the leaderboard entry, clears the saved run, and may show
the loss interstitial. **A revive after that double-counts.** One run would post
two leaderboard entries — one at the wave it died on and one at the wave it
finally reached — and its attacks, kills and crypto would land twice in lifetime
stats.

So the order has to change: on game over, *offer* first, and only record,
submit and clear the save once the player declines, or once a revive has been
spent and the run ends for real. That reordering is the actual work here; the ad
is the easy half. It wants a test that a revived run produces exactly one
leaderboard entry and one set of stats.

#### Policy — decided by the owner

- **One revive per run.** Stated on the button, so nobody watches an ad
  expecting a second.
- **REMOVE ADS does not cover the revive ad.** Owner's call, and it is the
  right one commercially: REMOVE ADS buys freedom from ads the player did not
  ask for, and the revive is one they did. The revive ad is the price of the
  revive, not an interruption. The store answers this properly with the pack in
  §F2, which is what a player who never wants to watch one buys.
  - The button's wording has to be straight about it, or a REMOVE ADS owner
    feels cheated the first time it appears: *"WATCH AD TO CONTINUE"*, and in
    the store, *"REMOVE ADS covers ads between runs. Revive ads are separate."*
- **The loss interstitial.** A player who watches a rewarded ad must not then be
  shown an interstitial when the run finally ends. A revive spends the run's ad
  budget; suppress the interstitial for that run.
- **The cooldown.** `AdPolicy`'s 180s gap exists to stop *unsolicited*
  interstitials stacking up. A revive is player-initiated and should be exempt,
  or a quick second loss offers a revive the player cannot take.
- **An unconfigured build** (`PlayServices.adsConfigured == false`, which is how
  the repo ships) has no ad to offer. The button is hidden rather than dead —
  the rule from §1.16: a control that cannot act must say so, and must never
  quietly do something else.

#### Why it is worth building

It is the one monetisation in the list that a player is *glad* to see: it
arrives exactly when they want something, it is opt-in, and it converts a lost
run into two more minutes of play. Google's own policy is comfortable with
rewarded ads on that shape as long as the value is stated up front and nothing
auto-plays, which is how the button reads.

---

### F2 ⬜ Store: a revive pack, and 10x the € in every pack

**Asked:** *"add an option in the store for '3 revives per run no ads for
revives ever - $4.99'. Also each pack should be 10x the amount of € given - even
for the skin packs and all buys that come with € included. makes it look more
valuable. Add in €5000 for each No ads after runs and No ads with 3 revive per
run packs."*

**The new product.** A permanent one-time purchase:

| | |
| --- | --- |
| Product id | `revive_pack` |
| Type | Permanent (restorable) |
| Price | $4.99 |
| Grants | 3 revives per run, no ad ever required for a revive, **+ €5,000** |

It supersedes the once-per-run limit for everyone who owns it, and the revive
button skips the ad entirely for them. Worth naming clearly in the store —
*"3 REVIVES PER RUN · NO REVIVE ADS EVER"* — because "no ads" appearing on two
different products is exactly the kind of thing that generates refund requests
when a player assumes one covers the other.

**Ten times the €, everywhere.** Every `grantsBudget` in `store/Sku.kt`
multiplied by ten, plus €5,000 onto `no_ads`:

| Product | € now | € after |
| --- | ---: | ---: |
| `budget_small` ($0.99) | 150 | **1,500** |
| `budget_medium` ($2.99) | 500 | **5,000** |
| `budget_large` ($4.99) | 900 | **9,000** |
| `core_skin_pack` ($2.50) | 200 | **2,000** |
| `bg_pack` ($4.99) | 200 | **2,000** |
| `starter_pack` ($4.99) | 200 | **2,000** |
| `no_ads` ($4.99) | — | **5,000** (new) |
| `revive_pack` ($4.99) | — | **5,000** (new) |

Two consequences to handle in the same change, or the numbers stop meaning
anything:

1. **The firmware curve is priced against the old scale.** €9,000 against a
   curve built when the largest pack was €900 either buys the whole tree
   outright or does not — it needs checking against `Balance.firmwareCost` and
   `MAX_FIRMWARE_LEVEL` before release. If a single $4.99 pack maxes firmware,
   the € economy is over. Either the curve's late costs rise with it, or the
   ×10 lands as a presentation change on a rescaled economy — **everything**
   ×10, including what waves pay out, so the ratio a player experiences is
   unchanged and only the numbers look bigger. That second option is almost
   certainly what is wanted here ("makes it look more valuable"), and it is the
   safer one.
2. `StoreTest` asserts the € packs get better per euro as they get larger. ×10
   preserves the ratios, so it should pass untouched — which is the point of
   having written it that way.

**Play Console:** `revive_pack` is a new product id that must be created before
release; `RELEASING.md` §2's table needs the row and the revised € column.

### F3 ⬜ Confirm what reaches the Google account, and say so in the UI

**Asked:** *"make sure purchases and player progress and money, damage, highest
wave save to google account/google drive (requires permission) [only saves
progress that's why permission is needed]"*

Most of this **already shipped in 1.15.0** — cloud save on Play Games Saved
Games, which is Drive app-data storage, which is why linking asks permission.
What travels today: highest wave, every lifetime stat including damage, €
budget, firmware, unlocks, the callsign and the run history.

The one word to be careful about is **purchases**, and the honest answer is that
the player gets exactly what they are asking for by a better route:

- Google Play already carries purchases to a new device. Sign into the same
  account, press RESTORE, and everything owned comes back — that is Play's job
  and it does it correctly.
- Entitlements are deliberately **not** in the save snapshot, and should stay
  out. A save file that grants paid content is a save file that can be edited to
  grant paid content, and a stale one could revoke a purchase made an hour ago.

So the work here is not plumbing, it is **verification and wording**: confirm on
two real devices that progress *and* purchases both arrive, and make the GOOGLE
PLAY screen say plainly that purchases come from Play and progress comes from
the linked save. The screen half-says it now; after the revive pack exists it
should also list revives owned, since that is the next thing someone will worry
about losing.

---

## G. Balance and scaling

### G1 ❓ Range scaling: ×1.25 every 5 levels

**Asked:** *"level ups need to scale multiplied by 1.25x the amount if range
scaling every 5 levels. Late game feels impossible... scaling multiplier level
ups every 5 levels means the ability to reach extremely high waves. Long play
time means long user interaction... trust me on this. balance isn't going to be
broken. waves past 100 will just feel more possible."*

**The goal is right and the diagnosis is right.** Range is the stat that barely
moves today:

```kotlin
UPGRADE_DAMAGE_GROWTH = 0.20f   // +20% of base per level -> x20.8 at level 100
UPGRADE_RANGE_GROWTH  = 0.007f  // +0.7% of base per level -> x1.69 at level 100
UPGRADE_RANGE_CAP     = 1.75f
```

Damage multiplies twenty-fold over a run and range barely moves — so a level-50
agent hits like a truck and still cannot see anything, which is exactly the
"late game feels impossible" the owner describes. TARPIT at level 50 has an aura
of 269 units on a 1600-unit map.

**The one number that needs a decision is the top end**, because ×1.25
compounding every 5 levels is very steep:

| Level | multiplier | FIREWALL (base 168) | TARPIT (base 300 after §G2) |
| ---: | ---: | ---: | ---: |
| 25 | ×3.1 | 512 | 915 |
| 50 | ×9.3 | 1,564 | 2,794 |
| 75 | ×28.4 | 4,769 | 8,517 |
| 100 | ×86.7 | 14,568 | 26,017 |

The map is 1,600 × 760. At level 50 every agent already covers the entire
battlefield from wherever it stands, and placement — the thing the 79 deployment
nodes exist for — stops being a decision. That is not an argument against the
change; it is an argument about **where it stops**.

Three shapes, all of which deliver "late game feels possible":

| | Level 25 | Level 50 | Level 100 | Placement still matters? |
| --- | ---: | ---: | ---: | --- |
| **(a)** ×1.25 / 5 levels, uncapped — as asked | ×3.1 | ×9.3 | ×86.7 | No, from ~L45 |
| **(b)** ×1.25 / 5 levels, capped at ×6 | ×3.1 | ×6 | ×6 | Yes, and the cap arrives ~L37 |
| **(c)** +25% of base / 5 levels (linear) | ×2.25 | ×3.5 | ×6 | Yes |

**Recommendation: (b).** It is the owner's formula exactly, for every level a
player will actually be upgrading through, and the cap only bites past the point
where uncapped range has already erased the map. It is also a single constant to
change later if it wants loosening.

❓ Needs a pick. **If no answer comes, build (b)** — it honours the request and
leaves the map intact, and the cap is one number to revisit.

### G2 ⬜ TARPIT base range ×1.5

**Asked:** *"Multiply 1.5X tarpit starting range. the aura is too small for late
game builds even when leveled to 50. This prevents some late game builds from
working."*

200 → **300**. Straightforward, and it compounds with §G1 — worth setting both
in one change so the aura is judged once, at its real size, rather than twice.

TARPIT is a slow field rather than a gun, so its "range" is an aura the player
reads as an area on screen. Check the rendered circle at level 50 under whatever
§G1 lands on before calling it done; the renderer draws it in
`drawTarpitFields`.

### G3 ❓ Give one of the samey units splash damage

**Asked:** *"Modify a unit that is almost the same as others to do splash
damage."*

The candidate is **IPS**. It costs 70, hits for 5.4 and reaches 170 — more
expensive than IDS (55) for 120 less range, and its "RAPID BLOCK" identity is
hard to feel next to FIREWALL. It is already flagged in §D1 as the roster's
outlier, and splash gives it a reason to exist that none of the others cover:
the answer to a swarm.

That also tidies §D2: with IPS owning crowds, REDHAT and BLUEHAT can stay
single-target siege units without leaving a hole.

❓ Confirm IPS is the unit meant — CRYPTOGRAPHER (105 / 26 dmg / 220) is the
other candidate that reads as a near-duplicate.

---

## H. Tutorial

### H1 ⬜ A guided first run

**Asked:** *"During tutorial force player to add 2x Firewall and 2x tarpit.
explain and point arrow to wave count, and Crypto◇ 'money earned to buy agents
to defend server, Crypto◇ earned for every kill and every wave completed'. make
sure arrows in tutorial text box point to the actual word 'Wave 1' and ◇120.
also make sure the skip button doesn't block text boxes, moves out of the way
during tutorial, and doesn't block 'Wave 1' and '◇120' when pointing to them.
Possibly ask if they want to know about enemy types and bosses and give quick
overview of the enemies their names and boss names and types, explain Jam,
explain Tarpit slows local enemies within range."*

Four separate pieces:

1. **Scripted placements.** The tutorial gates progress until the player has
   placed 2 × FIREWALL and 2 × TARPIT. The steps exist
   (`GameOverlays.tutorialContentFor`); what is missing is the engine-side
   condition that advances only on the right placement, and highlighting the
   nodes that count.
2. **Arrows that point at real things.** This is the one with teeth. The wave
   and crypto readouts are drawn inside the **native Canvas world**
   (`BattlefieldRenderer.drawFieldStatus`), while the tutorial card is
   **Compose**, so an arrow from the card to the word `WAVE 1` has to cross that
   boundary: the world's coordinates go through `WorldTransform` (scale and
   letterbox offsets) to land on a screen position the Compose layer can point
   at. Doable — the transform is already there and already exact — but it is
   real work, not a graphic. Everything else in this item is easy by comparison.
3. **The SKIP button must get out of the way.** It sits top-right (1.17.0), which
   is exactly where `◇ 120` is. While a step is pointing at the crypto readout
   it has to move — to the opposite corner, or below the card. The rule already
   in `PROGRESS.md` applies: a control that blocks the thing it is explaining is
   worse than no control.
4. **An optional briefing.** *"Want a rundown of what is coming?"* → the threat
   types with names and what each does, the boss (and, after §C1, the variants),
   what JAM does to an agent, and that TARPIT slows everything inside its aura.
   Every word of this already exists as `codexEntry` text on `EnemyType` and as
   the ability text on `AgentType`, so the briefing can be generated from the
   catalog rather than written twice and left to drift.

Copy the owner specified, verbatim, for the crypto step: *"money earned to buy
agents to defend server, Crypto◇ earned for every kill and every wave
completed."*

---

## I. Feel

### I1 ⬜ Boss and elite death explosions

**Asked:** *"Need to add an animation when bosses and elites are killed like a
blast radiating out of pixels of their unit color... can radiate out 500-700
range starting from where unit died and will make the game feel premium. this is
one of the most important things I'd like to make the whole game feel more
prevalent."*

Agreed, and it is cheap for how much it buys. The renderer already has an
effects pass (`drawEffects`, `drawBossExplosion`) and the engine already has
fixed-capacity pools, so this is a new effect kind rather than new machinery.

Shape: on a boss or elite death, emit a few hundred pixel shards from the death
point in the threat's own colour, travelling out to 500–700 units with drag, a
brief bright core flash, and a fade. Because the simulation allocates nothing
per frame by design, the shards want a **pre-sized pool** — a fixed array reused
per explosion, not a list built on death.

Two things to hold on to while building it:

- **It must not hide the board.** A 700-unit blast covers nearly half the map.
  Short-lived and quickly transparent, drawn *under* the threat chips and the
  HUD so nothing it covers is something the player needs mid-fight.
- **Battery saver and `backgroundAnimation = false` must scale it down**, the
  way every other effect in the renderer already does.

Verifiable the way the other visual work has been: rasterize a frame a few
hundred milliseconds after a boss dies and measure that the ink is there, is the
right colour, and is gone by the time it would interfere.

---

## J. Options for the owner to choose

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

## K. Sequence

The dependencies decide most of this. When the owner says *continue*, take the
first unfinished item here and work it.

**Quick wins with no decisions outstanding: A1, A2, G2, I1.**


1. **A1, A2** — self-contained UI, no decisions needed beyond the corner in A2.
2. **D1** — numbers and one new trait; no dependencies.
3. **C1** — boss variants. Unblocks B1's dossier, C2, and E2.
4. **B1** — the dossier, once there is something worth showing in it.
5. **D2** — RH/BH, once the guard rails in §D2 are chosen.
6. **I1** — the death explosions. Self-contained, and the owner rates it the
   highest-value item in the list for how the game *feels*.
7. **G1, G2, G3** — the scaling pass. One change, judged once, so the aura and
   the curve are not tuned twice against each other.
8. **F1** — the revive. Independent of the boss and map work, but it reorders
   the run-end path, so it is better done while that path is quiet than
   alongside a change to it.
9. **F2** — the store pass: the revive pack and the ×10 € rescale. After F1,
   because the pack sells something that has to exist first.
10. **H1** — the tutorial. Best done late: it teaches the game, and the game is
    still changing shape above it.
11. **E1** — the map layer. Largest, and worth its own version.
12. **E2** — the AI bosses, last, because they need C1, D2 and E1.
13. **F3** — the two-device verification, once there is a Play Console.

Backgrounds (§F) can slot in anywhere; they touch nothing else.

Ship as a new version when a group lands, not per item — the owner asked for
this build to be pushed as a new version once the agents are in.
