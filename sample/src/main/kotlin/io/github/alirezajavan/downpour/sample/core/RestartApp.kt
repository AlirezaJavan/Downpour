package io.github.alirezajavan.downpour.sample.core

import android.content.Context
import android.content.Intent

/**
 * [Downpour.getInstance] is a process-wide singleton that ignores config changes after first
 * creation, so applying a new [SampleSettings] requires a fresh process. This relaunches the
 * launcher activity in a new task and kills the current process.
 */
fun restartApp(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
    intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    if (intent != null) context.startActivity(intent)
    Runtime.getRuntime().exit(0)
}
