package com.medtrack.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.medtrack.schedule.ScheduleJson
import com.medtrack.schedule.slot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * v2: a medication can have several schedule rules and several photos, and dose logs record
 * which after-meal rule they belong to. Room runs migrations before it turns foreign keys on,
 * so recreating `medications` does not cascade-delete dose logs.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE dose_logs ADD COLUMN `slot` TEXT")
        db.execSQL(
            "CREATE TABLE `medications_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `genericName` TEXT NOT NULL, " +
                "`brandName` TEXT, `doseText` TEXT, `notes` TEXT, `ingredients` TEXT NOT NULL, `usedFor` TEXT, `rxcui` TEXT, " +
                "`rxnormStatus` TEXT NOT NULL, `pubchemStatus` TEXT NOT NULL, `medlineStatus` TEXT NOT NULL, `photos` TEXT NOT NULL, " +
                "`active` INTEGER NOT NULL, `schedule` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
        )
        val shared = "id, genericName, brandName, doseText, notes, ingredients, usedFor, rxcui, rxnormStatus, pubchemStatus, medlineStatus, active, schedule, createdAt"
        db.execSQL("INSERT INTO medications_new ($shared, photos) SELECT $shared, '[]' FROM medications")
        db.query("SELECT id, schedule, photoPath FROM medications").use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val schedule = migrateScheduleJson(c.getString(1))
                val photo = if (c.isNull(2)) null else c.getString(2)
                db.execSQL("UPDATE medications_new SET schedule = ?, photos = ? WHERE id = ?", arrayOf(schedule, photosJson(photo), id))
                mealSlot(schedule)?.let { slot ->
                    db.execSQL("UPDATE dose_logs SET slot = ? WHERE medicationId = ?", arrayOf(slot, id))
                }
            }
        }
        db.execSQL("DROP TABLE medications")
        db.execSQL("ALTER TABLE medications_new RENAME TO medications")
    }
}

/** v3: a medication's form (tablet, inhaler, nebuliser…). Existing medications start without one. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE medications ADD COLUMN `form` TEXT")
    }
}

/** v4: a medication's schedule becomes a list of steps (a tapering course); existing ones become one step. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE medications SET schedule = '[' || schedule || ']'")
    }
}

/** v5: what drops are for, and each dose's side (left/right/both). Existing rows have neither. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE medications ADD COLUMN `dropSite` TEXT")
        db.execSQL("ALTER TABLE dose_logs ADD COLUMN `dropSite` TEXT")
        db.execSQL("ALTER TABLE dose_logs ADD COLUMN `side` TEXT")
    }
}

private val json = Json { ignoreUnknownKeys = true }

/** v1 stored one rule as `"rule": {...}`; v2 stores `"rules": [{...}]`. */
internal fun migrateScheduleJson(text: String): String {
    val obj = json.parseToJsonElement(text).jsonObject
    val rule = obj["rule"] ?: return text
    return JsonObject(obj - "rule" + ("rules" to JsonArray(listOf(rule)))).toString()
}

private fun photosJson(path: String?): String = JsonArray(listOfNotNull(path).map(::JsonPrimitive)).toString()

/** A v1 after-meal medication had only that rule, so all of its logs belong to its meal slot. */
private fun mealSlot(scheduleJson: String): String? =
    ScheduleJson.decode(scheduleJson).rules.singleOrNull()?.slot
