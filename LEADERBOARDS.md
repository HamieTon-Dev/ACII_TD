# Worldwide leaderboards — one per level and difficulty

Owner's choice (2026-10-01): **B, all per level and difficulty.** That is 30
Play Games leaderboards, plus the two all-level ones that already exist
(Standard and HACK:AI, configured in `secrets.properties`).

## Creating them (Play Console → Play Games Services → Leaderboards)

For each row below: **Add leaderboard**, name it exactly as shown, then set

| Setting | Value |
| --- | --- |
| Score format | Numeric, 0 decimal places |
| Ordering | Larger is better |
| Limits | none |

Play Console then shows the board's **ID** (starts `CgkI`). Paste it into the
last column here and send me the table (or paste the IDs into
`app/src/main/java/com/cyopstd/game/ads/LevelLeaderboards.kt` yourself).
Leaderboard IDs are not secret, so they live in the code, not in secrets.

A board left empty simply doesn't exist yet: that level and difficulty keeps
its on-device ranking and nothing is posted for it.

| # | Name | Key | ID (CgkI…) |
| --- | --- | --- | --- |
| 1 | NETWORK PERIMETER · NETWORK DEFENCE | `perimeter\|standard` |  |
| 2 | NETWORK PERIMETER · HACK:AI | `perimeter\|hack_ai` |  |
| 3 | NETWORK PERIMETER · KERNEL MODE | `perimeter\|kernel_mode` |  |
| 4 | HUGGING-FACE · NETWORK DEFENCE | `hugging_face\|standard` |  |
| 5 | HUGGING-FACE · HACK:AI | `hugging_face\|hack_ai` |  |
| 6 | HUGGING-FACE · KERNEL MODE | `hugging_face\|kernel_mode` |  |
| 7 | NEURAL-MESH · NETWORK DEFENCE | `neural_mesh\|standard` |  |
| 8 | NEURAL-MESH · HACK:AI | `neural_mesh\|hack_ai` |  |
| 9 | NEURAL-MESH · KERNEL MODE | `neural_mesh\|kernel_mode` |  |
| 10 | DUCK-USB · NETWORK DEFENCE | `duck_usb\|standard` |  |
| 11 | DUCK-USB · HACK:AI | `duck_usb\|hack_ai` |  |
| 12 | DUCK-USB · KERNEL MODE | `duck_usb\|kernel_mode` |  |
| 13 | DDoS · NETWORK DEFENCE | `ddos\|standard` |  |
| 14 | DDoS · HACK:AI | `ddos\|hack_ai` |  |
| 15 | DDoS · KERNEL MODE | `ddos\|kernel_mode` |  |
| 16 | MIRAI · NETWORK DEFENCE | `trident\|standard` |  |
| 17 | MIRAI · HACK:AI | `trident\|hack_ai` |  |
| 18 | MIRAI · KERNEL MODE | `trident\|kernel_mode` |  |
| 19 | RING-ZERO · NETWORK DEFENCE | `spiral\|standard` |  |
| 20 | RING-ZERO · HACK:AI | `spiral\|hack_ai` |  |
| 21 | RING-ZERO · KERNEL MODE | `spiral\|kernel_mode` |  |
| 22 | WANNACRY · NETWORK DEFENCE | `zigzag\|standard` |  |
| 23 | WANNACRY · HACK:AI | `zigzag\|hack_ai` |  |
| 24 | WANNACRY · KERNEL MODE | `zigzag\|kernel_mode` |  |
| 25 | HONEYPOT · NETWORK DEFENCE | `helix\|standard` |  |
| 26 | HONEYPOT · HACK:AI | `helix\|hack_ai` |  |
| 27 | HONEYPOT · KERNEL MODE | `helix\|kernel_mode` |  |
| 28 | HEARTBLEED · NETWORK DEFENCE | `braid\|standard` |  |
| 29 | HEARTBLEED · HACK:AI | `braid\|hack_ai` |  |
| 30 | HEARTBLEED · KERNEL MODE | `braid\|kernel_mode` |  |
