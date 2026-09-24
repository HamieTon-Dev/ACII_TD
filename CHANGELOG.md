# Changelog

All notable changes to CyOps TD.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/).

---

## [1.34.1]

### Changed — cleartext networking is now refused outright

The app has never opened a connection of its own: account linking, cloud save,
purchases and ads all go through Google's SDKs over TLS. That is now stated in
the manifest rather than inherited from the target SDK, so "encrypted in
transit" is a property of the built app that can be checked rather than a
default that happens to apply.

### Changed — the store no longer sells ad removal in a build with no ads

REMOVE ADS is hidden when no AdMob ids are configured. Nothing changes once
they are.

### Added — release verification covers more than ads

`tools/verify-release.sh` (was `verify-release-ads.sh`) now also reports
whether the built artifact permits cleartext traffic and whether it is
debuggable, read from the compiled manifest.

---

## [1.34.0]

### Added — pinch to zoom the battlefield

Two fingers zoom the board, and drag pans it once you are in. The HUD strip,
the integrity and wave readouts, the agent roster and the control bar do not
move or scale — only the battlefield does.

This closes the small-screen problem the 1.32.0 audit found: on a 4-inch phone
held sideways, adjacent deployment nodes sit about 20dp apart, which no tap
radius can separate. Zoomed all the way in they are comfortably over the 48dp
a fingertip needs.

- Zoom runs from the fitted board to three times it. Fully out is the whole
  battlefield; pinching out the last little way snaps exactly back to it.
- The board cannot be dragged off screen.
- A pinch never places an agent, never selects a node, and never clears a
  selection — including when a finger lands or lifts directly on a node.
- Zoom survives pausing and backgrounding the app, and resets on a new run.

There is no double-tap to reset, deliberately: a double-tap detector has to
hold every single tap for the double-tap timeout before delivering it, and a
single tap is how you deploy.

### Fixed — the About screen was telling players the game has no ads

It carried a panel headed WHAT THIS GAME DOES NOT DO which promised no
advertisements, no in-app purchases, no cloud save and no internet permission.
Every line was true when it was written and false once monetisation shipped.
What is shown now is read from the build, so it cannot claim otherwise.

### Added — the tutorial now explains what you are defending

It ran for eleven cards without ever saying what CORE-SERVER is, where its
integrity is shown, or what happens at zero. A new second card does, before
the player is asked to do anything.

The integrity readout also now says INTEGRITY rather than HP, matching what
the tutorial teaches and what every other screen already called it.

### Added — the agent roster teaches the real concept as well as the unit

Every agent now carries a REAL-WORLD explanation and an IN-GAME one, shown
separately. They used to be one paragraph in which a beginner had no way to
tell which sentence was true of the world: "An Intrusion Detection System
watches network activity for suspicious behaviour and raises the alarm. It
sees further than anything else you can deploy."

Several entries were corrected in the process. CRYPTOGRAPHER implied real
encryption could be broken and now says plainly that it cannot. QUANTUM
DEFENDER conflated quantum key distribution with post-quantum cryptography,
which are different things. ZERO-DAY HUNTER and ROOT ADMIN now say they are
informal terms rather than job titles.

### Added — About: AI assistance, trademarks, copyright

An AI-assisted development statement, a trademark and third-party notice, and
a copyright line using the year at runtime. The trademark notice disclaims
affiliation and claims no licence, because there is none to claim.

---

## [1.33.0]

### Added — Google Play release preparation, and the consent layer it needs

Targets Android API 36, which is what Play requires for new uploads. Build
tooling moved with it (Gradle 8.13, Android Gradle Plugin 8.13.2) and the
Google Mobile Ads SDK is now 24.5.0.

**Consent.** The game now runs Google's User Messaging Platform before any ad
is requested: consent is refreshed at startup, the form is shown where a region
requires one, and the ads SDK is not initialised at all until consent permits
it. Previously the SDK started the moment the game did. A player who declines,
or who is offline on first launch, gets the game with no ads and no ad revive —
which is the harmless outcome.

Where Google says a privacy entry point is required, **Settings gains a PRIVACY
OPTIONS control** that reopens the form, so consent can be withdrawn without
reinstalling. It is hidden everywhere it would have nothing to show.

**Debug builds now use Google's test ad units, always.** The whole revive path
— preload, offer, show, reward, revive — can be exercised on a real phone with
no AdMob account and without a single real impression. Release builds use only
the ids supplied at build time, and a release with none configured shows no ads
rather than falling back to a test one.

### Fixed — a revive could be spent twice across a process restart

One rewarded revive per run, and the counter lived only in memory. A rewarded
ad puts its own Activity in front of the game, which is exactly when Android is
most willing to kill what is behind it — so "one per run" quietly became "one
per process". The count is now written into the saved run the moment a revive
is granted.

### Changed — the keystore password is out of the build file

Release signing reads its path, password, alias and key password from Gradle
properties, an untracked `keystore.properties`, or environment variables. A
build with none of them configured still succeeds and produces an unsigned
bundle, which is the honest failure — the previous arrangement had a password
as a string literal in a committed file.

### Added — four release documents

`PLAY_STORE_RELEASE.md`, `ADMOB_SETUP.md`, `PRIVACY_AND_DATA_SAFETY.md` and
`RELEASE_CHECKLIST.md`, plus `tools/verify-release.sh`, which reads the ad
ids back out of a built bundle and fails if a Google test id is in it.

