# CyOps TD — Balance Reference

Every number that shapes how the game feels lives in
**`app/src/main/java/com/packetbastion/asciidefense/core/Balance.kt`**, plus the
per-type stat tables in `model/AgentType.kt` and `model/EnemyType.kt`.

Nothing in `Balance.kt` depends on Android, so it can be edited and unit-tested
freely. `app/src/test/.../BalanceTest.kt` asserts the *shape* of these curves —
monotonicity, caps, and the relationship between reward growth and difficulty
growth — so a careless tweak fails the build rather than quietly ruining the
game.

---

## 1. Server and starting economy

| Constant | Value | Notes |
| --- | --- | --- |
| `SERVER_MAX_HP` | `100` | CORE-SERVER integrity. |
| `STARTING_CRYPTO` | `120` | Affords two FIREWALLs, or a FIREWALL plus an IDS. |

`STARTING_CRYPTO` is the single most sensitive number for first-play feel. Below
~40 the player cannot open at all; above ~150 the first three waves are free.

---

## 2. Enemy scaling

### Health

```
healthMultiplier(wave) = 1 + wave × 0.06 + wave^1.25 × 0.008
```

| Constant | Value |
| --- | --- |
| `HEALTH_LINEAR` | `0.06` |
| `HEALTH_POWER_COEFF` | `0.008` |
| `HEALTH_POWER_EXP` | `1.25` |

A linear + gentle-exponential hybrid. The linear term dominates early (keeping
the opening approachable), the power term takes over late (keeping the endless
mode from going flat).

| Wave | Multiplier |
| --- | --- |
| 1 | ×1.07 |
| 5 | ×1.36 |
| 10 | ×1.74 |
| 20 | ×2.54 |
| 30 | ×3.36 |
| 50 | ×5.06 |
| 100 | ×8.53 |

`BalanceTest` enforces that wave 10 is **less than 15% harder than wave 9** —
directly addressing "avoid making wave 10 dramatically harder than wave 9".

### Speed

```
speedMultiplier(wave) = min(1 + wave × 0.006, 1.65)
```

| Constant | Value |
| --- | --- |
| `SPEED_PER_WAVE` | `0.006` |
| `SPEED_MAX_MULTIPLIER` | `1.65` |

Speed is the most punishing stat to scale — it shrinks the player's reaction
window and their effective tower coverage at the same time. It creeps slowly and
hard-caps at wave ~108 so late packets stay readable.

### Armour

```
waveArmorBonus(wave) = floor(wave / 12) × 0.5
```

Deliberately coarse. It nudges the player toward heavier-hitting agents in the
late game without invalidating rapid-fire ones (armour is floored — see §6).

---

## 3. Wave composition

### Enemy count

```
waveEnemyCount(wave) = min(6 + wave × 1.15 + wave^1.12 × 0.30, 46)
```

| Constant | Value |
| --- | --- |
| `MAX_WAVE_ENEMIES` | `46` |

| Wave | Enemies |
| --- | --- |
| 1 | 7 |
| 5 | 13 |
| 10 | 20 |
| 20 | 38 |
| 30+ | 46 (capped) |

Sub-linear and capped **on purpose**. Difficulty past wave 30 comes entirely
from health, armour, composition and elite density — not from flooding the
renderer. A phone draws 40 interesting packets far better than 400 boring ones.

### Spawn interval

```
spawnInterval(wave) = max(1.05 - wave × 0.012, 0.34)   seconds
```

Reaches the 0.34 s floor at wave ~59. Without a floor, late waves become one
indivisible blob with no readable structure.

### Elite chance

```
eliteChance(wave) = clamp((wave - 4) × 0.014, 0, 0.32)
```

Zero until wave 5 — the player meets their first boss before their first elite,
one new idea at a time. Caps at 32%, reached around wave 27.

An elite variant of an ordinary archetype gets:

| Stat | Change |
| --- | --- |
| Health | ×2.1 |
| Armour | +1.5 |
| Server damage | +2 |
| Speed | ×0.92 |

### Archetype pools

