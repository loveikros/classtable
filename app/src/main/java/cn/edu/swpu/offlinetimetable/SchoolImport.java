package cn.edu.swpu.offlinetimetable;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Reads only course fields from the rendered timetable popup. */
public final class SchoolImport {
    private SchoolImport() { }

    public static final String EXTRACT = "(function(){var t=document.querySelector('#wdkbTable');"
            + "if(!t)return JSON.stringify({error:'请先打开我的课表弹窗'});"
            + "var rows=[];t.querySelectorAll('td[data-role=item]').forEach(function(c){"
            + "var day=Number(c.getAttribute('data-week')),start=Number(c.getAttribute('data-begin-unit')),end=Number(c.getAttribute('data-end-unit'));"
            + "Array.from(c.children).forEach(function(block){var d=Array.from(block.children);"
            + "if(d.length<4)return;rows.push({day:day,start:start,end:end,weeks:d[0].textContent,name:d[1].querySelector('b')?d[1].querySelector('b').textContent:d[1].textContent,teacher:d[2].textContent,location:d[3].textContent});});});"
            + "return JSON.stringify({rows:rows});})()";

    public static List<Timetable.Course> parse(String json) {
        try {
            JSONObject root = new JSONObject(json);
            if (root.has("error")) throw new IllegalArgumentException(root.getString("error"));
            JSONArray rows = root.getJSONArray("rows");
            if (rows.length() == 0) throw new IllegalArgumentException("弹窗中没有可导入的课程安排");
            List<Timetable.Course> courses = new ArrayList<>();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                String fullName = row.getString("name").trim();
                int split = fullName.lastIndexOf('(');
                String name = split > 0 && fullName.endsWith(")") ? fullName.substring(0, split).trim() : fullName;
                String className = split > 0 && fullName.endsWith(")") ? fullName.substring(split + 1, fullName.length() - 1).trim() : "";
                String weeks = row.getString("weeks").replace("周", "").replace("第", "").trim();
                Timetable.Course next = new Timetable.Course(0, 0, name, className,
                        row.getString("teacher").trim(), row.getString("location").trim(),
                        row.getInt("day"), row.getInt("start"), row.getInt("end"),
                        Timetable.normalizeWeeks(weeks), false);
                Timetable.validate(next);
                boolean merged = false;
                for (int j = 0; j < courses.size(); j++) {
                    Timetable.Course old = courses.get(j);
                    if (old.weekday == next.weekday && old.endPeriod + 1 == next.startPeriod
                            && old.name.equals(next.name) && old.className.equals(next.className)
                            && old.teacher.equals(next.teacher) && old.location.equals(next.location)
                            && old.weeks.equals(next.weeks)) {
                        courses.set(j, new Timetable.Course(0, 0, old.name, old.className, old.teacher,
                                old.location, old.weekday, old.startPeriod, next.endPeriod, old.weeks, false));
                        merged = true;
                        break;
                    }
                }
                if (!merged) courses.add(next);
            }
            return courses;
        } catch (IllegalArgumentException ex) { throw ex;
        } catch (Exception ex) { throw new IllegalArgumentException("课表结构无法解析，请核对学校弹窗", ex); }
    }
}
