package com.brodinsprayerbook

import android.content.Context
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.brodinsprayerbook.data.FormulaEngine
import com.brodinsprayerbook.data.PrayerBookDb
import org.junit.rules.ExternalResource
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog

val context: Context get() = ApplicationProvider.getApplicationContext()

/** A throwaway database for tests that only need the DAO. Close it when done. */
fun memoryDb(): PrayerBookDb =
    Room.inMemoryDatabaseBuilder(context, PrayerBookDb::class.java).allowMainThreadQueries().build()

/** Run everything queued on the main thread: layout, spinner selections, posted work. */
fun idle() = shadowOf(Looper.getMainLooper()).idle()

/**
 * Robolectric gives each test fresh files and preferences, but statics outlive a test.
 * This puts the app's own statics back, so the next test opens a new, empty database.
 */
class AppStateRule : ExternalResource() {
    override fun before() = reset()
    override fun after() = reset()
    private fun reset() {
        PrayerBookDb.close()
        FormulaEngine.increment = 5.0
        // FileProvider remembers the first test's folders; without this, sharing a file fails in every later test
        FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }
            .let { (it.get(null) as MutableMap<*, *>).clear() }
    }
}

fun View.descendants(): Sequence<View> = sequence {
    yield(this@descendants)
    if (this@descendants is ViewGroup) for (i in 0 until childCount) yieldAll(getChildAt(i).descendants())
}

inline fun <reified T : View> View.all(): List<T> = descendants().filterIsInstance<T>().toList()

fun View.withText(text: String): TextView =
    all<TextView>().first { it.text.toString() == text }

/** The dialog on screen. The app only shows AppCompat dialogs. */
fun latestDialog(): AlertDialog = ShadowDialog.getLatestDialog() as AlertDialog

fun AlertDialog.title(): String = findViewById<TextView>(androidx.appcompat.R.id.alertTitle)!!.text.toString()

fun AlertDialog.message(): String = findViewById<TextView>(android.R.id.message)!!.text.toString()

fun AlertDialog.tap(button: Int) {
    getButton(button).performClick()
    idle()
}

/** Choose a row of a list dialog by its text. */
fun AlertDialog.pick(label: String) {
    val adapter = listView.adapter
    val position = (0 until adapter.count).first { label in adapter.getItem(it).toString() }
    listView.performItemClick(null, position, adapter.getItemId(position))
    idle()
}

fun AlertDialog.items(): List<String> = (0 until listView.adapter.count).map { listView.adapter.getItem(it).toString() }