| Waves | Pool (weight) |
| --- | --- |
| 1–2 | PACKET 100 |
| 3 | PACKET 62, BOT 38 |
| 4 | PACKET 48, BOT 28, MALWARE 24 |
| 5–7 | PACKET 38, BOT 22, MALWARE 26, EXPLOIT 14 |
| 8–10 | + TROJAN 16 |
| 11–14 | + ENCRYPTED 14 |
| 15–20 | + DDoS 8 |
| 21–30 | + ZERO-DAY 4 |
| 31+ | Reweighted toward TROJAN/EXPLOIT/ENCRYPTED/DDoS; ZERO-DAY 8 |

Swarm archetypes (BOT, DDoS) spawn as a burst in one lane, sized 3 → 7 by wave
band.

---

## 4. Bosses

| Constant | Value |
| --- | --- |
| `BOSS_WAVE_INTERVAL` | `5` |
| `BOSS_WARNING_SECONDS` | `2.6` |

```
bossHealthMultiplier(wave) = healthMultiplier(wave) × (1 + (cycle - 1) × 0.30)
bossCycle(wave)            = wave / 5
```

Bosses scale on their own steeper curve on top of the base curve.

| Wave | Cycle | Boss HP (base 520) |
| --- | --- | --- |
| 5 | 1 | ~764 |
| 10 | 2 | ~1 339 |
| 20 | 4 | ~3 141 |
| 50 | 10 | ~13 434 |

### Boss count

| Cycle | Bosses |
| --- | --- |
| 1–3 (waves 5–15) | 1 |
| 4–8 (waves 20–40) | 2 |
| 9+ (wave 45+) | 3 |

Multiple bosses enter in **distinct lanes**, raising pressure without inflating
entity count.

### Modifier introduction

| Cycle | Wave | Count | Pool |
| --- | --- | --- | --- |
| 1 | 5 | **0** | — (a clean first boss, on purpose) |
| 2 | 10 | 1 | ARMOR PLATING |
| 3 | 15 | 1 | + SPEED BURST |
| 4 | 20 | 2 | + FIREWALL RESISTANCE |
| 5 | 25 | 2 | + REGENERATION |
| 6 | 30 | 2 | + ENCRYPTION SHIELD |
| 7 | 35 | 3 | + PACKET REPLICATION |
| 8+ | 40+ | 3–4 | All, including AGENT DISRUPTION |

Modifier behaviour:

| Modifier | Effect |
| --- | --- |
| ARMOR PLATING | Armour +8 + wave × 0.25 |
| SPEED BURST | ×2.1 speed for 1.4 s, every 7.5 s |
| FIREWALL RESISTANCE | Firewall damage ×0.65 |
| REGENERATION | Heals 1.2% of max HP per second |
| ENCRYPTION SHIELD | Counts as encrypted (×0.45 from non-Cryptographers) |
| PACKET REPLICATION | Spawns a BOT or PACKET escort every 5 s |
| AGENT DISRUPTION | Halves fire rate of agents within 260 units for 3 s, every 8 s |

---

## 5. Economy

### Kill rewards

| Constant | Value |
| --- | --- |
| `REWARD_NORMAL` | `1` |
| `REWARD_ELITE_MIN` / `MAX` | `2` / `3` |
| `REWARD_BOSS_MIN` | `5` |

```
reward = baseTier × rewardMultiplier(wave)
rewardMultiplier(wave) = 1 + wave × 0.030
bossReward = (5 + cycle × 3) × rewardMultiplier(wave)
```

Rewards grow at **3% per wave** while enemy health grows far faster. This is the
central economic tension, and `BalanceTest` asserts it directly:

| | Wave 1 → 50 growth |
| --- | --- |
| Enemy health | ×6.2 |
| Reward | ×2.4 |

If those ever cross, the late game becomes an unlimited-money sandbox and every
upgrade decision stops mattering.

### Other flows

| Flow | Formula |
| --- | --- |
| Wave clear bonus | `8 + wave × 2` |
| Sell refund | 70% of total invested (`SELL_REFUND_RATIO`) |

