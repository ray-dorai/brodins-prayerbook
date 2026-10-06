package com.brodinsprayerbook.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.brodinsprayerbook.context
import com.brodinsprayerbook.memoryDb
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ProgramPorterTest {
    private lateinit var db: PrayerBookDb
    private lateinit var dao: PrayerBookDao

    private val csv = ProgramCsv.HEADER + "\n" +
        "1,1,\"Mon, heavy\",Bench Press,3,3,77.5% 1RM,120,\n" +
        "1,1,\"Mon, heavy\",KB Row,2,10-15/side,35,60,optional\n" +
        "1,2,Tue,Squat,1,5+,85% TM,,\n" +
        "2,1,\"Mon, heavy\",Bench Press,3,3,80% 1RM,120,\n"

    @Before fun open() {
        db = memoryDb()
        dao = db.dao()
    }

    @After fun close() = db.close()

    private fun program(name: String) = dao.allPrograms().single { it.name == name }

    @Test fun csvBecomesAProgramWithNumberedSets() {
        val result = ProgramPorter.importCsv(dao, "My Block", ProgramCsv.parse(csv))

        assertTrue(result.message, result.success)
        assertEquals("Imported 'My Block': 3 days, 9 sets", result.message)
        val program = program("My Block")
        assertEquals(2, program.cycleWeeks)
        assertEquals(2, program.daysPerWeek)
        assertFalse(program.isBuiltIn)

        val days = dao.daysForProgram(program.id)
        assertEquals(listOf(0 to 0, 0 to 1, 1 to 0), days.map { it.weekIndex to it.dayIndex })
        assertEquals("Mon, heavy", days[0].name)

        val monday = dao.plannedSetsForDay(days[0].id)
        assertEquals(listOf(1, 2, 3, 4, 5), monday.map { it.setNumber })
        assertEquals(listOf("Bench Press", "Bench Press", "Bench Press", "KB Row", "KB Row"), monday.map { it.exerciseName })
        assertEquals("1RM*0.775", monday[0].weightFormula)
        assertEquals(listOf(120, 120, 120, 60, 60), monday.map { it.restSeconds })
        assertEquals("10-15/side", monday[3].repsText)
        assertEquals("optional", monday[3].note)
        assertTrue(dao.plannedSetsForDay(days[1].id).single().isAmrap)
    }

    @Test fun liftsPrescribedAsAPercentageAreOfferedAMax() {
        // Known already, but not as a main lift
        dao.insertExercise(Exercise(name = "Squat"))
        ProgramPorter.importCsv(dao, "My Block", ProgramCsv.parse(csv))

        assertEquals(setOf("Bench Press", "Squat"), dao.mainLifts().map { it.name }.toSet())
        assertEquals(3, dao.allExercises().size)
    }

    @Test fun exercisesAreMatchedByNameWhateverTheCase() {
        val known = dao.insertExercise(Exercise(name = "Bench Press", isMainLift = true))
        ProgramPorter.importCsv(dao, "P", ProgramCsv.parse("exercise,reps\nbench press,5\n"))

        assertEquals(1, dao.allExercises().size)
        assertEquals(known, dao.plannedSetsForDay(dao.daysForProgram(program("P").id).single().id).single().exerciseId)
    }

    @Test fun importingUnderAnExistingNameReplacesTheProgram() {
        ProgramPorter.importCsv(dao, "My Block", ProgramCsv.parse(csv))
        ProgramPorter.importCsv(dao, "My Block", ProgramCsv.parse("exercise,reps\nPull-Up,8\n"))

        val days = dao.daysForProgram(program("My Block").id)
        assertEquals("Day 1", days.single().name)
        assertEquals("Pull-Up", dao.plannedSetsForDay(days.single().id).single().exerciseName)
    }

    @Test fun anEmptyFileImportsNothing() {
        val result = ProgramPorter.importCsv(dao, "Nothing", emptyList())
        assertFalse(result.success)
        assertTrue(dao.allPrograms().isEmpty())
    }

    @Test fun anExportedProgramImportsBackUnchanged() {
        ProgramPorter.importCsv(dao, "My Block", ProgramCsv.parse(csv))

        val file = ProgramPorter.exportCsv(context, dao, program("My Block").id)!!

        assertEquals("my_block.csv", file.name)
        assertEquals(csv, file.readText())
    }

    @Test fun exportingAProgramThatIsGoneGivesNoFile() {
        assertEquals(null, ProgramPorter.exportCsv(context, dao, 99))
    }

    /** programs/high_frequency_strength.csv is the shareable copy of the built-in; they must not drift apart. */
    @Test fun theBuiltInHighFrequencyProgramMatchesItsSpreadsheet() {
        ProgramSeeder.seedHighFrequencyStrength(dao)

        val exported = ProgramPorter.exportCsv(context, dao, program("High Frequency Strength").id)!!

        assertEquals(File("../programs/high_frequency_strength.csv").readText(), exported.readText())
    }

    @Test fun theSpreadsheetImportsToTheSameSetsAsTheBuiltIn() {
        ProgramSeeder.seedHighFrequencyStrength(dao)
        ProgramPorter.importCsv(dao, "Copy", ProgramCsv.parse(File("../programs/high_frequency_strength.csv").readText()))

        fun sets(name: String) = dao.daysForProgram(program(name).id).flatMap { day ->
            dao.plannedSetsForDay(day.id).map { listOf(day.weekIndex, day.dayIndex, day.name) + it.copy(id = 0, programDayId = 0) }
        }
        assertEquals(404, sets("Copy").size)
        assertEquals(sets("High Frequency Strength"), sets("Copy"))
    }

    @Test fun olderJsonExportsStillImport() {
        val json = """{"name":"Old","cycleWeeks":1,"daysPerWeek":1,"days":[{"weekIndex":0,"dayIndex":0,"name":"OHP Day",
            "sets":[{"exercise":"Overhead Press","set":1,"reps":5,"formula":"TM*0.65","amrap":false},
                    {"exercise":"Overhead Press","set":2,"reps":5,"formula":"TM*0.85","amrap":true,"note":"all out"}]}]}"""

        assertTrue(ProgramPorter.importFromJson(dao, json).success)
        val sets = dao.plannedSetsForDay(dao.daysForProgram(program("Old").id).single().id)
        assertEquals(listOf("TM*0.65", "TM*0.85"), sets.map { it.weightFormula })
        assertTrue(sets[1].isAmrap)
        assertEquals("all out", sets[1].note)

        val again = ProgramPorter.importFromJson(dao, json)
        assertFalse(again.success)
        assertEquals("Program 'Old' already exists", again.message)
    }
}
