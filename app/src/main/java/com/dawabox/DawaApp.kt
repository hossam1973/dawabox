package com.dawabox

import android.app.Application
import com.dawabox.reminders.Notifier
import com.dawabox.voice.Speaker

class DawaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        Speaker.init(this)
    }
}
