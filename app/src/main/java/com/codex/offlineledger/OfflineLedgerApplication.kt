package com.codex.offlineledger

import android.app.Application
import com.codex.offlineledger.work.ReminderScheduler

class OfflineLedgerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ReminderScheduler.ensureChannel(this)
        ReminderScheduler.schedule(this)
    }
}
