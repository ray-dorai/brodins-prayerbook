package com.brodinsprayerbook.ui

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.OpenableColumns
import android.text.Editable
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.brodinsprayerbook.R
import com.brodinsprayerbook.data.*
import java.io.File
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var dao: PrayerBookDao
    /** The day being logged. Today unless the date in the header was changed. */
    private var viewDate: LocalDate = LocalDate.now()
    private val dateStr: String get() = viewDate.toString()
    private var currentProgramId: Long = 0
    private var currentProgramDay: ProgramDay? = null
    private var pendingDayId: Long? = null
    private val prefs by lazy { getSharedPreferences("prayerbook", MODE_PRIVATE) }
    private val unit: String get() = prefs.getString("unit", "lb") ?: "lb"
    private var restTimer: CountDownTimer? = null
    private var restEndsAt = 0L       // on the elapsedRealtime clock
    private var lastRestSeconds = 0   // what the strip restarts with
    private var resumed = false
    private val notifyPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importProgramFrom(uri)
    }
    private val restoreLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirmRestore(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        applySystemInsets()

        dao = PrayerBookDb.get(this).dao()
        // Opened from a shared file before first run: set up first
        if (dao.allExercises().isEmpty()) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
        FormulaEngine.increment = prefs.getFloat("increment", 5f).toDouble()

        setupProgramSpinner()

        findViewById<Button>(R.id.btnFinish).setOnClickListener { finishSession() }
        findViewById<View>(R.id.btnSettings).setOnClickListener { showSettings() }
        findViewById<View>(R.id.dateHeader).setOnClickListener { pickDate() }
        findViewById<View>(R.id.restTimer).setOnClickListener { if (restTimer != null) stopRest() else startRest() }
        showRestIdle()
        applyKeepScreenOn()
        findViewById<Button>(R.id.btnExport).setOnClickListener { exportCsv() }
        findViewById<Button>(R.id.btnManage).setOnClickListener { showManageDialog() }
        if (savedInstanceState == null) handleSharedFile(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSharedFile(intent)
    }

    /** A program CSV opened or shared from another app. */
    private fun handleSharedFile(intent: Intent?) {
        val uri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> @Suppress("DEPRECATION") intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            else -> null
        } ?: return
        importProgramFrom(uri)
    }

    // --- Edge-to-edge: header runs under the status bar, bottom bar under the nav bar ---

    private fun applySystemInsets() {
        val root = findViewById<View>(R.id.root)
        val header = findViewById<View>(R.id.header)
        val bottomBar = findViewById<View>(R.id.bottomBar)
        val headerTop = header.paddingTop
        val barBottom = bottomBar.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            header.updatePadding(top = headerTop + bars.top)
            // The keyboard covers the nav bar, so only one of the two applies
            bottomBar.updatePadding(bottom = barBottom + if (ime.bottom > 0) 0 else bars.bottom)
            root.updatePadding(left = bars.left, right = bars.right, bottom = ime.bottom)
            insets
        }
    }

    // --- Date: tap the header to log or correct another day ---

    private fun pickDate() {
        DatePickerDialog(this, { _, y, m, d ->
            viewDate = LocalDate.of(y, m + 1, d)
            setupProgramSpinner()
        }, viewDate.year, viewDate.monthValue - 1, viewDate.dayOfMonth).apply {
            datePicker.maxDate = System.currentTimeMillis()
        }.show()
    }

    // --- Program & Day selection ---

    private fun setupProgramSpinner() {
        val programs = dao.allPrograms()
        val spinner = findViewById<Spinner>(R.id.programSpinner)
        // The old listener must not fire while the list is being replaced
        spinner.onItemSelectedListener = null
        spinner.adapter = ArrayAdapter(this, R.layout.spinner_item,
            programs.map { it.name }).also { it.setDropDownViewResource(R.layout.spinner_dropdown) }
        if (programs.isEmpty()) {
            currentProgramId = 0
            currentProgramDay = null
            findViewById<Spinner>(R.id.daySpinner).adapter = null
            findViewById<LinearLayout>(R.id.exerciseContainer).removeAllViews()
            return
        }

        // Reopen on the program last in use
        val activeIdx = programs.indexOfFirst { it.id == prefs.getLong("activeProgramId", -1) }
        if (activeIdx >= 0) spinner.setSelection(activeIdx)

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                currentProgramId = programs[pos].id
                prefs.edit().putLong("activeProgramId", currentProgramId).apply()
                setupDaySpinner(programs[pos])
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun setupDaySpinner(program: Program) {
        val state = dao.stateFor(program.id) ?: UserProgramState(programId = program.id).also { dao.upsertState(it) }

        val isToday = viewDate == LocalDate.now()
        val dow = viewDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()).uppercase()
        findViewById<TextView>(R.id.dateHeader).apply {
            text = "$dow  $dateStr${if (isToday) "" else "  (PAST)"}\n" +
                "CYCLE ${state.cycleCount}  ·  WEEK ${state.currentWeek + 1} OF ${program.cycleWeeks}"
            setTextColor(getColor(if (isToday) R.color.lapis else R.color.crimson_light))
        }

        val days = dao.daysForProgram(program.id)
        val spinner = findViewById<Spinner>(R.id.daySpinner)
        spinner.onItemSelectedListener = null
        if (days.isEmpty()) {
            currentProgramDay = null
            spinner.adapter = null
            findViewById<LinearLayout>(R.id.exerciseContainer).removeAllViews()
            return
        }

        spinner.adapter = ArrayAdapter(this, R.layout.spinner_item,
            days.map {
                val weekLabel = if (program.cycleWeeks > 1) "W${it.weekIndex + 1} " else ""
                "$weekLabel${it.name.ifBlank { "Day ${it.dayIndex + 1}" }}"
            }
        ).also { it.setDropDownViewResource(R.layout.spinner_dropdown) }

        // Open on: a day asked for from the calendar, else what was logged on a past date,
        // else the next session due in the program
        val dayIds = days.map { it.id }
        val wanted = pendingDayId?.takeIf { it in dayIds }
            ?: if (isToday) null else dao.logsOn(dateStr).firstOrNull { it.programDayId in dayIds }?.programDayId
        pendingDayId = null
        val defaultIdx = (if (wanted != null) days.indexOfFirst { it.id == wanted }
            else days.indexOfFirst { it.weekIndex == state.currentWeek && it.dayIndex == state.currentDay }
        ).coerceAtLeast(0)
        spinner.setSelection(defaultIdx)

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                renderDay(program, days[pos])
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    // --- Render the day with formula resolution ---

    private fun renderDay(program: Program, programDay: ProgramDay) {
        val container = findViewById<LinearLayout>(R.id.exerciseContainer)
        container.removeAllViews()

        currentProgramDay = programDay
        val cycle = dao.stateFor(programDay.programId)?.cycleCount ?: 1
        val planned = dao.plannedSetsForDay(programDay.id)
        val log = dao.logFor(dateStr, programDay.id) ?: run {
            dao.insertWorkoutLog(WorkoutLog(
                date = dateStr, programDayId = programDay.id, cycle = cycle,
                programName = program.name, week = programDay.weekIndex + 1, dayName = programDay.name
            ))
            dao.logFor(dateStr, programDay.id)!!
        }
        findViewById<Button>(R.id.btnFinish).text = if (log.completed) "Finished ✓" else "Finish Session"
        val actuals = dao.actualSetsForLog(log.id).toMutableList()

        val byExercise = planned.groupBy { it.exerciseId }

        for ((exerciseId, sets) in byExercise) {
            val exerciseView = LayoutInflater.from(this).inflate(R.layout.item_exercise, container, false)
            // Illuminated initial: first letter in vermilion
            val exerciseName = sets.first().exerciseName
            exerciseView.findViewById<TextView>(R.id.exerciseName).text = SpannableString(exerciseName).apply {
                if (exerciseName.isNotEmpty()) setSpan(ForegroundColorSpan(getColor(R.color.crimson_light)),
                    0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            val last = dao.lastTime(exerciseId, dateStr)
            if (last.isNotEmpty()) exerciseView.findViewById<TextView>(R.id.lastTime).apply {
                text = "Last (${last.first().date}):  " + summarize(last)
                visibility = View.VISIBLE
            }
            // Rest for this exercise: the lifter's own, else the program's, else the default
            val exerciseRest = sets.first().exerciseRest
            val restFor = { ps: PlannedSetWithName ->
                if (exerciseRest > 0) exerciseRest else if (ps.restSeconds > 0) ps.restSeconds
                else prefs.getInt("restSeconds", 120)
            }
            exerciseView.findViewById<TextView>(R.id.restLabel).apply {
                val lengths = sets.map(restFor).distinct()
                text = "rest " + lengths.joinToString(" / ") { clock(it) }
                isSelected = exerciseRest > 0
                setOnClickListener { promptExerciseRest(exerciseId, exerciseName, exerciseRest) }
            }
            val setsContainer = exerciseView.findViewById<LinearLayout>(R.id.setsContainer)

            // Get training max for this exercise
            val userMax = dao.maxFor(exerciseId)
            val tm = userMax?.trainingMax

            for (ps in sets) {
                val row = LayoutInflater.from(this).inflate(R.layout.item_set_row, setsContainer, false)

                // Left: resolve formula to actual weight
                val resolvedWeight = FormulaEngine.resolve(ps.weightFormula, tm, userMax?.oneRepMax)
                val repsLabel = ps.repsText.ifBlank { if (ps.isAmrap) "${ps.reps}+" else "${ps.reps}" }
                val weightLabel = FormulaEngine.formatWeight(resolvedWeight)
                val main = if (resolvedWeight != null) "$weightLabel × $repsLabel" else "× $repsLabel"
                val label = SpannableString(if (ps.note.isNotBlank()) "$main  ${ps.note}" else main)
                label.setSpan(StyleSpan(Typeface.BOLD), 0, main.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (ps.isAmrap) label.setSpan(ForegroundColorSpan(getColor(R.color.amrap_gold)),
                    0, main.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (ps.note.isNotBlank()) {
                    label.setSpan(ForegroundColorSpan(getColor(R.color.lapis)),
                        main.length, label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    label.setSpan(StyleSpan(Typeface.ITALIC), main.length, label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                row.findViewById<TextView>(R.id.plannedLabel).text = label

                // Right: find or create actual
                var actual = actuals.find { it.exerciseId == exerciseId && it.setNumber == ps.setNumber }
                if (actual == null) {
                    val newId = dao.insertActualSet(ActualSet(
                        workoutLogId = log.id, exerciseId = exerciseId, setNumber = ps.setNumber
                    ))
                    actual = ActualSetWithName(newId, log.id, exerciseId, ps.setNumber,
                        null, null, "", null, "", null, sets.first().exerciseName)
                }

                bindActualInputs(row, actual, ps, resolvedWeight, repsLabel, restFor(ps))
                setsContainer.addView(row)
            }
            container.addView(exerciseView)
        }
    }

    /** "130×1, 135×1, 130×3 ×3" — runs of identical sets are folded. */
    private fun summarize(sets: List<LastSet>): String {
        val parts = mutableListOf<Pair<String, Int>>()
        for (s in sets) {
            val text = (s.weight?.let { FormulaEngine.formatWeight(it) + "×" } ?: "×") + s.reps
            if (parts.lastOrNull()?.first == text) parts[parts.size - 1] = text to parts.last().second + 1
            else parts += text to 1
        }
        return parts.joinToString(",  ") { (t, n) -> if (n > 1) "$t ×$n" else t }
    }

    private fun bindActualInputs(row: View, actual: ActualSetWithName, ps: PlannedSetWithName,
                                 plannedWeight: Double?, plannedReps: String, restSeconds: Int) {
        val wt = row.findViewById<EditText>(R.id.inputWeight)
        val rp = row.findViewById<EditText>(R.id.inputReps)
        val rpe = row.findViewById<EditText>(R.id.inputRpe)
        val noteBtn = row.findViewById<View>(R.id.btnNote)
        val saveBtn = row.findViewById<View>(R.id.btnSaveSet)
        var note = actual.notes
        // Reps that can be committed without typing: a plain count, not "as many as possible" or a range
        val fixedReps = !ps.isAmrap && ps.repsText.isBlank()

        actual.weight?.let { wt.setText(FormulaEngine.formatWeight(it)) }
        actual.reps?.let { rp.setText(it.toString()) }
        actual.rpe?.let { rpe.setText(FormulaEngine.formatWeight(it)) }

        // Ghost text shows the prescription; the noose commits it
        if (plannedWeight != null) wt.hint = FormulaEngine.formatWeight(plannedWeight)
        rp.hint = if (ps.isAmrap) "max" else if (fixedReps) ps.reps.toString() else "rep"

        fun refresh() {
            saveBtn.isSelected = rp.text.toString().toIntOrNull() != null
            noteBtn.isSelected = note.isNotBlank()
        }
        refresh()

        // Everything typed is saved at once. Once a set is logged its prescription is frozen with it.
        val wasLogged = actual.reps != null
        fun save() {
            dao.updateActualSet(ActualSet(
                id = actual.id, workoutLogId = actual.workoutLogId,
                exerciseId = actual.exerciseId, setNumber = actual.setNumber,
                weight = wt.text.toString().toDoubleOrNull(), reps = rp.text.toString().toIntOrNull(),
                notes = note,
                plannedWeight = if (wasLogged) actual.plannedWeight ?: plannedWeight else plannedWeight,
                plannedReps = if (wasLogged) actual.plannedReps.ifBlank { plannedReps } else plannedReps,
                rpe = rpe.text.toString().toDoubleOrNull()
            ))
            refresh()
        }

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { save() }
        }
        wt.addTextChangedListener(watcher)
        rp.addTextChangedListener(watcher)
        rpe.addTextChangedListener(watcher)

        // The noose: commit the set as prescribed (or as typed), then rest
        saveBtn.setOnClickListener {
            if (wt.text.isBlank() && plannedWeight != null) wt.setText(FormulaEngine.formatWeight(plannedWeight))
            if (rp.text.isBlank()) {
                if (!fixedReps) {
                    rp.requestFocus()
                    toast("Enter the reps you got, then tap again.")
                    return@setOnClickListener
                }
                rp.setText(ps.reps.toString())
            }
            save()
            val weight = wt.text.toString().toDoubleOrNull()
            val reps = rp.text.toString().toIntOrNull()
            if (reps == null) return@setOnClickListener
            if (prefs.getBoolean("restAutoStart", true)) startRest(restSeconds)
            // Auto-detect new 1RM from AMRAP sets
            if (ps.isAmrap && weight != null && reps > 0) checkAndUpdate1RM(actual.exerciseId, weight, reps)
        }

        noteBtn.setOnClickListener {
            val input = EditText(this).apply {
                setText(note)
                hint = "Note for this set"
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                setSelection(text.length)
            }
            AlertDialog.Builder(this).setTitle("Note").setView(padded(input))
                .setPositiveButton("Save") { _, _ -> note = input.text.toString().trim(); save() }
                .setNegativeButton("Cancel", null).show()
        }
    }

    private fun padded(v: View): View {
        val pad = (20 * resources.displayMetrics.density).toInt()
        return FrameLayout(this).apply { setPadding(pad, pad / 2, pad, 0); addView(v) }
    }

    /**
     * If an AMRAP set yields a higher estimated 1RM, prompt to update.
     */
    private fun checkAndUpdate1RM(exerciseId: Long, weight: Double, reps: Int) {
        val estimated = FormulaEngine.estimate1RM(weight, reps)
        val current = dao.maxFor(exerciseId)
        if (current == null || estimated > current.oneRepMax * 1.01) { // >1% improvement
            val exerciseName = dao.allExercises().find { it.id == exerciseId }?.name ?: "this lift"
            val rounded = FormulaEngine.roundToPlate(estimated)
            AlertDialog.Builder(this)
                .setTitle("New PR Detected!")
                .setMessage("$exerciseName: estimated 1RM = ${FormulaEngine.formatWeight(rounded)} $unit\n" +
                    "(${FormulaEngine.formatWeight(weight)} × $reps)\n\nUpdate your max?")
                .setPositiveButton("Update") { _, _ ->
                    Maxes.set(dao, exerciseId, rounded, dateStr, "amrap")
                    toast("Updated! All programs recalculated.")
                    // Re-render to show new weights
                    pendingDayId = currentProgramDay?.id
                    setupProgramSpinner()
                }
                .setNegativeButton("Not yet", null)
                .show()
        }
    }

    // --- Rest timer ---

    private fun restView() = findViewById<TextView>(R.id.restTimer)
    private fun clock(seconds: Int) = "%d:%02d".format(seconds / 60, seconds % 60)

    private fun stripSeconds() = if (lastRestSeconds > 0) lastRestSeconds else prefs.getInt("restSeconds", 120)

    private fun showRestIdle() {
        restView().setTextColor(getColor(R.color.text_dim))
        restView().text = "Rest  ${clock(stripSeconds())}  ·  tap to start"
    }

    /** Start resting. With no length given, repeats the last one used (the default at first). */
    private fun startRest(seconds: Int = stripSeconds()) {
        restTimer?.cancel()
        val total = seconds
        if (total <= 0) return
        lastRestSeconds = total
        restEndsAt = SystemClock.elapsedRealtime() + total * 1000L
        askToNotifyOnce()
        restView().setTextColor(getColor(R.color.amber_glow))
        restTimer = object : CountDownTimer(total * 1000L, 200) {
            override fun onTick(ms: Long) {
                restView().text = "REST  ${clock(((ms + 999) / 1000).toInt())}  ·  tap to stop"
            }
            override fun onFinish() {
                restTimer = null
                restView().setTextColor(getColor(R.color.crimson_light))
                restView().text = "REST OVER  ·  tap to restart"
                // Off screen, the system alarm does the alerting instead
                if (resumed) restAlert()
            }
        }.start()
    }

    private fun stopRest() {
        restTimer?.cancel()
        restTimer = null
        showRestIdle()
    }

    private fun restAlert() {
        if (prefs.getBoolean("restVibrate", true)) try {
            getSystemService(Vibrator::class.java)
                ?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300), -1))
        } catch (_: Exception) {}
        if (prefs.getBoolean("restSound", true)) try {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 500)
            restView().postDelayed({ tone.release() }, 1000)
        } catch (_: Exception) {}
    }

    /** Android 13+ needs permission before the background alert can show. Asked once. */
    private fun askToNotifyOnce() {
        if (android.os.Build.VERSION.SDK_INT < 33 || prefs.getBoolean("askedToNotify", false)) return
        prefs.edit().putBoolean("askedToNotify", true).apply()
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED)
            notifyPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    // Leaving the screen mid-rest hands the alert to the system; coming back takes it over again
    override fun onResume() {
        super.onResume()
        resumed = true
        RestAlarm.cancel(this)
    }

    override fun onPause() {
        resumed = false
        if (restTimer != null) RestAlarm.schedule(this, restEndsAt)
        super.onPause()
    }

    override fun onDestroy() {
        restTimer?.cancel()
        super.onDestroy()
    }

    private fun promptExerciseRest(exerciseId: Long, name: String, current: Int) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "Seconds — blank to follow the program"
            if (current > 0) setText(current.toString())
        }
        AlertDialog.Builder(this).setTitle("Rest for $name").setView(padded(input))
            .setPositiveButton("Save") { _, _ ->
                dao.setExerciseRest(exerciseId, (input.text.toString().toIntOrNull() ?: 0).coerceIn(0, 3600))
                pendingDayId = currentProgramDay?.id
                setupProgramSpinner()
            }.setNegativeButton("Cancel", null).show()
    }

    // --- Settings ---

    private fun applyKeepScreenOn() {
        if (prefs.getBoolean("keepScreenOn", false)) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun showSettings() {
        val pad = (20 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
        }
        fun label(text: String) = layout.addView(TextView(this).apply { this.text = text; textSize = 14f })
        fun check(label: String, key: String, default: Boolean) = CheckBox(this).apply {
            text = label
            isChecked = prefs.getBoolean(key, default)
        }.also { layout.addView(it) }

        label("Default rest between sets (seconds)")
        val seconds = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(prefs.getInt("restSeconds", 120).toString())
        }
        layout.addView(seconds)
        val autoStart = check("Start the rest timer when I save a set", "restAutoStart", true)
        val vibrate = check("Vibrate when rest ends", "restVibrate", true)
        val sound = check("Sound when rest ends", "restSound", true)
        val screenOn = check("Keep the screen on", "keepScreenOn", false)

        label("Weight unit (a label only; numbers are not converted)")
        val units = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        val lb = RadioButton(this).apply { text = "lb"; id = View.generateViewId() }
        val kg = RadioButton(this).apply { text = "kg"; id = View.generateViewId() }
        units.addView(lb); units.addView(kg)
        units.check(if (unit == "kg") kg.id else lb.id)
        layout.addView(units)

        label("Round calculated weights to the nearest")
        val increment = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(FormulaEngine.formatWeight(prefs.getFloat("increment", 5f).toDouble()))
        }
        layout.addView(increment)

        AlertDialog.Builder(this).setTitle("Settings").setView(ScrollView(this).apply { addView(layout) })
            .setPositiveButton("Save") { _, _ ->
                val step = increment.text.toString().toFloatOrNull()?.takeIf { it > 0f } ?: 5f
                prefs.edit()
                    .putInt("restSeconds", (seconds.text.toString().toIntOrNull() ?: 120).coerceIn(0, 3600))
                    .putBoolean("restAutoStart", autoStart.isChecked)
                    .putBoolean("restVibrate", vibrate.isChecked)
                    .putBoolean("restSound", sound.isChecked)
                    .putBoolean("keepScreenOn", screenOn.isChecked)
                    .putString("unit", if (units.checkedRadioButtonId == kg.id) "kg" else "lb")
                    .putFloat("increment", step)
                    .apply()
                FormulaEngine.increment = step.toDouble()
                applyKeepScreenOn()
                if (restTimer == null) showRestIdle()
                pendingDayId = currentProgramDay?.id
                setupProgramSpinner()
            }.setNegativeButton("Cancel", null).show()
    }

    // --- Finishing a session moves the program on: next day, next week, next cycle ---

    private fun finishSession() {
        val day = currentProgramDay ?: return
        val prog = dao.allPrograms().find { it.id == day.programId } ?: return
        val state = dao.stateFor(prog.id) ?: return
        val log = dao.logFor(dateStr, day.id) ?: return
        if (log.completed) { confirmReopen(log, day, state); return }
        dao.updateWorkoutLog(log.copy(completed = true))

        val days = dao.daysForProgram(prog.id)
        val doneIdx = days.indexOfFirst { it.id == day.id }
        val pointerIdx = days.indexOfFirst {
            it.weekIndex == state.currentWeek && it.dayIndex == state.currentDay
        }.coerceAtLeast(0)

        // Redoing an earlier session is recorded but doesn't move the program backwards
        if (doneIdx < pointerIdx) {
            toast("Session recorded.")
            pendingDayId = day.id
            setupProgramSpinner()
            return
        }

        val next = days.getOrNull(doneIdx + 1)
        if (next != null) {
            dao.upsertState(state.copy(currentWeek = next.weekIndex, currentDay = next.dayIndex))
            val weekNote = if (next.weekIndex != day.weekIndex) "Week ${next.weekIndex + 1} begins. " else ""
            toast("${weekNote}Next: ${next.name.ifBlank { "Day ${next.dayIndex + 1}" }}")
            setupProgramSpinner()
        } else {
            val changes = applyCycleMaxes(prog.id, state.cycleCount)
            dao.upsertState(state.copy(currentWeek = 0, currentDay = 0, cycleCount = state.cycleCount + 1))
            val summary = if (changes.isEmpty()) "No lift beat its current max, so the weights stay as they were."
                else "New maxes from what you lifted:\n\n" + changes.joinToString("\n")
            AlertDialog.Builder(this)
                .setTitle("Cycle ${state.cycleCount} complete")
                .setMessage("$summary\n\nCycle ${state.cycleCount + 1} begins at week 1.")
                .setPositiveButton("Onward", null)
                .show()
            setupProgramSpinner()
        }
    }

    /** Undo a finish: the session opens again and the program steps back to it. */
    private fun confirmReopen(log: WorkoutLog, day: ProgramDay, state: UserProgramState) {
        AlertDialog.Builder(this)
            .setTitle("Reopen this session?")
            .setMessage("It will be marked unfinished and become the next session due. " +
                "Sets you saved stay saved. Maxes changed at the end of a cycle are not changed back.")
            .setPositiveButton("Reopen") { _, _ ->
                dao.updateWorkoutLog(log.copy(completed = false))
                dao.upsertState(state.copy(
                    currentWeek = day.weekIndex, currentDay = day.dayIndex,
                    cycleCount = minOf(state.cycleCount, log.cycle)
                ))
                pendingDayId = day.id
                setupProgramSpinner()
            }.setNegativeButton("Cancel", null).show()
    }

    /** End of cycle: raise each main lift's 1RM to the best it showed during the cycle. */
    private fun applyCycleMaxes(programId: Long, cycle: Int): List<String> {
        val mainLifts = dao.mainLifts().associateBy { it.id }
        val best = FormulaEngine.bestOneRepMaxes(
            dao.liftedInCycle(programId, cycle).filter { it.exerciseId in mainLifts })
        val changes = mutableListOf<String>()
        for ((exerciseId, est) in best) {
            val current = dao.maxFor(exerciseId)
            if (current != null && est <= current.oneRepMax) continue
            Maxes.set(dao, exerciseId, est, dateStr, "cycle")
            val from = current?.let { FormulaEngine.formatWeight(it.oneRepMax) } ?: "—"
            changes += "${mainLifts[exerciseId]!!.name}: $from → ${FormulaEngine.formatWeight(est)}"
        }
        return changes
    }

    // --- Calendar of past sessions ---

    private fun showHistory() {
        val sessions = dao.sessions().filter { it.setsLogged > 0 || it.completed }
        if (sessions.isEmpty()) { toast("Nothing logged yet."); return }
        val labels = sessions.map { s ->
            val dow = try {
                LocalDate.parse(s.date).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            } catch (_: Exception) { "" }
            val where = if (s.program.isBlank()) "" else "\n${s.program} · C${s.cycle} W${s.week} · ${s.dayName}"
            "${s.date} $dow${if (s.completed) "  ✓" else ""}$where"
        }
        AlertDialog.Builder(this).setTitle("Calendar")
            .setItems(labels.toTypedArray()) { _, which -> showSession(sessions[which], labels[which]) }
            .setNegativeButton("Close", null).show()
    }

    private fun showSession(s: SessionRow, title: String) {
        val lines = dao.actualSetsForLog(s.logId)
            .filter { it.reps != null || it.weight != null }
            .groupBy { it.exerciseName }
            .map { (name, sets) ->
                name + "\n" + sets.joinToString("\n") {
                    "   ${FormulaEngine.formatWeight(it.weight)} × ${it.reps ?: "—"}" +
                        (it.rpe?.let { r -> "  @${FormulaEngine.formatWeight(r)}" } ?: "") +
                        if (it.notes.isNotBlank()) "  (${it.notes})" else ""
                }
            }
        val dialog = AlertDialog.Builder(this).setTitle(title)
            .setMessage(if (lines.isEmpty()) "Finished with no sets logged." else lines.joinToString("\n\n"))
            .setPositiveButton("Back") { _, _ -> showHistory() }
        // Open the day itself to correct it, if its program still has that day
        val day = s.programDayId
        val programId = day?.let { id -> dao.allPrograms().firstOrNull { p -> dao.daysForProgram(p.id).any { it.id == id } }?.id }
        if (day != null && programId != null) dialog.setNeutralButton("Open to edit") { _, _ ->
            viewDate = try { LocalDate.parse(s.date) } catch (_: Exception) { LocalDate.now() }
            prefs.edit().putLong("activeProgramId", programId).apply()
            pendingDayId = day
            setupProgramSpinner()
        }
        dialog.show()
    }

    // --- Export: the log and the max history, as two spreadsheets ---

    private fun share(files: List<File>, type: String, title: String) {
        val uris = ArrayList<Uri>(files.map { FileProvider.getUriForFile(this, "$packageName.fileprovider", it) })
        val intent = if (uris.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
            else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        intent.type = type
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(intent, title))
    }

    private fun exportCsv() {
        share(listOf(
            CsvExport.exportLog(this, dao.allActualsForExport(), unit),
            CsvExport.exportMaxes(this, dao.maxHistoryForExport(), unit)
        ), "text/csv", "Export Prayerbook")
    }

    // --- Manage dialog ---

    private fun showManageDialog() {
        val items = listOf<Pair<String, () -> Unit>>(
            "Calendar" to ::showHistory,
            "Update Maxes" to ::showUpdateMaxes,
            "Skip to Next Week" to ::advanceWeek,
            "Import Program (CSV)" to { importLauncher.launch(arrayOf("text/*", "application/json", "*/*")) },
            "Export Program (CSV)" to ::exportProgram,
            "Delete Program" to ::deleteProgram,
            "Add Exercise" to ::promptAddExercise,
            "Back Up Everything" to ::backup,
            "Restore From Backup" to { restoreLauncher.launch(arrayOf("*/*")) }
        )
        AlertDialog.Builder(this).setTitle("Manage")
            .setItems(items.map { it.first }.toTypedArray()) { _, w -> items[w].second() }
            .show()
    }

    private fun pickProgram(title: String, then: (Program) -> Unit) {
        val programs = dao.allPrograms()
        if (programs.isEmpty()) { toast("No programs"); return }
        AlertDialog.Builder(this).setTitle(title)
            .setItems(programs.map { it.name }.toTypedArray()) { _, which -> then(programs[which]) }
            .show()
    }

    private fun exportProgram() = pickProgram("Export which program?") { program ->
        val file = ProgramPorter.exportCsv(this, dao, program.id)
        if (file != null) share(listOf(file), "text/csv", "Share Program") else toast("Export failed")
    }

    private fun deleteProgram() = pickProgram("Delete which program?") { program ->
        AlertDialog.Builder(this).setTitle("Delete ${program.name}?")
            .setMessage("The program is removed. Sessions already logged with it stay in the calendar and the export.")
            .setPositiveButton("Delete") { _, _ ->
                dao.deleteProgram(program.id)
                setupProgramSpinner()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun importProgramFrom(uri: Uri) {
        val text = try {
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        } catch (_: Exception) { null }
        if (text == null) { toast("Could not read file"); return }

        // Older JSON exports still import
        if (text.trimStart().startsWith("{")) {
            val result = try { ProgramPorter.importFromJson(dao, text) }
                catch (e: Exception) { ProgramPorter.ImportResult(false, "Invalid format: ${e.message}") }
            longToast(result.message)
            if (result.success) setupProgramSpinner()
            return
        }

        val rows = try { ProgramCsv.parse(text) }
            catch (e: Exception) { longToast("Can't import: ${e.message}"); return }

        var fileName = ""
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) fileName = it.getString(0) ?: ""
            }
        } catch (_: Exception) {}
        if (fileName.isBlank()) fileName = uri.lastPathSegment?.substringAfterLast('/') ?: ""
        val input = EditText(this).apply {
            setText(fileName.substringBeforeLast('.').replace('_', ' ').trim())
            hint = "Program name"
        }
        AlertDialog.Builder(this).setTitle("Name this program").setView(padded(input))
            .setPositiveButton("Import") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isBlank()) { toast("A name is needed."); return@setPositiveButton }
                fun run() {
                    val result = ProgramPorter.importCsv(dao, name, rows)
                    longToast(result.message)
                    if (result.success) setupProgramSpinner()
                }
                if (dao.allPrograms().none { it.name == name }) run()
                else AlertDialog.Builder(this).setTitle("Replace $name?")
                    .setMessage("A program with this name exists. Its days and sets are replaced by the file's, " +
                        "and it starts again at week 1. Logged sessions are kept.")
                    .setPositiveButton("Replace") { _, _ -> run() }
                    .setNegativeButton("Cancel", null).show()
            }.setNegativeButton("Cancel", null).show()
    }

    // --- Backup: the whole database as one file ---

    private fun backup() {
        val file = File(getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS),
            "prayerbook_backup_${LocalDate.now()}.db")
        try {
            PrayerBookDb.backupTo(this, file)
            share(listOf(file), "application/octet-stream", "Save Backup")
        } catch (e: Exception) { longToast("Backup failed: ${e.message}") }
    }

    private fun confirmRestore(uri: Uri) {
        AlertDialog.Builder(this).setTitle("Restore from this backup?")
            .setMessage("Everything in the app now — programs, maxes and the whole log — is replaced by the backup.")
            .setPositiveButton("Restore") { _, _ ->
                val ok = try {
                    contentResolver.openInputStream(uri)?.let { PrayerBookDb.restoreFrom(this, it) } ?: false
                } catch (_: Exception) { false }
                if (!ok) { longToast("That file is not a Prayerbook backup."); return@setPositiveButton }
                startActivity(Intent(this, OnboardingActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                finish()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showUpdateMaxes() {
        val mainLifts = dao.mainLifts()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
        }
        val inputs = mutableMapOf<Long, EditText>()
        for (lift in mainLifts) {
            val current = dao.maxFor(lift.id)
            val label = TextView(this).apply {
                text = "${lift.name}: current 1RM = ${current?.let { FormulaEngine.formatWeight(it.oneRepMax) } ?: "—"} $unit"
                textSize = 13f
            }
            layout.addView(label)
            val input = EditText(this).apply {
                hint = "New 1RM"
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                current?.oneRepMax?.let { setText(FormulaEngine.formatWeight(it)) }
            }
            layout.addView(input)
            inputs[lift.id] = input
        }
        AlertDialog.Builder(this).setTitle("Update Maxes").setView(ScrollView(this).apply { addView(layout) })
            .setPositiveButton("Save") { _, _ ->
                for ((eid, input) in inputs) {
                    val v = input.text.toString().toDoubleOrNull()?.takeIf { it > 0 } ?: continue
                    Maxes.set(dao, eid, v, LocalDate.now().toString(), "manual")
                }
                toast("Maxes updated. All programs recalculated.")
                pendingDayId = currentProgramDay?.id
                setupProgramSpinner()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun advanceWeek() {
        if (currentProgramId == 0L) return
        val prog = dao.allPrograms().find { it.id == currentProgramId } ?: return
        val state = dao.stateFor(prog.id) ?: return
        val nextWeek = (state.currentWeek + 1) % prog.cycleWeeks
        val newCycle = if (nextWeek == 0) state.cycleCount + 1 else state.cycleCount
        dao.upsertState(state.copy(currentWeek = nextWeek, currentDay = 0, cycleCount = newCycle))
        toast("Week ${nextWeek + 1} of ${prog.cycleWeeks}")
        setupProgramSpinner()
    }

    private fun promptAddExercise() {
        val input = EditText(this).apply { hint = "Exercise name" }
        AlertDialog.Builder(this).setTitle("New Exercise").setView(padded(input))
            .setPositiveButton("Add") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotBlank()) dao.insertExercise(Exercise(name = name))
            }.show()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    private fun longToast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
