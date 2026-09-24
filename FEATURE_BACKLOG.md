# FEATURE BACKLOG — requested add-ons

Everything the owner has asked for that is **not built yet**, written down so a
session that picks this up can start without re-deriving the spec. Shipped work
lives in `CHANGELOG.md`; the state of the project lives in `PROGRESS.md`. This
file is the queue.

_Opened: 2026-09-24, against v1.17.0._

**Status:** ⬜ not started · 🟨 in progress · ✅ done (then move it to the
changelog) · ❓ needs a decision from the owner first.

---

## A. HUD and readability — ✅ shipped in 1.18.0

Both items are done and their detail now lives in `CHANGELOG.md` [1.18.0]:

- **A1** — the control bar is half its old height, via a `dense` flag on
  `CompactButton` used by that bar alone.
- **A2** — WAVE and ◇ CRYPTO are stacked in the top-right, larger, in the one
  block of the field nothing else uses.

Found while doing them: the battlefield had been painting over the top status
strip every frame since 1.5.2. Also fixed in 1.18.0.

---

## B. The boss panel

### B1 ✅ shipped in 1.22.0 — a button that opens the current boss's dossier

**Asked:** *"Add a small button to show current boss click and it has a pop up
window to show its health, stats, debuff, and modifiers and name of the type of
attack."*

A **BOSS** button sits in the control bar and is only there while a boss is on
the field — no dead control to press. It opens a two-column panel at the top
right: on the left the variant's glyph, name, an integrity meter with the raw
`420 / 1000` under it, and a strip carrying ARMOUR, SPEED and TO CORE; on the
right every modifier it rolled, each with the description that was already
written for it and never shown. A ZOMBIE that has already come back says so
under its name.

It reads the live enemy through `frameTick` rather than a snapshot, so the
numbers move while it is open — which is the only reason to open it mid-fight.
It closes itself when the boss dies, so the next one does not throw it over the
board unasked.

**What this cost, and the lesson:** the first build asserted the text was there,
and the text *was* there — laid out, at a height of zero, off the bottom of a
fixed-height panel. Two of the eight tests failed with "not displayed" and no
clue which line. Fetching the laid-out bounds is what found it: the signature
measured `78 x 0` and DISTANCE TO CORE's value `6 x 0`. Same lesson as the HUD
strip in 1.18.0, one level up: **a semantics assertion proves a composable
exists; only its measured bounds prove it is on screen.**

---

## C. Boss identity

### C1 ✅ shipped in 1.21.0 — the mechanism, plus the two you named

`BossVariant` is a table: glyph, health/armour/speed weighting, a signature
line and the earliest cycle it may appear on. Adding one is a row.

Shipped with `[!!!]` BREACH (the original), **`[GG]` GOOD GAME** (the wall —
huge, armoured, slow) and **`[ZZ]` ZOMBIE** (gets back up once at 40%). The
banner now names the boss and what it does while there is still time to build
for it.

❓ **The rest of §J's list is still yours to pick from** — `[SS]`, `[RM]`,
`[∑∑∑]`, `[©©©]`, `[∆∆∆]`, `[XX]`. Each is now one table row plus, where it has
a signature behaviour, a hook like ZOMBIE's.

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

### D1 ✅ shipped in 1.19.0

IPS 170 → 260 and given splash; FIREWALL 168 → 195 and made immune to JAM;
every dearer agent raised so reach stays monotone with price. Detail in
`CHANGELOG.md` [1.19.0].

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

### F1 ✅ shipped in 1.23.0 — watch an ad to continue at half integrity

**Asked:** *"if player loses add a button to choose to watch a 30 second ad to
revive player at same round with half health of core server and continue."*

**WATCH AD TO CONTINUE** sits on the game-over screen when — and only when —
the run is genuinely lost, the run's one revive is unspent, and a rewarded ad
is actually loaded. Otherwise there is no button, not a dead one.

Taking it clears the board, restores half the *mode's* maximum integrity
(rounded up, so 50 on standard and 35 on Hack:AI), keeps the agents, their
levels and the ◇ crypto, and drops back into the **preparing** phase for the
same wave — a moment to spend and re-place before it comes again, which is what
makes it read as a reprieve rather than a stay of execution.