Selling always returns less than was invested, at every level — so repositioning
has a real cost and cannot be used as a free undo.

---

## 6. Agents

### Deployment costs and unlocks

| Agent | Cost | DMG | Rate/s | Range | Unlock |
| --- | ---: | ---: | ---: | ---: | ---: |
| FIREWALL | 40 | 9.0 | 1.15 | 168 | start |
| IDS | 55 | 7.0 | 1.00 | 268 | start |
| IPS | 70 | 4.4 | 3.30 | 158 | wave 3 |
| SANDBOX | 80 | 3.0 | 0.85 | 186 | wave 8 |
| ANALYST | 95 | 30.0 | 0.62 | 200 | wave 5 |
| CRYPTOGRAPHER | 105 | 14.0 | 1.05 | 205 | wave 10 |
| ZERO-DAY HUNTER | 150 | 26.0 | 1.15 | 225 | wave 15 |
| AI SENTINEL | 185 | 15.0 | 1.50 | 235 | wave 20 |
| NETWORK ARCHITECT | 210 | 8.0 | 0.80 | 230 | wave 50 |
| QUANTUM DEFENDER | 240 | 34.0 | 1.05 | 245 | wave 30 |
| ROOT ADMIN | 320 | 78.0 | 0.95 | 255 | wave 40 |

Raw DPS is intentionally *not* monotonic with cost. IPS has the worst
single-hit damage in the game and is still one of the best purchases in the
right situation. Cost buys a *role*, not a number.

### Upgrade curve

| Constant | Value |
| --- | --- |
| `MAX_AGENT_LEVEL` | `10` |
| `UPGRADE_DAMAGE_GROWTH` | `0.26` per level |
| `UPGRADE_RATE_GROWTH` | `0.055` per level |
| `UPGRADE_RANGE_GROWTH` | `0.035` per level |

```
statAtLevel(L) = base × (1 + growth × (L - 1))
```

At level 10: **×3.34 damage**, ×1.50 rate, ×1.32 range — a total DPS multiplier
of roughly ×5.

Damage scales hardest because it is the stat that keeps pace with enemy health.
Range scales least: it is the strongest stat in a tower defence game and would
trivialise node placement if it grew freely.

### Upgrade cost

```
upgradeCost(baseCost, level) = baseCost × (0.55 + 0.30 × level) × (1 + level × 0.08)
```

For a FIREWALL (base 40):

| Level → | Cost | Cumulative |
| --- | ---: | ---: |
| 1→2 | 36 | 76 |
| 2→3 | 66 | 142 |
| 3→4 | 102 | 244 |
| 5→6 | 191 | 566 |
| 9→10 | 384 | 1 671 |

Quadratic-ish, so spreading levels across several agents stays competitive with
maxing one. There is no single dominant strategy.

---

## 7. Damage counter-play

All in `ProjectileSystem.damageMultiplier()`.

| Constant | Value | Meaning |
| --- | ---: | --- |
| `CRYPTOGRAPHER_VS_ENCRYPTED` | `3.00` | The hardest counter in the game |
| `ENCRYPTED_RESISTANCE` | `0.45` | Everything else vs encrypted |
| `ANALYST_VS_ELITE` | `1.80` | vs elites and bosses |
| `IDS_VS_FAST` | `1.45` | vs archetypes at ≥100 base speed |
| `IPS_VS_SWARM` | `1.35` | vs BOT and DDoS |
| `FIREWALL_RESISTED` | `0.65` | Firewall vs FIREWALL RESISTANCE boss |
| `MIN_ARMOR_PENETRATION` | `0.18` | Armour floor |

The armour floor matters: `damage = max(damage - armour, raw × 0.18)`. Without
it, an IPS (4.4 per hit) against a wave-40 Trojan (armour ~11) would deal
literally nothing. With it, IPS is *weakened* against armour but never bricked.
A wrong pick should be punished, not invalidated.

### Ability constants

