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

---

## Rendered previews

These two were **not** taken on a device. They are the real Compose screens
rasterized under Robolectric (`GraphicsMode.NATIVE`) at the game's own
1600×760 world size, which is the only way this build environment can produce
an accurate picture of a screen — it has no KVM, so its emulator renders at a
few frames per second.

| File | What it shows |
| --- | --- |
| `menu-google-play.png` | Main menu with the GOOGLE PLAY entry and the AURORA living background |
| `cloud-save-unlinked.png` | The Google Play account screen before a Google account is linked |
| `cloud-save-linked.png` | The same screen once progress is following the account |

Treat them as accurate for layout, wording and colour, and as *unverified* for
anything that depends on a real device: system font fallback, animation and
the exact look of the backdrop in motion.

To regenerate one, render the screen into a `Bitmap` from a Robolectric test
the way `MenuBackdropRenderTest` does — draw `activity.window.decorView` into a
`Canvas` rather than calling `captureToImage()`, which waits for a window
redraw that never arrives while the test clock is driven by hand.
