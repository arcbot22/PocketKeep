package ca.pocketkeep.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Preserve the original day interval; skip missed occurrences rather than duplicating them. */
fun nextDueDate(dueDate: LocalDate, intervalDays: Int, today: LocalDate): LocalDate {
    require(intervalDays in 1..36500) { "Repeat interval must be 1–36500 days" }
    val elapsed = ChronoUnit.DAYS.between(dueDate, today)
    val periods = if (elapsed < 0) 1L else elapsed / intervalDays + 1
    return dueDate.plusDays(periods * intervalDays)
}