| Ability | Constant | Value |
| --- | --- | --- |
| Sandbox slow | duration | 2.2 s |
| Sandbox slow | factor | 0.60 at L1 → 0.42 at L10 |
| Hunter crit | chance / multiplier | 25% / ×3 |
| Sentinel multi-lock | targets | 3 |
| Quantum chain | bounces / ratio / radius | 2 / 55% / 130 units |
| Architect aura | damage / rate | +30% / +20% (does not stack) |

---

## 8. Timing and feel

| Constant | Value |
| --- | --- |
| `PROJECTILE_SPEED` | 980 world units/s |
| `DAMAGE_NUMBER_LIFETIME` | 0.75 s |
| `DEATH_EFFECT_LIFETIME` | 0.45 s |
| `BOSS_DEATH_EFFECT_LIFETIME` | 1.10 s |
| `AUTO_START_DELAY` | 4.0 s |
| `GAME_SPEEDS` | 1×, 2×, 3× |
| `MAX_FRAME_DELTA` | 0.05 s |

Projectile speed is fast enough that shots read as instant contributions but
slow enough to be visible — the whole point of the ASCII trails.

---

## 9. Pool caps

| Pool | Capacity |
| --- | --- |
| Enemies | 72 |
| Agents | 32 (one per deployment node) |
| Projectiles | 140 |
| Effects | 110 |

These are hard ceilings on worst-case frame cost. When a pool is exhausted the
spawn is skipped rather than the pool grown.

---

## 10. First-play target

The stated design goal is that a new player realistically survives past wave 5.
`GameEngineTest.a well defended run survives past wave five` plays that opening
automatically — two FIREWALLs, then reinvesting into nodes and upgrades as
crypto allows — and asserts the run clears the first boss.

| Wave | Intended experience |
| --- | --- |
| 1 | Easy. Learn the loop. 7 plain packets. |
| 2 | Slightly larger, same enemy. |
| 3 | First swarm (BOT). IPS unlocks. |
| 4 | Mixed types. MALWARE appears. |
| 5 | **First boss**, no modifiers. ANALYST unlocks. |
| 6–9 | EXPLOIT, then TROJAN. First elites. |
| 10 | **Second boss**, first modifier. CRYPTOGRAPHER unlocks. |

---

## 11. Tuning workflow

1. Edit `Balance.kt` (or a stat in `AgentType` / `EnemyType`).
2. `./gradlew test` — the curve-shape assertions catch structural mistakes
   immediately.
3. For play-feel changes, the simulation tests in `GameEngineTest` can be
   pointed at any wave and run headlessly in well under a second, which makes it
   practical to check a change across fifty waves without touching a device.

---

## 12. Revision: difficulty pass and the meta-loop

A play report — wave 9, 24 integrity left, every agent still at level 1 — made
the problem concrete. The enemies were not the issue; the economy was. Upgrades
cost more than a second agent, so the correct play was always "buy another
level-1 tower", and the board never got stronger, only wider.

### What was softened

| | Before | After |
| --- | --- | --- |
| `STARTING_CRYPTO` | 90 | **120** |
| `HEALTH_LINEAR` | 0.08 | **0.06** |
| `HEALTH_POWER_COEFF` | 0.010 | **0.008** |
| `SPEED_PER_WAVE` | 0.006 | **0.005** |
| `SPEED_MAX_MULTIPLIER` | 1.65 | **1.55** |
| Enemy count | `6 + w×1.15 + w^1.12×0.30` | **`5 + w×0.95 + w^1.10×0.25`** |
| `MAX_WAVE_ENEMIES` | 46 | **42** |
| Spawn interval floor | 0.34 s | **0.38 s** |
| Elite chance begins | wave 5 | **wave 7** |
| Elite chance cap | 32% | **28%** |
| Armour from wave number | every 12 waves | **every 15 waves** |
| Wave clear bonus | `8 + w×2` | **`12 + w×3`** |
| Boss health per cycle | +30% | **+26%** |

At wave 9 that is ×1.66 health instead of ×1.88, 16 packets instead of 19, and
roughly four times the accumulated crypto.

