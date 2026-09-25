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

### A3 ✅ shipped in 1.30.0 — the integrity numbers are off the rack

**Asked:** *"since we have a HP bar at the top now, the numbers for HP on the
server in the wave area is unnecessary."*

Correct — they are drawn twice. The HUD strip carries `CORE-SERVER · HP 100 /
100` with a coloured `IntegrityBar` beside it, and `BattlefieldRenderer
.drawServer` then paints `100 / 100` at 27pt inside the rack as well. The
second one costs a chunk of the most valuable space on the board and says
nothing the first did not.

**Remove the numbers only.** Two things nearby look like the same thing and
are not:

- **The bar on the rack stays.** It is a *spatial* cue — integrity where the
  damage is landing — and it reads at a glance without moving your eyes off
  the lanes. That is a different job from a figure in the status strip, which
  is the one you read deliberately.
- **`!! INTEGRITY LOW !!` stays.** It is an alarm, not a readout, and it is
  the only thing on the board that tells a player mid-wave that they are
  about to lose.

**The bit to watch:** the rack's chase circuit is drawn *around* the figures —
the comment in `drawServer` says as much, "the number the chase circuit is
drawn around, so it is set large enough to be worth framing". Deleting the
text leaves the animation framing empty space, so the bar wants centring in
the space the numbers vacate rather than being left where it is.

Done. The bar moved up into the space the figures vacated so the chase
circuit still frames something, and a test diffs two frames that differ only
in integrity: whatever pixels move are the integrity display by construction,
and their *shape* says whether it is a bar or a line of digits. Deleting the
bar by mistake would otherwise have looked exactly like a successful change.

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

### C2 ✅ DECIDED — Agent-versus-variant damage bonuses

**Owner's answer (2026-09-25):** *"2x damage for both."* Both counter pairings
deal double damage against the variant they counter. Flat and readable rather
than a curve — the player can reason about it without a spreadsheet.

**Original note follows.**

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

### D2 ✅ DECIDED — [REDHAT] and [BLUEHAT]

**Owner's spec (2026-09-25), verbatim intent:**

| | |
| --- | --- |
| Unlock | wave **30** |
| Cost | **◇400** each |
| Fire rate | as high as the **highest currently in the roster** |
| Targeting | **auto-focus bosses first**, overriding normal targeting |
| Deployment cap | **4 of each**, independently |

*"dont fight me on it this is what I want ... balance is for late game trust
me I tested it and wave 101 is impossible with the best build on the map."*

Taken as given. The stated problem is real and specific: waves past ~100 put
4+ bosses on the board at once and no existing agent answers that, because
every one of them targets by position rather than by threat. A boss-seeking
high-rate agent is the missing tool, and the ◇400 price plus a wave-30 unlock
plus a hard cap of 4 is what keeps it from trivialising the mid game.

**Original note follows.**

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

### E1 ◐ "Hugging-Face" — architecture and the save guard done

**Asked:** *"Create another base level named Hugging-face with a different lane
layout. this level unlocks by reaching wave 100 of Hack AI level. this one will
include new AI bosses and elites with new debuffs and modifiers."*

**Status: the architecture and the save guard landed in 1.28.0 and 1.30.0.**
What remains is the unlock condition (wave 100 on Hack:AI specifically, which
needs a per-mode best wave) and the menu wiring to choose a level.

**This is the largest item in the list, and it is architecture before content.**
`WorldGeometry` is a Kotlin `object` — a singleton of hard-coded constants from
which the routes and all 79 deployment nodes are *derived*. Every system reads
it statically: the engine, the renderer, the transform, the save.

A second map means turning it into an interface with two implementations, and
the derivation is the good news — a new layout is a new set of constants, and
the nodes come out of it automatically, the same way the current map's do.

~~The trap to write down now: **`SavedRun` stores placements by node id**, and
node ids are positions in a per-map array.~~ **Closed in 1.30.0.** `SavedRun`
carries `mapId` and `modeId`, both defaulting to the original so saves already
on players' phones resume where they were played, and `continueGame` selects
the level and the mode *before* restoring — placements are node ids, so
restoring first and selecting after would scatter the board. An unknown id
falls back to the original map rather than throwing. Six tests, including one
that asserts the two maps genuinely disagree about what node 17 means, so the
rest of the file is not guarding against nothing.

Unlock: wave 100 **on Hack:AI specifically**, so the identity needs a per-mode
best wave rather than the single `highestWave` it keeps today. `GameMode`
already has `unlockAtWave`; this extends it to "on which mode".

The leaderboard already carries `modeId` per entry, so a third mode lands on the
board with no change.

### E2 ✅ DECIDED — the Hugging-Face bosses

**Owner's spec (2026-09-25). Four bosses, two families, different jobs.**

**Family 1 — the jammers.** `[○_○]` and `[●_●]`, the deliberately silly
hugging-face look.

| Boss | Jams | Range | Cadence |
| --- | --- | --- | --- |
| `[○_○]` | **[REDHAT] only** | ~100 units | once every 5s |
| `[●_●]` | **[BLUEHAT] only** | ~100 units | once every 5s |

*"they both cannot do both only the type I specified."* Each jams exactly one
hat agent type and is harmless to the other. That is the whole design: it makes
the cap of 4-and-4 a real decision rather than "bring eight of whichever".
Cool colours, or a rapid colour-spectrum cycle.

**Family 2 — the heavies.** `[₩₩₩]` and `[¥¥¥]`.

- **No jam at all** against hat agents.
- **~1.2x normal boss health.**
- Cool colour theme, and **size** is what reads as "this is a boss".

**Original note follows.**

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

### F2 ✅ shipped in 1.24.0 — the revive pack, and ten times the €

**Asked:** *"add an option in the store for '3 revives per run no ads for
revives ever - $4.99'. Also each pack should be 10x the amount of € given —
even for the skin packs and all buys that come with € included. makes it look
more valuable. Add in €5000 for each No ads after runs and No ads with 3 revive
per run packs."*

**`revive_pack`**, $4.99, permanent: three revives per run, no ad in front of
any of them, and €5,000. It sits next to REMOVE ADS in CONVENIENCE so the two
products with "ads" in them are read side by side, and every place either one
appears now says which ads it covers.

