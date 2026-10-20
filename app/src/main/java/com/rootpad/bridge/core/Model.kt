package com.rootpad.bridge.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer

/**
 * Xbox 配置のボタン。UI 上の表示名を担う。
 */
enum class PadButton(val subKey: String = "") {
    A,
    B,
    X,
    Y,
    LB("btn.LB"),
    RB("btn.RB"),
    LT("btn.LT"),
    RT("btn.RT"),
    VIEW("btn.VIEW"),
    MENU("btn.MENU"),
    GUIDE("btn.GUIDE"),
    LS_PRESS("btn.LS_PRESS"),
    RS_PRESS("btn.RS_PRESS"),
    DPAD_UP("btn.DPAD_UP"),
    DPAD_DOWN("btn.DPAD_DOWN"),
    DPAD_LEFT("btn.DPAD_LEFT"),
    DPAD_RIGHT("btn.DPAD_RIGHT"),
    LS_STICK("keys.stick_left"),
    RS_STICK("keys.stick_right");

    /** 表示名 (言語別)。短い記号はそのまま、名称はテーブル参照。 */
    fun label(lang: ResolvedLang): String = when (this) {
        A, B, X, Y -> name
        LS_PRESS -> if (lang == ResolvedLang.JA) "L3" else "L3"
        RS_PRESS -> if (lang == ResolvedLang.JA) "R3" else "R3"
        else -> Strings.get(lang, subKey)
    }
}

/**
 * 変換エンジンの割り当て先。キーボード/マウスボタンからこれらへ割り当てる。
 */
@Serializable(with = PadActionSerializer::class)
enum class PadAction(val key: String) {
    NONE("common.unassigned"),
    A("btn.A"),
    B("btn.B"),
    X("btn.X"),
    Y("btn.Y"),
    LB("btn.LB"),
    RB("btn.RB"),
    LT("btn.LT"),
    RT("btn.RT"),
    VIEW("btn.VIEW"),
    MENU("btn.MENU"),
    GUIDE("btn.GUIDE"),
    LS_PRESS("btn.LS_PRESS"),
    RS_PRESS("btn.RS_PRESS"),
    DPAD_UP("btn.DPAD_UP"),
    DPAD_DOWN("btn.DPAD_DOWN"),
    DPAD_LEFT("btn.DPAD_LEFT"),
    DPAD_RIGHT("btn.DPAD_RIGHT"),
    LS_UP("btn.LS_UP"),
    LS_DOWN("btn.LS_DOWN"),
    LS_LEFT("btn.LS_LEFT"),
    LS_RIGHT("btn.LS_RIGHT"),
    RS_UP("btn.RS_UP"),
    RS_DOWN("btn.RS_DOWN"),
    RS_LEFT("btn.RS_LEFT"),
    RS_RIGHT("btn.RS_RIGHT");

    /** 表示名 (言語別) */
    fun label(lang: ResolvedLang): String = Strings.get(lang, key)

    companion object {
        /** JSON 変換用 (不正値は NONE にフォールバック) */
        fun fromName(name: String?): PadAction =
            entries.firstOrNull { it.name == name } ?: NONE
    }
}

/** 未知の割り当て名を NONE へフォールバックさせるシリアライザ (後方互換) */
object PadActionSerializer : kotlinx.serialization.KSerializer<PadAction> {
    private val stringSer = String.serializer()

    override val descriptor: kotlinx.serialization.descriptors.SerialDescriptor
        get() = stringSer.descriptor

    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: PadAction) {
        encoder.encodeString(value.name)
    }

    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): PadAction =
        PadAction.fromName(stringSer.deserialize(decoder))
}

/** 感度カーブの種別 */
enum class CurveType(val key: String) {
    LINEAR("curve.LINEAR"),
    SQUARE("curve.SQUARE"),
    SMOOTH("curve.SMOOTH");

    /** 表示名 (言語別) */
    fun label(lang: ResolvedLang): String = Strings.get(lang, key)
}

/**
 * マウス → 右スティック変換の設定。
 * 単位: sensitivity は「1ティック(4ms)あたり 1px の移動がスティック最大値の何％になるか」
 * ではなく「px → スティック値の倍率」(BASE=64 相当で正規化される)。
 */
