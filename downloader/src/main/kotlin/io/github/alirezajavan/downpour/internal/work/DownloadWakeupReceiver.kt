package io.github.alirezajavan.downpour.internal.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import io.github.alirezajavan.downpour.internal.util.AndroidLogger

internal class DownloadWakeupReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent?,
    ) {
        val action = intent?.action ?: return
        if (action != ACTION_WAKEUP && action != Intent.ACTION_BOOT_COMPLETED) return

        val logger = AndroidLogger(true)
        logger.d("DownloadWakeupReceiver: wakeup alarm received ($action)")

        // Start the service immediately. Being in the onReceive of an alarm (especially an exact
        // one) provides a window to start a foreground service even from the background.
        // This prevents ForegroundServiceStartNotAllowedException when the engine later
        // decides to start a download.
        io.github.alirezajavan.downpour.internal.service.DownloadService
            .start(context)
        DownloadRecoveryWorker.schedule(context)
    }

    companion object {
        private const val ACTION_WAKEUP = "io.github.alirezajavan.downpour.action.WAKEUP"
        private const val REQUEST_CODE = 0x445057

        fun schedule(
            context: Context,
            delayMillis: Long,
        ) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, DownloadWakeupReceiver::class.java).setAction(ACTION_WAKEUP)
            val pendingIntent =
                PendingIntent.getBroadcast(
                    context,
                    REQUEST_CODE,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

            val triggerAt = System.currentTimeMillis() + delayMillis

            // On Android 12+, exact alarms grant an exemption to start foreground services from
            // the background. We prefer exact if the permission is granted.
            val canScheduleExact =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    alarmManager.canScheduleExactAlarms()
                } else {
                    true
                }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (canScheduleExact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        pendingIntent,
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        pendingIntent,
                    )
                }
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        }

        fun cancel(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, DownloadWakeupReceiver::class.java).setAction(ACTION_WAKEUP)
            val pendingIntent =
                PendingIntent.getBroadcast(
                    context,
                    REQUEST_CODE,
                    intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
                )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
            }
        }
    }
}