**Ten times the €, everywhere** — and this is the part that needed care. Ten
times the € *in the packs alone* would have been a tenfold buff to paying. So
the whole economy moved together: `Balance.BUDGET_SCALE = 10` multiplies what a
wave pays out and what a firmware level costs as well as what every pack
grants. €9,000 against the new curve buys exactly the levels €900 bought
against the old one — there is a test that asserts precisely that — so the
ratios a player experiences are unchanged and only the numbers are bigger,
which is what "makes it look more valuable" means.

| Product | € before | € now |
| --- | ---: | ---: |
| `budget_small` | 150 | **1,500** |
| `budget_medium` | 500 | **5,000** |
| `budget_large` | 900 | **9,000** |
| `core_skin_pack` | 200 | **2,000** |
| `bg_pack` | 200 | **2,000** |
| `starter_pack` | 200 | **2,000** |
| `no_ads` | — | **5,000** |
| `revive_pack` | — | **5,000** |

#### The three things that would have gone wrong quietly

**Existing saves.** A save written before the rescale carries € at a tenth of
the new scale, and leaving it there would have made every existing player ten
times poorer against the new firmware costs overnight. That is not a rounding
error, it is somebody's purchase. `GameRepository.migrateBudgetScale()`
multiplies the stored budget and lifetime total once and stamps the save;
running it twice would be as wrong as never running it, and a test runs it
three times.

**REMOVE ADS leaking into revives.** `STARTER_PACK` unlocks `no_ads`, so any
"ads removed implies revive ads removed" shortcut would have handed three
revives to a bundle that never claimed to sell them. Only a product that says
it covers revives covers revives, and `Entitlements.reviveAdsRemoved` is the
single place that is decided.

**A product nobody could buy.** `revive_pack` was in the catalog, in the
`RELEASING.md` table and on no screen. The store's sections are now data
(`STORE_SECTIONS`) and a test asserts every `Sku` is reachable from a screen —
it fails with the offending id named.

#### Still to do outside the code

`revive_pack` is a new Play Console product id and must be created before
release. `RELEASING.md` §2 carries the row and the revised € column.

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

### H1 ✅ shipped in 1.25.0 — a guided first run

**Asked:** *"During tutorial force player to add 2x Firewall and 2x tarpit.
explain and point arrow to wave count, and Crypto◇ ... make sure arrows in
tutorial text box point to the actual word 'Wave 1' and ◇120. also make sure the
skip button doesn't block text boxes, moves out of the way during tutorial, and
doesn't block 'Wave 1' and '◇120' when pointing to them. Possibly ask if they
want to know about enemy types and bosses..."*

Eleven cards instead of five, in `TutorialScript` — a table, not a `when` over
an integer, because the card, the arrow and the SKIP button all have to agree
about the current step.

**Forced placements.** Two FIREWALLs, then two TARPITs, and picking the wrong
agent says so rather than silently moving on. The count comes from the board,
not from a tally: place, sell, place again is one agent, and a counter
incremented per placement would have said two.

**Arrows that point at real things.** `WAVE 1` and `◇ 120` are drawn by
`BattlefieldRenderer` in world units; the card is Compose, in screen pixels.
`FieldStatusAnchors` is the bridge — the renderer draws its plate *from* those
rects and the tutorial aims at them, so the arrow cannot point at where the
readout used to be. Proved against pixels: change only the crypto number, and
every pixel that moves has to fall inside the box the arrow points at.

**SKIP gets out of the way.** Its home is the top right, which is exactly where
the readouts are — measured at 1530–1592 × 88–139, straight over the plate. On
the two pointing steps it moves to the bottom left, clear of the card (top
left), the readouts (top right) and the deploy panel (bottom centre). A test
walks every card and asserts SKIP is on screen at each one.

**The briefing** is offered, not imposed, and generated from `EnemyType
.codexEntry`, the `BossVariant` signatures and the agents' ability text rather
than written a second time — so a renamed threat or a new boss cannot leave it
quietly describing a game that no longer exists.

#### Three things found by building it

**The readout said WAVE 0.** A new player's first sight of the game was a wave
that does not exist, and the arrow could not point at the words "WAVE 1"
because they were not there. The HUD already showed `--` and the banner already
said PERIMETER READY; the readout was the only thing claiming a wave zero.

**The forced placements cost exactly the starting purse.** 2 × 40 + 2 × 20 =
120 = `STARTING_CRYPTO`, to the crypto. There is now a test on it, because agent
costs and the starting purse are edited by different people for different
reasons and a tutorial that asks for four agents the player cannot afford
cannot be finished.

**Skipping the tutorial did not always stick.** The flag is written
asynchronously, and a progress emission from before the write overwrote the
in-memory copy — so skip, lose, RETRY handed the tutorial straight back.

---

## I. Feel — ✅ shipped in 1.20.0

**I1** — bosses and elites now die in a blast of pixel shards in their own
colour, out to 700 / 500 units, with a shockwave ring and a white core. One
pooled effect carries a seed and the renderer derives every shard from it, so a
420-piece explosion allocates nothing. Detail in `CHANGELOG.md` [1.20.0].

---

## L. Studio ident — ✅ shipped in 1.26.0, fixed in 1.28.0

### L3 ✅ 1.29.0 — the wordmark is a drawable now

Two attempts at rendering it as monospace ASCII both looked perfect in the
test renderer and were unreadable on the owner's phone. The second attempt
measured the font at runtime and was *worse* than the first, because it made
the rows touch — and on that device touching rows smear, while the version
with gaps stayed legible.

That is the point at which chasing metrics was the wrong activity.
`res/drawable/hamieton_banner.xml` is the mark as **geometry**: one drawn `#`
per cell, so the ASCII look is kept exactly, with no font, no line height and
no per-line centring anywhere in it. It is identical at every size on every
screen, which is the only way a logo can be correct.

Generated by `tools/build_banner.py` from `DEVELOPER_BANNER`, so the drawable
and the accessibility label cannot come to describe different things.

