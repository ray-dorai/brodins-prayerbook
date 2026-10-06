package com.brodinsprayerbook.data

/**
 * Seeds all built-in programs. Each program defines its own days, weeks, sets, and formulas.
 * Everything is expressed in terms of TM (Training Max) so it auto-updates when maxes change.
 */
object ProgramSeeder {

    fun seed(dao: PrayerBookDao) {
        // Seed the full exercise library first
        ExerciseLibrary.seed(dao)

        // Look up main lifts by name
        val all = dao.allExercises().associateBy { it.name }
        val squat = all["Squat"]!!.id
        val bench = all["Bench Press"]!!.id
        val dead  = all["Deadlift"]!!.id
        val ohp   = all["Overhead Press"]!!.id
        val row   = all["Barbell Row"]!!.id

        seed531(dao, squat, bench, dead, ohp)
        seedStartingStrength(dao, squat, bench, dead, ohp, row)
        seedStrongLifts(dao, squat, bench, dead, ohp, row)
        seedTexasMethod(dao, squat, bench, dead, ohp)
        seedMadcow(dao, squat, bench, dead, row)
        seedGZCL(dao, squat, bench, dead, ohp)
        seedNSuns(dao, squat, bench, dead, ohp)
        seedJuggernaut(dao, squat, bench, dead, ohp)
    }

    // =====================================================================
    // 5/3/1 — Wendler
    // 4-week cycle: Week 1 (5s), Week 2 (3s), Week 3 (5/3/1), Week 4 (deload)
    // 4 days: OHP, Dead, Bench, Squat
    // =====================================================================
    private fun seed531(dao: PrayerBookDao, sq: Long, bn: Long, dl: Long, ohp: Long) {
        val prog = dao.insertProgram(Program(
            name = "5/3/1", description = "Wendler's 5/3/1. 4-week cycle, percentage-based.",
            periodizationType = "block", cycleWeeks = 4, daysPerWeek = 4,
            progressionRule = "recalc_tm_per_cycle", isBuiltIn = true
        ))

        data class W531Set(val pct: String, val reps: Int, val amrap: Boolean = false)

        val weeks = listOf(
            listOf(W531Set("TM*0.65", 5), W531Set("TM*0.75", 5), W531Set("TM*0.85", 5, true)),       // Week 1: 5s
            listOf(W531Set("TM*0.70", 3), W531Set("TM*0.80", 3), W531Set("TM*0.90", 3, true)),       // Week 2: 3s
            listOf(W531Set("TM*0.75", 5), W531Set("TM*0.85", 3), W531Set("TM*0.95", 1, true)),       // Week 3: 5/3/1
            listOf(W531Set("TM*0.40", 5), W531Set("TM*0.50", 5), W531Set("TM*0.60", 5))              // Week 4: deload
        )

        val dayLifts = listOf(
            Pair("OHP Day", ohp), Pair("Deadlift Day", dl),
            Pair("Bench Day", bn), Pair("Squat Day", sq)
        )

        for ((weekIdx, weekSets) in weeks.withIndex()) {
            for ((dayIdx, dayInfo) in dayLifts.withIndex()) {
                val dayId = dao.insertProgramDay(ProgramDay(
                    programId = prog, weekIndex = weekIdx, dayIndex = dayIdx, name = dayInfo.first
                ))
                for ((setIdx, s) in weekSets.withIndex()) {
                    dao.insertPlannedSet(PlannedSet(
                        programDayId = dayId, exerciseId = dayInfo.second,
                        setNumber = setIdx + 1, reps = s.reps, weightFormula = s.pct, isAmrap = s.amrap
                    ))
                }
            }
        }
    }

