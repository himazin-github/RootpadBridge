package com.rootpad.bridge.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * アプリの DI に近い役割。Application で初期化される。
 */
object AppGraph {
    @Volatile
    private var appContext: Context? = null

    lateinit var profiles: ProfileRepository
        private set

    lateinit var langStore: LangStore
        private set

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext

        // 言語設定を最初に読み込む (既定プロファイル名などに影響する)
        langStore = LangStore(context.applicationContext)
        LangState.lang = langStore.load()

        profiles = ProfileRepository(ProfileStore(context.applicationContext))
        profiles.load()
    }

    /** 言語を変更して保存する */
    fun setLanguage(lang: Lang) {
        LangState.lang = lang
        langStore.save(lang)
    }

    val context: Context
        get() = appContext ?: error("AppGraph.init() が未呼び出し")
}

/**
 * プロファイル一覧とアクティブプロファイルの管理。
 * 変更は即座に UI へ反映され、磁盤にも保存される。
 */
class ProfileRepository(private val store: ProfileStore) {

    private val _profiles = MutableStateFlow<List<MappingProfile>>(emptyList())
    val profiles: StateFlow<List<MappingProfile>> = _profiles.asStateFlow()

    private val _activeId = MutableStateFlow("")
    val activeId: StateFlow<String> = _activeId.asStateFlow()

    val active: MappingProfile
        get() = _profiles.value.firstOrNull { it.id == _activeId.value }
            ?: _profiles.value.firstOrNull()
            ?: defaultProfile()

    fun load() {
        val list = store.list()
        _profiles.value = list
        _activeId.value = store.activeId()
    }

    fun setActive(id: String) {
        if (_profiles.value.none { it.id == id }) return
        _activeId.value = id
        store.setActive(id)
    }

    fun updateActive(transform: (MappingProfile) -> MappingProfile) {
        val cur = active
        val next = transform(cur)
        if (next == cur) return
        save(next)
    }

    fun save(profile: MappingProfile) {
        store.save(profile)
        _profiles.value = _profiles.value
            .map { if (it.id == profile.id) profile else it }
            .let { if (it.none { p -> p.id == profile.id }) it + profile else it }
    }

    fun create(name: String): MappingProfile {
        val p = newProfile(store.newId(), name)
        save(p)
        return p
    }

    fun duplicate(id: String) {
        val src = _profiles.value.firstOrNull { it.id == id } ?: return
        val suffix = Strings.get(LangState.current(), "profiles.copy_suffix")
        val copy = src.copy(id = store.newId(), name = src.name + suffix)
        save(copy)
    }

    fun rename(id: String, name: String) {
        val p = _profiles.value.firstOrNull { it.id == id } ?: return
        save(p.copy(name = name))
    }

    fun delete(id: String) {
        if (_profiles.value.size <= 1) return // 最低 1 件は残す
        store.delete(id)
        val newList = _profiles.value.filterNot { it.id == id }
        _profiles.value = newList
        if (_activeId.value == id) {
            setActive(newList.first().id)
        }
    }

    fun resetToDefault(id: String) {
        val p = _profiles.value.firstOrNull { it.id == id } ?: return
        save(p.copy(
            keyBindings = defaultKeyBindings(),
            mouse = MouseSettings(),
            grabDevices = true,
            installKeylayout = true,
            autoStartOnBoot = false
        ))
    }
}
