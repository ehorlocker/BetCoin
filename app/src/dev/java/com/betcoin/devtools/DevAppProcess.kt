package com.betcoin.devtools

import android.app.Activity
import android.content.Intent
import android.os.Process

/** Restarts the app in a fresh task (a fresh activity re-evaluates the launch route). */
fun restartApp(activity: Activity) {
    val intent = activity.packageManager.getLaunchIntentForPackage(activity.packageName)
    if (intent != null) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        activity.startActivity(intent)
    }
    activity.finish()
}

/** Closes the app: removes the task (so no saved state is restored) and kills the process. */
fun closeApp(activity: Activity) {
    activity.finishAndRemoveTask()
    Process.killProcess(Process.myPid())
}