    // =====================================================================
    // Starting Strength — Rippetoe
    // Linear progression. 2 alternating days (A/B). Add 5lbs/session.
    // =====================================================================
    private fun seedStartingStrength(dao: PrayerBookDao, sq: Long, bn: Long, dl: Long, ohp: Long, row: Long) {
        val prog = dao.insertProgram(Program(
            name = "Starting Strength", description = "Linear progression. Add 5 lbs each session.",
            periodizationType = "linear", cycleWeeks = 1, daysPerWeek = 2,
            progressionRule = "add_5_per_session", isBuiltIn = true
        ))

        // Day A: Squat 3×5, Bench 3×5, Deadlift 1×5
        val dayA = dao.insertProgramDay(ProgramDay(programId = prog, weekIndex = 0, dayIndex = 0, name = "Day A"))
        for (s in 1..3) dao.insertPlannedSet(PlannedSet(programDayId = dayA, exerciseId = sq, setNumber = s, reps = 5, weightFormula = "TM*1.0"))
        for (s in 1..3) dao.insertPlannedSet(PlannedSet(programDayId = dayA, exerciseId = bn, setNumber = s, reps = 5, weightFormula = "TM*1.0"))
        dao.insertPlannedSet(PlannedSet(programDayId = dayA, exerciseId = dl, setNumber = 1, reps = 5, weightFormula = "TM*1.0"))

        // Day B: Squat 3×5, OHP 3×5, Deadlift 1×5 (or Rows/Cleans in some variants)
        val dayB = dao.insertProgramDay(ProgramDay(programId = prog, weekIndex = 0, dayIndex = 1, name = "Day B"))
        for (s in 1..3) dao.insertPlannedSet(PlannedSet(programDayId = dayB, exerciseId = sq, setNumber = s, reps = 5, weightFormula = "TM*1.0"))
        for (s in 1..3) dao.insertPlannedSet(PlannedSet(programDayId = dayB, exerciseId = ohp, setNumber = s, reps = 5, weightFormula = "TM*1.0"))
        dao.insertPlannedSet(PlannedSet(programDayId = dayB, exerciseId = dl, setNumber = 1, reps = 5, weightFormula = "TM*1.0"))
    }

    // =====================================================================
    // StrongLifts 5×5 — Mehdi
    // Linear. A/B alternating. 5×5 except deadlift 1×5.
    // =====================================================================
    private fun seedStrongLifts(dao: PrayerBookDao, sq: Long, bn: Long, dl: Long, ohp: Long, row: Long) {
        val prog = dao.insertProgram(Program(
            name = "StrongLifts 5×5", description = "5×5 on main lifts. Add 5 lbs per session.",
            periodizationType = "linear", cycleWeeks = 1, daysPerWeek = 2,
            progressionRule = "add_5_per_session", isBuiltIn = true
        ))

        // Day A: Squat 5×5, Bench 5×5, Row 5×5
        val dayA = dao.insertProgramDay(ProgramDay(programId = prog, weekIndex = 0, dayIndex = 0, name = "Day A"))
        for (s in 1..5) dao.insertPlannedSet(PlannedSet(programDayId = dayA, exerciseId = sq, setNumber = s, reps = 5, weightFormula = "TM*1.0"))
        for (s in 1..5) dao.insertPlannedSet(PlannedSet(programDayId = dayA, exerciseId = bn, setNumber = s, reps = 5, weightFormula = "TM*1.0"))
        for (s in 1..5) dao.insertPlannedSet(PlannedSet(programDayId = dayA, exerciseId = row, setNumber = s, reps = 5, weightFormula = "TM*1.0"))

        // Day B: Squat 5×5, OHP 5×5, Deadlift 1×5
        val dayB = dao.insertProgramDay(ProgramDay(programId = prog, weekIndex = 0, dayIndex = 1, name = "Day B"))
        for (s in 1..5) dao.insertPlannedSet(PlannedSet(programDayId = dayB, exerciseId = sq, setNumber = s, reps = 5, weightFormula = "TM*1.0"))
        for (s in 1..5) dao.insertPlannedSet(PlannedSet(programDayId = dayB, exerciseId = ohp, setNumber = s, reps = 5, weightFormula = "TM*1.0"))
        dao.insertPlannedSet(PlannedSet(programDayId = dayB, exerciseId = dl, setNumber = 1, reps = 5, weightFormula = "TM*1.0"))
    }

