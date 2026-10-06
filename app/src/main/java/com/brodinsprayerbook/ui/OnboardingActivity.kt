package com.brodinsprayerbook.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.brodinsprayerbook.R
import com.brodinsprayerbook.data.*
import java.time.LocalDate

class OnboardingActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)

        val dao = PrayerBookDb.get(this).dao()

        if (dao.allExercises().isEmpty()) ProgramSeeder.seed(dao)
        // Programs added after first release; no-op once present
        ProgramSeeder.seedHighFrequencyStrength(dao)

        if (dao.allMaxes().isNotEmpty()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        val mainLifts = dao.mainLifts()

        // Colors from our palette
        val parchment = getColor(R.color.parchment)
        val amber = getColor(R.color.amber)
        val textWarm = getColor(R.color.text_warm)
        val textDim = getColor(R.color.text_dim)
        val textLight = getColor(R.color.text_light)

        val headerFont = try { ResourcesCompat.getFont(this, R.font.unifraktur) } catch (_: Exception) { null }
        val bodyFont = try { ResourcesCompat.getFont(this, R.font.crimson_text) } catch (_: Exception) { null }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(80, 120, 80, 80)
            setBackgroundColor(parchment)
        }

        // Brodin himself
        val logoSize = (220 * resources.displayMetrics.density).toInt()
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.brodin_logo)
            adjustViewBounds = true
            contentDescription = "Brodin"
            layoutParams = LinearLayout.LayoutParams(logoSize, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = 24
            }
        }
        layout.addView(logo)

        // Title — blackletter
        val title = TextView(this).apply {
            text = "Brodin's\nPrayerbook"
            textSize = 34f
            typeface = headerFont ?: Typeface.SERIF
            setTextColor(amber)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 8)
        }
        layout.addView(title)

        // Ornamental divider
        val rule = View(this).apply {
            setBackgroundColor(getColor(R.color.crimson))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 6).apply {
                bottomMargin = 24
            }
        }
        layout.addView(rule)

        // Subtitle
        val subtitle = TextView(this).apply {
            text = "Declare thy maximal offerings, brother.\nAll programmes shall be reckoned from these.\nLeave blank any lift thou dost not train."
            textSize = 15f
            typeface = bodyFont ?: Typeface.SERIF
            setTextColor(textWarm)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 40)
        }
        layout.addView(subtitle)

        val inputs = mutableMapOf<Long, EditText>()
        for (lift in mainLifts) {
            val label = TextView(this).apply {
                text = lift.name
                textSize = 17f
                typeface = headerFont ?: Typeface.SERIF
                setTextColor(amber)
                setPadding(0, 20, 0, 4)
            }
            layout.addView(label)

            val input = EditText(this).apply {
                hint = "1RM"
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                textSize = 20f
                typeface = bodyFont ?: Typeface.SERIF
                setTextColor(textLight)
                setHintTextColor(textDim)
                setBackgroundResource(R.drawable.input_box)
                setPadding(24, 16, 24, 16)
            }
            layout.addView(input)
            inputs[lift.id] = input
        }

        // Spacer
        layout.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 48)
        })

        // Begin button
        val btn = Button(this).apply {
            text = "Commence Thy Training"
            textSize = 16f
            typeface = headerFont ?: Typeface.SERIF
            setTextColor(getColorStateList(R.color.btn_text))
            setBackgroundResource(R.drawable.btn_medieval)
            setPadding(32, 16, 32, 16)
        }
        layout.addView(btn)

        btn.setOnClickListener {
            val today = LocalDate.now().toString()
            // Only the lifts thy programme uses need a max; the rest may be left blank
            var bad = false
            val entered = mutableMapOf<Long, Double>()
            for ((exerciseId, input) in inputs) {
                val text = input.text.toString().trim()
                if (text.isEmpty()) continue
                val value = text.toDoubleOrNull()
                if (value == null || value <= 0) { input.error = "Enter a number"; bad = true }
                else entered[exerciseId] = value
            }
            if (!bad && entered.isEmpty()) {
                Toast.makeText(this, "Enter at least one max.", Toast.LENGTH_SHORT).show()
            } else if (!bad) {
                for ((exerciseId, value) in entered) Maxes.set(dao, exerciseId, value, today, "onboarding")
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }

        val scroll = ScrollView(this).apply {
            setBackgroundColor(parchment)
            addView(layout)
        }
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val i = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            v.setPadding(i.left, i.top, i.right, i.bottom)
            insets
        }
        setContentView(scroll)
    }
}