@Serializable
data class MouseSettings(
    val sensitivityX: Float = 1.0f,
    val sensitivityY: Float = 1.0f,
    val invertX: Boolean = false,
    val invertY: Boolean = false, // 視点上下を反転したい場合のみ有効化
    val deadzone: Float = 0.06f,
    val acceleration: Float = 0.25f,
    val curve: CurveType = CurveType.LINEAR,
    val maxDeflection: Float = 1.0f,
    val idleReturnMs: Long = 40,
    val decayMs: Long = 60,
    val buttonBindings: Map<Int, PadAction> = defaultMouseBindings(),
    /**
     * ホイール方向 → アクション。キーと同様に任意のアクションへ割り当て可能。
     * キーは "UP"/"DOWN"/"LEFT"/"RIGHT" (垂直/水平ホイール) で保存する。
     */
    @Serializable(with = ScrollBindingsSerializer::class)
    val scrollBindings: Map<ScrollDirection, PadAction> = defaultScrollBindings()
) {
    companion object {
        /** 感度の下限 (これ未満はほぼ動かない) */
        const val SENSITIVITY_MIN = 0.05f
        /** 感度の上限。FPS で素早く振り向けるよう大きめに確保する。 */
        const val SENSITIVITY_MAX = 50.0f
    }
}

/**
 * 1 つのマッピングプロファイル。
 * keyBindings / buttonBindings は Linux input コード → 割り当て先の集合。
 * 1 つのキーに複数アクションを割り当て可能 (同時押し)。
 */
@Serializable
data class MappingProfile(
    val id: String,
    val name: String,
    @Serializable(with = KeyBindingsSerializer::class)
    val keyBindings: Map<Int, Set<PadAction>> = defaultKeyBindings(),
    val mouse: MouseSettings = MouseSettings(),
    /** 物理デバイスを EVIOCGRAB で独占し、Android への二重送信を防ぐ */
    val grabDevices: Boolean = true,
    /** Xbox 用キーレイアウト/IDC を /data/system/devices に導入する */
    val installKeylayout: Boolean = true,
    /** 端末再起動後に自動で変換を再開する */
    val autoStartOnBoot: Boolean = false
)

/**
 * 旧形式 (Map<Int, PadAction>) と新形式 (Map<Int, [PadAction]>) の両方を読める
 * シリアライザ。書き出しは常に新形式。
 */
object KeyBindingsSerializer :
    kotlinx.serialization.KSerializer<Map<Int, Set<PadAction>>> {

    private val mapSer =
        kotlinx.serialization.builtins.MapSerializer(
            Int.serializer(),
            kotlinx.serialization.builtins.SetSerializer(PadActionSerializer)
        )

    override val descriptor = mapSer.descriptor

    override fun serialize(
        encoder: kotlinx.serialization.encoding.Encoder,
        value: Map<Int, Set<PadAction>>
    ) = mapSer.serialize(encoder, value)

    override fun deserialize(
        decoder: kotlinx.serialization.encoding.Decoder
    ): Map<Int, Set<PadAction>> {
        // JSON なら要素ごとに「文字列 or 配列」を判定して読む
        val input = decoder as? kotlinx.serialization.json.JsonDecoder
            ?: return mapSer.deserialize(decoder)
        val obj = input.decodeJsonElement()
            as? kotlinx.serialization.json.JsonObject
            ?: return emptyMap()
        val result = LinkedHashMap<Int, Set<PadAction>>()
        for ((k, v) in obj) {
            val key = k.toIntOrNull() ?: continue
            val actions = when (v) {
                is kotlinx.serialization.json.JsonArray ->
                    v.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                is kotlinx.serialization.json.JsonPrimitive -> listOf(v.content)
                else -> emptyList()
            }.map { PadAction.fromName(it) }.filter { it != PadAction.NONE }.toSet()
            if (actions.isNotEmpty()) result[key] = actions
        }
        return result
    }
}

