# Screenshots

This directory is intentionally empty in the initial release.

The environment the project was built in has no KVM acceleration, so its
emulator renders in software at a few frames per second — fine for confirming
that the app installs, launches and draws correctly, but not for producing
screenshots that represent how the game actually looks and moves.

To capture them on a real device or an accelerated emulator:

```bash
adb exec-out screencap -p > docs/screenshots/menu.png
```

The README links to these filenames:

| Screen | File |
| --- | --- |
| Main menu | `menu.png` |
| Battlefield mid-wave | `battlefield.png` |
| Boss warning | `boss.png` |
| Agent management panel | `upgrade.png` |
| Codex | `codex.png` |
