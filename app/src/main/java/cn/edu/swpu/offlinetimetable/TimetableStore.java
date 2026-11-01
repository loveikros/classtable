package cn.edu.swpu.offlinetimetable;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Local source of truth; no network or account permission is required. */
public final class TimetableStore extends SQLiteOpenHelper {
    public TimetableStore(Context context) {
        super(context, "timetable.db", null, 3);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE semesters (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE, anchor_date TEXT, anchor_week INTEGER)");
        db.execSQL("CREATE TABLE courses (id INTEGER PRIMARY KEY AUTOINCREMENT, semester_id INTEGER NOT NULL REFERENCES semesters(id), name TEXT NOT NULL, class_name TEXT NOT NULL, teacher TEXT NOT NULL, location TEXT NOT NULL, weekday INTEGER NOT NULL, start_period INTEGER NOT NULL, end_period INTEGER NOT NULL, weeks TEXT NOT NULL, manually_modified INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE settings (id INTEGER PRIMARY KEY CHECK (id = 1), active_semester_id INTEGER)");
        db.execSQL("INSERT INTO settings(id, active_semester_id) VALUES (1, NULL)");
        createDateExceptions(db);
        createSource(db);
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) createDateExceptions(db);
        if (oldVersion < 3) createSource(db);
    }

    private void createDateExceptions(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE date_exceptions (id INTEGER PRIMARY KEY AUTOINCREMENT, semester_id INTEGER NOT NULL, date TEXT NOT NULL, kind TEXT NOT NULL, course_id INTEGER NOT NULL, name TEXT NOT NULL, class_name TEXT NOT NULL, teacher TEXT NOT NULL, location TEXT NOT NULL, start_period INTEGER NOT NULL, end_period INTEGER NOT NULL)");
        db.execSQL("CREATE UNIQUE INDEX one_exception_per_course_date ON date_exceptions(semester_id,date,course_id) WHERE course_id != 0");
        db.execSQL("CREATE INDEX date_exceptions_for_day ON date_exceptions(semester_id,date)");
    }

    private void createSource(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE source_courses (id INTEGER PRIMARY KEY AUTOINCREMENT, semester_id INTEGER NOT NULL, name TEXT NOT NULL, class_name TEXT NOT NULL, teacher TEXT NOT NULL, location TEXT NOT NULL, weekday INTEGER NOT NULL, start_period INTEGER NOT NULL, end_period INTEGER NOT NULL, weeks TEXT NOT NULL)");
        db.execSQL("INSERT INTO source_courses(semester_id,name,class_name,teacher,location,weekday,start_period,end_period,weeks) SELECT semester_id,name,class_name,teacher,location,weekday,start_period,end_period,weeks FROM courses WHERE manually_modified=0");
        db.execSQL("CREATE TABLE manual_deletions (semester_id INTEGER NOT NULL, signature TEXT NOT NULL, PRIMARY KEY(semester_id,signature))");
    }

    private ContentValues sourceValues(long semesterId, Timetable.Course course) {
        ContentValues row = new ContentValues();
        row.put("semester_id", semesterId);
        row.put("name", course.name); row.put("class_name", course.className);
        row.put("teacher", course.teacher); row.put("location", course.location);
        row.put("weekday", course.weekday); row.put("start_period", course.startPeriod);
        row.put("end_period", course.endPeriod);
        row.put("weeks", Timetable.normalizeWeeks(course.weeks));
        return row;
    }

    public List<Timetable.Semester> semesters() {
        List<Timetable.Semester> result = new ArrayList<>();
        try (Cursor rows = getReadableDatabase().rawQuery(
                "SELECT id,name,anchor_date,anchor_week FROM semesters ORDER BY id", null)) {
            while (rows.moveToNext()) {
                String date = rows.getString(2);
                result.add(new Timetable.Semester(rows.getLong(0), rows.getString(1),
                        date == null ? null : LocalDate.parse(date), rows.getInt(3)));
            }
        }
        return result;
    }

    public long activeSemesterId() {
        try (Cursor row = getReadableDatabase().rawQuery("SELECT active_semester_id FROM settings WHERE id=1", null)) {
            return row.moveToFirst() && !row.isNull(0) ? row.getLong(0) : -1;
        }
    }

    public long createSemester(String name) {
        String trimmed = name.trim();
        if (trimmed.isEmpty()) throw new IllegalArgumentException("请输入学期名称");
        for (Timetable.Semester semester : semesters()) {
            if (semester.name.equals(trimmed)) throw new IllegalArgumentException("学期名称已存在");
        }
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues values = new ContentValues();
            values.put("name", trimmed);
            long id = db.insertOrThrow("semesters", null, values);
            ContentValues selected = new ContentValues();
            selected.put("active_semester_id", id);
            db.update("settings", selected, "id=1", null);
            db.setTransactionSuccessful();
            return id;
        } finally {
            db.endTransaction();
        }
    }

    public long importSemester(String name, List<Timetable.Course> courses) {
        String trimmed = name.trim();
        if (trimmed.isEmpty()) throw new IllegalArgumentException("请输入学期名称");
        if (courses.isEmpty()) throw new IllegalArgumentException("没有可导入的课程安排");
        for (Timetable.Semester semester : semesters())
            if (semester.name.equals(trimmed)) throw new IllegalArgumentException("学期名称已存在");
        for (Timetable.Course course : courses) Timetable.validate(course);
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues semester = new ContentValues();
            semester.put("name", trimmed);
            long id = db.insertOrThrow("semesters", null, semester);
            for (Timetable.Course course : courses) {
                ContentValues row = new ContentValues();
                row.put("semester_id", id);
                row.put("name", course.name);
                row.put("class_name", course.className);
                row.put("teacher", course.teacher);
                row.put("location", course.location);
                row.put("weekday", course.weekday);
                row.put("start_period", course.startPeriod);
                row.put("end_period", course.endPeriod);
                row.put("weeks", Timetable.normalizeWeeks(course.weeks));
                row.put("manually_modified", 0);
                db.insertOrThrow("courses", null, row);
                db.insertOrThrow("source_courses", null, sourceValues(id, course));
            }
            ContentValues selected = new ContentValues();
            selected.put("active_semester_id", id);
            db.update("settings", selected, "id=1", null);
            db.setTransactionSuccessful();
            return id;
        } finally { db.endTransaction(); }
    }

    public void selectSemester(long id) {
        try (Cursor row = getReadableDatabase().rawQuery("SELECT id FROM semesters WHERE id=?",
                new String[]{Long.toString(id)})) {
            if (!row.moveToFirst()) throw new IllegalArgumentException("学期不存在");
        }
        ContentValues values = new ContentValues();
        values.put("active_semester_id", id);
        getWritableDatabase().update("settings", values, "id=1", null);
    }

    public void setAnchor(long semesterId, LocalDate date, int week) {
        if (week < 1 || week > 99) throw new IllegalArgumentException("周次须在 1 至 99 之间");
        ContentValues values = new ContentValues();
        values.put("anchor_date", date.toString());
        values.put("anchor_week", week);
        if (getWritableDatabase().update("semesters", values, "id=?",
                new String[]{Long.toString(semesterId)}) != 1)
            throw new IllegalArgumentException("学期不存在");
    }

    public List<Timetable.Course> courses(long semesterId) {
        List<Timetable.Course> result = new ArrayList<>();
        try (Cursor rows = getReadableDatabase().rawQuery(
                "SELECT id,semester_id,name,class_name,teacher,location,weekday,start_period,end_period,weeks,manually_modified FROM courses WHERE semester_id=?",
                new String[]{Long.toString(semesterId)})) {
            while (rows.moveToNext()) {
                result.add(new Timetable.Course(rows.getLong(0), rows.getLong(1), rows.getString(2),
                        rows.getString(3), rows.getString(4), rows.getString(5), rows.getInt(6),
                        rows.getInt(7), rows.getInt(8), rows.getString(9), rows.getInt(10) != 0));
            }
        }
        return result;
    }

    public long saveCourse(Timetable.Course course) {
        Timetable.validate(course);
        ContentValues values = new ContentValues();
        values.put("semester_id", course.semesterId);
        values.put("name", course.name.trim());
        values.put("class_name", course.className.trim());
        values.put("teacher", course.teacher.trim());
        values.put("location", course.location.trim());
        values.put("weekday", course.weekday);
        values.put("start_period", course.startPeriod);
        values.put("end_period", course.endPeriod);
        values.put("weeks", Timetable.normalizeWeeks(course.weeks));
        values.put("manually_modified", 1);
        SQLiteDatabase db = getWritableDatabase();
        if (course.id == 0) return db.insertOrThrow("courses", null, values);
        int updated = db.update("courses", values, "id=? AND semester_id=?",
                new String[]{Long.toString(course.id), Long.toString(course.semesterId)});
        if (updated != 1) throw new IllegalArgumentException("课程安排不存在");
        return course.id;
    }

    public void deleteCourse(long semesterId, long courseId) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            String[] args = {Long.toString(courseId), Long.toString(semesterId)};
            if (db.delete("courses", "id=? AND semester_id=?", args) != 1)
                throw new IllegalArgumentException("课程安排不存在");
            db.delete("date_exceptions", "course_id=? AND semester_id=?", args);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public List<Timetable.DateException> dateExceptions(long semesterId, LocalDate date) {
        List<Timetable.DateException> result = new ArrayList<>();
        try (Cursor rows = getReadableDatabase().rawQuery(
                "SELECT id,semester_id,date,kind,course_id,name,class_name,teacher,location,start_period,end_period FROM date_exceptions WHERE semester_id=? AND date=? ORDER BY id",
                new String[]{Long.toString(semesterId), date.toString()})) {
            while (rows.moveToNext()) result.add(new Timetable.DateException(rows.getLong(0),
                    rows.getLong(1), LocalDate.parse(rows.getString(2)),
                    Timetable.DateException.Kind.valueOf(rows.getString(3).toUpperCase(java.util.Locale.ROOT)),
                    rows.getLong(4), rows.getString(5), rows.getString(6), rows.getString(7),
                    rows.getString(8), rows.getInt(9), rows.getInt(10)));
        }
        return result;
    }

    public long saveDateException(Timetable.DateException exception) {
        Timetable.validate(exception);
        boolean editingExisting = false;
        if (exception.id != 0) {
            for (Timetable.DateException existing : dateExceptions(exception.semesterId, exception.date)) {
                if (existing.id == exception.id && existing.courseId == exception.courseId) {
                    editingExisting = true;
                    break;
                }
            }
            if (!editingExisting) throw new IllegalArgumentException("日期例外不存在");
        }
        boolean foundSemester = false;
        for (Timetable.Semester semester : semesters()) {
            if (semester.id == exception.semesterId) {
                foundSemester = true;
                if (semester.anchorDate == null) throw new IllegalArgumentException("请先设置周次锚点");
                if (exception.courseId != 0 && !editingExisting) {
                    boolean foundCourse = false;
                    int week = WeekMath.weekAt(semester.anchorDate, semester.anchorWeek, exception.date);
                    for (Timetable.Course course : Timetable.coursesForWeek(courses(semester.id), week)) {
                        if (course.id == exception.courseId
                                && course.weekday == exception.date.getDayOfWeek().getValue()) {
                            foundCourse = true;
                            break;
                        }
                    }
                    if (!foundCourse) throw new IllegalArgumentException("该日期没有这条课程安排");
                }
                break;
            }
        }
        if (!foundSemester) throw new IllegalArgumentException("学期不存在");
        ContentValues values = new ContentValues();
        values.put("semester_id", exception.semesterId);
        values.put("date", exception.date.toString());
        values.put("kind", exception.kind.name().toLowerCase(java.util.Locale.ROOT));
        values.put("course_id", exception.courseId);
        values.put("name", exception.name.trim());
        values.put("class_name", exception.className.trim());
        values.put("teacher", exception.teacher.trim());
        values.put("location", exception.location.trim());
        values.put("start_period", exception.startPeriod);
        values.put("end_period", exception.endPeriod);
        SQLiteDatabase db = getWritableDatabase();
        if (exception.id == 0) {
            if (exception.courseId != 0) {
                List<Timetable.DateException> current = dateExceptions(exception.semesterId, exception.date);
                for (Timetable.DateException item : current) {
                    if (item.courseId == exception.courseId) {
                        db.update("date_exceptions", values, "id=?", new String[]{Long.toString(item.id)});
                        return item.id;
                    }
                }
            }
            return db.insertOrThrow("date_exceptions", null, values);
        }
        if (db.update("date_exceptions", values, "id=? AND semester_id=?",
                new String[]{Long.toString(exception.id), Long.toString(exception.semesterId)}) != 1)
            throw new IllegalArgumentException("日期例外不存在");
        return exception.id;
    }

    public void deleteDateException(long semesterId, long exceptionId) {
        if (getWritableDatabase().delete("date_exceptions", "id=? AND semester_id=?",
                new String[]{Long.toString(exceptionId), Long.toString(semesterId)}) != 1)
            throw new IllegalArgumentException("日期例外不存在");
    }
}