/** 初期キー設定 (Linux input コード)。仕様書の既定割り当てに準拠。 */
fun defaultKeyBindings(): Map<Int, Set<PadAction>> = mapOf(
    LinuxKeys.KEY_W to setOf(PadAction.LS_UP),
    LinuxKeys.KEY_A to setOf(PadAction.LS_LEFT),
    LinuxKeys.KEY_S to setOf(PadAction.LS_DOWN),
    LinuxKeys.KEY_D to setOf(PadAction.LS_RIGHT),
    LinuxKeys.KEY_SPACE to setOf(PadAction.A),
    LinuxKeys.KEY_Q to setOf(PadAction.B),
    LinuxKeys.KEY_E to setOf(PadAction.X),
    LinuxKeys.KEY_R to setOf(PadAction.Y),
    LinuxKeys.KEY_LEFTSHIFT to setOf(PadAction.LB),
    LinuxKeys.KEY_F to setOf(PadAction.RB),
    LinuxKeys.KEY_TAB to setOf(PadAction.VIEW),
    LinuxKeys.KEY_ESC to setOf(PadAction.MENU),
    LinuxKeys.KEY_LEFTCTRL to setOf(PadAction.LS_PRESS),
    LinuxKeys.KEY_Z to setOf(PadAction.RS_PRESS),
    LinuxKeys.KEY_1 to setOf(PadAction.LB),
    LinuxKeys.KEY_2 to setOf(PadAction.RB),
    LinuxKeys.KEY_G to setOf(PadAction.GUIDE),
    LinuxKeys.KEY_UP to setOf(PadAction.DPAD_UP),
    LinuxKeys.KEY_DOWN to setOf(PadAction.DPAD_DOWN),
    LinuxKeys.KEY_LEFT to setOf(PadAction.DPAD_LEFT),
    LinuxKeys.KEY_RIGHT to setOf(PadAction.DPAD_RIGHT)
)

/** 初期マウスボタン設定 */
fun defaultMouseBindings(): Map<Int, PadAction> = mapOf(
    LinuxKeys.BTN_LEFT to PadAction.RT,
    LinuxKeys.BTN_RIGHT to PadAction.LT,
    LinuxKeys.BTN_SIDE to PadAction.LB,
    LinuxKeys.BTN_EXTRA to PadAction.RB,
    LinuxKeys.BTN_MIDDLE to PadAction.NONE
)

/** ホイールの方向 */
enum class ScrollDirection(val key: String) {
    UP("scroll.UP"),
    DOWN("scroll.DOWN"),
    LEFT("scroll.LEFT"),
    RIGHT("scroll.RIGHT");

    /** 表示名 (言語別) */
    fun label(lang: ResolvedLang): String = Strings.get(lang, key)

    /** JSON 変換用 (不正値は UP にフォールバック) */
    companion object {
        fun fromName(name: String?): ScrollDirection? =
            entries.firstOrNull { it.name == name }
    }
}

/** 未知のホイール方向を無視するシリアライザ */
object ScrollBindingsSerializer :
    kotlinx.serialization.KSerializer<Map<ScrollDirection, PadAction>> {

    private val stringKeyMap =
        kotlinx.serialization.builtins.MapSerializer(
            String.serializer(),
            PadActionSerializer
        )

    override val descriptor = stringKeyMap.descriptor

    override fun serialize(
        encoder: kotlinx.serialization.encoding.Encoder,
        value: Map<ScrollDirection, PadAction>
    ) = stringKeyMap.serialize(encoder, value.mapKeys { it.key.name })

    override fun deserialize(
        decoder: kotlinx.serialization.encoding.Decoder
    ): Map<ScrollDirection, PadAction> =
        stringKeyMap.deserialize(decoder)
            .mapNotNull { (k, v) -> ScrollDirection.fromName(k)?.let { it to v } }
            .toMap()
}

/** 初期ホイール設定: 上下は十字キー相当、チルトは未割当 */
fun defaultScrollBindings(): Map<ScrollDirection, PadAction> = mapOf(
    ScrollDirection.UP to PadAction.DPAD_UP,
    ScrollDirection.DOWN to PadAction.DPAD_DOWN,
    ScrollDirection.LEFT to PadAction.NONE,
    ScrollDirection.RIGHT to PadAction.NONE
)

/** プロファイル生成ユーティリティ */
fun newProfile(id: String, name: String): MappingProfile =
    MappingProfile(id = id, name = name)

fun defaultProfile(name: String = "Default"): MappingProfile =
    MappingProfile(id = "default", name = name)
