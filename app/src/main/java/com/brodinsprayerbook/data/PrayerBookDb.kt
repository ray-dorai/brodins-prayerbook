package com.brodinsprayerbook.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Exercise::class, UserMax::class, Program::class, ProgramDay::class,
                PlannedSet::class, WorkoutLog::class, ActualSet::class, UserProgramState::class,
                MaxHistory::class],
    version = 5
)
abstract class PrayerBookDb : RoomDatabase() {
    abstract fun dao(): PrayerBookDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE PlannedSet ADD COLUMN repsText TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE PlannedSet ADD COLUMN note TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE WorkoutLog ADD COLUMN cycle INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE WorkoutLog ADD COLUMN completed INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ActualSet ADD COLUMN plannedWeight REAL")
                db.execSQL("ALTER TABLE ActualSet ADD COLUMN plannedReps TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE ActualSet ADD COLUMN rpe REAL")
                db.execSQL("ALTER TABLE WorkoutLog ADD COLUMN programName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE WorkoutLog ADD COLUMN week INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE WorkoutLog ADD COLUMN dayName TEXT NOT NULL DEFAULT ''")
                db.execSQL("""
                    UPDATE WorkoutLog SET
                      programName = COALESCE((SELECT p.name FROM ProgramDay d JOIN Program p ON p.id = d.programId
                                              WHERE d.id = WorkoutLog.programDayId), ''),
                      week = COALESCE((SELECT d.weekIndex + 1 FROM ProgramDay d WHERE d.id = WorkoutLog.programDayId), 0),
                      dayName = COALESCE((SELECT d.name FROM ProgramDay d WHERE d.id = WorkoutLog.programDayId), '')
                """)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS MaxHistory (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, exerciseId INTEGER NOT NULL,
                        date TEXT NOT NULL, oneRepMax REAL NOT NULL, source TEXT NOT NULL)
                """)
                db.execSQL("""
                    INSERT INTO MaxHistory (exerciseId, date, oneRepMax, source)
                    SELECT exerciseId, updatedDate, oneRepMax, 'onboarding' FROM UserMax
                """)
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE PlannedSet ADD COLUMN restSeconds INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE Exercise ADD COLUMN restSeconds INTEGER NOT NULL DEFAULT 0")
                // Give the built-in High Frequency Strength its rests: 2:00 main work, 1:00 kettlebell, 3:00 max tests
                val hfsDays = "SELECT d.id FROM ProgramDay d JOIN Program p ON p.id = d.programId " +
                    "WHERE p.name = 'High Frequency Strength' AND p.isBuiltIn = 1"
                db.execSQL("UPDATE PlannedSet SET restSeconds = 120 WHERE programDayId IN ($hfsDays)")
                db.execSQL("UPDATE PlannedSet SET restSeconds = 60 WHERE programDayId IN ($hfsDays) AND exerciseId IN " +
                    "(SELECT id FROM Exercise WHERE name LIKE 'KB %' OR name = 'Goblet Split Squat')")
                db.execSQL("UPDATE PlannedSet SET restSeconds = 180 WHERE programDayId IN ($hfsDays AND d.weekIndex = 5) " +
                    "AND note IN ('ramp', 'RPE 8', 'max test, optional')")
            }
        }

        private const val DB_NAME = "prayerbook.db"

        /** Copy the whole database to [dest]. Flushes pending writes first. */
        fun backupTo(ctx: Context, dest: java.io.File) {
            get(ctx).openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
            ctx.getDatabasePath(DB_NAME).copyTo(dest, overwrite = true)
        }

        /** Replace the database with a backup file. Returns false if it isn't a SQLite file. */
        fun restoreFrom(ctx: Context, input: java.io.InputStream): Boolean {
            val bytes = input.use { it.readBytes() }
            if (bytes.size < 16 || String(bytes, 0, 15, Charsets.US_ASCII) != "SQLite format 3") return false
            synchronized(this) {
                close()
                val path = ctx.getDatabasePath(DB_NAME)
                java.io.File(path.path + "-wal").delete()
                java.io.File(path.path + "-shm").delete()
                path.writeBytes(bytes)
            }
            return true
        }

        /** Close the open database and forget it; the next [get] opens the file afresh. */
        fun close() = synchronized(this) {
            instance?.close()
            instance = null
        }

        @Volatile private var instance: PrayerBookDb? = null
        fun get(ctx: Context): PrayerBookDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(ctx.applicationContext, PrayerBookDb::class.java, DB_NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).allowMainThreadQueries().build().also { instance = it }
        }
    }
}
