package com.vidsource.app.data

import org.json.JSONObject
import java.net.URI
import java.util.UUID

/**
 * A manually configured video source ("channel"), e.g. https://cinejoy.pk/
 */
data class SourceProfile(
    val id: String,
    val name: String,
    val url: String
) {

    /** Host part of the URL, used for favicon lookup. */
    fun host(): String = try {
        URI(url).host ?: url
    } catch (e: Exception) {
        url
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("url", url)

    companion object {

        const val DEMO_ID = "cinejoy-demo"

        val DEMO_SOURCE = SourceProfile(
            id = DEMO_ID,
            name = "CineJoy",
            url = "https://cinejoy.pk/"
        )

        fun fromJson(o: JSONObject): SourceProfile = SourceProfile(
            id = o.optString("id", UUID.randomUUID().toString()),
            name = o.optString("name", "Source"),
            url = o.optString("url", "")
        )

        fun create(name: String, url: String): SourceProfile =
            SourceProfile(UUID.randomUUID().toString(), name.trim(), normalizeUrl(url))

        fun normalizeUrl(raw: String): String {
            val t = raw.trim()
            return if (t.startsWith("http://") || t.startsWith("https://")) t else "https://$t"
        }
    }
}
