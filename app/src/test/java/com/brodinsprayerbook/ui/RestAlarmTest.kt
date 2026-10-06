package com.brodinsprayerbook.ui

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.brodinsprayerbook.context
import com.brodinsprayerbook.idle
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(AndroidJUnit4::class)
class RestAlarmTest {
    private val alarms get() = shadowOf(context.getSystemService(AlarmManager::class.java))
    private val notifications get() = context.getSystemService(NotificationManager::class.java)
    private val shown get() = shadowOf(notifications).allNotifications

    @Test fun theAlarmIsSetForTheExactMomentRestEndsAndWakesThePhone() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val restEnds = SystemClock.elapsedRealtime() + 90_000
        RestAlarm.schedule(context, restEnds)

        val alarm = alarms.scheduledAlarms.single()
        assertEquals(AlarmManager.ELAPSED_REALTIME_WAKEUP, alarm.type)
        assertEquals(restEnds, alarm.triggerAtMs)
        assertEquals(ShadowAlarmManager.WINDOW_EXACT, alarm.windowLengthMs)
        assertTrue("must fire in doze, while the phone is in a pocket", alarm.isAllowWhileIdle)
    }

    @Test fun whereExactAlarmsAreNotAllowedItStillWakesThePhoneAboutThen() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val restEnds = SystemClock.elapsedRealtime() + 90_000
        RestAlarm.schedule(context, restEnds)

        val alarm = alarms.scheduledAlarms.single()
        assertEquals(AlarmManager.ELAPSED_REALTIME_WAKEUP, alarm.type)
        assertEquals(restEnds, alarm.triggerAtMs)
        assertTrue(alarm.isAllowWhileIdle)
    }

    @Test fun startingAnotherRestMovesTheAlarmRatherThanAddingOne() {
        RestAlarm.schedule(context, 60_000)
        RestAlarm.schedule(context, 120_000)

        assertEquals(120_000, alarms.scheduledAlarms.single().triggerAtMs)
    }

    @Test fun whenTheAlarmFiresTheLifterIsToldRestIsOver() {
        RestAlarm.schedule(context, SystemClock.elapsedRealtime() + 1_000)

        alarms.scheduledAlarms.single().operation!!.send()
        idle()

        val notification = shown.single()
        assertEquals("Rest over", notification.extras.getString("android.title"))
        assertEquals("rest_sv", notification.channelId)
        assertNotNull("tapping it must open the app", notification.contentIntent)
        val channel = notifications.getNotificationChannel("rest_sv")
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        assertNotNull(channel.sound)
        assertArrayEquals(longArrayOf(0, 300, 150, 300), channel.vibrationPattern)
    }

    @Test fun theAlertFollowsTheSoundAndVibrationSettings() {
        context.getSharedPreferences("prayerbook", Context.MODE_PRIVATE).edit()
            .putBoolean("restSound", false).putBoolean("restVibrate", false).commit()

        RestAlarm.notifyRestOver(context)

        assertEquals("rest_qn", shown.single().channelId)
        val channel = notifications.getNotificationChannel("rest_qn")
        assertNull(channel.sound)
        assertFalse(channel.shouldVibrate())
    }

    @Test fun cancellingClearsBothTheAlarmAndAnAlertAlreadyShown() {
        RestAlarm.schedule(context, SystemClock.elapsedRealtime() + 90_000)
        RestAlarm.notifyRestOver(context)

        RestAlarm.cancel(context)

        assertTrue(alarms.scheduledAlarms.isEmpty())
        assertTrue(shown.isEmpty())
    }
}
