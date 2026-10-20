package com.rootpad.bridge

import android.app.Application
import com.rootpad.bridge.core.AppGraph

class RootPadApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
    }
}
