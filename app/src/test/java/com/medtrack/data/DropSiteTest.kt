package com.medtrack.data

import com.medtrack.schedule.DoseSide
import com.medtrack.schedule.DropSite
import com.medtrack.schedule.Schedule
import com.medtrack.schedule.ScheduleRule
import com.medtrack.schedule.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class DropSiteTest {
    private val start = LocalDate.parse("2026-10-18")
    private val left = ScheduleRule.DailyTimes(listOf(LocalTime.of(8, 0)), side = Side.LEFT)
    private val noSide = ScheduleRule.DailyTimes(listOf(LocalTime.of(8, 0)))
    private fun med(form: MedForm?, site: DropSite?, rule: ScheduleRule = left) =
        MedicationEntity(genericName = "M", form = form, dropSite = site, steps = listOf(Schedule(rule, start)), createdAt = Instant.EPOCH)

    @Test fun sideOfIgnoresSideUnlessSidedDrops() {
        assertEquals(DoseSide(DropSite.EYE, Side.LEFT), med(MedForm.DROPS, DropSite.EYE).sideOf(left))
        assertNull(med(MedForm.TABLET, DropSite.EYE).sideOf(left))
        assertNull(med(MedForm.DROPS, DropSite.OTHER).sideOf(left))
        assertNull(med(MedForm.DROPS, DropSite.EYE).sideOf(noSide))
        assertNull(med(MedForm.TABLET, DropSite.EYE).sidedSite)
    }

    @Test fun validation() {
        assertEquals("Choose what the drops are for", med(MedForm.DROPS, null).sideValidationError())
        assertNull(med(MedForm.TABLET, null).sideValidationError())
        assertEquals("Choose which eye", med(MedForm.DROPS, DropSite.EYE, noSide).sideValidationError())
        assertNull(med(MedForm.DROPS, DropSite.EYE).sideValidationError())
        assertNull(med(MedForm.DROPS, DropSite.OTHER, noSide).sideValidationError())
    }
}
