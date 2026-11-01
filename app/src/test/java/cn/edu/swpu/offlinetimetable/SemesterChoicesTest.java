package cn.edu.swpu.offlinetimetable;

import org.junit.Test;
import java.time.LocalDate;
import static org.junit.Assert.assertEquals;

public final class SemesterChoicesTest {
    @Test public void autumnPutsCurrentTermFirstAndNearbyTermsNext() {
        String[] choices = SemesterChoices.forDate(LocalDate.of(2026, 10, 23));
        assertEquals("2026年秋季学期", choices[0]);
        assertEquals("2027年春季学期", choices[1]);
        assertEquals("2026年春季学期", choices[2]);
        assertEquals("2025年秋季学期", choices[4]);
    }

    @Test public void januaryStillBelongsToPreviousAutumn() {
        assertEquals("2025年秋季学期",
                SemesterChoices.forDate(LocalDate.of(2026, 1, 15))[0]);
        assertEquals("2026年春季学期",
                SemesterChoices.forDate(LocalDate.of(2026, 2, 15))[0]);
        assertEquals("2026年秋季学期",
                SemesterChoices.forDate(LocalDate.of(2026, 8, 15))[0]);
    }
}
