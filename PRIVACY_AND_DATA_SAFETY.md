# Privacy and Data Safety

An inventory of every third-party SDK in the build and every category of data
it can collect, written so that Play's **Data Safety** form can be filled in
from facts rather than from guesses.

> **This is an engineering inventory, not legal advice, and not a privacy
> policy.** It tells you what the code does. A lawyer, or at minimum a careful
> reading of each SDK's own current disclosure, should confirm the answers you
> submit. SDK behaviour changes between versions; re-check this file whenever a
> dependency is upgraded.

---

## 1. What the game itself collects: nothing

Worth stating first because it is unusual and it simplifies most of the form.

- Every asset is **generated at runtime** — the music, the sound effects, the
  graphics. The APK ships no audio files and no photographic assets.
- All progress (saved run, unlocked agents, statistics, settings, purchases)
  is stored **on the device** via Jetpack DataStore, in the app's private
  storage.
- There is **no account, no login and no analytics**. The game does not have a
  server.
- The "leaderboard" is local to the device unless Play Games Services is
  configured, and the callsign a player types is a freely chosen string that
  the game never validates as a real name.
- The app opens **no network connection at all** in a build with no Play ids
  configured. It selects no-op gateways and behaves as a fully offline game.

Everything below is therefore about the SDKs, not about the game.

---

## 2. Third-party SDKs in the build

| SDK | Version | Why it is here | Optional? |
| --- | --- | --- | --- |
| Google Mobile Ads (AdMob) | 24.5.0 | the interstitial after a lost run, and the rewarded ad that grants a revive | yes — absent ids, no ads |
| Google User Messaging Platform (UMP) | 3.2.0 | the GDPR / US-states consent form in front of advertising | ships with Ads |
| Google Play Billing | 7.1.1 | in-app purchases | yes |
| Play Games Services v2 | 20.1.2 | optional cloud save | yes — off by default |
| AndroidX / Jetpack Compose | BOM 2024.12.01 | UI toolkit | no — collects nothing |
| Kotlin / kotlinx.serialization | 2.0.21 / 1.7.3 | language and save format | no — collects nothing |

> **On the Mobile Ads version.** 24.5.0 rather than the newest 25.x. The 25.x
> artifacts are compiled with Kotlin 2.3 metadata, which this project's Kotlin
> 2.0.21 toolchain cannot read; adopting it means a Kotlin and Compose-compiler
> upgrade, which is a separate piece of work with its own risk. 24.5.0 is a
> current, supported release and meets every requirement here.

---

## 3. What each SDK can collect

### Google Mobile Ads — the only real answer on this form

This is where essentially all the Data Safety disclosure comes from.

| Category | Collected | Shared | Purpose |
| --- | --- | --- | --- |
| **Device or other IDs** — the Advertising ID (AAID) | yes | yes | advertising, fraud prevention |
| **App activity** — ad interactions, impressions, clicks | yes | yes | advertising, analytics |
| **App info and performance** — crashes, diagnostics | yes | yes | analytics |
| **Location** — approximate, derived from IP | yes | yes | advertising |
| **Device info** — model, OS version, locale, screen | yes | yes | advertising |

Notes for the form:

- This is **collected and shared**, not merely collected: it goes to Google
  and to Google's ad partners.
- It is **not** encrypted-at-rest-only data the app holds; it leaves the
  device.
- Users **can** request deletion — the AAID is resettable and deletable from
  Android's own settings, which is the correct answer to "can users request
  data deletion".
- Data collection is **not optional per-user** in the sense Play means, but
  consent is honoured where required (see UMP below), and a user who declines
  gets no ads at all in this build.

Google's own current disclosure is authoritative and should be checked before
submitting:
<https://developers.google.com/admob/android/privacy/play-data-disclosure>

### Google User Messaging Platform

Collects consent state and a coarse region signal in order to decide whether a
form is required, and stores the player's answer on the device. Its whole
purpose is to *restrict* what Mobile Ads may do.

In this app it is wired so that **the Mobile Ads SDK is not initialised at all
until consent has settled and permits ads** — `GameViewModel.startConsentThenAds`.
A player who declines is not shown ads and no ad request is made. Where Google
reports that a privacy entry point is required, Settings gains a **PRIVACY
OPTIONS** row that reopens the form, so consent can be withdrawn without
reinstalling.

