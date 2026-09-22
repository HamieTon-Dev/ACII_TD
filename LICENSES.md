# Licenses

## Packet Bastion: ASCII Defense

All first-party content in this repository is original work created for this
project:

- **Source code** — every Kotlin file under
  `app/src/main/java/com/packetbastion/asciidefense/`.
- **Artwork** — the adaptive launcher icon
  (`app/src/main/res/drawable/ic_launcher_*.xml`) is original vector artwork.
  All in-game visuals are drawn procedurally at runtime from ASCII characters
  and primitive shapes; there are no image assets.
- **Audio** — the game ships **no audio files**. Every sound effect and the
  ambient loop are synthesized at runtime by
  `app/src/main/java/com/packetbastion/asciidefense/audio/ToneSynth.kt` from the
  recipes in `SoundBank.kt`. No sampled, commercial or third-party audio is
  used, copied or derived from.
- **Text** — the Codex entries, tutorial copy and all in-game strings are
  original writing.

No proprietary game code, assets or audio were copied from any source.

---

## Third-party dependencies

Every runtime and build dependency is **Apache License 2.0**, which permits
redistribution in a compiled application without additional obligations beyond
attribution.

### Runtime dependencies (shipped in the APK)

| Dependency | Version | License |
| --- | --- | --- |
| `androidx.core:core-ktx` | 1.15.0 | Apache-2.0 |
| `androidx.lifecycle:lifecycle-runtime-ktx` | 2.8.7 | Apache-2.0 |
| `androidx.lifecycle:lifecycle-runtime-compose` | 2.8.7 | Apache-2.0 |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.8.7 | Apache-2.0 |
| `androidx.activity:activity-compose` | 1.9.3 | Apache-2.0 |
| `androidx.compose:compose-bom` | 2024.12.01 | Apache-2.0 |
| `androidx.compose.ui:ui` | via BOM | Apache-2.0 |
| `androidx.compose.ui:ui-graphics` | via BOM | Apache-2.0 |
| `androidx.compose.ui:ui-tooling-preview` | via BOM | Apache-2.0 |
| `androidx.compose.material3:material3` | via BOM | Apache-2.0 |
| `androidx.navigation:navigation-compose` | 2.8.5 | Apache-2.0 |
| `androidx.datastore:datastore-preferences` | 1.1.1 | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.7.3 | Apache-2.0 |
| Kotlin standard library | 2.0.21 | Apache-2.0 |

### Debug-only dependencies (not in the release APK)

| Dependency | Version | License |
| --- | --- | --- |
| `androidx.compose.ui:ui-tooling` | via BOM | Apache-2.0 |

### Test-only dependencies (not shipped)

| Dependency | Version | License |
| --- | --- | --- |
| `junit:junit` | 4.13.2 | Eclipse Public License 1.0 |
| `org.robolectric:robolectric` | 4.14.1 | Apache-2.0 |
| `androidx.compose.ui:ui-test-junit4` | via BOM | Apache-2.0 |
| `androidx.compose.ui:ui-test-manifest` | via BOM | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test` | 1.9.0 | Apache-2.0 |

These are test-scope dependencies only. They are not compiled into, linked
against or distributed with either APK. (`ui-test-manifest` is declared
`debugImplementation` because Compose's test rule requires its manifest entry to
be present in the debug variant; it is excluded from the release build.)

### Build tooling (not shipped)

| Tool | Version | License |
| --- | --- | --- |
| Android Gradle Plugin | 8.7.3 | Apache-2.0 |
| Kotlin Gradle Plugin | 2.0.21 | Apache-2.0 |
| Gradle | 8.11.1 | Apache-2.0 |

---

## Fonts

The game uses `android.graphics.Typeface.MONOSPACE` — the monospace face already
present on the device. **No font files are bundled**, so there is no font
licensing obligation.

Bundling JetBrains Mono (SIL Open Font License 1.1) or Roboto Mono
(Apache-2.0) would both be permissible and would make ASCII box-drawing metrics
identical across OEM font stacks. The platform font was chosen instead to keep
the APK small; see Known Issues in `DEVELOPMENT_STATUS.md`.

---

## Build-environment note

`settings.gradle.kts` lists Google's read-only Maven Central mirror
(`maven-central.storage-download.googleapis.com`) ahead of `mavenCentral()`.
This is a network convenience only — it changes where artifacts are fetched
from, not which artifacts, and `mavenCentral()` remains in the list as a
fallback. It has no licensing implications.

---

## Apache License 2.0

Full text: <https://www.apache.org/licenses/LICENSE-2.0>

## Eclipse Public License 1.0

Full text: <https://www.eclipse.org/legal/epl-v10.html>
