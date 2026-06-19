package com.sidebed.light.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.sidebed.light.MainActivity
import com.sidebed.light.data.SidebedSettings
import java.util.Calendar

/**
 * Manages the two daily exact alarms that auto-arm (at the start time) and
 * auto-disarm (at the end time) the bedside light.
 *
 * The arm alarm uses [AlarmManager.setAlarmClock] because it is doze-exempt and is
 * permitted to start a foreground service from the background. Each alarm reschedules
 * itself for the next day when it fires.
 */
object ScheduleManager {

    const val ACTION_AUTO_ARM = "com.sidebed.light.schedule.AUTO_ARM"
    const val ACTION_AUTO_DISARM = "com.sidebed.light.schedule.AUTO_DISARM"

    private const val REQ_ARM = 100
    private const val REQ_DISARM = 101
    private const val ALL_DAYS = 0b111_1111 // 7 day bits, Sunday..Saturday

    /** (Re)install both alarms from the current settings, or cancel if disabled. */
    fun apply(context: Context, settings: SidebedSettings) {
        val am = context.getSystemService(AlarmManager::class.java)
        cancel(context, am)
        if (!settings.scheduleEnabled) return
        scheduleArm(context, am, settings.scheduleStartMinutes, settings.scheduleDaysMask)
        scheduleDisarm(context, am, settings.scheduleEndMinutes)
    }

    /** Re-arm the next occurrence after one fires. */
    fun rescheduleAfterFire(context: Context, settings: SidebedSettings, action: String) {
        if (!settings.scheduleEnabled) return
        val am = context.getSystemService(AlarmManager::class.java)
        when (action) {
            ACTION_AUTO_ARM ->
                scheduleArm(context, am, settings.scheduleStartMinutes, settings.scheduleDaysMask)

            ACTION_AUTO_DISARM -> scheduleDisarm(context, am, settings.scheduleEndMinutes)
        }
    }

    fun cancel(
        context: Context,
        am: AlarmManager = context.getSystemService(AlarmManager::class.java),
    ) {
        am.cancel(armPendingIntent(context))
        am.cancel(disarmPendingIntent(context))
    }

    private fun scheduleArm(context: Context, am: AlarmManager, minutes: Int, daysMask: Int) {
        // No enabled day -> don't schedule an arm at all.
        val triggerAt = nextOccurrence(minutes, daysMask) ?: return
        val pi = armPendingIntent(context)
        runCatching {
            if (canScheduleExact(am)) {
                val show = PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE,
                )
                am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, show), pi)
            } else {
                // No exact-alarm permission: best-effort inexact. May be delayed and may not
                // start the foreground service from deep background — granting "Alarms &
                // reminders" in Settings makes the schedule reliable.
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        }
    }

    private fun scheduleDisarm(context: Context, am: AlarmManager, minutes: Int) {
        // Disarm runs every day at the end time (a no-op if nothing is armed), so the light
        // always turns off in the morning after any scheduled or manual night.
        val triggerAt = nextOccurrence(minutes) ?: return
        val pi = disarmPendingIntent(context)
        runCatching {
            if (canScheduleExact(am)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        }
    }

    private fun canScheduleExact(am: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()

    private fun armPendingIntent(context: Context) = PendingIntent.getBroadcast(
        context,
        REQ_ARM,
        Intent(context, ScheduleReceiver::class.java).setAction(ACTION_AUTO_ARM),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun disarmPendingIntent(context: Context) = PendingIntent.getBroadcast(
        context,
        REQ_DISARM,
        Intent(context, ScheduleReceiver::class.java).setAction(ACTION_AUTO_DISARM),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Next epoch-millis at the given minutes-from-midnight on an enabled day, or null. */
    private fun nextOccurrence(minutesFromMidnight: Int, daysMask: Int = ALL_DAYS): Long? {
        val mask = daysMask and ALL_DAYS
        if (mask == 0) return null
        val now = Calendar.getInstance()
        val target = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, minutesFromMidnight / 60)
            set(Calendar.MINUTE, minutesFromMidnight % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.timeInMillis <= now.timeInMillis) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
        // Advance up to a week to the next enabled weekday (Calendar.SUNDAY=1 -> bit 0).
        for (i in 0 until 7) {
            val dayBit = 1 shl (target.get(Calendar.DAY_OF_WEEK) - 1)
            if (mask and dayBit != 0) return target.timeInMillis
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
        return null
    }
}