### Boss completion bonus

Boss waves are where a run either stabilises or dies, so they are now also where
it gets the capital to rebuild.

```
bossClearBonus(wave) = (20 + (cycle − 1) × 15) × rewardMultiplier(wave)
```

| Constant | Value |
| --- | --- |
| `BOSS_CLEAR_BONUS_BASE` | `20` |
| `BOSS_CLEAR_BONUS_STEP` | `15` |

| Wave | Cycle | Bonus |
| --- | ---: | ---: |
| 5 | 1 | ◇ 23 |
| 10 | 2 | ◇ 47 |
| 20 | 4 | ◇ 111 |
| 50 | 10 | ◇ 419 |

Paid on clearing the wave, on top of the ordinary clear bonus.

### Agents now reach level 100

Ten levels made each upgrade a large, rare, expensive decision. A hundred makes
them small, frequent and cheap — which is the difference between an economy you
can engage with and one you can only watch.

| Constant | Before | After |
| --- | --- | --- |
| `MAX_AGENT_LEVEL` | 10 | **100** |
| `UPGRADE_DAMAGE_GROWTH` | 0.26 / level | **0.20 / level** |
| `UPGRADE_RATE_GROWTH` | 0.055 | **0.018** |
| `UPGRADE_RANGE_GROWTH` | 0.035 | **0.007**, capped at ×1.75 |

```
statAtLevel(L) = base × (1 + growth × (L − 1))
upgradeCost(base, level) = base × (0.30 + 0.14 × level)
```

For a FIREWALL (base ◇40):

| Level → | Cost | Cumulative |
| --- | ---: | ---: |
| 1→2 | ◇ 17 | ◇ 57 |
| 5→6 | ◇ 40 | ◇ 158 |
| 10→11 | ◇ 68 | ◇ 413 |
| 25→26 | ◇ 152 | ◇ 1,700 |
| 50→51 | ◇ 292 | ◇ 5,700 |
| 99→100 | ◇ 566 | ◇ 28,900 |

Damage reaches ×20.8 at level 100. **Range is the one capped stat** — unbounded
range would make node placement stop mattering long before level 100.

Because a hundred taps is an ordeal rather than a design, the management panel
buys in bulk: `+1`, `+10`, and `MAX` (which spends down to the last affordable
level and stops).

### € BUDGET and CORE FIRMWARE

The meta-loop. € is banked every tenth wave and **survives the run that earned
it**; it buys a permanent damage multiplier that applies to every agent in every
future match.

| Constant | Value |
| --- | --- |
| `BUDGET_MILESTONE_INTERVAL` | `10` waves |
| `BUDGET_BASE` | `5` |
| `MAX_FIRMWARE_LEVEL` | `10,000` |
| `FIRMWARE_DAMAGE_PER_LEVEL` | `0.005` (+0.5%) |

```
budgetAward(wave)   = 5 × (wave/10)²          // quadratic in the milestone
firmwareCost(level) = 3 + level               // € for the next level
firmwareDamage(L)   = 1 + L × 0.005
```

The award is **quadratic in the milestone index**, so depth is what pays:

| Wave | € |
| --- | ---: |
| 10 | 5 |
| 20 | 20 |
| 30 | 45 |
| 50 | 125 |
| 100 | 500 |

A run to wave 50 banks € 275 in total — one deep run is worth more than five
shallow ones.

| Firmware level | Damage | Cumulative € |
| ---: | ---: | ---: |
| 10 | ×1.05 | 75 |
| 50 | ×1.25 | 1,375 |
| 100 | ×1.50 | 5,250 |
| 1,000 | ×6.00 | 502,500 |
| 10,000 | ×51.0 | ~50,000,000 |

The cap is nominal. At that cost curve nobody reaches it, which is the point of
calling the scaling indefinite.

Firmware is applied **before** armour and before the counter table, so it helps
a Cryptographer against encryption exactly as much as it helps a Firewall
against plain traffic. It does not touch enemy health, rewards or wave
composition — only the player's side of the fight.

#### Why one firmware level is invisible

