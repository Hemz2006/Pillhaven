package com.medtrack.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [MedicationEntity::class, DoseLogEntity::class], version = 5, exportSchema = true)
@TypeConverters(Converters::class)
abstract class MedTrackDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun doseLogDao(): DoseLogDao
}