### L2 ✅ CLOSED — the banner is fine

**Owner, 2026-09-25:** *"banner should be fine unless I specify on a later
build you let me test."* The vector-drawable wordmark from L3 stands. Reopen
only if a device test says otherwise.

**Original note follows.**

The block-ASCII logo sheared into an unreadable diagonal on hardware while
looking perfect in the test renderer. Two separate causes, and the second is
the one that matters:

1. The row spacing was a `lineHeight` derived from the ink height of `#` **in
   Robolectric's substitute font**. A device's monospace face has different
   metrics, so the rows overlapped.
2. `textAlign = Center` centres each line *independently*, and trailing spaces
   do not count toward a line's measured width — so every row of the banner
   was centred to a different width and the whole logo slid diagonally.

There is no constant that is correct on every device, because the number
depends on a font chosen at runtime. So nothing is guessed now: the banner is
drawn row by row onto a Canvas with `Paint.Align.LEFT`, at baselines one
measured ink-height apart, using the paint that is about to draw it. Neither
per-line centring nor a stale metric can affect it.

**The lesson, which is wider than this bug:** the test renderer substitutes
its own fonts. Anything whose correctness depends on font metrics cannot be
verified there, however convincing the preview looks — and a preview that
looks right is worse than no preview, because it stops you asking.



**Asked:** *"Need a splash page before menu upon loading the game that shows
the developer fading in and out. It should be ASCII in big format
'HamieTon.dev'."*

A publisher card before the boot screen: `HamieTon.dev` in block ASCII, faded
up over 0.6s, held still for 0.8s, faded down over 0.6s, then the existing
CyOps TD boot splash and the menu. Tappable to skip, because by the twentieth
launch an ident is between the player and the game.

The boot splash lost its own small `HAMIETON-DEV` mark in the same change —
the studio now has a screen to itself, and signing the game twice in four
seconds is worse than signing it once.

**What the work actually was: getting ASCII to render as a logo.** Three
separate things had to be measured rather than guessed, and each of them made
the banner unreadable on its own.

- **The theme gives body text `letterSpacing = 0.3.sp`.** Right for prose,
  fatal for a grid: a third of a point added to every cell smears the
  letterforms. Set to zero here.
- **Compose's default leading pushed the rows apart** (or overlapped them),
  so the block read as scattered punctuation. `includeFontPadding = false`
  plus a trimmed `LineHeightStyle` makes `lineHeight` mean what it says.
- **A `#` inks exactly 0.71 of its point size** in the platform monospace
  face — measured with `Paint.getTextBounds`, not estimated — so that is the
  line height at which the blocks touch and form solid letters. The font's
  natural leading of 1.172 leaves visible gaps.

The type is scaled to the screen rather than set in `sp`: the banner is 70
columns wide and wrapping it would turn a logo into wreckage.

The block is also given the plain-text name as its accessibility label, since
a screen reader handed the raw art reads out pipes and hashes — and that label
is the only thing a test can check to prove the banner still says what it is
meant to say.

---

## M. Menu boot sequence

### M1 ❌ WITHDRAWN PERMANENTLY — the menu power-on

**Reconfirmed 2026-09-25:** *"definitely drop the animation and dont ship it on
later builds."* Not a deferral. The code is gone and must not come back.

**Original note follows.**

Shipped in 1.27.0 and **removed in 1.28.0 at the owner's request**: *"the main
menu looks nothing like a server boot or pc or anything similar. icons just
flash in lol. forget the menu flash cut all that shit out."*

Worth keeping the entry rather than deleting it, because the failure is
instructive. The spec asked for something that reads as *hardware coming up*,
and what got built was elements fading in on a stagger. Every individual beat
matched the description — dark, an LED, a flicker, a settle — and the whole
thing still read as a UI animation, because staggered opacity is what a UI
animation is made of whatever order you put it in. Nothing in the tests could
have caught that: they asserted the beats existed and held, which they did.

`MenuBoot`, the power LED, the per-element reveal and the MENU INITIALIZATION
setting are all gone. What replaced it is M2.

### M4 ✅ shipped in 1.31.0 — menu music eases in

**Reported on 1.28.0:** *"the music starts from app open. delay music for 5
seconds then slowly introduce it by scaling volume from 0% → 100% (of volume
modifier set in settings for music — do not bypass music modifier in settings).
this is just for startup to slowly introduce the music."*

Right, and the timing is worse than it looks on paper. Start-up runs the studio
ident (2.0s, carrying the boot chime) and then the boot splash (1.9s), so the
menu appears at about 3.9s — which means the music currently lands **a fraction
of a second after the boot chime finishes**, at full volume. Two pieces of audio
back to back with no gap reads as one of them interrupting the other.

#### The shape

- **Five seconds of silence from app open**, then a fade up. That puts roughly
  three seconds between the chime ending and the music starting, which is the
  gap the startup is missing.
- **Fade to the player's setting, not past it.** The ramp is a *multiplier* on
  `GameSettings.musicVolume`, so 0% → 100% means 0 → whatever they chose. A
  player who set music to 20% gets a fade that ends at 20%, and one who set it
  to zero hears nothing at any point in the fade. This is the part to get right
  — the obvious implementation ramps an absolute volume and quietly overrides
  the setting, which is the same bug as bypassing it outright.
- **Startup only.** Coming back to the menu from a match must not re-fade; that
  would make every trip through the menu feel like the app relaunching.

#### The details that will bite

- **The slider has to stay live during the fade.** If a player opens SETTINGS
  and moves music while the ramp is running, the ramp must track the new
  setting rather than finish to the old one. That falls out naturally if the
  ramp is stored as a fraction and multiplied at the point `setVolume` is
  called, and does not if the ramp writes an absolute level.
- **`MusicEngine` already applies `MUSIC_TRIM` (0.55)** on top of the setting.
  The ramp is a third multiplier, not a replacement for either.
- **Backgrounding during the fade.** 1.29.0 made pausing silence everything;
  resuming should not restart the five-second wait. The fade belongs to the
  launch, and the launch already happened.
- **Do not delay the boot chime with it.** That is a separate sound with its
  own moment and should still fire on the ident.

