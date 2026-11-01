package cn.edu.swpu.offlinetimetable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class Timetable {
    private Timetable() { }

    public static final class Semester {
        public final long id;
        public final String name;
        public final LocalDate anchorDate;
        public final int anchorWeek;

        public Semester(long id, String name, LocalDate anchorDate, int anchorWeek) {
            this.id = id;
            this.name = name;
            this.anchorDate = anchorDate;
            this.anchorWeek = anchorWeek;
        }
    }

    public static final class Course {
        public final long id;
        public final long semesterId;
        public final String name;
        public final String className;
        public final String teacher;
        public final String location;
        public final int weekday;
        public final int startPeriod;
        public final int endPeriod;
        public final String weeks;
        public final boolean manuallyModified;

        public Course(long id, long semesterId, String name, String className, String teacher,
                      String location, int weekday, int startPeriod, int endPeriod,
                      String weeks, boolean manuallyModified) {
            this.id = id;
            this.semesterId = semesterId;
            this.name = name;
            this.className = className;
            this.teacher = teacher;
            this.location = location;
            this.weekday = weekday;
            this.startPeriod = startPeriod;
            this.endPeriod = endPeriod;
            this.weeks = weeks;
            this.manuallyModified = manuallyModified;
        }
    }

    public static final class DateException {
        public enum Kind { CANCEL, CHANGE, EXTRA }
        public final long id;
        public final long semesterId;
        public final LocalDate date;
        public final Kind kind;
        public final long courseId;
        public final String name;
        public final String className;
        public final String teacher;
        public final String location;
        public final int startPeriod;
        public final int endPeriod;

        public DateException(long id, long semesterId, LocalDate date, Kind kind, long courseId,
                             String name, String className, String teacher, String location,
                             int startPeriod, int endPeriod) {
            this.id = id;
            this.semesterId = semesterId;
            this.date = date;
            this.kind = kind;
            this.courseId = courseId;
            this.name = name;
            this.className = className;
            this.teacher = teacher;
            this.location = location;
            this.startPeriod = startPeriod;
            this.endPeriod = endPeriod;
        }
    }

    public static final class DayEntry {
        public final Course course;
        public final DateException exception;
        public final String name;
        public final String location;
        public final int startPeriod;
        public final int endPeriod;

        private DayEntry(Course course, DateException exception, String name, String location,
                         int startPeriod, int endPeriod) {
            this.course = course;
            this.exception = exception;
            this.name = name;
            this.location = location;
            this.startPeriod = startPeriod;
            this.endPeriod = endPeriod;
        }
    }

    public static void validate(DateException exception) {
        if (exception.date == null) throw new IllegalArgumentException("请选择日期");
        if (exception.kind == null) throw new IllegalArgumentException("日期例外类型无效");
        if ((exception.kind == DateException.Kind.EXTRA) != (exception.courseId == 0))
            throw new IllegalArgumentException("请选择要调整的课程安排");
        if (exception.kind != DateException.Kind.CANCEL) {
            if (exception.name.trim().isEmpty()) throw new IllegalArgumentException("请输入课程名称");
            if (exception.startPeriod < 1 || exception.endPeriod > 12
                    || exception.endPeriod < exception.startPeriod)
                throw new IllegalArgumentException("节次须在 1 至 12 之间，且结束不早于开始");
        }
    }

    public static List<DayEntry> entriesForDate(List<Course> courses,
                                                 List<DateException> exceptions,
                                                 LocalDate date, int week) {
        List<DayEntry> entries = new ArrayList<>();
        Map<Long, DateException> overrides = new HashMap<>();
        Set<Long> matched = new HashSet<>();
        for (DateException exception : exceptions) {
            if (exception.date.equals(date) && exception.courseId != 0)
                overrides.put(exception.courseId, exception);
        }
        for (Course course : coursesForWeek(courses, week)) {
            if (course.weekday != date.getDayOfWeek().getValue()) continue;
            DateException exception = overrides.get(course.id);
            if (exception != null) matched.add(exception.id);
            entries.add(exception == null
                    ? new DayEntry(course, null, course.name, course.location,
                            course.startPeriod, course.endPeriod)
                    : new DayEntry(course, exception,
                            exception.kind == DateException.Kind.CANCEL ? course.name : exception.name,
                            exception.location,
                            exception.kind == DateException.Kind.CANCEL ? course.startPeriod : exception.startPeriod,
                            exception.kind == DateException.Kind.CANCEL ? course.endPeriod : exception.endPeriod));
        }
        for (DateException exception : exceptions) {
            if (exception.date.equals(date) && exception.kind == DateException.Kind.EXTRA
                    && !matched.contains(exception.id))
                entries.add(new DayEntry(null, exception, exception.name, exception.location,
                        exception.startPeriod, exception.endPeriod));
        }
        entries.sort((a, b) -> {
            int byPeriod = Integer.compare(a.startPeriod, b.startPeriod);
            return byPeriod != 0 ? byPeriod : Long.compare(
                    a.exception == null ? a.course.id : a.exception.id,
                    b.exception == null ? b.course.id : b.exception.id);
        });
        return entries;
    }

    public static String normalizeWeeks(String input) {
        TreeSet<Integer> values = new TreeSet<>();
        for (String piece : input.replace('，', ',').split(",", -1)) {
            String token = piece.trim();
            if (token.isEmpty()) throw new IllegalArgumentException("请输入适用周次，例如 1-16,18");
            String[] range = token.split("-", -1);
            if (range.length > 2) throw new IllegalArgumentException("周次格式应为 1-16,18");
            try {
                int first = Integer.parseInt(range[0].trim());
                int last = range.length == 2 ? Integer.parseInt(range[1].trim()) : first;
                if (first < 1 || last < first || last > 99) throw new NumberFormatException();
                for (int week = first; week <= last; week++) values.add(week);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("周次须为 1 至 99，格式如 1-16,18");
            }
        }
        StringBuilder result = new StringBuilder();
        for (int week : values) {
            if (result.length() > 0) result.append(',');
            result.append(week);
        }
        return result.toString();
    }

    public static void validate(Course course) {
        if (course.name.trim().isEmpty()) throw new IllegalArgumentException("请输入课程名称");
        if (course.weekday < 1 || course.weekday > 7) throw new IllegalArgumentException("请选择星期");
        if (course.startPeriod < 1 || course.endPeriod > 12 || course.endPeriod < course.startPeriod)
            throw new IllegalArgumentException("节次须在 1 至 12 之间，且结束不早于开始");
        normalizeWeeks(course.weeks);
    }

    public static List<Course> coursesForWeek(List<Course> courses, int week) {
        if (week < 1) return Collections.emptyList();
        List<Course> visible = new ArrayList<>();
        for (Course course : courses) {
            String normalized = normalizeWeeks(course.weeks);
            if ((',' + normalized + ',').contains(',' + Integer.toString(week) + ',')) visible.add(course);
        }
        visible.sort((a, b) -> {
            int byDay = Integer.compare(a.weekday, b.weekday);
            if (byDay != 0) return byDay;
            int byPeriod = Integer.compare(a.startPeriod, b.startPeriod);
            return byPeriod != 0 ? byPeriod : Long.compare(a.id, b.id);
        });
        return visible;
    }
}