Nothing about the revive rules changed: still voluntary, still rewarded-only,
still one per run, still the same wave at 50% integrity with every agent and
every coin intact, and still granted from the reward callback and nowhere else.

---

## [1.32.0]

### Fixed — two deployment nodes were drawn on top of each other

On the perimeter map, fourteen pairs of nodes sat 26 world units apart — the
exact radius a node is drawn at, so the two circles almost entirely overlapped.
On a desk monitor it read as a slightly thick node. Letterboxed onto a small
phone those two nodes are **9dp** apart: one fingertip covers both, and you
got whichever one the arithmetic preferred.

Nodes are now required to keep a node's width between them. The perimeter map
has 67 deployment spots where it had 81; the fourteen it lost were the
duplicates, and where two candidates collided the one kept is the one covering
more of the route. The Hugging-Face map was already clear.

### Changed — a saved run from an older build is no longer resumed

Agents are stored by node id, and a node id is a position in a derived array,
so the change above means a run saved before this version refers to different
ground. Those runs are now discarded instead of restored onto the wrong spots,
and CONTINUE does not offer them. Nothing permanent is affected — progress,
purchases, unlocks and statistics are stored separately.

### Added — every screen is checked at every screen size

A release blocker rather than polish: Play lists thousands of devices. Every
screen is now rendered at eight viewports, from a 4-inch phone held sideways
to a tablet, including the system font at 1.3× and 2×, and checked for
anything laid out past the edge that cannot be scrolled to. The battlefield is
checked separately and exactly, across eighty aspect ratios from square to
3:1.

This is layout arithmetic, not rendering. Font metrics on a real phone still
differ from the test environment's, which is how the studio wordmark got
through twice.

---

## [1.31.0]

### Changed — the music eases in at launch

It used to arrive at full volume a fraction of a second after the boot chime
finished, which read as one sound interrupting the other. Now there are five
seconds of quiet from opening the app, then the music rises over four seconds.

It rises to **your** volume, not past it. The fade is a multiplier on the
music setting, so if you have music at 20% it ends at 20%, and if you have it
off you hear nothing at any point. Moving the slider during the fade takes
effect immediately.

Launch only — coming back to the menu from a run does not replay it.

---

## [1.30.0]

### Changed — integrity is shown once, not twice

The `100 / 100` printed inside the server rack is gone. The top bar already
carries `CORE-SERVER · HP 100 / 100`, and the second copy cost a chunk of the
most valuable space on the board to say nothing new.

The bar on the rack stays — it shows integrity *where the damage is landing*,
readable without taking your eyes off the lanes — and so does the INTEGRITY
LOW alarm, which is the only warning you get mid-wave. The bar has moved up
into the space the figures left, so the rack's chase lights still frame
something.

### Fixed — a saved run now remembers which level it was played on

Groundwork for the second map, and a bug waiting to happen without it. Agents
are saved by node id, and node 17 is one patch of ground on one level and a
completely different one on another — so a run resumed on the wrong map would
have scattered your whole board, silently. Saves now record the level and the
mode, and restore onto them.

Runs saved before this release resume exactly where they were played.

---

## [1.29.0]

### Fixed — the studio logo now scales to any screen

It is a drawable rather than text. Two attempts at rendering it as monospace
ASCII looked right in testing and were unreadable on a real phone, because the
spacing depended on a font chosen at runtime and those differ by device.

Every character is still a drawn `#`, so it is the same mark — it simply has
no font in it any more, and looks identical at every size.

### Fixed — music kept playing when the app was minimised

Worse than it sounded: backgrounding the game *mid-run* switched it to the
menu music and carried on playing over whatever you opened instead. The pause
handler was calling the "you have left a match" path, which starts music
rather than stopping it.

Backgrounding now silences everything, including the boot sound, and coming
back resumes whichever screen you were on. Previously, returning to the menu
after minimising left it silent too — resume only restarted music if a run was
in progress.

---

## [1.28.0]

### Fixed — the studio logo was unreadable on a real phone

The `HamieTon.dev` banner sheared into a diagonal mess on hardware while
looking perfect in the test renderer. Two causes, and the second is the one
that mattered: the row spacing came from a font measurement taken in the test
renderer rather than on the device, and — worse — centred text centres each
line *independently*, so every row of the logo was centred to a different
width and the whole thing slid sideways as it went down.

It is drawn glyph by glyph now, left-aligned, at spacing measured from the
font actually in use. There is no longer a number that can be wrong.

### Fixed — menu music never played on a fresh launch

Not since it was added, in 1.11.0. The menu track only ever started after
you had been into a match and come back out; open the game and go straight to
the menu and there was silence. Every arrival at the menu now starts it.

### Changed — the menu power-on is gone, and a boot sound takes its place

The menu no longer flickers in. It was meant to read as hardware coming up and
it read as icons flashing, which is not the same thing and is not worth a
second and a half of everyone's time. `MENU INITIALIZATION` has gone from
SETTINGS with it.

In its place, **a PC boot sound plays once over the studio ident**. It is the
only audio file in the game — everything else is generated — and it is there
because it does the job the animation was failing to do, in a fifth of the
screen time.

---

## [1.27.0]

### Added — the main menu powers on

The menu no longer just appears. It starts black, one green LED catches
off-centre and settles, the interface flickers in panel by panel in a scattered
order, and then everything is lit and ready. About a second and a half.

