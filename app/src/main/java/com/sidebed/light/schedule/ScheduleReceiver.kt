package com.sidebed.light.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sidebed.light.data.settingsRepository
import com.sidebed.light.service.ServiceController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Fires when a scheduled auto-arm / auto-disarm alarm goes off. */
class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val settings = appContext.settingsRepository.current()
                when (action) {
                    ScheduleManager.ACTION_AUTO_ARM ->
                        if (settings.scheduleEnabled) ServiceController.arm(appContext, fromSchedule = true)

                    ScheduleManager.ACTION_AUTO_DISARM ->
                        ServiceController.disarm(appContext)
                }
                ScheduleManager.rescheduleAfterFire(appContext, settings, action)
            } finally {
                pending.finish()
            }
        }
    }
}