Done: five seconds of silence, then a four-second rise.
`AudioEngine.startupRampAt` is a plain function of elapsed seconds so the shape
is asserted rather than watched, and the ramp is applied as a multiplier
wherever the level is set — so moving the slider mid-fade tracks immediately,
and a player at zero hears nothing at any point in it. Driven off
`elapsedRealtime`, so backgrounding mid-fade and returning does not restart the
wait.

### M3 ✅ fixed in 1.29.0 — music kept playing with the app in the background

**Reported:** *"when app is minimized the music keeps going — this will be
reported as a bug not a feature."*

Correct on both counts, and it was worse than described. `onAppPaused()`
called `audio.setInMatch(false)`, which does not stop anything — it is the
"you have left a match, play the menu track" path. So backgrounding the game
mid-run **switched to the menu music and kept playing it** over whatever the
player had opened instead.

Two lines, and the second is the one that was hiding:

- Pausing now calls `stopMusic()`, which silences every track and the boot
  chime, and deliberately leaves `inMatch` alone so the game still knows which
  screen the player left.
- Resuming used to restart music *only if a match was running*, so
  backgrounding at the menu and coming back left it silent. It now resumes
  whichever screen they were on.

**Why nothing caught it:** nothing in the suite could answer "is the game
making noise right now" — the state lived inside two `MediaPlayer`s. It is a
property now (`AudioEngine.musicWanted`), and reverting either line fails
three tests.

### M2 ✅ shipped in 1.28.0 — the machine comes up, on the soundtrack

**Asked:** *"add in a sound that sounds like a Windows 95 pc booting up while
the menu loads in."*

The owner supplied the audio, which settles the obvious question: the Windows
95 startup sound is Microsoft's, and imitating it closely enough to be
recognisable would be the same problem wearing a different hat. Their file
ships as `res/raw/boot_chime.wav` and plays once per launch over the studio
ident.

**It is the only audio file in the game.** Everything else is synthesized —
no licensing surface, nothing to ship — so this is a deliberate exception,
noted here because the next person to look will wonder why `res/raw` exists
at all. It plays through its own `MediaPlayer` rather than the effect pool:
two seconds at 48kHz is far too long for a `SoundPool`, which is built for
70-millisecond blips.

#### The bug found while wiring it up

**Menu music has never played on a fresh launch.** Not since it was added in
1.11.0. `setInMatch` early-returns when the state has not changed, and at
launch it has not — `inMatch` starts false — so nothing ever asked the menu
track to start. It only ever began after a player had been into a match and
come back out. `AudioEngine.enterMenu()` now exists and every arrival at the
menu calls it, including the first.

---

## N. Screen sizes

### N1 ◐ **NECESSITY** — every screen must work on every phone *(audit landed 1.32.0)*

**Asked:** *"check all elements of the game so it will function on any screen
size as well. add this as a necessity to backlog."*

Marked a necessity rather than an improvement, and it is the right call: this
is a **release blocker**, not polish. Google Play lists a device catalogue in
the thousands and a layout that breaks on a narrow phone is a one-star review
from someone who never got to play.

**The splash proved the risk is real.** It looked correct in every preview
captured here and was unreadable on the owner's actual phone, twice. Anything
this audit checks by rendering in the test environment is checking something
the device does not necessarily do.

#### What was built (1.32.0)

Two tests, because there turned out to be two entirely different questions.

**`ScreenSizeTest`** renders every screen a player can reach — main menu,
store, agents, firmware, loadout, settings, codex, statistics, about,
leaderboard, the boss dossier, the pause overlay, the game-over summary and a
live match with the roster open — at eight viewports: 568×320, 640×360, 800×400, 900×380,
1000×460, 1280×800, plus 640×360 at font scales 1.3× and 2×. 128 cases. It
fails naming anything laid out past the edge that no scroll can reach.

**`WorldFitTest`** answers the battlefield, which is not Compose at all: a
fixed 1600×760 world letterboxed by `WorldTransform`. Because that is pure
arithmetic it does not sample device sizes and hope — it sweeps eighty
viewports from square to 3:1 and asserts the world lands inside all of them,
the aspect is preserved, the letterbox bars are even, and a tap round-trips to
the point it was drawn from.

#### What it found

The Compose screens were already clean at every size, including 2× text —
every one of them is built around a scroll column, which is what saved them.

The fault was on the battlefield, and it is one no layout check could have
seen. **The perimeter map generated fourteen pairs of deployment nodes 26
world units apart** — two circles of radius 26 drawn almost entirely on top of
each other. Nothing clipped, nothing overflowed. Letterboxed onto a 568×320dp
phone those two nodes sit **9dp** apart: one fingertip covers both and the
player gets whichever the arithmetic preferred.

Fixed in the derivation rather than by nudging the candidate rows, so a future
map cannot reintroduce it: `WorldGeometry.MIN_NODE_SPACING` drops a candidate
stacked on one already taken, keeping whichever of the two covers more route.
Perimeter went from 81 nodes to 67; Hugging-Face was already clean at 62.

That change moves node ids, and node ids are save keys, so `SAVE_VERSION` went
to 2 and `SavedRun.isRestorable` now checks it. `version` had been written
since the first build and read by *nothing* — the field meant to stop a stale
save being misread could not stop anything.

#### What is honestly still open

- **A real phone.** Font metrics differ here; the splash proved that twice.
  Nothing in this audit replaces installing the APK on a device.
- **Node density on the smallest screens.** Nodes are now ≥56 world units
  apart, which is ~20dp on a 568×320dp phone. That is better than 9dp and
  still under Material's 48dp guidance, and it cannot be fixed by enlarging
  the tap radius: nearest-node already wins every tap inside it, so a bigger
  radius only eats the empty-space tap that clears a selection
  (`WorldFitTest` pins that ceiling). Fixing it properly means a sparser grid
  on small screens, or pinch-to-zoom on the board. **Owner's call** — it is a
  design change, not a bug fix.

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


