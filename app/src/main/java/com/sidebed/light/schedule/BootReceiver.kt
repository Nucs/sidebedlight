package com.sidebed.light.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sidebed.light.data.settingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Re-installs the schedule after a reboot or app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val settings = appContext.settingsRepository.current()
                if (settings.scheduleEnabled) ScheduleManager.apply(appContext, settings)
            } finally {
                pending.finish()
            }
        }
    }
}
