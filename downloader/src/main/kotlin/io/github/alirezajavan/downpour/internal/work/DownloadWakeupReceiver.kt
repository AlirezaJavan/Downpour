package io.github.alirezajavan.downpour.internal.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
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