It runs **once per launch**, not every time you come back from the store, and
**any tap skips it**. `MENU INITIALIZATION` in SETTINGS turns it off entirely —
off means off, not faster, so the menu is drawn complete with no fade at all.
Battery saver switches it off too, for the same reason it drops the living
backgrounds.

The flicker is the same on every launch. That is deliberate: a menu that boots
differently each time reads as something being broken rather than as a feature.

### Fixed

A settings switch that another setting overrides now dims and says so instead
of showing a state it is not actually in.

---

## [1.26.0]

### Added — the studio ident

`HamieTon.dev` in big block ASCII now opens the game: it fades up, holds still
long enough to be read, and fades away, then the CyOps TD boot screen and the
menu. Tap it to skip — an ident is for the first launch, and by the twentieth
it is between the player and the game.

The boot screen lost its own small HAMIETON-DEV mark in the same change. The
studio has a screen to itself now, and signing the game twice in four seconds
is worse than signing it once.

### The part that was not obvious

Getting ASCII to render as a logo took three measurements rather than three
guesses, and each one on its own made the banner unreadable:

- the theme adds a third of a point of letter spacing to body text, which is
  right for prose and fatal for a grid
- Compose's default line spacing pushed the rows apart into scattered
  punctuation, or overlapped them into a smear
- a `#` inks exactly 0.71 of its point size in the platform monospace face, so
  that is the line height at which the blocks touch and form solid letters

The banner is also scaled to the screen rather than set at a fixed size — it is
seventy characters wide, and wrapping it would turn a logo into wreckage — and
it carries the plain-text name as its accessibility label, because a screen
reader handed the raw art reads out pipes and hashes.

---

## [1.25.0]

### Added — the tutorial actually teaches the game now

Eleven cards instead of five, and the first-run experience finally explains the
two numbers a new player stares at without being told what they mean.

**Arrows that point at the real thing.** The WAVE and ◇ CRYPTO readouts are
drawn inside the battlefield canvas; the tutorial card is a normal UI panel.
The arrow now crosses that boundary properly — it is aimed using the renderer's
own measurements of where it drew those readouts, so it lands on them at any
screen size and cannot drift to where they used to be.

**SKIP gets out of its own way.** It lived in the top-right corner, which is
exactly where the WAVE and ◇ readouts are — measured sitting straight over the
plate. On the two steps that point at them it moves to the bottom left, clear
of the card, the readouts and the deploy panel. It is reachable on every single
step, including the ones that wait for you to tap something and draw no buttons
of their own.

**It insists on two FIREWALLs and two TARPITs**, and says so if you pick
something else rather than quietly moving on. A player who ignores the cards
and just starts playing is never left staring at a stale step.

**An optional briefing.** *"Know what is coming?"* — yes gives you the threats,
the three boss identities, what JAM does to an agent and why TARPIT deals no
damage. No goes straight to playing. It is generated from the game's own
catalog, so it cannot drift out of date the way a hand-written one would.

### Fixed

**The game used to say WAVE 0.** The first thing a new player saw was a wave
that does not exist. It now reads WAVE 1 before the first wave, which is what
the top bar (`--`) and the preparation banner (PERIMETER READY) already meant.

**Skipping the tutorial did not always stick.** Skip it, lose the run, press
RETRY, and it could come straight back — the "done" flag is saved in the
background and a stale read could undo it.

---

## [1.24.0]

### Added — REVIVE PACK, and ten times the € everywhere

**REVIVE PACK**, $4.99: three revives per run instead of one, **no ad in front
of any of them**, and €5,000. It sits next to REMOVE ADS in the store's
CONVENIENCE section on purpose — they are the two products with "ads" in the
name, and they cover different ads. REMOVE ADS takes away the interstitial
between runs. REVIVE PACK takes away the rewarded ad in front of a revive.
Neither implies the other, and the store, the account screen and the revive
button all say so.

**Every € figure is now ten times what it was.**

| Product | € before | € now |
| --- | ---: | ---: |
| €150 BUDGET | 150 | **1,500** |
| €500 BUDGET | 500 | **5,000** |
| €900 BUDGET | 900 | **9,000** |
| CORE SKIN PACK | 200 | **2,000** |
| ALL LIVING BACKGROUNDS | 200 | **2,000** |
| STARTER PACK | 200 | **2,000** |
| REMOVE ADS | — | **5,000** |
| REVIVE PACK | — | **5,000** |

**Nothing got cheaper or more expensive.** Ten times the € in the packs alone
would have been a tenfold buff to paying, so the whole economy moved together:
what a wave pays out and what a firmware level costs went up by the same ten.
€9,000 buys exactly the firmware levels €900 used to buy — there is a test that
asserts that directly. The numbers are bigger and the game is the same, which
is the point.

### Your existing € was carried across

A save written before this release holds € at the old scale. It is multiplied
by ten once, on first launch, and stamped so it can never be multiplied twice.
Firmware levels already bought are untouched — only the currency changed
denomination. Nobody ends up poorer than they were.

### Also

A product in the catalog that no screen listed could be priced, restored, and
never bought. The store's sections are now data, and a test asserts every
product is reachable from a screen — REVIVE PACK was caught by it.

---

## [1.23.0]

### Added — watch an ad to continue

