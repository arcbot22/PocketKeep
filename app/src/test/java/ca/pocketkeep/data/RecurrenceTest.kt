package ca.pocketkeep.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurrenceTest {
    @Test fun completingOnDueDateAdvancesOneInterval() {
        assertEquals(date("2026-11-27"), nextDueDate(date("2026-11-20"), 7, date("2026-11-20")))
    }

    @Test fun earlyCompletionPreservesScheduleAnchor() {
        assertEquals(date("2026-11-27"), nextDueDate(date("2026-11-20"), 7, date("2026-11-18")))
    }

    @Test fun lateCompletionSkipsMissedOccurrencesWithoutDrifting() {
        assertEquals(date("2026-11-22"), nextDueDate(date("2026-11-01"), 7, date("2026-11-20")))
    }

    @Test fun completionOnLaterScheduledDayProducesFutureOccurrence() {
        assertEquals(date("2026-11-22"), nextDueDate(date("2026-11-01"), 7, date("2026-11-15")))
    }

    @Test fun dailyScheduleCrossesLeapDay() {
        assertEquals(date("2028-02-29"), nextDueDate(date("2028-02-28"), 1, date("2028-02-28")))
        assertEquals(date("2028-03-01"), nextDueDate(date("2028-02-28"), 1, date("2028-02-29")))
    }

    @Test fun dayIntervalCrossesYearBoundary() {
        assertEquals(date("2027-01-02"), nextDueDate(date("2026-12-26"), 7, date("2026-12-26")))
    }

    @Test fun longOverdueSchedulesRemainAnchoredAndFuture() {
        val anchor = date("2000-01-01")
        val today = date("2026-11-20")
        val next = nextDueDate(anchor, 30, today)
        assertTrue(next.isAfter(today))
        assertEquals(0L, java.time.temporal.ChronoUnit.DAYS.between(anchor, next) % 30)
        assertTrue(!next.minusDays(30).isAfter(today))
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroIntervalIsRejected() { nextDueDate(date("2026-11-20"), 0, date("2026-11-20")) }

    @Test(expected = IllegalArgumentException::class)
    fun negativeIntervalIsRejected() { nextDueDate(date("2026-11-20"), -1, date("2026-11-20")) }

    @Test(expected = IllegalArgumentException::class)
    fun excessiveIntervalIsRejected() { nextDueDate(date("2026-11-20"), 36501, date("2026-11-20")) }

    @Test fun maximumSupportedIntervalIsAccepted() {
        val due = date("2026-11-20")
        assertEquals(due.plusDays(36500), nextDueDate(due, 36500, due))
    }

    private fun date(value: String) = LocalDate.parse(value)
}
