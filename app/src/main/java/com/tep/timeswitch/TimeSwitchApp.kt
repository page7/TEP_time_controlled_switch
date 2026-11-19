package com.tep.timeswitch

import android.app.Application
import android.content.Context

class TimeSwitchApp : Application() {
    init { instance = this }

    companion object {
        lateinit var instance: Context
            private set
    }
}