### Google Play Billing

| Category | Collected | Shared | Purpose |
| --- | --- | --- | --- |
| **Purchase history** | yes | yes | app functionality |

The app never sees a payment instrument. It receives a purchase token and an
order id from Play, stores the order id locally so a consumable cannot pay out
twice, and nothing else. No card number, no name, no address ever reaches this
code.

### Play Games Services v2 — only if configured

Off unless `cyops.games.appId` is set at build time.

| Category | Collected | Shared | Purpose |
| --- | --- | --- | --- |
| **Personal info** — Play Games player id and display name | yes | yes | app functionality |
| **App activity** — saved game data | yes | yes | app functionality |

If you ship without a Play Games project id, none of this applies and it should
not be declared.

### AndroidX, Compose, Kotlin, kotlinx.serialization

Collect nothing. No disclosure.

---

## 3a. Encryption: account linking and cloud save

The question Play's Data Safety form asks is whether user data is **encrypted
in transit**. The answer is yes, and it is worth writing down *why*, because the
reason is structural rather than a setting somebody remembered to enable.

### The app opens no connections of its own

There is no HTTP client, no socket and no URL anywhere in this codebase —
verified, not assumed. Every byte that leaves the device goes through a Google
SDK:

| Path | Carried by | Transport |
| --- | --- | --- |
| Account linking | Play Games Services v2 (`GamesSignInClient`) | TLS to Google |
| Cloud save read/write | Play Games Services (`SnapshotsClient`) | TLS to Google |
| Purchases | Play Billing | TLS to Google |
| Ads and consent | Mobile Ads SDK, UMP | TLS to Google |

Because the app never makes a raw request, there is no code path that *could*
send anything unencrypted. `usesCleartextTraffic="false"` is declared on the
application anyway, so the prohibition is a property of the built artifact that
can be read back out of it rather than a default inherited from the target SDK.
`tools/verify-release.sh` checks it on the APK's compiled manifest before every
release.

### Account linking never touches a credential

The app does not implement sign-in. It asks Play Games to sign the player in
and receives two things back: a boolean, and a display name. There is no
password, no OAuth token, no refresh token and no session — nothing of that
kind is handled, stored or transmitted by this code, so there is nothing of
that kind to encrypt or to leak. The "link account" button is a handover to
Google and back.

### What the cloud save actually contains

A JSON document, UTF-8 encoded, written into the player's **own** Play Games
snapshot on their **own** Google account:

- lifetime statistics and unlocked agents;
- € budget and firmware level;
- cosmetic choices;
- the run in progress, if any;
- the local leaderboard, which is the player's own past scores;
- `PlayerIdentity` — a "callsign" the player types.

The callsign is the only free-text field in the app, and it is sanitised to
`A–Z`, `0–9`, `_` and `-`, capped at 16 characters, on the way in. It cannot
hold an email address, a phone number or a sentence, which is deliberate: the
ASCII UI needs it to fit a monospace cell, and the side effect is that the one
place a player could type personal data into this game will not accept it.

Two things are deliberately **not** in the payload. **Entitlements**, because
Play is the only honest source of truth for what someone has bought.
**Settings**, because they belong to a device rather than to a player.

### At rest

| Where | Protection |
| --- | --- |
| On the device | Jetpack DataStore in app-private storage, inside the app sandbox, on a file-based-encrypted userdata partition (mandatory since Android 10) |
| In the Play Games snapshot | Google's infrastructure, encrypted at rest |
| In Android Auto Backup | Encrypted in transit and at rest; on Android 9+ additionally encrypted with a client-side secret derived from the device lock screen, so Google itself cannot read it |

**The local DataStore is deliberately not additionally encrypted**, and that is
a decision rather than an omission. Play's User Data policy requires secure
*transmission*; it does not require app-private files to be encrypted at rest,
and the platform already encrypts the partition they sit on. Wrapping them in
Jetpack Security would add a hardware-keystore dependency, a migration path for
every existing install, and a failure mode where an unavailable or invalidated
key destroys a player's entire progress — which is a real risk taken on behalf
of data that is game statistics and a sixteen-character handle. If anything
genuinely sensitive is ever stored, this decision has to be revisited, and that
is the trigger to look for.

### Auto Backup