A lost run can now be continued. **WATCH AD TO CONTINUE** appears on the
game-over screen, and taking it clears the board, restores half the mode's
core integrity — 50 on standard, 35 on Hack:AI, rounded up — and drops you back
into the preparing phase for the same wave with your agents, their levels and
your ◇ crypto exactly as they were. A moment to spend and re-place before the
wave comes again, rather than a shove straight back into the swarm that just
killed you.

**One per run**, and the button says so.

### The four things that were easy to get wrong

**The reward, not the dismissal.** This is a rewarded ad, which is a different
format from the interstitial the game already showed. An interstitial calls
back when it is *dismissed*, so a revive hung off that callback would be a
revive granted for closing the ad after two seconds. The new
`AdGateway.showRewarded` reports whether the reward was actually earned, and
the revive hangs off that and nothing else. Every path through it — earned,
skipped, failed to show, none loaded, no activity attached, the SDK throwing —
calls back exactly once. A player who is owed a revive and gets a dead screen
instead has lost a run to a bug.

**The double count.** The run used to be recorded, submitted to the
leaderboard and cleared from the save the instant the core fell. A revive after
that would have posted *two* leaderboard entries for one run and landed its
kills, crypto and damage twice in lifetime statistics. The order is now: show
the summary, offer the revive, and write nothing until the player is finished
with the run. Six tests fail if that split is undone.

**Backgrounding the offer.** A run sitting on an unanswered offer is
deliberately unrecorded — so leaving the app there would have been a way to
erase a bad run. It is now recorded when the app is backgrounded.

**The ad budget.** A run that already paid a rewarded ad is not then shown the
loss interstitial when it finally ends.

### REMOVE ADS does not cover the revive ad

The owner's call, and the game says so in two places rather than letting a
paying player find out at the worst possible moment: on the button itself
(*"REMOVE ADS covers ads between runs; revive ads are separate"*) and on the
GOOGLE PLAY account screen, whose ADVERTISING row now reads **REMOVED · REVIVE
ADS SEPARATE** instead of the *REMOVED · NONE* that would now be untrue.
REMOVE ADS buys freedom from ads you did not ask for. The revive is one you
did, and it is opt-in.

A build with no rewarded ad unit configured (`cyops.admob.rewardedId`, which is
separate and optional) shows **no button at all** rather than a dead one.

---

## [1.22.0]

### Added — you can finally ask what you are fighting

A boss could arrive carrying four modifiers, each with a name, a tag and a
written description, and the only clue the game ever gave was a banner that
flashed past before the fight. "Why is this one not dying?" had an answer that
existed in the code and was never shown to anybody.

A **BOSS** button now sits in the control bar **while a boss is on the field**,
and nowhere else — the game does not offer a control that cannot do anything.
It opens a dossier at the top right:

- the variant's glyph and name, and — for a `[ZZ]` that has already come back —
  *REANIMATED, it will not come back again*
- an integrity meter with the raw numbers under it, `420 / 1000`
- ARMOUR, SPEED and DISTANCE TO CORE in one strip
- **every modifier it rolled, with its description** — the part that answers the
  question

It reads the live enemy rather than a snapshot, so the numbers move while it is
open, which is the only reason to open it mid-fight. It closes itself when the
boss dies, so the next boss does not throw it over the board unasked.

### The bug this shipped with, and how it was found

The first build of the panel put four stat rows in a fixed-height column, and
the bottom two ran off the end of it. Nothing said so: the composables were
there, they were laid out, and the test that asserted their text existed passed
on three of them. Two tests failed with *"the component is not displayed"* and
no indication of which line.

Fetching the laid-out bounds of each node found it in one run — the signature
line measured `78 x 0` and DISTANCE TO CORE's value measured `6 x 0`. That is
what "present but never seen" looks like from outside.

Three independent things now stop it recurring: the three secondary numbers
share one strip instead of costing four rows, the panel is sized past what its
content measures rather than up to it, and the column scrolls with the same
MORE BELOW hint the agent panel uses — so a longer signature or a larger system
font degrades into a scroll rather than into silence.

It is the 1.18.0 HUD lesson one level up: **a semantics assertion proves a
composable exists; only its measured bounds prove it is on screen.**

---

## [1.21.0]

### Added — bosses have identities now

Until this release there was exactly one boss in the game. `[!!!]` BREACH, every
time, with a different set of modifiers rolled onto it — and modifiers make a
boss *harder*, they do not make it a different opponent. A player at wave 60 had
met the same thing a dozen times.

`BossVariant` is a table: glyph, health/armour/speed weighting, a signature line
and the earliest boss cycle it may appear on. Everything else — the modifier
roll, the rewards, the route rotation — is untouched and applies on top.

| | | |
| --- | --- | --- |
| `[!!!]` | **BREACH** | The original. No tricks, just weight |
| `[GG]` | **GOOD GAME** | ×1.6 health, +7 armour, 22% slower. The wall — bring something that ignores armour |
| `[ZZ]` | **ZOMBIE** | 12% faster, and **gets back up once at 40% health** |

New opponents arrive one at a time as the cycles climb, the way modifiers
already did, and cycle 1 stays a plain fight against a plain opponent. The boss
banner names which one is coming and what it does, while there is still time to
build for it.

The remaining variants from the backlog's list are one table row each, and are
waiting on the owner to choose.

### The two details that were not obvious

**ZOMBIE's revive lives on the enemy, not in the damage path.** `Enemy.tryRevive()`
is asked before a death is finalised, so every way of landing a killing blow — a
shot, splash, a chain — goes through the same rule and none of them has to
remember it. That is the same reasoning that moved jamming onto `Agent.jam()`
last release.