A playtest reported buying one firmware level and not noticing a difference.
That is the tuning working as written, not a fault: **one level is +0.5%**. A
Firewall dealing 12 damage a shot goes to 12.06. Nothing on screen changes —
not the shots-to-kill on any enemy, not the wave clear time. The multiplier is
designed to be bought in dozens, not in ones, and the cost curve assumes it:
€ 80, the total banked by a run reaching wave 40, buys 10 levels and ×1.05.

Two consequences worth stating plainly:

- **The system is a long-haul curve, not a purchase you feel.** It is meant to
  be the thing that makes run forty easier than run four. Across the first
  several deep runs it is worth a few percent, and it stays worth a few percent
  until the hundreds.
- **The readout used to round the first few levels away.** Both screens printed
  the multiplier with two decimals, so level 1 (×1.005) displayed as `×1.00`
  and level 2 as `×1.01` — the player paid € 3 and watched a number not move.
  Worse, the per-level figure was computed as `(0.005 * 100).toInt()`, which
  truncates to zero and told the player outright that an upgrade does nothing.
  Both were display faults; the arithmetic underneath was always correct.

**Fixed in 1.5.1.** The multiplier now prints with three decimals, which is the
right precision for this constant specifically: the step is 0.005, so *every
single level changes the last digit* and no purchase is ever invisible. The
per-level figure prints as `+0.5%`, the install panel says which multiplier the
next level takes you to before you buy it, and a bulk purchase states its total
gain. A test walks 400 consecutive levels and asserts no two print the same.

The **tuning** was left alone: +0.5% a level is the intended curve, and the
cost table is built around buying it in dozens.

### Deployment spots

48 spots became 79 in 1.6.0. The additions are the mid-map corridor between
the two routes — strong ground, covering both at once — and the outer band
down the right-hand side, which is weak ground that only a long-ranged agent
can use at all. A spot is offered if *any* agent can work from it, and the
deploy overlay dims the ones the agent in hand cannot reach.

Net effect on difficulty: mildly easier, concentrated in the mid-map corridor.
The outer-band spots cost the same as any other and cover far less, so they are
a choice rather than a free upgrade.

### Threat roster change

`[P] PACKET` was removed. A packet is ordinary traffic, so naming the baseline
enemy after it taught the player something untrue. The baseline threat is now
`[SQL] SQL INJECTION` — the most common real attack there is — with
`[SQL2] BLIND SQLi` as a tougher armoured variant entering around wave 11.

A test asserts `PACKET` cannot come back and that the `[P]` glyph now belongs
solely to the IPS agent.

---

## 13. Revision: the serpentine map and the unkillable boss

A play report said bosses could not be killed "no matter what I tried", and that
straight lanes were the reason. Both halves turned out to be right, for a reason
worth writing down.

### Why bosses were unkillable

Two causes compounded, and only one was about numbers.

**The targeting bug.** Bosses move slower than the trash escorting them. Under
FIRST targeting — "closest to the server" — every escort in the wave therefore
outranks the boss permanently. The whole board shot the escorts while the boss
strolled the entire route untouched. No amount of upgrading fixed it, because
upgrading does not change *what* gets shot.

`CombatSystem.selectTarget` now gives bosses priority under FIRST and STRONGEST.
LAST and WEAKEST deliberately keep ignoring bosses, which is what makes them
useful: they are how a player assigns an agent to escort clean-up.

**Health tuned for coverage that does not exist.** Instrumenting a wave-5 fight
showed a five-agent board kept the boss under fire for **11 seconds of its
50-second journey**. Five towers cannot cover a 2,353-unit route. Boss health had
been set as though they could.

| Constant | Before | After |
| --- | --- | --- |
| `BOSS` base health | 520 | **250** |
| `BOSS` base speed | 30 | **44** |
| `BOSS` base armour | 3 | **2** |
| First-cycle term | 1.00 | **0.65** (`BOSS_FIRST_CYCLE_SOFTENING`) |
| Per-cycle term | +0.26 | **+0.45** (`BOSS_CYCLE_SCALING`) |

