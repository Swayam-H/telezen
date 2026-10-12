package com.telezen.app.core

import com.telezen.app.data.db.DaySummary
import com.telezen.app.data.db.goalMet
import java.time.LocalDate

data class StreakInfo(val current: Int, val best: Int)

object Streaks {
    /**
     * Current streak counts consecutive goal-met days ending yesterday, plus today if today is
     * already under the goal. Days without a summary break the streak.
     */
    fun compute(summaries: List<DaySummary>, today: LocalDate): StreakInfo {
        val byDay = summaries.associateBy { LocalDate.parse(it.day) }

        var current = 0
        var d = today.minusDays(1)
        while (byDay[d]?.goalMet() == true) {
            current++
            d = d.minusDays(1)
        }
        if (byDay[today]?.goalMet() == true) current++

        var best = 0
        var run = 0
        var prev: LocalDate? = null
        for (day in byDay.keys.sorted()) {
            val met = byDay.getValue(day).goalMet()
            run = if (met) (if (prev != null && prev.plusDays(1) == day) run + 1 else 1) else 0
            prev = if (met) day else null
            best = maxOf(best, run)
        }
        return StreakInfo(current, maxOf(best, current))
    }
}
