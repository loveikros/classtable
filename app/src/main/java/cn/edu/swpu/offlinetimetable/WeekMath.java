package cn.edu.swpu.offlinetimetable;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

/** A 周次锚点 applies to its entire Monday-to-Sunday week. */
public final class WeekMath {
    private WeekMath() { }

    public static int weekAt(LocalDate anchorDate, int anchorWeek, LocalDate date) {
        LocalDate anchorMonday = anchorDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate dateMonday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return anchorWeek + Math.toIntExact(ChronoUnit.WEEKS.between(anchorMonday, dateMonday));
    }
}
