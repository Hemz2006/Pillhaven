package com.medtrack.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.medtrack.schedule.DoseSide
import com.medtrack.schedule.DropSite
import com.medtrack.schedule.MealType
import com.medtrack.schedule.Side
import com.medtrack.schedule.ScheduleRule
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** A database written by v1 of the app opens in the current version with every medication, photo and dose log intact. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "migration-test.db"

    @Before fun createV1() {
        context.deleteDatabase(name)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile?.mkdirs() }, null).use { db ->
            db.execSQL(
                "CREATE TABLE `medications` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `genericName` TEXT NOT NULL, `brandName` TEXT, " +
                    "`doseText` TEXT, `notes` TEXT, `ingredients` TEXT NOT NULL, `usedFor` TEXT, `rxcui` TEXT, `rxnormStatus` TEXT NOT NULL, " +
                    "`pubchemStatus` TEXT NOT NULL, `medlineStatus` TEXT NOT NULL, `photoPath` TEXT, `active` INTEGER NOT NULL, " +
                    "`schedule` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE TABLE `dose_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `medicationId` INTEGER NOT NULL, `dueAt` INTEGER NOT NULL, " +
                    "`status` TEXT NOT NULL, `trigger` TEXT NOT NULL, `forDate` TEXT NOT NULL, `actedAt` INTEGER, `snoozedUntil` INTEGER, " +
                    "FOREIGN KEY(`medicationId`) REFERENCES `medications`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL("CREATE UNIQUE INDEX `index_dose_logs_medicationId_dueAt` ON `dose_logs` (`medicationId`, `dueAt`)")
            db.execSQL(
                "INSERT INTO medications VALUES (1, 'pantoprazole', 'Pan 40', '40 mg', 'before food', '[]', NULL, NULL, 'OK', 'OK', 'OK', " +
                    "'/data/user/0/com.medtrack/files/photos/a.jpg', 1, " +
                    "'{\"rule\":{\"type\":\"meal\",\"meal\":\"BREAKFAST\",\"delayMinutes\":30,\"fallbackTime\":\"10:00\"},\"startDate\":\"2026-10-13\"}', 1000)",
            )
            db.execSQL(
                "INSERT INTO medications VALUES (2, 'metformin', NULL, NULL, NULL, '[]', NULL, NULL, 'NOT_RUN', 'NOT_RUN', 'NOT_RUN', NULL, 1, " +
                    "'{\"rule\":{\"type\":\"daily\",\"times\":[\"08:00\",\"21:00\"]},\"startDate\":\"2026-10-13\",\"endDate\":\"2026-10-20\"}', 2000)",
            )
            db.execSQL("INSERT INTO dose_logs VALUES (1, 1, 5000, 'TAKEN', 'MEAL', '2026-10-13', 5100, NULL)")
            db.execSQL("INSERT INTO dose_logs VALUES (2, 2, 6000, 'MISSED', 'SCHEDULE', '2026-10-13', NULL, NULL)")
            db.version = 1
        }
    }

    @After fun cleanUp() {
        context.deleteDatabase(name)
    }

    @Test fun keepsMedicationsPhotosAndLogs() = runBlocking {
        val db = Room.databaseBuilder(context, MedTrackDatabase::class.java, name).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).allowMainThreadQueries().build()
        try {
            val pan = db.medicationDao().get(1)!!
            assertEquals("Pan 40", pan.brandName)
            assertEquals(null, pan.form)
            assertEquals(listOf("/data/user/0/com.medtrack/files/photos/a.jpg"), pan.photos)
            assertEquals(listOf(ScheduleRule.AfterMeal(MealType.BREAKFAST, 30, LocalTime.of(10, 0))), pan.steps.single().rules)

            val metformin = db.medicationDao().get(2)!!
            assertEquals(emptyList<String>(), metformin.photos)
            assertEquals(listOf(ScheduleRule.DailyTimes(listOf(LocalTime.of(8, 0), LocalTime.of(21, 0)))), metformin.steps.single().rules)
            assertEquals(LocalDate.parse("2026-10-20"), metformin.steps.single().endDate)

            val logs = db.doseLogDao().since(Instant.EPOCH).sortedBy { it.id }
            assertEquals(listOf(1L, 2L), logs.map { it.medicationId })
            assertEquals(listOf("meal:BREAKFAST", null), logs.map { it.slot })
            assertEquals(DoseStatus.TAKEN, logs.first().status)

            // v5: no medication says what drops are for, and no old dose has a side.
            assertEquals(null, pan.dropSite)
            assertEquals(listOf(null, null), logs.map { it.doseSide })

            // Foreign keys still work after the table was rebuilt.
            db.medicationDao().delete(metformin)
            assertEquals(listOf(1L), db.doseLogDao().since(Instant.EPOCH).map { it.medicationId })

            // New doses keep their side.
            db.doseLogDao().insertIgnore(
                DoseLogEntity(medicationId = 1, dueAt = Instant.ofEpochMilli(9000), status = DoseStatus.PENDING, trigger = DoseTrigger.SCHEDULE,
                    forDate = LocalDate.parse("2026-10-14"), dropSite = DropSite.EYE, side = Side.RIGHT),
            )
            assertEquals(DoseSide(DropSite.EYE, Side.RIGHT), db.doseLogDao().find(1, Instant.ofEpochMilli(9000))!!.doseSide)
        } finally {
            db.close()
        }
    }
}
