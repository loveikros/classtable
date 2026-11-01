package cn.edu.swpu.offlinetimetable;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.content.ContentValues;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.Set;

/** Versioned allowlist of timetable data. Credentials are never read. */
public final class LocalBackup {
    private LocalBackup() { }
    private static final String[] TABLES = {"semesters", "courses", "source_courses",
            "manual_deletions", "date_exceptions", "settings"};
    private static final String[][] COLUMNS = {
            {"id", "name", "anchor_date", "anchor_week"},
            {"id", "semester_id", "name", "class_name", "teacher", "location", "weekday", "start_period", "end_period", "weeks", "manually_modified"},
            {"id", "semester_id", "name", "class_name", "teacher", "location", "weekday", "start_period", "end_period", "weeks"},
            {"semester_id", "signature"},
            {"id", "semester_id", "date", "kind", "course_id", "name", "class_name", "teacher", "location", "start_period", "end_period"},
            {"id", "active_semester_id"}
    };

    public static String export(TimetableStore store) {
        SQLiteDatabase db = store.getReadableDatabase();
        JSONObject root = new JSONObject();
        try {
            root.put("format", "offline-timetable"); root.put("version", 1);
            for (int t = 0; t < TABLES.length; t++) {
                JSONArray rows = new JSONArray();
                try (Cursor cursor = db.query(TABLES[t], COLUMNS[t], null, null, null, null, null)) {
                    while (cursor.moveToNext()) {
                        JSONObject row = new JSONObject();
                        for (int c = 0; c < COLUMNS[t].length; c++)
                            row.put(COLUMNS[t][c], cursor.isNull(c) ? JSONObject.NULL :
                                    cursor.getType(c) == Cursor.FIELD_TYPE_INTEGER ? cursor.getLong(c) : cursor.getString(c));
                        rows.put(row);
                    }
                }
                root.put(TABLES[t], rows);
            }
            return root.toString();
        } catch (Exception ex) { throw new IllegalStateException("无法导出课表", ex); }
    }

    public static final class Preview {
        public final JSONObject data;
        public final String summary;
        Preview(JSONObject data, String summary) { this.data = data; this.summary = summary; }
    }