    // =====================================================================
    // Texas Method
    // Weekly undulating: Volume Monday, Recovery Wednesday, Intensity Friday
    // =====================================================================
    private fun seedTexasMethod(dao: PrayerBookDao, sq: Long, bn: Long, dl: Long, ohp: Long) {
        val prog = dao.insertProgram(Program(
            name = "Texas Method", description = "Weekly undulating: volume → recovery → intensity.",
            periodizationType = "undulating", cycleWeeks = 1, daysPerWeek = 3,
            progressionRule = "add_5_per_cycle", isBuiltIn = true
        ))

        // Monday: Volume — 5×5 @ 90% of 5RM (~TM*0.81)
        val mon = dao.insertProgramDay(ProgramDay(programId = prog, weekIndex = 0, dayIndex = 0, name = "Volume (Mon)"))
        for (s in 1..5) dao.insertPlannedSet(PlannedSet(programDayId = mon, exerciseId = sq, setNumber = s, reps = 5, weightFormula = "TM*0.81"))
        for (s in 1..5) dao.insertPlannedSet(PlannedSet(programDayId = mon, exerciseId = bn, setNumber = s, reps = 5, weightFormula = "TM*0.81"))
        dao.insertPlannedSet(PlannedSet(programDayId = mon, exerciseId = dl, setNumber = 1, reps = 5, weightFormula = "TM*0.81"))

        // Wednesday: Recovery — 2×5 @ 80% of Monday's weight
        val wed = dao.insertProgramDay(ProgramDay(programId = prog, weekIndex = 0, dayIndex = 1, name = "Recovery (Wed)"))
        for (s in 1..2) dao.insertPlannedSet(PlannedSet(programDayId = wed, exerciseId = sq, setNumber = s, reps = 5, weightFormula = "TM*0.65"))
        for (s in 1..2) dao.insertPlannedSet(PlannedSet(programDayId = wed, exerciseId = ohp, setNumber = s + 2, reps = 5, weightFormula = "TM*0.65"))

        // Friday: Intensity — work to 1×5 PR or 1RM attempt
        val fri = dao.insertProgramDay(ProgramDay(programId = prog, weekIndex = 0, dayIndex = 2, name = "Intensity (Fri)"))
        dao.insertPlannedSet(PlannedSet(programDayId = fri, exerciseId = sq, setNumber = 1, reps = 5, weightFormula = "TM*1.0", isAmrap = false))
        dao.insertPlannedSet(PlannedSet(programDayId = fri, exerciseId = bn, setNumber = 1, reps = 5, weightFormula = "TM*1.0", isAmrap = false))
        dao.insertPlannedSet(PlannedSet(programDayId = fri, exerciseId = dl, setNumber = 1, reps = 5, weightFormula = "TM*1.0", isAmrap = true))
    }

