package com.brodinsprayerbook.ui

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.brodinsprayerbook.R

/**
 * The rest timer's alert while the app is not on screen. The activity hands the end time to
 * the system alarm when it leaves the foreground and takes it back when it returns, so the
 * alert still fires if the screen is off or the app has been closed.
 */
object RestAlarm {
    private const val NOTIFICATION_ID = 1

    private fun pending(ctx: Context) = PendingIntent.getBroadcast(
        ctx, 0, Intent(ctx, RestAlarmReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** [atElapsed] is on the SystemClock.elapsedRealtime() clock. */
    fun schedule(ctx: Context, atElapsed: Long) {
        val alarms = ctx.getSystemService(AlarmManager::class.java) ?: return
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        if (exact) alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, atElapsed, pending(ctx))
        else alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, atElapsed, pending(ctx))
    }

    fun cancel(ctx: Context) {
        ctx.getSystemService(AlarmManager::class.java)?.cancel(pending(ctx))
        ctx.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }

    fun notifyRestOver(ctx: Context) {
        val prefs = ctx.getSharedPreferences("prayerbook", Context.MODE_PRIVATE)
        val sound = prefs.getBoolean("restSound", true)
        val vibrate = prefs.getBoolean("restVibrate", true)
        val manager = ctx.getSystemService(NotificationManager::class.java) ?: return

        // A channel's sound and vibration are fixed once created, so there is one per combination
        val channelId = "rest_${if (sound) "s" else "q"}${if (vibrate) "v" else "n"}"
        manager.createNotificationChannel(
            NotificationChannel(channelId, "Rest timer", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Tells you when rest between sets is over"
                if (!sound) setSound(null, null)
                enableVibration(vibrate)
                if (vibrate) vibrationPattern = longArrayOf(0, 300, 150, 300)
            })

        val open = PendingIntent.getActivity(ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(NOTIFICATION_ID, NotificationCompat.Builder(ctx, channelId)
            .setSmallIcon(R.drawable.ic_noose)
            .setContentTitle("Rest over")
            .setContentText("Thy next set awaits.")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build())
    }
}

class RestAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = RestAlarm.notifyRestOver(context)
}
