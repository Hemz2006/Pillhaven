package com.medtrack

import kotlinx.coroutines.flow.MutableStateFlow
import com.medtrack.data.UiSettings
import android.content.Context
import android.util.Log
import androidx.room.Room
import com.medtrack.data.AppPrefs
import com.medtrack.data.MIGRATION_1_2
import com.medtrack.data.MIGRATION_2_3
import com.medtrack.data.MIGRATION_3_4
import com.medtrack.data.MIGRATION_4_5
import com.medtrack.data.MedTrackDatabase
import com.medtrack.data.MedicationRepository
import com.medtrack.data.PhotoStore
import com.medtrack.health.HealthConnectGateway
import com.medtrack.health.MealCheckScheduler
import com.medtrack.health.MealChecker
import com.medtrack.lookup.BrandIndex
import com.medtrack.lookup.MedicationLookup
import com.medtrack.lookup.urlConnectionFetch
import com.medtrack.reminders.AlarmScheduler
import com.medtrack.reminders.DoseNotifier
import com.medtrack.reminders.ReminderEngine
import com.medtrack.schedule.hasMealRule
import com.medtrack.ui.theme.applyToSystem
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** App-wide singletons. [appScope] is for work that must outlive a screen (dose actions, saves). */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> Log.e("MedTrack", "Unhandled error in appScope", e) })
    val db: MedTrackDatabase = Room.databaseBuilder(context, MedTrackDatabase::class.java, "medtrack.db")
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
        .build()
    val prefs = AppPrefs(context)

    /** Theme and accessibility sizes; every screen (and the alarm) redraws when they change. */
    val uiSettings = MutableStateFlow(prefs.uiSettings)

    fun updateUiSettings(transform: (UiSettings) -> UiSettings) {
        val previous = uiSettings.value
        val next = transform(previous)
        prefs.uiSettings = next
        uiSettings.value = next
        if (next.theme != previous.theme) next.theme.applyToSystem(appContext)
    }
    val photos = PhotoStore(context)
    val lookup = MedicationLookup(urlConnectionFetch)
    val brands = BrandIndex(context)
    val engine = ReminderEngine(db.medicationDao(), db.doseLogDao(), AlarmScheduler(context), DoseNotifier(context), prefs)
    val health = HealthConnectGateway(context)
    val mealChecker = MealChecker(health::mealsBetween, db.medicationDao(), db.doseLogDao(), engine)
    private val mealScheduler = MealCheckScheduler(context)
    val medications = MedicationRepository(db.medicationDao(), photos) { refreshReminders() }

    suspend fun refreshReminders() {
        engine.sync()
        mealScheduler.update(db.medicationDao().active().any { it.steps.hasMealRule })
    }
}