    // =====================================================================
    // Madcow 5×5
    // Weekly linear with ramping sets. 4 weeks displayed.
    // =====================================================================
    private fun seedMadcow(dao: PrayerBookDao, sq: Long, bn: Long, dl: Long, row: Long) {
        val prog = dao.insertProgram(Program(
            name = "Madcow 5×5", description = "Ramping 5×5. Weekly linear progression.",
            periodizationType = "linear", cycleWeeks = 1, daysPerWeek = 3,
            progressionRule = "add_5_per_cycle", isBuiltIn = true
        ))

        // Monday: Ramping 5×5 — Squat, Bench, Row
        val mon = dao.insertProgramDay(ProgramDay(programId = prog, weekIndex = 0, dayIndex = 0, name = "Heavy (Mon)"))
        val rampPcts = listOf("TM*0.60", "TM*0.70", "TM*0.80", "TM*0.90", "TM*1.0")
        for ((s, pct) in rampPcts.withIndex()) {
            dao.insertPlannedSet(PlannedSet(programDayId = mon, exerciseId = sq, setNumber = s + 1, reps = 5, weightFormula = pct))
            dao.insertPlannedSet(PlannedSet(programDayId = mon, exerciseId = bn, setNumber = s + 1, reps = 5, weightFormula = pct))
            dao.insertPlannedSet(PlannedSet(programDayId = mon, exerciseId = row, setNumber = s + 1, reps = 5, weightFormula = pct))
        }

        // Wednesday: Light — Squat 2×5 @ 80%, OHP ramp, Deadlift ramp
        val wed = dao.insertProgramDay(ProgramDay(programId = prog, weekIndex = 0, dayIndex = 1, name = "Light (Wed)"))
        for (s in 1..2) dao.insertPlannedSet(PlannedSet(programDayId = wed, exerciseId = sq, setNumber = s, reps = 5, weightFormula = "TM*0.80"))
        for (s in 1..4) dao.insertPlannedSet(PlannedSet(programDayId = wed, exerciseId = dl, setNumber = s, reps = 5,
            weightFormula = listOf("TM*0.60", "TM*0.70", "TM*0.80", "TM*0.90")[s - 1]))

        // Friday: Squat ramp to 1×3, Bench ramp to 1×3, Row ramp to 1×3
        val fri = dao.insertProgramDay(ProgramDay(programId = prog, weekIndex = 0, dayIndex = 2, name = "PR Day (Fri)"))
        val ramp3 = listOf("TM*0.60", "TM*0.70", "TM*0.80", "TM*0.90")
        for ((s, pct) in ramp3.withIndex()) {
            dao.insertPlannedSet(PlannedSet(programDayId = fri, exerciseId = sq, setNumber = s + 1, reps = 5, weightFormula = pct))
            dao.insertPlannedSet(PlannedSet(programDayId = fri, exerciseId = bn, setNumber = s + 1, reps = 5, weightFormula = pct))
            dao.insertPlannedSet(PlannedSet(programDayId = fri, exerciseId = row, setNumber = s + 1, reps = 5, weightFormula = pct))
        }
        // Top set at TM for 3 reps
        dao.insertPlannedSet(PlannedSet(programDayId = fri, exerciseId = sq, setNumber = 5, reps = 3, weightFormula = "TM*1.0"))
        dao.insertPlannedSet(PlannedSet(programDayId = fri, exerciseId = bn, setNumber = 5, reps = 3, weightFormula = "TM*1.0"))
        dao.insertPlannedSet(PlannedSet(programDayId = fri, exerciseId = row, setNumber = 5, reps = 3, weightFormula = "TM*1.0"))
    }

    // =====================================================================
    // GZCL Method — Simplified (The Rippler style)
    // T1: Heavy compound 5×3+, T2: Medium 3×10, T3: Light 3×15
    // =====================================================================
    private fun seedGZCL(dao: PrayerBookDao, sq: Long, bn: Long, dl: Long, ohp: Long) {
        val prog = dao.insertProgram(Program(
            name = "GZCL", description = "Tiered system: T1 heavy, T2 medium, T3 light.",
            periodizationType = "block", cycleWeeks = 3, daysPerWeek = 4,
            progressionRule = "gzcl_progression", isBuiltIn = true
        ))

        data class Tier(val formula: String, val reps: Int, val sets: Int, val amrap: Boolean = false)

        // Week 1: T1 5×3+ @ 85%, Week 2: 6×2+ @ 90%, Week 3: 10×1+ @ 95%
        val t1Weeks = listOf(
            Tier("TM*0.85", 3, 5, true),
            Tier("TM*0.90", 2, 6, true),
            Tier("TM*0.95", 1, 10, true)
        )

        val dayLifts = listOf(
            Pair("Squat Day", sq), Pair("Bench Day", bn),
            Pair("Deadlift Day", dl), Pair("OHP Day", ohp)
        )

        for ((weekIdx, t1) in t1Weeks.withIndex()) {
            for ((dayIdx, dayInfo) in dayLifts.withIndex()) {
                val dayId = dao.insertProgramDay(ProgramDay(
                    programId = prog, weekIndex = weekIdx, dayIndex = dayIdx, name = "${dayInfo.first} — W${weekIdx + 1}"
                ))
                // T1: main lift
                for (s in 1..t1.sets) {
                    dao.insertPlannedSet(PlannedSet(
                        programDayId = dayId, exerciseId = dayInfo.second, setNumber = s,
                        reps = t1.reps, weightFormula = t1.formula, isAmrap = s == t1.sets && t1.amrap
                    ))
                }
                // T2: secondary lift (opposite movement) 3×10 @ 65%
                val t2Lift = when (dayInfo.second) {
                    sq -> dl; dl -> sq; bn -> ohp; else -> bn
                }
                for (s in 1..3) {
                    dao.insertPlannedSet(PlannedSet(
                        programDayId = dayId, exerciseId = t2Lift, setNumber = t1.sets + s,
                        reps = 10, weightFormula = "TM*0.65"
                    ))
                }
            }
        }
    }

