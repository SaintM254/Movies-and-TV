package com.vidsource.app.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray

/**
 * Stores the manually added source profiles and which one is currently active.
 */
class SourceRepository(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences("vidsource", Context.MODE_PRIVATE)

    fun getSources(): MutableList<SourceProfile> {
        val raw = prefs.getString(KEY_SOURCES, null)
        val list = mutableListOf<SourceProfile>()
        if (raw != null) {
            try {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    list.add(SourceProfile.fromJson(arr.getJSONObject(i)))
                }
            } catch (e: Exception) {
                // Corrupt state: fall through to defaults.
            }
        }
        if (list.isEmpty()) {
            list.add(SourceProfile.DEMO_SOURCE)
            saveSources(list)
        }
        return list
    }

    fun saveSources(list: List<SourceProfile>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit { putString(KEY_SOURCES, arr.toString()) }
    }

    fun add(source: SourceProfile) {
        val list = getSources()
        list.add(source)
        saveSources(list)
    }

    fun update(source: SourceProfile) {
        val list = getSources()
        val index = list.indexOfFirst { it.id == source.id }
        if (index >= 0) {
            list[index] = source
            saveSources(list)
        }
    }

    fun delete(id: String) {
        val list = getSources().filterNot { it.id == id }
        saveSources(list)
        if (activeSourceId() == id && list.isNotEmpty()) {
            setActive(list.first().id)
        }
    }

    fun activeSourceId(): String? = prefs.getString(KEY_ACTIVE, null)

    fun setActive(id: String) {
        prefs.edit { putString(KEY_ACTIVE, id) }
    }

    fun getActive(): SourceProfile {
        val list = getSources()
        val id = activeSourceId()
        return list.firstOrNull { it.id == id } ?: list.first()
    }

    fun nextSource(currentId: String, step: Int): SourceProfile {
        val list = getSources()
        if (list.isEmpty()) return SourceProfile.DEMO_SOURCE
        val index = list.indexOfFirst { it.id == currentId }.let { if (it < 0) 0 else it }
        val next = (index + step).mod(list.size)
        return list[next]
    }

    companion object {
        private const val KEY_SOURCES = "sources"
        private const val KEY_ACTIVE = "active_source"
    }
}
