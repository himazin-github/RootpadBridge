package com.rootpad.bridge.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rootpad.bridge.core.AppGraph
import com.rootpad.bridge.service.RootPadService

/**
 * 端末再起動後の復旧: アクティブプロファイルで「自動再開」が有効な場合のみ
 * サービスを再起動する (ユーザー許可外の常駐は行わない)。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val profile = AppGraph.profiles.active
        if (profile.autoStartOnBoot) {
            RootPadService.start(context)
        }
    }
}
