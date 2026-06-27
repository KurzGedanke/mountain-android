package de.thorejahn.mountain.data.model

import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
data class Band(
    val id: Int,
    val name: String,
    val slug: String,
    val genre: String? = null,
    val logo: String? = null,
    val image: String? = null,
    val instagram: String? = null,
    val spotify: String? = null,
    val appleMusic: String? = null,
    val bandcamp: String? = null,
    val description: LocalizedText? = null,
)

/**
 * Localized free text returned by the band API. Both keys are always present in the
 * response, but either value may be null. German is the default language.
 */
@Serializable
data class LocalizedText(
    val de: String? = null,
    val en: String? = null,
) {
    /**
     * Resolve to a single string. German is the default; when [preferEnglish] is set
     * English wins. Falls back to the other language when the preferred value is null.
     */
    fun resolve(preferEnglish: Boolean): String? =
        if (preferEnglish) (en ?: de) else (de ?: en)
}

/** Resolved description for the current device language (German unless the device is English). */
val Band.descriptionText: String?
    get() = description?.resolve(preferEnglish = Locale.getDefault().language == "en")

/**
 * URL sanitization rule (§2.1): the API signals a missing link with `null` (or an empty
 * string). A real link is any non-empty value — note that Instagram and Bandcamp profile
 * URLs canonically end with a trailing `/`, so a trailing slash must NOT be treated as
 * "missing".
 */
fun sanitizedUrl(value: String?): String? =
    value?.trim()?.takeIf { it.isNotEmpty() }

val Band.imageUrl get() = sanitizedUrl(image)
val Band.logoUrl get() = sanitizedUrl(logo)
val Band.spotifyUrl get() = sanitizedUrl(spotify)
val Band.appleMusicUrl get() = sanitizedUrl(appleMusic)
val Band.bandcampUrl get() = sanitizedUrl(bandcamp)
val Band.instagramUrl get() = sanitizedUrl(instagram)

/** True if any streaming/social link resolves (note: NOT logo/image). */
val Band.hasLinks: Boolean
    get() = spotifyUrl != null || appleMusicUrl != null || bandcampUrl != null || instagramUrl != null