1. ~~A1, A2, A3~~ — ✅ 1.18.0 and 1.30.0.
2. ~~D1~~ — ✅ 1.19.0, together with all of §G.
3. ~~C1~~ — ✅ 1.21.0. B1, C2 and E2 are unblocked.
4. ~~B1~~ — ✅ 1.22.0.
5. ~~F1~~ — ✅ 1.23.0.
6. **D2** — RH/BH. **Blocked on the owner:** the four guard rails in §D2 need
   a pick before this can be built. Skipped rather than stalled on.
7. ~~F2~~ — ✅ 1.24.0.
8. ~~H1~~ — ✅ 1.25.0.
9. ~~M1~~ — ✅ 1.27.0.
10. **E1** — the map layer. Largest, and worth its own version.
10b. **N1** — the screen-size audit. A release blocker rather than a feature,
    so it comes before anything else ships to the store.
11. **E2** — the AI bosses, last, because they need C1, D2 and E1.
12. **F3** — the two-device verification, once there is a Play Console.

Backgrounds (§F) can slot in anywhere; they touch nothing else.

Ship as a new version when a group lands, not per item — the owner asked for
this build to be pushed as a new version once the agents are in.

---

## O. Production Google Play release with AdMob rewarded ads

### O1 ◐ **RELEASE BLOCKER** — 2026 Play requirements, UMP consent, release pathway *(landed 1.33.0; owner input required to finish)*

**Asked, verbatim spec (2026-09-24).** The owner supplied a 23-point brief.
Summarised here; the numbering below is the owner's so nothing gets lost.

> "We are preparing CyOpsTD / Packet Bastion for a production Google Play
> release with Google AdMob rewarded advertising. Do NOT redesign or remove
> the existing revive system."

**Also said, and it corrects a mistake of mine:** *"even the Samsung S9 was
2960 x 1440 pixels. youre focusing on the wrong things now."* The N1 sweep was
in **dp**, not pixels — an S9 is roughly 845×411dp in landscape, so 640×360dp
was a real budget-phone window and not an absurd one. But the point behind it
stands: N1 is done, further screen-size work is not where the release risk is,
and this section is.

#### Intended revive behaviour — already built, must not regress

Audited before touching anything, and the existing implementation already
matches every line of the brief:

| Requirement | Where it lives | State |
| --- | --- | --- |
| Voluntary "WATCH AD TO CONTINUE" | `GameOverOverlay`, offered only when `canReviveNow` | ✅ |
| Rewarded format only | `AdGateway.showRewarded`, a separate unit from the interstitial | ✅ |
| One revive per run | `Balance.REVIVES_PER_RUN = 1`, checked in `canReviveNow` | ✅ |
| Resume the same wave | `GameEngine.reviveRun()` steps `currentWave` back so the wave is rebuilt | ✅ |
| Core to 50% | `Balance.REVIVE_INTEGRITY_FRACTION = 0.5f` | ✅ |
| Agents and currency preserved | `reviveRun()` clears enemies/projectiles/effects only | ✅ |
| Never soft-lock | every path through `AdMobGateway` calls back exactly once | ✅ |
| Grant only from the reward callback | `earned` is latched in `onUserEarnedReward` and read on dismissal | ✅ |
| User-initiated only | nothing calls `watchAdToRevive()` but the button | ✅ |

The REVIVE PACK (§F2) raises the allowance and skips the ad for someone who
paid to be rid of it. That is a purchase, not a second free revive, and it
stays.

#### What the brief actually adds

1. `compileSdk` / `targetSdk` **36**. Needs an AGP bump; 8.7.3 does not know
   API 36.
2. Confirm `minSdk 24` against the current Mobile Ads SDK.
3. Current stable Mobile Ads SDK (was 23.6.0).
4. App ID and rewarded unit ID kept **separate** and clearly documented.
   `ca-app-pub-…~…` is the app; `ca-app-pub-…/…` is the unit. Already
   separate; the documentation is what is missing.
5. **Debug builds must always use Google's test rewarded unit**
   `ca-app-pub-3940256099942544/5224354917`, never a production one.
6. Release builds take the rewarded unit from `cyops.admob.rewardedId`.
7. **UMP (consent) SDK** — none today. Refresh at startup, show the form when
   required, survive errors, expose a Privacy Options entry from Settings when
   Google says one is required, and request ads only when consent allows.
8. Manifest audit: app-id metadata, network permissions, `AD_ID`, merged SDK
   permissions, nothing the game does not need.
9. Rewarded lifecycle — mostly built; the gap is the debug logging in (11).
10. One revive per run, surviving Activity recreation, reset on a new run.
11. Debug logging: `AD_LOAD_STARTED`, `AD_LOADED`, `AD_LOAD_FAILED`,
    `AD_SHOW_STARTED`, `AD_SHOW_FAILED`, `AD_DISMISSED`, `REWARD_EARNED`,
    `REVIVE_GRANTED`. No user data in logs.
12. Release config: not debuggable, no dev menus, no test-only controls, **no
    test ad ids**, API 36, builds as an `.aab`, APK still available locally.
13. **Signing audit.** No keystores or passwords in source control.
    *Today's build has a keystore password in `app/build.gradle.kts`.* That is
    the finding, and it must be fixed.
14. `versionCode` an incrementable integer.
15. Four documents: `PLAY_STORE_RELEASE.md`, `ADMOB_SETUP.md`,
    `PRIVACY_AND_DATA_SAFETY.md`, `RELEASE_CHECKLIST.md`.
16–19. What each document must cover.
20. Full test suite, plus tests for the rewarded-revive state machine.
21. Debug APK on the test rewarded unit.
22. Release AAB, failing safely or using explicit placeholders rather than
    inventing production ids.
23. A completion report listing every id, path and remaining blocker.

**On versionName.** The brief says *"Preserve versionName 1.23.0 unless a
version bump is required by the existing build history."* It is: the build
history is at 1.32.0 and Play refuses a `versionCode` it has already seen.
1.23.0 shipped as versionCode 27.

#### Standing constraints

*"Do not make unrelated gameplay or visual changes. Do not remove existing
functionality. Run tests before declaring the release ready."*

#### What landed in 1.33.0