    public static Preview inspect(String text) {
        try {
            JSONObject root = new JSONObject(text);
            if (!"offline-timetable".equals(root.getString("format"))) throw new IllegalArgumentException("不是课程表备份文件");
            if (root.getInt("version") != 1) throw new IllegalArgumentException("不支持此备份版本");
            for (int t = 0; t < TABLES.length; t++) {
                JSONArray rows = root.getJSONArray(TABLES[t]);
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.getJSONObject(i);
                    for (String column : COLUMNS[t]) if (!row.has(column))
                        throw new IllegalArgumentException("备份缺少字段：" + column);
                }
            }
            Set<Long> semesterIds = new HashSet<>(), courseIds = new HashSet<>();
            Set<String> semesterNames = new HashSet<>();
            JSONArray semesters = root.getJSONArray("semesters");
            StringBuilder summary = new StringBuilder();
            for (int i = 0; i < semesters.length(); i++) {
                JSONObject semester = semesters.getJSONObject(i);
                long id = semester.getLong("id");
                if (!semesterIds.add(id) || id <= 0 || !semesterNames.add(semester.getString("name"))
                        || semester.getString("name").trim().isEmpty())
                    throw new IllegalArgumentException("学期数据无效");
                if (!semester.isNull("anchor_date")) java.time.LocalDate.parse(semester.getString("anchor_date"));
                int count = 0;
                JSONArray courses = root.getJSONArray("courses");
                for (int j = 0; j < courses.length(); j++) if (courses.getJSONObject(j).getLong("semester_id") == id) count++;
                summary.append(semester.getString("name")).append(" · ").append(count).append(" 条课程安排\n");
            }
            for (String table : new String[]{"courses", "source_courses", "date_exceptions", "manual_deletions"}) {
                JSONArray rows = root.getJSONArray(table);
                for (int i = 0; i < rows.length(); i++)
                    if (!semesterIds.contains(rows.getJSONObject(i).getLong("semester_id")))
                        throw new IllegalArgumentException("备份包含无效学期引用");
            }
            JSONArray courses = root.getJSONArray("courses");
            for (int i = 0; i < courses.length(); i++) {
                JSONObject row = courses.getJSONObject(i);
                long id = row.getLong("id");
                if (id <= 0 || !courseIds.add(id)) throw new IllegalArgumentException("课程安排编号无效");
                Timetable.validate(new Timetable.Course(id, row.getLong("semester_id"), row.getString("name"),
                        row.getString("class_name"), row.getString("teacher"), row.getString("location"),
                        row.getInt("weekday"), row.getInt("start_period"), row.getInt("end_period"),
                        row.getString("weeks"), row.getInt("manually_modified") != 0));
            }
            Set<Long> sourceIds = new HashSet<>();
            JSONArray source = root.getJSONArray("source_courses");
            for (int i = 0; i < source.length(); i++) {
                JSONObject row = source.getJSONObject(i);
                long id = row.getLong("id");
                if (id <= 0 || !sourceIds.add(id)) throw new IllegalArgumentException("来源课程编号无效");
                Timetable.validate(new Timetable.Course(id, row.getLong("semester_id"), row.getString("name"),
                        row.getString("class_name"), row.getString("teacher"), row.getString("location"),
                        row.getInt("weekday"), row.getInt("start_period"), row.getInt("end_period"),
                        row.getString("weeks"), false));
            }
            JSONArray exceptions = root.getJSONArray("date_exceptions");
            Set<Long> exceptionIds = new HashSet<>();
            for (int i = 0; i < exceptions.length(); i++) {
                JSONObject row = exceptions.getJSONObject(i);
                if (row.getLong("id") <= 0 || !exceptionIds.add(row.getLong("id")))
                    throw new IllegalArgumentException("日期例外编号无效");
                if (row.getLong("course_id") != 0 && !courseIds.contains(row.getLong("course_id")))
                    throw new IllegalArgumentException("日期例外引用了不存在的课程安排");
                Timetable.validate(new Timetable.DateException(row.getLong("id"), row.getLong("semester_id"),
                        java.time.LocalDate.parse(row.getString("date")),
                        Timetable.DateException.Kind.valueOf(row.getString("kind").toUpperCase(java.util.Locale.ROOT)),
                        row.getLong("course_id"), row.getString("name"), row.getString("class_name"),
                        row.getString("teacher"), row.getString("location"),
                        row.getInt("start_period"), row.getInt("end_period")));
            }
            JSONArray deletions = root.getJSONArray("manual_deletions");
            Set<String> deletionKeys = new HashSet<>();
            for (int i = 0; i < deletions.length(); i++) {
                JSONObject row = deletions.getJSONObject(i);
                String signature = row.getString("signature");
                new JSONArray(signature);
                if (!deletionKeys.add(row.getLong("semester_id") + ":" + signature))
                    throw new IllegalArgumentException("手动删除记录重复");
            }
            JSONArray settings = root.getJSONArray("settings");
            if (settings.length() != 1 || settings.getJSONObject(0).getInt("id") != 1)
                throw new IllegalArgumentException("当前学期设置无效");
            if (!settings.getJSONObject(0).isNull("active_semester_id")
                    && !semesterIds.contains(settings.getJSONObject(0).getLong("active_semester_id")))
                throw new IllegalArgumentException("当前学期不存在");
            return new Preview(root, summary.length() == 0 ? "备份中没有学期" : summary.toString());
        } catch (IllegalArgumentException ex) { throw ex;
        } catch (Exception ex) { throw new IllegalArgumentException("备份格式无效或文件已损坏", ex); }
    }

    public static void restore(TimetableStore store, Preview preview) {
        SQLiteDatabase db = store.getWritableDatabase();
        db.beginTransaction();
        try {
            for (int t = TABLES.length - 1; t >= 0; t--) db.delete(TABLES[t], null, null);
            for (int t = 0; t < TABLES.length; t++) {
                JSONArray rows = preview.data.getJSONArray(TABLES[t]);
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.getJSONObject(i);
                    ContentValues values = new ContentValues();
                    for (String column : COLUMNS[t]) {
                        Object value = row.get(column);
                        if (value == JSONObject.NULL) values.putNull(column);
                        else if (value instanceof Number) values.put(column, ((Number) value).longValue());
                        else values.put(column, value.toString());
                    }
                    db.insertOrThrow(TABLES[t], null, values);
                }
            }
            db.setTransactionSuccessful();
        } catch (Exception ex) { throw new IllegalArgumentException("恢复失败，原课表已保留", ex);
        } finally { db.endTransaction(); }
    }
}