    // =====================================================================
    // nSuns LP (5-day variant, simplified to 4-day for sanity)
    // High volume, percentage-based, AMRAP on top sets
    // =====================================================================
    private fun seedNSuns(dao: PrayerBookDao, sq: Long, bn: Long, dl: Long, ohp: Long) {
        val prog = dao.insertProgram(Program(
            name = "nSuns LP", description = "High volume linear progression. AMRAP top sets.",
            periodizationType = "linear", cycleWeeks = 1, daysPerWeek = 4,
            progressionRule = "nsuns_amrap", isBuiltIn = true
        ))

        // Day 1: Bench T1 (9 sets) + OHP T2
        val d1 = dao.insertProgramDay(ProgramDay(programId = prog, weekIndex = 0, dayIndex = 0, name = "Bench / OHP"))
        val benchT1 = listOf(
            "TM*0.65" to 8, "TM*0.75" to 6, "TM*0.85" to 4, "TM*0.85" to 4, "TM*0.85" to 4,
            "TM*0.80" to 5, "TM*0.75" to 6, "TM*0.70" to 7, "TM*0.65" to 8
        )
        for ((s, pair) in benchT1.withIndex()) {
            dao.insertPlannedSet(PlannedSet(programDayId = d1, exerciseId = bn, setNumber = s + 1,
                reps = pair.second, weightFormula = pair.first, isAmrap = s == 2))
        }
        val ohpT2 = listOf("TM*0.50" to 6, "TM*0.60" to 5, "TM*0.70" to 3, "TM*0.70" to 5,
            "TM*0.70" to 7, "TM*0.70" to 4, "TM*0.70" to 6, "TM*0.70" to 8)
        for ((s, pair) in ohpT2.withIndex()) {
            dao.insertPlannedSet(PlannedSet(programDayId = d1, exerciseId = ohp, setNumber = benchT1.size + s + 1,
                reps = pair.second, weightFormula = pair.first))
        }

        // Day 2: Squat T1 (9 sets) + Sumo DL T2
        val d2 = dao.insertProgramDay(ProgramDay(programId = prog, weekIndex = 0, dayIndex = 1, name = "Squat / Deadlift"))
        val sqT1 = listOf(
            "TM*0.65" to 5, "TM*0.75" to 3, "TM*0.85" to 1, "TM*0.85" to 3, "TM*0.85" to 3,
            "TM*0.80" to 3, "TM*0.75" to 5, "TM*0.70" to 5, "TM*0.65" to 5
        )
        for ((s, pair) in sqT1.withIndex()) {
            dao.insertPlannedSet(PlannedSet(programDayId = d2, exerciseId = sq, setNumber = s + 1,
                reps = pair.second, weightFormula = pair.first, isAmrap = s == 2))
        }
        for ((s, pair) in listOf("TM*0.50" to 5, "TM*0.60" to 5, "TM*0.70" to 3, "TM*0.70" to 5,
            "TM*0.70" to 7, "TM*0.70" to 4, "TM*0.70" to 6, "TM*0.70" to 8).withIndex()) {
            dao.insertPlannedSet(PlannedSet(programDayId = d2, exerciseId = dl, setNumber = sqT1.size + s + 1,
                reps = pair.second, weightFormula = pair.first))
        }

        // Day 3: OHP T1 + Bench T2
        val d3 = dao.insertProgramDay(ProgramDay(programId = prog, weekIndex = 0, dayIndex = 2, name = "OHP / Bench"))
        for ((s, pair) in listOf(
            "TM*0.65" to 5, "TM*0.75" to 3, "TM*0.85" to 1, "TM*0.85" to 3, "TM*0.85" to 3,
            "TM*0.80" to 3, "TM*0.75" to 5, "TM*0.70" to 5, "TM*0.65" to 5
        ).withIndex()) {
            dao.insertPlannedSet(PlannedSet(programDayId = d3, exerciseId = ohp, setNumber = s + 1,
                reps = pair.second, weightFormula = pair.first, isAmrap = s == 2))
        }
        for ((s, pair) in listOf("TM*0.50" to 6, "TM*0.60" to 5, "TM*0.70" to 3, "TM*0.70" to 5,
            "TM*0.70" to 7, "TM*0.70" to 4, "TM*0.70" to 6, "TM*0.70" to 8).withIndex()) {
            dao.insertPlannedSet(PlannedSet(programDayId = d3, exerciseId = bn, setNumber = 9 + s + 1,
                reps = pair.second, weightFormula = pair.first))
        }

        // Day 4: Deadlift T1 + Squat T2
        val d4 = dao.insertProgramDay(ProgramDay(programId = prog, weekIndex = 0, dayIndex = 3, name = "Deadlift / Squat"))
        for ((s, pair) in listOf(
            "TM*0.65" to 5, "TM*0.75" to 3, "TM*0.85" to 1, "TM*0.85" to 3, "TM*0.85" to 3,
            "TM*0.80" to 3, "TM*0.75" to 3, "TM*0.70" to 3, "TM*0.65" to 3
        ).withIndex()) {
            dao.insertPlannedSet(PlannedSet(programDayId = d4, exerciseId = dl, setNumber = s + 1,
                reps = pair.second, weightFormula = pair.first, isAmrap = s == 2))
        }
        for ((s, pair) in listOf("TM*0.50" to 5, "TM*0.60" to 5, "TM*0.70" to 3, "TM*0.70" to 5,
            "TM*0.70" to 7, "TM*0.70" to 4, "TM*0.70" to 6, "TM*0.70" to 8).withIndex()) {
            dao.insertPlannedSet(PlannedSet(programDayId = d4, exerciseId = sq, setNumber = 9 + s + 1,
                reps = pair.second, weightFormula = pair.first))
        }
    }