| # | Requirement | State |
| --- | --- | --- |
| 1 | `compileSdk` / `targetSdk` 36 | ✅ via AGP 8.13.2 + Gradle 8.13 |
| 2 | `minSdk` vs the Ads SDK | ✅ 24, above the SDK's own floor |
| 3 | Current Mobile Ads SDK | ◐ 24.5.0, **not** 25.x — see the note below |
| 4 | App id separate from unit id | ✅ separate properties, documented in ADMOB_SETUP.md |
| 5 | Debug always uses the test rewarded unit | ✅ set per build type, not a flag |
| 6 | Release uses `cyops.admob.rewardedId` | ✅ |
| 7 | UMP consent | ✅ startup refresh, form when required, errors survived, PRIVACY OPTIONS in Settings, SDK not initialised until consent allows |
| 8 | Manifest audit | ✅ four declared permissions, each justified in the file; SDK-merged ones inventoried in PRIVACY_AND_DATA_SAFETY.md |
| 9 | Rewarded lifecycle | ✅ was already correct; logging added |
| 10 | One revive per run, across recreation | ✅ **was broken** — see below |
| 11 | Debug logging vocabulary | ✅ all eight events under tag `CyOpsAds` |
| 12 | Release configuration | ✅ not debuggable, minified, no test ids, builds as `.aab`, APK still available |
| 13 | Signing audit | ✅ **found a password in the build file**; now read from outside source control |
| 14 | Incrementable `versionCode` | ✅ 37 |
| 15–19 | Four documents | ✅ |
| 20 | Tests | ✅ 580 green, including a new rewarded-revive state machine suite |
| 21 | Debug APK on the test unit | ✅ |
| 22 | Release AAB | ✅ builds; **unsigned and ad-free** without the owner's ids and key, by design |

#### Two things the audit actually found

**A revive could be spent twice.** "One per run" lived only in the ViewModel.
A rewarded ad puts its own Activity in front of the game, which is precisely
when Android is most willing to kill what is behind it — so the rule was really
"one per process". The count is now written into the saved run the instant a
revive is granted, and `RewardedReviveStateMachineTest` kills and rebuilds the
ViewModel to prove it survives.

**A keystore password was in a committed file.** `app/build.gradle.kts` carried
`storePassword = "cyopstd"` for the local self-signed dev key. Throwaway key,
real password in source control. Signing now reads four values from Gradle
properties, an untracked `keystore.properties`, or the environment.

#### Why the Ads SDK is 24.5.0 and not 25.5.0

25.x resolves and downloads fine, and then fails to compile: its artifacts
carry Kotlin **2.3** metadata and this project is on Kotlin 2.0.21. Adopting it
means a Kotlin upgrade, which drags the Compose compiler with it — a separate
piece of work with its own risk, and not something to bundle into a release
change. 24.5.0 is current, supported, and satisfies every requirement in the
brief.

#### Still blocked on the owner

None of these can be done from this repository:

- AdMob **application id**, **interstitial unit id**, **rewarded unit id**.
- An **upload keystore**. Release builds are currently unsigned.
- A **consent message published in the AdMob console** — UMP shows whatever is
  published there, and shows nothing if nothing is.
- Play Console products, `revive_pack` included.
- A **privacy policy URL**, mandatory because the app shows ads.
- The closed test: **12 testers, 14 continuous days**, which is a calendar
  delay rather than a work item.

---

## P. Pinch-to-zoom, and the content/legal audit

Logged 2026-09-24 from a twelve-part owner brief. The owner's numbering is kept
so nothing gets lost.

### P1 ✅ Battleground pinch-to-zoom *(1.34.0)*

**Asked:** *"I agree that pinch-to-zoom is the better solution. IMPLEMENT
PINCH-TO-ZOOM ON ALL SUPPORTED SCREEN SIZES. However, zooming/panning must
apply ONLY to the actual battleground/playfield."*

Closes the open question from §N1. Nodes are ~20dp apart on the smallest
screen, and no tap radius can separate targets that close — a bigger radius
only eats the empty-space tap that clears a selection.

**Must zoom:** the battlefield canvas only.
**Must NOT zoom:** the top banner, integrity readout, wave readout, crypto
readout, pause/settings, the agent roster and its cards, the control bar, and
anything else that is HUD rather than ground.

Requirements, verbatim in substance:

- two-finger pinch, smooth; pan while zoomed
- **no accidental placement, selection or deselection during a pinch** —
  including the second finger landing, and a pinch that begins or ends over a
  node
- single-tap placement and nearest-node behaviour unchanged when not zooming
- stable world coordinates at every zoom level; agents stay on their nodes;
  enemies, projectiles and effects stay aligned
- sensible min/max: the whole board readable at minimum, nodes comfortably
  tappable at maximum on a small phone
- pan clamped so the board cannot be lost off-screen
- double-tap to reset **only if** it does not conflict with existing
  interaction — it does need checking, single-tap is placement
- not solved by changing global Android display scaling

### P2 ✅ Tutorial — core server integrity *(1.34.0)*

**Audit first.** Done: the tutorial has eleven steps and **none of them says
what the Core Server is, where its integrity is shown, or that zero integrity
ends the run.** The closest are "stop them before they land" (step 1) and "for
as long as you hold the server" (step 2), which assume the player already knows.

So this is a real gap, not a redundant page.

### P3 ✅ Game over terminology — reported, unchanged, owner's call

**Audit first.** Done. Current wording is already themed:

> **NETWORK COMPROMISED**
> CORE-SERVER INTEGRITY 0 · CONNECTION TERMINATED

The brief says explicitly not to blindly replace themed text and to report it
for a decision. So: report, change nothing, unless the owner picks
"SERVER BREACHED — GAME OVER".

### P4 ✅ About section — rewritten *(1.34.0)*

An About screen exists and is reachable from the main menu. But its "WHAT THIS
GAME DOES NOT DO" panel was written for the offline build and the monetisation
work has made five of its seven claims false:

| Claim on screen | Actually |
| --- | --- |
| "No internet connection — the app has no INTERNET permission" | it has INTERNET |
| "No account, no login, no cloud save" | optional Play Games cloud save |
| "No advertisements" | interstitial + rewarded |
| "No in-app purchases or subscriptions" | a full store |
| "No analytics or telemetry" | the ads SDK collects |