```
bossHealth(wave) = 250 × healthMultiplier(wave) × (0.65 + (cycle − 1) × 0.45)
```

| Wave | Cycle | Boss HP |
| --- | ---: | ---: |
| 5 | 1 | 221 |
| 10 | 2 | 479 |
| 20 | 4 | 1,270 |
| 30 | 6 | 2,436 |
| 50 | 10 | 5,945 |
| 100 | 20 | 19,619 |

Three level-5 FIREWALLs now clear the wave-5 boss with the server untouched —
asserted permanently by `the wave five boss can be killed by a modest board`.
Wave 30 and beyond climb steeply, which is where difficulty belongs.

### The map

Three straight corridors became **two serpentine routes**.

| | Straight lanes | Serpentine |
| --- | ---: | ---: |
| Routes | 3 | 2 |
| Route length | 1,378 | **2,353** |
| Best node coverage at base range | 295 units | **732 units** |
| Deployment nodes | 32 (fixed grid) | 31 (derived) |

A straight lane gives a tower one pass at each target. A route that doubles back
past the same pocket gives it three or four. That is the whole reason a slow,
heavy target stops being invulnerable.

The two routes deliberately come close twice — running parallel across the
middle, then merging at (1150, 383) for the final approach — so a tower in
either convergence pocket covers both routes at once. That is the decision the
map is built around.

Boss routes rotate by cycle, so consecutive boss waves never arrive down the
same route and a board built for one side is not a permanent answer.

### Nodes are derived, not placed

Deployment nodes are no longer a hand-written grid. A candidate grid is filtered
to positions that clear every route, sit clear of the server, and actually cover
some route (`MIN_NODE_COVERAGE`). Move a waypoint and the nodes follow. It also
means no node exists that is useless to build on.

### What the composition test now claims

The old test asserted a mixed board beats stacking one agent type. Measuring it
with **equal crypto** rather than equal agent count showed that is simply not
true: focused ANALYST, focused ROOT ADMIN and a counter-led mix all land within a
few waves of each other.

What is true, and what the suite now guards, is that agent **choice** carries
real weight — the same budget spent on specialists goes far further than spent
on the cheap all-rounder. The counter multipliers themselves are asserted
directly elsewhere.

---

## 14. Revision: pockets that looked buildable but were not

A playtest marked six obvious tower spots inside the serpentine bends. Five were
being silently rejected, for a structural reason worth stating plainly.

A node needs `LANE_HEIGHT / 2 + NODE_RADIUS + margin` = **58 units** of clearance
from a route centreline. So a pocket between two route levels needs **116 units**
before a tower can stand in it. Half the pockets were 90 — they read as prime
real estate and could never be built on.

| | Before | After |
| --- | ---: | ---: |
| Pocket height | 122 / **90** (mixed) | **122** (uniform) |
| Convergence gap | 72 | 72 (unchanged, deliberate) |
| Node rows | uniform grid | **one per pocket centre** + margins |
| Node columns | 10 | **12** |
| Deployment nodes | 31 | **48** |
| Best node coverage | 732 | **899** |

The route levels are now named constants the waypoints are built from:

```kotlin
private const val POCKET_HEIGHT = 122f
private const val CONVERGENCE_GAP = 72f

const val A1 = 100f
const val A2 = A1 + POCKET_HEIGHT     // 222
const val A3 = A2 + POCKET_HEIGHT     // 344
const val B1 = A3 + CONVERGENCE_GAP   // 416
const val B2 = B1 + POCKET_HEIGHT     // 538
const val B3 = B2 + POCKET_HEIGHT     // 660
```

The convergence stays tight on purpose: the two routes are meant to run close
there and be covered from the pockets above and below, not built inside. A test
asserts no node ever lands in it.

Candidate rows are derived from the pockets rather than spread evenly. The old
uniform grid missed one pocket **by two units** — close enough to look like a
bug in the filter rather than in the spacing, which is exactly why the rule now
lives next to the numbers and is asserted in `MapGeometryTest`.
