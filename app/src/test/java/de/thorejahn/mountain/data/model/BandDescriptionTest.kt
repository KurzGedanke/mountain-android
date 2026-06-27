package de.thorejahn.mountain.data.model

import de.thorejahn.mountain.data.remote.AppJson
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the localized [Band.description] object shape introduced by the band API,
 * including decoding a captured live response (test/resources/bands_live.json).
 */
class BandDescriptionTest {

    private fun liveJson(): String =
        javaClass.classLoader!!.getResourceAsStream("bands_live.json")!!
            .bufferedReader().use { it.readText() }

    @Test
    fun decodesLiveResponseWithLocalizedDescription() {
        val bands = AppJson.decodeFromString(ListSerializer(Band.serializer()), liveJson())
        assertTrue("expected bands in live response", bands.isNotEmpty())

        // Every band carries a localized description object (both keys present per API contract).
        bands.forEach { b ->
            assertNotNull("band ${b.slug} missing description", b.description)
        }

        val desc = bands.first { it.slug == "amorphis" }.description!!
        assertTrue(desc.de!!.startsWith("Metal-Band aus Helsinki"))
        assertTrue(desc.en!!.startsWith("Metal band from Helsinki"))
        // German is the default rendering; English on request.
        assertEquals(desc.de, desc.resolve(preferEnglish = false))
        assertEquals(desc.en, desc.resolve(preferEnglish = true))
    }

    @Test
    fun realInstagramAndBandcampLinksSurviveSanitization() {
        val bands = AppJson.decodeFromString(ListSerializer(Band.serializer()), liveJson())
        // Live data: every band has a real Instagram URL, most have a real Bandcamp URL.
        assertTrue("expected real instagram links", bands.count { it.instagramUrl != null } > 0)
        assertTrue("expected real bandcamp links", bands.count { it.bandcampUrl != null } > 0)

        val amorphis = bands.first { it.slug == "amorphis" }
        // Trailing-slash profile URLs must be kept now.
        assertEquals("https://www.instagram.com/amorphisband/", amorphis.instagramUrl)
        assertTrue(amorphis.hasLinks)
    }

    @Test
    fun sanitizedUrlTreatsOnlyNullOrEmptyAsMissing() {
        assertNull(sanitizedUrl(null))
        assertNull(sanitizedUrl(""))
        assertNull(sanitizedUrl("   "))
        // Trailing slash is a valid URL (IG/Bandcamp profiles end with `/`).
        assertEquals("https://x.bandcamp.com/", sanitizedUrl("https://x.bandcamp.com/"))
        assertEquals("https://open.spotify.com/artist/abc", sanitizedUrl("https://open.spotify.com/artist/abc"))
    }

    @Test
    fun resolveDefaultsToGerman() {
        val t = LocalizedText(de = "Hallo", en = "Hello")
        assertEquals("Hallo", t.resolve(preferEnglish = false))
        assertEquals("Hello", t.resolve(preferEnglish = true))
    }

    @Test
    fun resolveFallsBackWhenPreferredIsNull() {
        assertEquals("Hello", LocalizedText(de = null, en = "Hello").resolve(preferEnglish = false))
        assertEquals("Hallo", LocalizedText(de = "Hallo", en = null).resolve(preferEnglish = true))
        assertNull(LocalizedText(de = null, en = null).resolve(preferEnglish = false))
    }

    @Test
    fun decodesNullLanguageValue() {
        // explicitNulls=false + nullable fields: a present-but-null value must decode cleanly.
        val json = """{"id":99,"name":"X","slug":"x","description":{"de":"Nur Deutsch","en":null}}"""
        val band = AppJson.decodeFromString(Band.serializer(), json)
        assertEquals("Nur Deutsch", band.description!!.de)
        assertNull(band.description!!.en)
        assertEquals("Nur Deutsch", band.description!!.resolve(preferEnglish = true))
    }
}