This is the most serious single item in the brief. A store listing whose own
About screen denies that the app shows ads is a Data Safety contradiction, and
it is in front of the player rather than buried in a document.

Also to add: game title, version (already there), developer identity, a short
development/educational statement, the AI disclosure (P5), the trademark notice
(P6), and a copyright line (P9).

### P5 ✅ AI-assisted development statement *(1.34.0)*

Owner supplied wording. Constraints: must not imply the game was autonomously
generated, must not imply any AI company sponsors/endorses/owns/publishes it,
no third-party logos, plain-text factual product references only.

**One discrepancy to settle:** the supplied wording says *"while learning
Python"*. This project is Kotlin/Android and contains no Python beyond two
build helper scripts in `tools/`. Putting "Python" on the About screen of a
Kotlin game is inaccurate in a statement whose whole purpose is accuracy, so
the wording will say "programming and software development" and the owner can
override. Flagged in the completion report.

### P6 ✅ Trademark / third-party terminology notice *(1.34.0)*

Owner supplied wording. Hard constraints, all of which are about *not*
overclaiming:

- **Do not claim a trademark licence.** No evidence of one in the repository —
  checked.
- Do not assert who owns "Blue Hat" without verifying the specific mark.
- No third-party logos, no imitation of Red Hat branding or trade dress.

**Audit result:** `[REDHAT]` and `[BLUEHAT]` appear **only in this backlog**
(§D2, still unimplemented and blocked on the owner). Neither string exists
anywhere in `app/src/main`. So there is nothing to rename today — but the
notice should go in now, and §D2 must be revisited before those units ship.
Red Hat's own guidance is two words, "Red Hat".

### P7 ✅ Agents section — real-world / in-game split *(1.34.0)*

**Audit first.** Done. Eleven agents, each already carrying two fields:
`abilitySummary` (in-game) and `codexEntry` (real-world). The data is mostly
there; two problems:

1. **The Agents screen shows only `abilitySummary`.** The educational half
   exists and is not displayed on the screen the brief is about.
2. Several `codexEntry` lines blend the two — "It sees further than anything
   else you can deploy" is gameplay inside a sentence about what an IDS is.

So: separate the two cleanly, show both, and audit each for accuracy. No new
agents — the brief says not to create duplicates to satisfy documentation.

### P8 ✅ Terminology accuracy audit *(1.34.0)*

Across agent names and descriptions, tutorial, store, upgrades, menus, About,
codex, game over and docs. Looking for anything implying real hacking
capability, real cryptocurrency value, corporate affiliation, official
certification, or sponsorship.

Known already: ◇ Crypto is disclaimed on the About screen today and that
disclaimer must survive the P4 rewrite.

### P9 ✅ Copyright line *(1.34.0)*

Developer identity established in the project is **`HamieTon.dev`**
(`DeveloperSplashScreen.DEVELOPER_NAME`). No `LICENSE` file exists; `LICENSES.md`
covers third-party dependencies and states first-party content is original.
README says the same. Nothing declares an open-source licence for the game
itself, so "All rights reserved" does not contradict anything. Year computed at
runtime rather than baked in. Do not invent a legal entity.

### P10 ✅ Responsive UI testing after zoom *(1.34.0)*

The owner listed twenty-odd interaction cases: rapid switching between adjacent
nodes, pinch with and without a selection, pinch beginning and ending over a
node, pan at every boundary, zoom during motion and animation, pause/resume,
background/resume, losing and restarting while zoomed. Required outcome: no
phantom or duplicate placements, no stuck touch state, no coordinate drift, no
HUD or agent-menu scaling, no crash, no slowdown.

### P11 ✅ Tests and documentation *(1.34.0)*

Minimum: world↔screen transforms, zoom bounds, pan bounds, selection under a
transform, touch cancellation when a pinch starts, tutorial strings, About
navigation, agent-index data integrity. Update existing docs rather than adding
duplicates.

### Standing constraints on all of §P

*"Do not make unrelated gameplay balance changes. Do not remove existing
features. Do not rename working units without a clear reason. Do not redesign
the overall HUD. Do not alter monetization behavior."*

---

### §P outcome (v1.34.0)

**Pinch-to-zoom.** Zoom lives in `WorldTransform`, which is the single place a
world coordinate has ever become a screen coordinate in this project. That is
the whole design: a `graphicsLayer` transform would scale the pixels the
renderer already drew and leave hit-testing to be corrected separately, which
is exactly how a zoomable board ends up placing agents a finger's width from
where they were tapped. Here drawing and hit-testing are the same expression
evaluated twice, so they cannot disagree.

- **1.0× to 3.0×.** Minimum *is* the fitted board, so "the whole battlefield is
  understandable at minimum zoom" holds by construction. Maximum was derived
  from the problem rather than picked: nodes are ≥56 world units apart, the
  smallest supported window (568×320dp) fits at 0.355, and 56 × 0.355 × z ≥ 48
  needs z ≥ 2.4. Three gives headroom.
- **Pan is clamped by geometry**, not by a rule: the allowed slack is how far
  the drawn board overflows the viewport, which is zero when it does not
  overflow. The board cannot be lost off-screen because there is nowhere to
  lose it to.
- **One gesture loop**, not a tap detector racing a transform detector. A
  gesture that ever had two fingers in it can never be a tap.
- **No double-tap reset.** A double-tap detector must hold every single tap for
  the double-tap timeout before delivering it, and a single tap is a placement
  — the most common action in the game. Pinching out below 1.04× snaps to
  exactly fitted instead, which is a reset with no cost to anything else.
- **Zoom is view state**: on the view model, not the engine and not the save.
  A test asserts `SavedRun` carries no field that looks like it.

**Game over wording — unchanged, and the owner's call.** The screen already
reads **NETWORK COMPROMISED** over **CORE-SERVER INTEGRITY 0 · CONNECTION
TERMINATED**. The brief said not to blindly replace themed text, so nothing
was replaced.