    // =====================================================================
    // Juggernaut Method
    // 16-week block periodization: 10s wave, 8s wave, 5s wave, 3s wave
    // Each wave: 4 weeks (accumulation, intensification, realization, deload)
    // =====================================================================
    private fun seedJuggernaut(dao: PrayerBookDao, sq: Long, bn: Long, dl: Long, ohp: Long) {
        val prog = dao.insertProgram(Program(
            name = "Juggernaut Method", description = "16-week block periodization. 10s → 8s → 5s → 3s waves.",
            periodizationType = "block", cycleWeeks = 16, daysPerWeek = 4,
            progressionRule = "recalc_tm_per_cycle", isBuiltIn = true
        ))

        val dayLifts = listOf(Pair("Squat", sq), Pair("Bench", bn), Pair("Deadlift", dl), Pair("OHP", ohp))

        // 4 waves × 4 weeks
        data class WaveWeek(val pct: String, val reps: Int, val sets: Int, val amrap: Boolean = false)
        val waves = listOf(
            // 10s wave
            listOf(WaveWeek("TM*0.60", 10, 4), WaveWeek("TM*0.67", 10, 4), WaveWeek("TM*0.75", 10, 4, true), WaveWeek("TM*0.40", 5, 3)),
            // 8s wave
            listOf(WaveWeek("TM*0.65", 8, 4), WaveWeek("TM*0.72", 8, 4), WaveWeek("TM*0.80", 8, 4, true), WaveWeek("TM*0.40", 5, 3)),
            // 5s wave
            listOf(WaveWeek("TM*0.70", 5, 4), WaveWeek("TM*0.77", 5, 4), WaveWeek("TM*0.85", 5, 4, true), WaveWeek("TM*0.40", 5, 3)),
            // 3s wave
            listOf(WaveWeek("TM*0.75", 3, 3), WaveWeek("TM*0.82", 3, 3), WaveWeek("TM*0.90", 3, 3, true), WaveWeek("TM*0.40", 5, 3))
        )

        var globalWeek = 0
        for (wave in waves) {
            for (ww in wave) {
                for ((dayIdx, dayInfo) in dayLifts.withIndex()) {
                    val dayId = dao.insertProgramDay(ProgramDay(
                        programId = prog, weekIndex = globalWeek, dayIndex = dayIdx, name = dayInfo.first
                    ))
                    for (s in 1..ww.sets) {
                        dao.insertPlannedSet(PlannedSet(
                            programDayId = dayId, exerciseId = dayInfo.second, setNumber = s,
                            reps = ww.reps, weightFormula = ww.pct, isAmrap = s == ww.sets && ww.amrap
                        ))
                    }
                }
                globalWeek++
            }
        }
    }

