package com.rootpad.bridge.root

import android.content.Context
import com.rootpad.bridge.core.Proto
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * root コマンドの実行部。
 *
 * 安全方針: 実行するコマンドは本クラスに固定定義したもののみ。
 * ユーザー入力や外部由来の文字列をシェルに渡す仕組みは作らない。
 */
object RootExecutor {

    data class RootStatus(val granted: Boolean, val detail: String)

    /** Magisk 等の su が存在しうる候補パス */
    private val suCandidates = listOf(
        "/sbin/su",
        "/system/xbin/su",
        "/system/bin/su",
        "/data/adb/magisk/su",
        "/su/bin/su",
        "/magisk/.core/bin/su"
    )

    /** 見つかった su のパス (PATH 解決に委ねる場合は null) */
    @Volatile
    private var suPath: String? = null

    fun findSu(): String? {
        suPath?.let { return it }
        for (p in suCandidates) {
            val f = File(p)
            if (f.exists() && f.canExecute()) {
                suPath = p
                return p
            }
        }
        return null
    }

    /**
     * root 権限の取得可否を確認する (実固定コマンド: su -c id)。
     */
    fun checkRoot(): RootStatus {
        val su = findSu()
        val candidates = listOfNotNull(su, "su")
        var lastDetail = "su が見つかりません"
        for (bin in candidates) {
            try {
                val pb = ProcessBuilder(bin, "-c", "id")
                pb.redirectErrorStream(true)
                val p = pb.start()
                val out = p.inputStream.bufferedReader().readText().trim()
                val finished = p.waitFor(5, TimeUnit.SECONDS)
                if (!finished) {
                    p.destroyForcibly()
                    lastDetail = "$bin が応答しません (Root許可ダイアログで拒否された可能性)"
                    continue
                }
                if (p.exitValue() == 0 && out.contains("uid=0")) {
                    suPath = bin
                    return RootStatus(true, out.lineSequence().firstOrNull().orEmpty())
                }
                lastDetail = out.lineSequence().firstOrNull() ?: "uid=0 ではありません"
            } catch (e: Exception) {
                lastDetail = e.javaClass.simpleName + ": " + (e.message ?: "")
            }
        }
        return RootStatus(false, lastDetail)
    }

    /**
     * root でデーモンを起動する。
     * @param command su -c に渡す完全なコマンド文字列 (アプリ側で固定文字列を組み立てたもの)
     */
    fun startRootProcess(suPath: String, command: String): Process {
        val pb = ProcessBuilder(suPath, "-c", command)
        pb.redirectErrorStream(true)
        return pb.start()
    }

    /**
     * assets の rootpad_d をアプリ領域へ展開し実行権限を付与する。
     * @return 展開済み実行ファイル。対応 ABI が無ければ null
     */
    fun prepareDaemonBinary(context: Context): File? {
        return try {
            val abis = android.os.Build.SUPPORTED_ABIS
            if (!abis.contains("arm64-v8a")) return null
            val asset = context.assets.open("rootpad_d").use { it.readBytes() }
            if (asset.isEmpty() || asset.size < 64) return null
            // ELF マジック確認 (arm64: 0x7F 'E' 'L' 'F' + ELFCLASS64)
            if (asset[0] != 0x7f.toByte() || asset[1] != 'E'.code.toByte() ||
                asset[2] != 'L'.code.toByte() || asset[3] != 'F'.code.toByte()
            ) {
                return null
            }
            val dir = File(context.filesDir, "daemon").apply { mkdirs() }
            val marker = File(dir, "rootpad_d.ver")
            val target = File(dir, "rootpad_d")
            val expectedVersion = daemonAssetVersion(context)
            val upToDate = target.exists() && target.length() == asset.size.toLong() &&
                marker.exists() && marker.readText() == expectedVersion
            if (!upToDate) {
                target.writeBytes(asset)
                marker.writeText(expectedVersion)
            }
            target.setExecutable(true, false)
            if (!target.canExecute()) null else target
        } catch (e: Exception) {
            null
        }
    }

    private fun daemonAssetVersion(context: Context): String =
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime.toString()
        } catch (e: Exception) {
            "0"
        }
}
