package cn.edu.swpu.offlinetimetable;

import org.junit.Test;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class WeekMathTest {
    @Test public void anchorWeekChangesOnlyOnMondayAndCanBeRecalibrated() {
        LocalDate wednesday = LocalDate.of(2026, 10, 21);
        assertEquals(7, WeekMath.weekAt(wednesday, 7, LocalDate.of(2026, 10, 25)));
        assertEquals(8, WeekMath.weekAt(wednesday, 7, LocalDate.of(2026, 10, 26)));
        assertEquals(6, WeekMath.weekAt(wednesday, 7, LocalDate.of(2026, 10, 18)));
        assertEquals(5, WeekMath.weekAt(LocalDate.of(2026, 10, 26), 5,
                LocalDate.of(2026, 10, 27)));
    }

    @Test public void differentCoursesSharingASlotAreSelectedByWeek() {
        List<Timetable.Course> courses = Arrays.asList(
                new Timetable.Course(1, 1, "课程甲", "", "", "", 1, 1, 2, "1,3", true),
                new Timetable.Course(2, 1, "课程乙", "", "", "", 1, 1, 2, "2,4", true));
        assertEquals("课程甲", Timetable.coursesForWeek(courses, 3).get(0).name);
        assertEquals("课程乙", Timetable.coursesForWeek(courses, 4).get(0).name);
        assertEquals(0, Timetable.coursesForWeek(courses, 5).size());
    }

    @Test public void dateExceptionsChangeOnlyTheirOwnDate() {
        LocalDate monday = LocalDate.of(2026, 10, 19);
        List<Timetable.Course> courses = Arrays.asList(
                new Timetable.Course(1, 1, "数学", "", "", "原教室", 1, 1, 2, "1,2", true),
                new Timetable.Course(2, 1, "物理", "", "", "", 1, 3, 4, "1,2", true));
        List<Timetable.DateException> exceptions = Arrays.asList(
                new Timetable.DateException(1, 1, monday, Timetable.DateException.Kind.CANCEL,
                        1, "数学", "", "", "原教室", 1, 2),
                new Timetable.DateException(2, 1, monday, Timetable.DateException.Kind.CHANGE,
                        2, "物理", "", "", "新教室", 3, 4),
                new Timetable.DateException(3, 1, monday, Timetable.DateException.Kind.EXTRA,
                        0, "补课", "", "", "实验室", 5, 6));
        List<Timetable.DayEntry> today = Timetable.entriesForDate(courses, exceptions, monday, 1);
        assertEquals(3, today.size());
        assertEquals(Timetable.DateException.Kind.CANCEL, today.get(0).exception.kind);
        assertEquals("新教室", today.get(1).location);
        assertEquals("补课", today.get(2).name);
        List<Timetable.DayEntry> nextMonday = Timetable.entriesForDate(courses, exceptions,
                monday.plusWeeks(1), 2);
        assertEquals(2, nextMonday.size());
        assertNull(nextMonday.get(0).exception);
        assertEquals("原教室", nextMonday.get(0).location);
        List<Timetable.Course> movedCourses = Arrays.asList(
                new Timetable.Course(1, 1, "数学", "", "", "原教室", 2, 1, 2, "1,2", true),
                courses.get(1));
        List<Timetable.DayEntry> afterRegularEdit = Timetable.entriesForDate(
                movedCourses, exceptions, monday, 1);
        assertEquals(2, afterRegularEdit.size());
        assertEquals("物理", afterRegularEdit.get(0).name);
        assertEquals("补课", afterRegularEdit.get(1).name);
    }
}