    // =====================================================================
    // High Frequency Strength — 6-week block
    // Bench / Squat / Trap-Bar, 5 days a week. Weeks 1–3 build, week 4 deload,
    // week 5 peak, week 6 realization (optional 1RM tests).
    // Percentages are of true 1RM, not training max. Heavy days ramp singles
    // to an autoregulated top single, so only the opening single has a weight.
    // Safe to call on an existing database: does nothing if already present.
    // =====================================================================
    fun seedHighFrequencyStrength(dao: PrayerBookDao) {
        val name = "High Frequency Strength"
        if (dao.allPrograms().any { it.name == name }) return

        val known = dao.allExercises().associateBy({ it.name }, { it.id }).toMutableMap()
        fun ex(n: String, category: String, isMain: Boolean = false) = known.getOrPut(n) {
            dao.insertExercise(Exercise(name = n, category = category, isMainLift = isMain))
        }
        val bench = ex("Bench Press", "Chest", true)
        val squat = ex("Squat", "Legs", true)
        val trap = ex("Trap Bar Deadlift", "Back", true)
        val kbPress = ex("KB Strict Press (1-arm)", "Shoulders")
        val kbRow = ex("KB Row (1-arm)", "Back")
        val kbCurl = ex("KB Curl", "Biceps")
        val kbHighPull = ex("KB High Pull", "Shoulders")
        val kbTriExt = ex("KB Overhead Triceps Extension", "Triceps")
        val kbRearDelt = ex("KB Rear-Delt Row", "Shoulders")
        val kbSplitSquat = ex("Goblet Split Squat", "Legs")
        val kbSwing = ex("KB Swing", "Legs")

        val prog = dao.insertProgram(Program(
            name = name,
            description = "6-week high-frequency block: bench, squat, trap-bar. Autoregulated top singles, % of 1RM backoffs.",
            periodizationType = "block", cycleWeeks = 6, daysPerWeek = 5,
            progressionRule = "retest_1rm_week_6", isBuiltIn = true
        ))

        fun pct(p: Double) = "1RM*${p / 100}"

        class Day(val id: Long) {
            private var n = 0
            fun sets(exercise: Long, count: Int, reps: Int, formula: String = "",
                     repsText: String = "", note: String = "", rest: Int = 120) {
                repeat(count) {
                    dao.insertPlannedSet(PlannedSet(
                        programDayId = id, exerciseId = exercise, setNumber = ++n, reps = reps,
                        weightFormula = formula, repsText = repsText, note = note, restSeconds = rest
                    ))
                }
            }
            /** Ramp of singles: opening single at a set %, small jumps, stop at the target RPE. */
            fun ramp(exercise: Long, startPct: Double, ramps: Int, top: String) {
                sets(exercise, 1, 1, pct(startPct), note = "start")
                sets(exercise, ramps, 1, note = "ramp")
                sets(exercise, 1, 1, note = top)
            }
            /** Realization day: ramp to an RPE 8 single, then test only if it moved fast. */
            fun test(exercise: Long) {
                sets(exercise, 2, 1, note = "ramp", rest = 180)
                sets(exercise, 1, 1, note = "RPE 8", rest = 180)
                sets(exercise, 1, 1, note = "max test, optional", rest = 180)
            }
        }

        fun day(week: Int, dayIdx: Int, label: String, build: Day.() -> Unit) {
            val day = Day(dao.insertProgramDay(ProgramDay(
                programId = prog, weekIndex = week, dayIndex = dayIdx, name = label
            )))
            day.build()
            // Anytime kettlebell work, 35 lb, spread through the day, 1:00 rest
            when (dayIdx) {
                0 -> day.sets(kbPress, 3, 8, "35", "8–12/side", rest = 60)
                1 -> { day.sets(kbRow, 3, 10, "35", "10–15/side", rest = 60); day.sets(kbCurl, 2, 10, "35", "10–15/side", rest = 60) }
                2 -> { day.sets(kbHighPull, 3, 10, "35", "10–15/side", rest = 60); day.sets(kbTriExt, 2, 10, "35", "10–15", rest = 60) }
                3 -> day.sets(kbRearDelt, 3, 12, "35", "12–20/side", rest = 60)
                4 -> { day.sets(kbSplitSquat, 2, 10, "35", "10–15/leg", rest = 60); day.sets(kbSwing, 2, 15, "35", "15–25", "optional", rest = 60) }
            }
        }

        // Weeks 1–5, % of 1RM per week
        val heavy     = listOf(77.5, 80.0, 82.5, 72.5, 82.5) // Mon bench 3×3, Tue squat 3×3
        val benchVol4 = listOf(70.0, 72.5, 72.5, 65.0, 70.0) // Tue bench 4×4
        val trapHeavy = listOf(72.5, 75.0, 77.5, 67.5, 77.5) // Wed trap-bar 3×3, Fri bench 3×3
        val benchLt   = listOf(60.0, 62.5, 62.5, 55.0, 60.0) // Wed bench 3×5
        val benchVol3 = listOf(75.0, 77.5, 80.0, 70.0, 80.0) // Thu bench 4×3
        val squatVol  = listOf(70.0, 72.5, 72.5, 65.0, 70.0) // Thu squat 3×4
        val trapVol   = listOf(67.5, 70.0, 70.0, 62.5, 70.0) // Fri trap-bar 3×4

        for (w in 0..4) {
            day(w, 0, "Mon — Heavy bench + light squat") {
                ramp(bench, 77.5, 3, "top, RPE 7–8")
                sets(bench, 3, 3, pct(heavy[w]))
                sets(squat, 3, 5, pct(60.0), note = "to 65%")
            }
            day(w, 1, "Tue — Heavy squat + bench volume") {
                ramp(squat, 77.5, 3, "top, RPE 7–8")
                sets(squat, 3, 3, pct(heavy[w]))
                sets(bench, 4, 4, pct(benchVol4[w]))
            }
            day(w, 2, "Wed — Heavy trap-bar + light bench") {
                ramp(trap, 77.5, 3, "top, RPE 7–8")
                sets(trap, 3, 3, pct(trapHeavy[w]))
                sets(bench, 3, 5, pct(benchLt[w]))
            }
            day(w, 3, "Thu — Bench volume + squat volume") {
                sets(bench, 4, 3, pct(benchVol3[w]))
                sets(squat, 3, 4, pct(squatVol[w]))
            }
            day(w, 4, "Fri — Trap-bar volume + bench specificity") {
                sets(trap, 3, 4, pct(trapVol[w]))
                ramp(bench, 75.0, 2, "top, RPE 7")
                sets(bench, 3, 3, pct(trapHeavy[w]))
            }
        }

        // Week 6: realization
        day(5, 0, "Mon — Bench realization") {
            test(bench)
            sets(squat, 2, 5, pct(60.0))
        }
        day(5, 1, "Tue — Recovery technique") {
            sets(bench, 3, 5, pct(60.0), note = "to 62.5%")
            sets(squat, 2, 3, pct(65.0))
        }
        day(5, 2, "Wed — Squat realization") {
            test(squat)
            sets(bench, 3, 5, pct(55.0))
        }
        day(5, 3, "Thu — Primer") {
            sets(bench, 3, 3, pct(60.0))
            sets(trap, 2, 3, pct(60.0))
        }
        day(5, 4, "Fri — Trap-bar realization") {
            test(trap)
            sets(bench, 2, 5, pct(55.0), note = "to 60%")
        }
    }
}