`allowBackup="true"` with `datastore/` included in both `backup_rules.xml` and
`data_extraction_rules.xml`, covering cloud backup and device-to-device
transfer. This is how progress survives a new phone for a player who never
links a Google account, and it is the floor the no-op cloud-save gateway falls
back to. It carries exactly the same data as the cloud save above.

---

## 4. Permissions, and why each one is there

The manifest declares four. The rest arrive from the SDKs and cannot be removed
without breaking them.

**Declared by the game:**

| Permission | Why |
| --- | --- |
| `VIBRATE` | haptics; switchable off in Settings |
| `INTERNET` | ads, consent, billing, optional cloud save — no gameplay feature uses it |
| `ACCESS_NETWORK_STATE` | required by the Mobile Ads SDK so it does not request with no network |
| `com.google.android.gms.permission.AD_ID` | the Mobile Ads SDK reads the advertising id |

**Merged in from SDKs:**

| Permission | From | Note |
| --- | --- | --- |
| `ACCESS_ADSERVICES_AD_ID` | Mobile Ads | Android Privacy Sandbox |
| `ACCESS_ADSERVICES_ATTRIBUTION` | Mobile Ads | Privacy Sandbox attribution |
| `ACCESS_ADSERVICES_TOPICS` | Mobile Ads | Privacy Sandbox topics |
| `WAKE_LOCK` | Mobile Ads | ad serving |
| `FOREGROUND_SERVICE` | Mobile Ads | ad serving |
| `com.android.vending.BILLING` | Play Billing | purchases |
| `…DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | AndroidX | internal, self-scoped |

Deliberately **not** removed: the five Mobile Ads permissions. They could be
stripped with a manifest-merger `tools:node="remove"`, and the temptation is
real because the game does not want a wake lock. But they are declared by the
SDK for its own runtime paths, removing one turns a supported configuration
into an unsupported one, and the failure mode is a crash inside Google's code
rather than a clean error. The trade — a slightly longer permission list
against a stability risk in the ad path — is not worth taking. This is also the
answer to give if a reviewer asks why a tower-defence game requests
`FOREGROUND_SERVICE`.

The game requests **no** camera, microphone, location, contacts, storage,
phone-state or nearby-devices permission, and asks for no runtime permission
at all.

---

## 5. Answering the Data Safety form

Assuming a release **with** AdMob configured and **without** Play Games:

**Does your app collect or share any of the required user data types?**
→ **Yes.**

Declare:

- **Location → Approximate location** — collected, shared, advertising.
- **Personal info** — nothing, unless Play Games is configured.
- **Financial info → Purchase history** — collected, shared, app functionality.
- **App activity → App interactions** — collected, shared, advertising and
  analytics.
- **App info and performance → Crash logs, Diagnostics** — collected, shared,
  analytics.
- **Device or other IDs** — collected, shared, advertising.

**Is all of the user data collected by your app encrypted in transit?**
→ **Yes.** The app opens no connections of its own; everything goes through a
Google SDK over TLS, and `usesCleartextTraffic="false"` is declared on the
application and verified against the built artifact. See §3a.

**Do you provide a way for users to request that their data be deleted?**
→ **Yes.** The advertising id is resettable and deletable in Android settings;
the app's own data is removed by uninstalling, and RESET PROGRESS in Settings
erases it in place.

**Ads:** the app contains ads. **Yes.**

**Target audience:** not children. Declaring a child audience changes the ad
rules substantially and this game is not built for one.

---

## 6. Your privacy policy must say

A URL is mandatory because the app shows ads. It has to cover, at minimum:

- that the app displays ads served by Google AdMob;
- that AdMob collects the advertising id, approximate location from IP, device
  information and ad-interaction data;
- that users can reset or delete the advertising id in Android settings;
- how to withdraw consent (the PRIVACY OPTIONS control in Settings, where
  Google requires it);
- that purchases are processed by Google Play and no payment details reach the
  app;
- that game progress is stored on the device;
- a contact address.

Google's advertising privacy page is the reference for the AdMob half:
<https://policies.google.com/technologies/partner-sites>

---

## 7. Re-check this file when

- a dependency in `gradle/libs.versions.toml` is upgraded;
- Play Games Services is configured for the first time;
- any analytics, crash-reporting or attribution SDK is added — none is present
  today, and adding one changes several answers above.
