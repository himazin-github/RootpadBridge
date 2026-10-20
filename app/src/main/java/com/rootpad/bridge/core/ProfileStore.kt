package com.rootpad.bridge.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * マッピングプロファイルの永続化 (JSON ファイル + アクティブIDは SharedPreferences)。
 */
class ProfileStore(context: Context) {

    private val dir = File(context.filesDir, "profiles").apply { mkdirs() }
    private val prefs: SharedPreferences =
        context.getSharedPreferences("rootpad_prefs", Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        encodeDefaults = true
    }

    fun list(): List<MappingProfile> {
        val files = dir.listFiles()
            ?.filter { f -> f.isFile && f.name.endsWith(".json") }
            ?: emptyList()
        val result = files.mapNotNull { f ->
            try {
                json.decodeFromString<MappingProfile>(f.readText())
            } catch (e: Exception) {
                null
            }
        }.sortedBy { it.name }
        return if (result.isEmpty()) {
            val def = defaultProfile(Strings.get(LangState.current(), "profile.default_name"))
            save(def)
            listOf(def)
        } else {
            result
        }
    }

    fun save(profile: MappingProfile) {
        val f = File(dir, sanitize(profile.id) + ".json")
        f.writeText(json.encodeToString(MappingProfile.serializer(), profile))
    }

    fun delete(id: String) {
        File(dir, sanitize(id) + ".json").delete()
    }

    fun activeId(): String {
        val id = prefs.getString(PREF_ACTIVE_ID, null)
        if (id != null && File(dir, sanitize(id) + ".json").exists()) return id
        val first = list().firstOrNull()?.id ?: "default"
        setActive(first)
        return first
    }

    fun setActive(id: String) {
        prefs.edit().putString(PREF_ACTIVE_ID, sanitize(id)).apply()
    }

    fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(12)

    private fun sanitize(id: String): String =
        id.replace(Regex("[^A-Za-z0-9_.-]"), "_").ifEmpty { "profile" }

    companion object {
        private const val PREF_ACTIVE_ID = "active_profile_id"
    }
}