**One HUD word changed.** The integrity readout said `HP 100 / 100` while
every other surface — game over, About, boss dossier, and now the tutorial —
called it integrity. It now reads `INTEGRITY 100 / 100`. One word, not a
redesign, and it is what the tutorial teaches by name.

**Ambiguous terminology found and handled** (§P8): "zero-day hunter" and "root
admin" are informal and now say so; "quantum" conflates quantum key
distribution with post-quantum cryptography, and the entry separates them;
CRYPTOGRAPHER implied encryption could be broken and now states the fictional
licence outright. `[REDHAT]`/`[BLUEHAT]` (§D2) still exist only in this
backlog — when they ship, Red Hat's guidance is two words and the hat-colour
sense must be the one explained.

---

## Q. Encryption of account-linking and cloud-save data

### Q1 ✅ Audited and hardened *(1.34.0)*

**Asked:** *"make sure that the user data used to link account and save to
cloud is encrypted as per google play policy."*

**Audited, and the position was already sound.** The finding worth recording is
*why*, because it is structural rather than a setting:

- **The app opens no connections of its own.** No HTTP client, no socket, no
  URL anywhere in `app/src/main`. Every byte that leaves the device goes
  through a Google SDK — Play Games Services for linking and cloud save, Play
  Billing for purchases, Mobile Ads and UMP for advertising — and each is TLS
  to Google. There is no code path that *could* send cleartext.
- **Account linking never touches a credential.** The app does not implement
  sign-in; it hands off to `GamesSignInClient` and receives a boolean and a
  display name. No password, token, session or refresh token exists in this
  codebase to encrypt or leak.
- **The callsign cannot hold personal data.** It is sanitised to `A–Z`, `0–9`,
  `_`, `-`, capped at 16 characters, on the way in. An email or a phone number
  does not survive it. That was done for the monospace UI; the privacy
  property is a side effect worth keeping.
- **At rest:** app-private DataStore on a file-based-encrypted partition
  (mandatory since Android 10); the Play Games snapshot on Google's
  infrastructure; Auto Backup encrypted with a client-side secret from the
  device lock screen on Android 9+, which Google itself cannot read.

**What changed.** `usesCleartextTraffic="false"` is now declared explicitly on
the application. It changes no behaviour — it is already the platform default
at targetSdk 28+ — but it turns "encrypted in transit" from an inherited
default into a property of the built artifact that can be read back out of it,
which is what Play's Data Safety form is actually asking about. It also makes a
dependency that ever permits cleartext fail the manifest merge instead of
winning it silently.

**Deliberately NOT done: encrypting the local DataStore.** Play's User Data
policy requires secure *transmission*, not encryption of app-private files at
rest, and the platform already encrypts the partition. Jetpack Security would
add a hardware-keystore dependency, a migration for every existing install, and
a failure mode where an invalidated key destroys a player's whole progress —
real risk, taken on behalf of game statistics and a sixteen-character handle.
**Revisit if anything genuinely sensitive is ever stored.**

**Verification added.** `DataEncryptionTest` asserts the no-raw-networking
guarantee, that no credential is stored, that the callsign rejects personal
data, and that the cloud payload carries no entitlements or settings. It was
checked against an injected `HttpURLConnection` to confirm it can fail.
`tools/verify-release.sh` (renamed from `verify-release-ads.sh`) now also reads
cleartext and debuggable state out of the compiled manifest of the built APK,
and was confirmed to fail on the debug APK.

**One trap recorded:** the *merged text* manifest carries source comments
through, so a grep of it for a cleartext declaration reported one that was only
the wording of a comment explaining the rule. Check the compiled binary
manifest via `aapt2 dump xmltree`, not the merged text.

---

## ♡. Music — real tracks replace the generated ones

### ♡1 ⚠️ **BLOCKED ON A RIGHTS ANSWER** — three supplied tracks

**Asked (2026-09-25):** three MP3s supplied, one per level.

| Level | File | Size |
| --- | --- | --- |
| First level (Perimeter / standard) | `matrix (bl studio loop) (slowed) [Remix]` — **on loop** | 3.4 MB |
| Hack:AI | `Falling [Remix]` | 4.5 MB |
| Hugging-Face | `VXLLAIN, iGRES, ENXK — Promise me the Sk… [Remix]` | 4.5 MB |

This closes the music thread that has been open since the generated chiptune
work was set aside: *"skip the track for now I'll find another solution for
music."* `AudioEngine.trackForMode()` is the single place all three redirect
through, exactly as the earlier note said it would be.

#### The blocker, stated once

All three filenames name identifiable commercial recordings and artists, and
all three are labelled "Remix". The ID3 tags have been stripped by an ffmpeg
re-encode, so the files carry no licence information of their own.

Two separate exposures, and the second is the sharper one:

1. **Shipping them on Google Play.** Publishing a recording you do not hold
   rights to is copyright infringement and a Play policy violation. The
   consequence is not a warning — it is a takedown, and repeated strikes end
   developer accounts.
2. **Committing them to the repository.** `HamieTon-Dev/ACII_TD` — if that
   repo is public, committing the MP3s *is itself publication*, before the
   game ships at all.

**So the files are deliberately NOT committed.** Nothing about this is a
judgement on the owner's taste — the tracks fit the game well. It is that the
one question that decides whether they can ship has not been asked yet.

#### What unblocks it

Any one of:

- a licence or written permission covering game use for each track;
- the tracks being the owner's own work, or a friend's with permission;
- replacement with royalty-free / CC-licensed music, or commissioned audio.

Until then: the music system is being built **file-based and swappable**, so
the moment cleared audio exists it is a drop-in. A **local test APK** with the
supplied tracks can be built for the owner's own evaluation, which is not
distribution — but that build must not be uploaded anywhere.

### ♡2 ⬜ File-based music architecture

Independent of the rights question and worth doing either way. Today every
sound is generated at runtime and the APK ships no audio files, which is why
`res/raw` does not exist yet. Needs: the raw resources, a loop-aware player
(the first track is specified **on loop**), the existing music-volume setting
and the 5-second startup fade honoured, and `trackForMode()` pointed at files
instead of the composer. Roughly +12 MB to the APK.