**A chip is measured from what will be drawn.** The threat chips that stop
labels smearing were sized from `EnemyType.glyph` and cached per type — but a
boss draws its *variant's* mark now, and `[GG]` is a character narrower than
`[!!!]`. A chip sized for the wrong string does not fit its label, which is the
one job the chip has. Bosses are measured live; ordinary threats keep the cache,
because there are dozens of them and their glyph never varies.

---

## [1.20.0]

### Added — bosses and elites die properly now

*"an animation when bosses and elites are killed like a blast radiating out of
pixels of their unit color… this is one of the most important things I'd like to
make the whole game feel more prevalent."*

A boss death throws **420 pixel shards** out to 700 units in the threat's own
colour, behind an expanding shockwave ring and a white-hot core. Elites get the
same at 500 units. It is over in under a second, and it is drawn **under** the
threat chips — a blast that covers a third of the board must not hide the wave
walking in underneath it.

**It costs one pooled object.** Every shard's angle, speed and size is derived
in the renderer from a seed on the effect, so a 420-piece explosion allocates
nothing and does not flood a pool sized for ordinary hits. That is the same
trick the backdrop columns and the rack's chase lights already use, and it is
what lets the engine keep its no-allocation-per-frame promise while throwing
this much across the screen. Each explosion seeds itself, so the same boss dying
twice is not the same picture twice.

Battery saver halves the radius rather than skipping the effect: the feedback
survives even when the spectacle does not.

### The tuning, because the first version was invisible

Written the obvious way — alpha fading as the square of remaining life — it
rendered as a faint speckle. By the time 260 one-pixel shards had spread across
a 700-unit disc they were at a third of their alpha and averaged less than a
pixel of coverage each. Brightness has to *outlast* the spread, so the alpha now
holds at full for the first 55% and only then drops, the shards are 2.4–7.4px
rather than 1.2–3.8, and there are 420 of them.

That is a change nothing but looking could have caught, which is why
`ShardBurstRenderTest` rasterizes real frames and measures them — that the
shards appear, that they travel outward, that an elite blast is smaller than a
boss one, and that it is *gone* by the time its lifetime is up. Its first
version measured red-dominant pixels anywhere on the board and reported the same
answer every time, because the ATTACK ORIGIN label is red and sits at x=16; it
now diffs against a control frame, which is the technique the field-status tests
already used for exactly that reason.

---

## [1.19.0]

### Changed — range finally grows, because the late game was impossible for an arithmetic reason

The owner's report was that the late game feels impossible and that reaching
very high waves needs range that scales. The numbers agreed with them:

```kotlin
UPGRADE_DAMAGE_GROWTH = 0.20f   // +20% of base per level -> x20.8 at level 100
UPGRADE_RANGE_GROWTH  = 0.007f  // +0.7% of base per level -> x1.69 at level 100
```

Damage multiplied twentyfold over a run while range barely moved, so a levelled
agent hit like a truck and still could not see anything. A level-50 TARPIT had
an aura of 269 units on a 1,600-unit board.

Range now climbs **×1.25 every five levels** — the owner's formula — **capped at
×6**, which the curve reaches at level 45. The cap is the one liberty taken, and
the reason is arithmetic too: uncapped, that formula reaches ×9.3 by level 50
and ×86.7 by level 100, at which point every agent covers the whole map from
wherever it stands and placement — the thing the 79 deployment nodes exist for —
stops being a decision. It is a single constant, deliberately, so it can be
loosened without touching the shape of the curve.

A level-50 agent went from ×1.34 of its base reach to ×6. That is the change the
owner asked for, and there is a test that states it in those terms.

### Changed — TARPIT's aura, and the end of IPS being the roster's embarrassment

- **TARPIT** base aura 200 → **300**, as asked. With the new curve a maxed
  tarpit covers over a thousand units, which is the "late game builds that could
  not work" problem answered.
- **IPS** 170 → **260** and it now **splashes** every shot to 78 units for 45% of
  the hit. It cost more than IDS for 120 less range and had an identity nobody
  could feel next to FIREWALL; splash is the job no other cheap agent does, and
  it makes a pack of BOTs a question of what you built rather than of how many
  turrets you own.
- **FIREWALL** 168 → **195**, and it **cannot be jammed**. The agent with the
  shortest reach has no choice but to stand where a boss can jam it, so
  immunity is what makes standing there worth doing.
- Every dearer agent's reach was **raised** to keep the owner's rule from the
  1.8.0 rebalance — a dearer agent must never reach less far than a cheaper one
  — and raised rather than cut, which is the owner's other rule. IDS still sees
  furthest of anything at 330.

### Changed — a jam is now the agent's business

`AGENT_DISRUPTION` used to set `agent.disruptedFor` directly from the enemy
system. Immunity implemented there would have been a rule that every future
jammer has to remember to check — and two more are already in the backlog
(`[₩₩₩]` and `[¥¥¥]` each jam a specific agent). It moved onto `Agent.jam()`,
which decides for itself, so a new jammer gets the behaviour for free.

---

## [1.18.0]

### Fixed — the top status strip has never been drawn

Found while doing the two small HUD items below, and it is the bigger news.

The renderer opens every frame with `canvas.drawColor`, which fills the whole
**clip** rather than the composable's box — and Compose does not clip a draw to
its layout bounds unless it is asked to. The battlefield sits directly below the
status strip in the same column, so every frame it painted straight over the
strip above it.