#### The four things that were easy to get wrong

**The reward, not the dismissal.** `AdGateway` grew `showRewarded`, whose
continuation carries whether the reward was *earned*. An interstitial calls
back on dismissal, so a revive hung off that callback is a revive granted for
closing the ad after two seconds. `AdMobGateway` latches the reward in
`onUserEarnedReward` and reads it when the ad closes, and every path — earned,
skipped, failed to show, none loaded, no activity, SDK throwing — calls the
continuation exactly once.

**The double count.** `onRunEnded()` used to record the run, submit the
leaderboard entry, clear the save and show the loss ad the instant the core
fell. A revive after that would have posted *two* leaderboard entries for one
run and landed its kills, crypto and damage twice in lifetime stats. It is now
split: the summary goes up and the revive is offered, and nothing is written
until `finalizeRun()` — which the decline, RETRY, MAIN MENU and backgrounding
the app all go through, and which is idempotent. Six tests fail if that split
is undone.

**Backgrounding the offer.** A run sitting on an unanswered offer is
deliberately unrecorded, so leaving the app there would have been a way to
erase a bad run. `onAppPaused()` finalizes it.

**The ad budget.** A run that spent a rewarded ad is not then charged the loss
interstitial when it finally ends.

#### Policy, as the owner set it

- **One revive per run** (`Balance.REVIVES_PER_RUN`), stated on the button.
- **REMOVE ADS does not cover it**, and the game says so in two places rather
  than letting a paying player discover it: the button's own line reads
  *"REMOVE ADS covers ads between runs; revive ads are separate"*, and the
  GOOGLE PLAY account screen's ADVERTISING row reads **REMOVED · REVIVE ADS
  SEPARATE** instead of the *REMOVED · NONE* that would now be a lie.
- **A build with no rewarded unit never offers it.** `cyops.admob.rewardedId`
  is a separate, optional id; `PlayServices.rewardedConfigured` is the gate.
- *"30 seconds" is still not ours to set* — length belongs to the ad format and
  the network. The app decides whether to offer one and what it grants.

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

## G. Balance and scaling — ✅ shipped in 1.19.0

- **G1** — range now climbs ×1.25 every five levels, capped at ×6 (reached at
  level 45). The owner's formula; the cap is the compromise, and it is one
  constant if it wants loosening.
- **G2** — TARPIT base aura 200 → 300.
- **G3** — IPS is the splash unit.

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

## I. Feel — ✅ shipped in 1.20.0

**I1** — bosses and elites now die in a blast of pixel shards in their own
colour, out to 700 / 500 units, with a shockwave ring and a white core. One
pooled effect carries a seed and the renderer derives every shard from it, so a
420-piece explosion allocates nothing. Detail in `CHANGELOG.md` [1.20.0].

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

**Quick wins with no decisions outstanding: none left — every remaining item
either depends on another or needs a decision noted in its section.**


1. ~~A1, A2~~ — ✅ 1.18.0.
2. ~~D1~~ — ✅ 1.19.0, together with all of §G.
3. ~~C1~~ — ✅ 1.21.0. B1, C2 and E2 are unblocked.
4. ~~B1~~ — ✅ 1.22.0.
5. ~~F1~~ — ✅ 1.23.0.
6. **D2** — RH/BH. **Blocked on the owner:** the four guard rails in §D2 need
   a pick before this can be built. Skipped rather than stalled on.
7. **F2** — the store pass: the revive pack and the ×10 € rescale. **Next**,
   and now unblocked — the pack sells a revive that exists.
8. **H1** — the tutorial. Best done late: it teaches the game, and the game is
    still changing shape above it.
9. **E1** — the map layer. Largest, and worth its own version.
10. **E2** — the AI bosses, last, because they need C1, D2 and E1.
11. **F3** — the two-device verification, once there is a Play Console.

Backgrounds (§F) can slot in anywhere; they touch nothing else.

Ship as a new version when a group lands, not per item — the owner asked for
this build to be pushed as a new version once the agents are in.
