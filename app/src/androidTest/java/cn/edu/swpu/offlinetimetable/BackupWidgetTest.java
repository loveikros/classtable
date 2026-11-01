package cn.edu.swpu.offlinetimetable;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class BackupWidgetTest {
    private Timetable.Course course(String name, int period) {
        return new Timetable.Course(0, 0, name, "班级", "教师", "教室", 1,
                period, period, "1-16", false);
    }

    @Test public void backupRoundTripAndRejectsBadFiles() throws Exception {
        ApplicationProvider.getApplicationContext().deleteDatabase("timetable.db");
        try (TimetableStore store = new TimetableStore(ApplicationProvider.getApplicationContext())) {
            long first = store.importSemester("秋季", Collections.singletonList(course("甲", 1)));
            store.setAnchor(first, LocalDate.of(2026, 10, 19), 8);
            long second = store.importSemester("春季", Collections.singletonList(course("乙", 2)));
            store.saveCourse(new Timetable.Course(0, second, "手动", "", "", "", 2, 3, 4, "1", true));
            store.saveDateException(new Timetable.DateException(0, first, LocalDate.of(2026, 10, 19),
                    Timetable.DateException.Kind.CANCEL, store.courses(first).get(0).id,
                    "", "", "", "", 0, 0));
            String backup = LocalBackup.export(store);
            assertFalse(backup.contains("password"));
            assertFalse(backup.contains("credential"));
            assertThrows(IllegalArgumentException.class, () -> LocalBackup.inspect("not json"));
            assertThrows(IllegalArgumentException.class, () -> LocalBackup.inspect(backup.replace("\"version\":1", "\"version\":0")));
            LocalBackup.Preview preview = LocalBackup.inspect(backup);
            assertTrue(preview.summary.contains("秋季"));
            assertTrue(preview.summary.contains("春季"));
            store.createSemester("临时");
            LocalBackup.restore(store, preview);
            assertEquals(2, store.semesters().size());
            assertEquals(second, store.activeSemesterId());
            assertEquals(2, store.courses(second).size());
            assertEquals(2, LocalBackup.inspect(LocalBackup.export(store)).data
                    .getJSONArray("source_courses").length());
            assertEquals(1, store.dateExceptions(first, LocalDate.of(2026, 10, 19)).size());
        }
    }

    @Test public void widgetShowsCurrentNextMoreAndEmptyStates() {
        LocalDate date = LocalDate.of(2026, 10, 19);
        List<Timetable.Course> courses = Arrays.asList(course("甲", 1), course("乙", 2), course("丙", 3));
        List<Timetable.DayEntry> entries = Timetable.entriesForDate(courses, Collections.emptyList(), date, 1);
        String[] during = TodayWidget.lines(entries, LocalTime.of(8, 20));
        assertTrue(during[0].contains("正在上"));
        assertTrue(during[0].contains("甲"));
        assertTrue(during[1].contains("乙"));
        assertEquals("另有 1 门", during[2]);
        assertEquals("今日课程已结束", TodayWidget.lines(entries, LocalTime.of(23, 0))[0]);
        assertEquals("今日无课", TodayWidget.lines(Collections.emptyList(), LocalTime.NOON)[0]);
        assertThrows(IllegalArgumentException.class, () -> TodayWidget.validateSchedule("08:00-08:45"));
        String shifted = TodayWidget.defaultSchedule().replace("08:00-08:45", "07:00-07:45");
        assertTrue(TodayWidget.lines(entries, LocalTime.of(7, 20), shifted)[0].contains("正在上"));
    }
}