The HUD composed. It laid out. It reported perfectly correct bounds. Only its
pixels were missing — which is exactly why the question *"is the top HUD strip
actually visible on a device?"* has sat unanswered in `PROGRESS.md` since 1.5.2,
and why the in-field corner readouts were added that release "partly to hedge
against it". They were not a hedge; they were the only reason the wave and the
crypto were readable at all.

The fix is one `clipToBounds()`. The test is the interesting part:
`MatchScreenRenderTest` draws the real screen and counts the **pixels** inside
the bounds Compose reports for the HUD. Removing the fix fails it; that was
checked rather than assumed. A semantics assertion proves a composable exists —
only pixels prove it is seen, and twelve releases went by on the difference.

### Changed — the control bar is half its old height

*"the AGENTS 1X 2X 3X 5X bar… it blocks the level of lower towers."*

46dp buttons plus 8dp of row padding each side made a 62dp strip across the
bottom of the screen. It is ~32dp now: a `dense` flag on `CompactButton` that
shrinks the minimum height, the padding and the text, used by the in-match bar
and nothing else. `NEXT WAVE · BREACH` is one line rather than two, since a
two-line label was setting the bar's height by itself.

This is a deliberate, narrow exception to the project's own
`MIN_TOUCH_HEIGHT_DP = 52`: five large, well-spaced, low-consequence buttons on
a screen held in two hands, where a mis-tap costs a speed change. Every other
button in the game keeps the full target, which is why it is a flag rather than
a smaller default.

The bar does not overlap the board — the world is scaled into whatever is left
over — so the win is that the board is now bigger, and everything drawn on it,
including the level under each agent, is drawn larger.

### Changed — WAVE and ◇ CRYPTO are stacked in the top-right

*"Put the wave number above the money count. make the money and wave number
slightly larger."*

31pt → 35pt, and both now sit in one right-aligned stack instead of at opposite
ends of a 1600-unit board.

The corner was chosen by measurement, not taste. The top-left is crowded: the
ATTACK ORIGIN marker is directly beneath it and lane 1 starts at y=73, which
leaves no room for a second line. And *both* corners sat on the row of nineteen
deployment nodes along y=38 — which is why 1.17.0 had to draw the readouts last
simply to win those pixels. No node is placed past x=1240 and the rack starts at
y=168, so the block above the rack is the one piece of the field nothing else
uses.

`FieldStatusRenderTest` moved with them, and one of its assertions got more
honest on the way: "neither readout touches the lanes" was a single y ceiling,
which was a fair proxy while the readouts spanned the whole width and is simply
wrong in a corner no corridor reaches. It now tests the real thing — the ink's
box against every route segment, in two dimensions.

---

## [1.17.0]

### Fixed — the chase lights glitched every time you killed something

The reported symptom was that the racetrack on the core "glitches while
defeating enemies" and the animation stutters. It was one mistake, in two
places, and it is worth writing down because it looks completely harmless:

```kotlin
val head = (time * (0.32f + load * 0.30f)) % 1f   // chase
val pulse = sin(time * (1.4f + load * 3.4f))      // status LEDs
```

Both animations speed up as the board fills, which is the intended behaviour —
the rack is a load indicator. But position here is the product of the **whole
elapsed time** and a rate that changes mid-run. So the instant a threat died,
the rate dropped and the position jumped with it, by `elapsed × Δrate`. A
minute into a run that is **more than twenty whole cycles**: the chase
teleported and the LEDs flickered, exactly when the player was looking at the
board because they had just killed something.

Phases are now integrated — `phase += delta × rate` — which keeps them
continuous across a rate change: a busier board makes the lights move faster
*from where they are* rather than moving them somewhere else. The step is
clamped, so a frame after a pause or a backgrounded app does not fling them
either, and a rewound clock (a new run) does not unwind them.

It lives in its own class, `RackAnimation`, with seven tests. The first of them
measures the *old* formula's jump, so the size of what was wrong stays on the
record.

### Changed — the numbers a player actually reads

Every one of these was reported as too small or too dim, and every one is a
number someone looks for on purpose rather than absorbs:

| | was | now |
| --- | --- | --- |
| Agent level, under each agent | 12pt dim grey | **17pt white** |
| Core integrity, inside the chase circuit | 20pt | **27pt white** |
| WAVE and ◇ corner readouts | 24pt at 67% alpha | **31pt, full strength, on a plate** |

The corner readouts also moved to the **end** of the draw order. There is a row
of nineteen deployment nodes along the same line, so whichever is drawn second
wins the corner — and the wave and the crypto are numbers you look up
mid-fight, while a node is a bracket you can still see the rest of and still
tap. Their plates are sized from a template rather than from the live number:
a plate measured against the text itself would grow and shrink every time
crypto changed, which during a wave is several times a second.

### Changed — the upgrade panel is two columns, and its actions never scroll

Reported as: *nothing indicates the players to scroll down to upgrade towers.*
It was one tall scrolling card with UPGRADE at the bottom, so on a short screen
the buttons were below the fold with nothing to suggest they existed.

Everything you **do** is now in its own column on the right — cost, +1 / +10 /
MAX, SELL, CLOSE — outside the scroll, always on screen. Everything you **read**
is on the left. The reading column can still overflow on a small phone, so it
says so: a `▼ MORE BELOW` hint appears at its bottom edge while there is more
below, and only while there is. An invisible scroll is the same as no scroll.

