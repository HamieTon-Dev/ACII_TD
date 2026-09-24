package com.cyopstd.game

import com.cyopstd.game.save.CloudSave
import com.cyopstd.game.save.PlayerIdentity
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The claim behind "encrypted in transit".
 *
 * Play's Data Safety form asks whether all user data is encrypted in transit,
 * and this app answers yes for a structural reason rather than a configured
 * one: **it opens no connections of its own.** Account linking, cloud save,
 * purchases, ads and consent all go through a Google SDK, and every one of
 * those is TLS to Google. There is no code path that could send anything
 * unencrypted because there is no code that sends anything at all.
 *
 * A claim of that shape is only true while it stays true. One `HttpURLConnection`
 * added in a year's time makes the Data Safety answer wrong, and nothing else
 * in the build would notice. So it is asserted here.
 *
 * `usesCleartextTraffic="false"` on the application is the belt to this braces,
 * and is checked against the compiled manifest of the built APK by
 * `tools/verify-release.sh` — a unit test cannot see the merged manifest, and
 * the merged *text* manifest is not trustworthy for this anyway: it carries
 * source comments through, and an earlier grep of it reported a cleartext
 * declaration that turned out to be the wording of a comment explaining the
 * rule.
 */
class DataEncryptionTest {

    private fun mainSources(): List<File> =
        File("src/main/java").walkTopDown().filter { it.extension == "kt" }.toList()

    @Test
    fun `the app opens no network connections of its own`() {
        val sources = mainSources()
        assertTrue("no sources found; the path is wrong", sources.size > 20)

        // Comments discuss networking constantly -- the whole point of several
        // of these files is to explain what does and does not go over a wire --
        // so only code lines are examined.
        val forbidden = listOf(
            "HttpURLConnection" to "a raw HTTP connection",
            "OkHttpClient" to "an OkHttp client",
            "java.net.Socket" to "a raw socket",
            "openConnection(" to "an opened URL connection",
            "URLConnection" to "a URL connection"
        )

        val offences = mutableListOf<String>()
        for (file in sources) {
            file.readLines().forEachIndexed { index, raw ->
                val line = raw.trim()
                if (line.startsWith("*") || line.startsWith("//") || line.startsWith("/*")) {
                    return@forEachIndexed
                }
                for ((needle, what) in forbidden) {
                    if (line.contains(needle)) {
                        offences += "${file.name}:${index + 1} uses $what"
                    }
                }
            }
        }

        assertTrue(
            "The app must not make network calls of its own -- everything goes " +
                "through a Google SDK over TLS, and that is what the Data Safety " +
                "form's 'encrypted in transit' answer rests on. Found:\n" +
                offences.joinToString("\n"),
            offences.isEmpty()
        )
    }

    @Test
    fun `no credential or token is stored`() {
        // The app does not implement sign-in. It asks Play Games to do it and
        // receives a boolean and a display name. There is no password, token
        // or session anywhere, so there is nothing of that kind to leak.
        val offences = mutableListOf<String>()
        for (file in mainSources()) {
            file.readLines().forEachIndexed { index, raw ->
                val line = raw.trim()
                if (line.startsWith("*") || line.startsWith("//") || line.startsWith("/*")) {
                    return@forEachIndexed
                }
                for (needle in listOf("accessToken", "refreshToken", "idToken", "authToken")) {
                    if (line.contains(needle)) offences += "${file.name}:${index + 1} ($needle)"
                }
            }
        }
        assertTrue(
            "a credential appeared in the app; account linking is supposed to be " +
                "a handover to Google and back:\n" + offences.joinToString("\n"),
            offences.isEmpty()
        )
    }

    // ------------------------------------------------ what leaves the device

    @Test
    fun `the callsign cannot hold personal data`() {
        // The only free-text field in the game, and the only place a player
        // could type something personal into it. Sanitised on the way in, so a
        // stored name is never anything but a handle.
        val attempts = listOf(
            "someone@example.com" to "SOMEONE",
            "+44 7700 900123" to "447700900123",
            "Jane Doe, 12 High St" to "JANEDOE12HIGH",
            "<script>alert(1)</script>" to "SCRIPTALERT1"
        )
        for ((raw, _) in attempts) {
            val clean = PlayerIdentity.sanitize(raw)
            assertFalse("an email survived sanitising: '$clean'", clean.contains("@"))
            assertFalse("a space survived sanitising: '$clean'", clean.contains(" "))
            assertFalse("punctuation survived sanitising: '$clean'", clean.contains("."))
            assertTrue(
                "the callsign is not capped: '$clean' is ${clean.length} characters",
                clean.length <= PlayerIdentity.MAX_LENGTH
            )
            assertTrue(
                "sanitising let something through that is not A-Z, 0-9, _ or -: '$clean'",
                clean.all { it in 'A'..'Z' || it in '0'..'9' || it == '_' || it == '-' }
            )
        }
    }

    @Test
    fun `the cloud save carries no entitlements and no settings`() {
        // Entitlements are absent because Play is the only honest source of
        // truth for what someone bought: if ownership travelled in a save file,
        // anyone who could write one could grant themselves every paid item.
        // Settings are absent because they belong to a device, not a player.
        val fields = CloudSave::class.java.declaredFields.map { it.name }
        for (name in fields) {
            assertFalse(
                "the cloud save carries entitlements ($name)",
                name.contains("entitlement", ignoreCase = true)
            )
            assertFalse(
                "the cloud save carries device settings ($name)",
                name.equals("settings", ignoreCase = true)
            )
        }
        assertTrue("the payload lost its identity field", "identity" in fields)
    }

    @Test
    fun `an empty identity is the default, so nothing is sent unasked`() {
        // A player who never types a callsign has nothing in the field, and a
        // fresh save must not invent one.
        assertEquals("", PlayerIdentity().username)
        assertFalse(PlayerIdentity().registered)
        assertEquals("", CloudSave().identity.username)
    }
}