The card is also shorter than it was — capped at 300dp tall instead of growing
to fit — so it no longer covers the field it is describing.

### Changed — SKIP is in the corner, for the whole tutorial

The tutorial's only SKIP lived inside the card, beside CONTINUE. Several steps
wait for the player to tap something specific and draw no buttons at all, so on
exactly those steps there was no way out of the tutorial. It is now a small
button in the screen's top-right corner, present at every step, and CONTINUE
takes the full width of the card it left.

---

## [1.16.0]

### Added — LOADOUT, because a skin you cannot wear is not a skin

The store sold six core skins, five living backgrounds and an agent palette,
and there was **nowhere to put any of them on**. The choice was stored, the
renderer read it, and nothing in the game could set it: a player could spend
five pounds and see no difference. That is the worst version of a store, and it
shipped that way for four releases.

MAIN MENU → LOADOUT now lists every look, owned or not, with a colour swatch
beside each one — a name tells you nothing about a colour scheme, and choosing a
look you cannot see is guesswork. Two rules hold it together:

- **Everything is listed, locked included.** A grid with the locked items hidden
  cannot tell a player what the store is even for. A locked row says what it is
  and stays inert when tapped; it is not a second checkout.
- **The free option is never locked.** TERMINAL and STATIC are always
  selectable, so there is always a way back to the plain game.

### Added — RUN MODE, because HACK:AI could not be selected either

`GameMode.HACK_AI` has existed since 1.9: tougher threats, closer together, less
integrity to give, richer rewards, unlocked by clearing wave 100. The engine
accepted it, the renderer named it at the top of a run, the view model held a
selection — and no control anywhere could change that selection, so every run
ever played was STANDARD.

The main menu now has a RUN MODE panel. HACK:AI is **visible while locked**,
with the wave that unlocks it and how far the player has got, because something
to aim at is worth more than a surprise. PLAY names the mode it is about to
start, since choosing a hard mode and forgetting is a wasted run.

There is a test that asserts every mode in `GameMode` appears on the menu — the
specific failure that let HACK:AI sit unreachable for four releases.

### Fixed — tapping 5× without owning it selected 3×

The speed row drew all four speeds and `applySpeedIndex` coerced into range, so
on a save that has not bought the fifth speed, pressing 5× quietly selected 3×
and highlighted it. That is worse than a dead button: it also misreports what
speed the match is running at. The locked speed is now marked and inert, and
says where to find it.

Three controls in one release that were wired end to end with nothing able to
reach them, all found the same way — by asking, for each thing the store sells
or the engine supports, *where does a player press this?*

---

## [1.15.0]

### Added — progress that survives the phone

Linking a Google account now carries a player's waves, agents, € and firmware
to any device they sign into. MAIN MENU → GOOGLE PLAY → CLOUD SAVE.

It is built on **Play Games Services Saved Games**, which means the save lives
in the player's own Google account — the app-private part of their Drive that
only this game can read. That is why linking shows Google's prompt asking for
permission to manage this game's saved data, and the panel says so *before* the
prompt appears rather than after, because that prompt is where a player decides
whether to trust this.

The alternative was a custom backend, and it was rejected on purpose: an
account system, a password reset flow, a privacy policy to write and honour, a
server to keep alive, and one more way for a player to lose everything. Saved
Games costs nothing to run and is the mechanism players already recognise.

Syncing happens when the player leaves the app and when a run ends — the two
moments where the local save has just been written and nobody is waiting. It
never prompts an unlinked player.

**What travels:** progress, unlocks, the callsign, the run history.
**What does not:** purchases, because Google Play is the source of truth for
those and a save file that could grant paid content would be a way to steal it;
and device settings, because volume and haptics belong to a device, not a
player.

### The rules for when two devices disagree

This is the part of cloud save that can actually cost someone something, so the
rules are written down and tested rather than left to whichever device syncs
last:

| | |
| --- | --- |
| Lifetime counters (waves, attacks blocked, bosses, € *earned*) | the maximum of both |
| Unlocks, tutorial, run history | union — nothing is ever taken away |
| The wallet: unspent € **and** the firmware level it bought | as one piece, from whichever save is further along |
| The run in progress | that same save's |
| Callsign | a claimed name is never replaced by an empty one |

The wallet rule is the one with teeth. Spend 500 € on firmware, then sync
against a save from before the purchase, and taking the maximum of each field
independently would hand back the money *and* keep the firmware. A ledger moves
as one piece or not at all. There is a test that states this as a property
rather than an example: whatever pair of saves goes in, the € and the firmware
that come out must both have come from the same save.

Two more properties are tested because they are what makes syncing safe to do
repeatedly: the merge gives the same answer whichever device performs it (so two
phones cannot ping-pong), and merging an already-merged save changes nothing (so
launching the game does not shuffle anyone's numbers).

### Fixed — the pause-time upload raced the pause-time save

Leaving the app wrote the run and pushed the snapshot in two separate
coroutines, so the upload could read the save before the run landed in it. The
failure was silent and would only ever show up on the *other* device, as a run
that had quietly gone missing. Both now happen in order, in one coroutine.

### Fixed — linking a new phone would have wiped the account's wallet

Found by writing the test for it rather than by reading the code, and it is the
worst bug this feature could have had. "Further along" was originally "newer",
and a save is stamped with the time it is *exported* — so a freshly installed
phone, with nothing played on it, always looked newer than the account it was
about to read from. Linking it would have taken the account's € and its run in
progress and replaced them with nothing.

Ordering is now by how much play a save represents (the sum of its lifetime
counters, each of which only grows), with the clock as a tiebreak only. That
also fixes a second problem nobody had hit yet: device clocks disagree, and a
phone set to the wrong year would otherwise have won every merge it took part
in. The same number is handed to Play Games as the snapshot's progress value, so
Google's own conflict resolution cannot disagree with ours.

### Added — a failure that costs nothing

Every path is pull, merge, apply, push — never push-then-pull. The merged save
is written locally *before* it is uploaded, so losing the network on the way up
leaves the player holding the better of the two saves rather than the worse one.
A failed sync says "GOOGLE UNREACHABLE — SAVE KEPT ON DEVICE" and the game
carries on.

### Fixed — an already-linked player was offered a link button

While linked-but-offline, the panel offered LINK GOOGLE ACCOUNT, which would
have sent someone who is already linked back through a sign-in they do not need.
That state now offers SYNC NOW, which is the thing that actually helps. Caught
by a test, not by reading the code.

### Note for a build with no Play Games project

Unconfigured — which is how this repository ships — the no-op gateway is
selected, the panel says NOT IN THIS BUILD, and saves stay on the device.
Android's own Auto Backup still restores them on a fresh install onto a phone
signed into the same Google account, so even that build is not a dead end.

---

## [1.14.0]

### Added — the Google Play account screen

A sub-menu whose only job is to answer one question honestly: *where do my
purchases live, and what happens to them when I change phone?*

Everything on it follows from one fact it refuses to obscure — **the game has
no account of its own.** There is no login, no password and no server holding
progress. A reassuring "signed in" badge would have been easy and would have
been a lie: a player who believes there is a CyOps account expects their waves
and their € to follow them to a new phone, and they do not. So the screen says
which of the two does, in as many words:

| | |
| --- | --- |
| PURCHASES | YOUR GOOGLE ACCOUNT |
| WAVES, € AND FIRMWARE | THIS DEVICE ONLY |

It also shows the live billing connection (CONNECTED / CONNECTING /
UNREACHABLE / NOT AVAILABLE), each with an explanation of what the player can
do about it; what the account owns, counted from the entitlements rather than
assumed; the callsign, labelled as local and *not* a Google account; and links
out to Play's own order history for refunds and receipts, because those are
Google's to handle and pretending otherwise would strand a player.

RESTORE stays pressable when Play is merely unreachable — that state is usually
a missing network, and the fix is to press it again once there is one — and is
disabled only when the build genuinely has no billing, because a restore button
that looks live but does nothing is worse than a dead one.

### Added — living backgrounds now theme the menus

An owned background no longer stops at the battlefield. It reaches every menu
backdrop through a single `CompositionLocal`, changing the drift's colour,
density and pace. The battlefield effects are native-Canvas and the menus are
Compose, so the theme carries by palette rather than by maintaining two
implementations of every backdrop.

Rasterized and measured rather than eyeballed: at 960×540 the free backdrop
inks 2,051 pixels and AURORA 4,270, LATTICE reads green where AURORA reads
blue, and every theme averages ≈0.02 luminance over the page — far under the
0.22 ceiling that separates a backdrop from something competing with the menu
in front of it.

### Fixed — ALL LIVING BACKGROUNDS granted three of five

`bg_pack` is titled ALL LIVING BACKGROUNDS and priced as a bundle, but its
grant list was written when there were three backgrounds and was never updated
when RAINFALL and PULSE were added. Anyone buying it would have received three
of the five it names. The test that should have caught this asserted the
literal `3`; it now asserts against the catalog, so adding a sixth background
cannot repeat the mistake quietly.

### Fixed — switching themes kept the previous one's motion

The backdrop's columns were remembered against the column count alone. Every
owned theme produces the same count, so changing from DRIFT to AURORA kept
DRIFT's drift speeds.

### Changed — the menu stopped claiming there are no ads

The main menu read `OFFLINE · NO ACCOUNT · NO ADS · NO PURCHASES`. Three
quarters of that stopped being true in 1.13.0. It now reads `PLAYS OFFLINE · NO
LOGIN REQUIRED`, and adds `· AD-FREE` only for a player who has actually bought
it. `README.md` carried the same stale claim about the `INTERNET` permission
and has been corrected with the real permission list.

---

## [1.9.0] – [1.13.0]

Logged in `PROGRESS.md` while the Play Store work was in flight, and summarized
here after the fact:

- **1.9.x** — SANDBOX removed; save state log (`PROGRESS.md`) written; splash
  screen with the HAMIETON-DEV mark; `Hack:AI` hard mode behind wave 100; the
  persistent player tag and build identifier on every screen.
- **1.10.0** — the store catalog, entitlements and persistence, with
  `NoBillingGateway` as the shipped default so the game stays playable when
  billing is not.
- **1.11.0** — menu music (bright 8-bit, against the game's lo-fi), core-server
  skins, living backgrounds, SPECTRUM agents, themed lane corridors.
- **1.12.0** — local leaderboard and callsign registration; one interstitial
  after a *lost* run, behind a tested policy.
- **1.13.0** — real Google Play Billing and AdMob, selected only when the build
  is configured for them. APK 1.18 MB → 3.02 MB.

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
