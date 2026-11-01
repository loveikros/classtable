package cn.edu.swpu.offlinetimetable;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public final class TodayWidget extends AppWidgetProvider {
    private static final String LEGACY_DEFAULT_SCHEDULE =
            "08:00-08:45,08:55-09:40,10:10-10:55,11:05-11:50,14:00-14:45,14:55-15:40," +
            "16:10-16:55,17:05-17:50,19:00-19:45,19:55-20:40,21:00-21:45,21:55-22:40";
    private static final String PREVIOUS_DEFAULT_SCHEDULE =
            "08:00-08:45,08:50-09:35,09:50-10:35,10:40-11:25,11:30-12:15,14:30-15:15," +
            "15:20-16:05,16:20-17:05,17:10-17:55,19:55-20:40,21:00-21:45,21:55-22:40";
    // Bell times shown in the supplied timetable image.
    private static final String[] START = {"08:00", "08:50", "09:50", "10:40", "11:30", "14:30",
            "15:20", "16:20", "17:10", "19:00", "19:50", "20:40"};
    private static final String[] END = {"08:45", "09:35", "10:35", "11:25", "12:15", "15:15",
            "16:05", "17:05", "17:55", "19:45", "20:35", "21:25"};

    public static String defaultSchedule() {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            if (i != 0) result.append(",");
            result.append(START[i]).append("-").append(END[i]);
        }
        return result.toString();
    }

    public static String currentSchedule(Context context) {
        String saved = context.getSharedPreferences("widget", Context.MODE_PRIVATE)
                .getString("schedule", null);
        return saved == null || LEGACY_DEFAULT_SCHEDULE.equals(saved)
                || PREVIOUS_DEFAULT_SCHEDULE.equals(saved) ? defaultSchedule() : saved;
    }

    public static String validateSchedule(String input) {
        String[] periods = input.trim().split(",", -1);
        if (periods.length != 12) throw new IllegalArgumentException("请输入 12 个节次时间，用英文逗号分隔");
        LocalTime previous = null;
        StringBuilder normalized = new StringBuilder();
        for (String period : periods) {
            String[] times = period.trim().split("-", -1);
            try {
                if (times.length != 2) throw new IllegalArgumentException();
                LocalTime start = LocalTime.parse(times[0].trim());
                LocalTime end = LocalTime.parse(times[1].trim());
                if (!end.isAfter(start) || (previous != null && !start.isAfter(previous)))
                    throw new IllegalArgumentException();
                if (normalized.length() != 0) normalized.append(",");
                normalized.append(start).append("-").append(end);
                previous = end;
            } catch (Exception ex) { throw new IllegalArgumentException("节次时间无效，请使用 HH:mm-HH:mm 格式且时间依次递增"); }
        }
        return normalized.toString();
    }

    static String[] lines(List<Timetable.DayEntry> entries, LocalTime now) {
        return lines(entries, now, defaultSchedule());
    }

    static String[] lines(List<Timetable.DayEntry> entries, LocalTime now, String schedule) {
        String[] periods = validateSchedule(schedule).split(",");
        List<Timetable.DayEntry> upcoming = new ArrayList<>();
        int scheduled = 0;
        for (Timetable.DayEntry entry : entries) {
            if (entry.exception != null && entry.exception.kind == Timetable.DateException.Kind.CANCEL) continue;
            scheduled++;
            if (now.isBefore(LocalTime.parse(periods[entry.endPeriod - 1].split("-")[1]))) upcoming.add(entry);
        }
        String[] result = {"", "", ""};
        if (upcoming.isEmpty()) {
            result[0] = scheduled == 0 ? "今日无课" : "今日课程已结束";
            return result;
        }
        for (int i = 0; i < Math.min(2, upcoming.size()); i++) {
            Timetable.DayEntry entry = upcoming.get(i);
            String start = periods[entry.startPeriod - 1].split("-")[0];
            String end = periods[entry.endPeriod - 1].split("-")[1];
            String state = now.isBefore(LocalTime.parse(start)) ? "" : "正在上 · ";
            result[i] = state + entry.name + "\n" + start + "-"
                    + end + (entry.location.isEmpty() ? "" : " · " + entry.location);
        }
        if (upcoming.size() > 2) result[2] = "另有 " + (upcoming.size() - 2) + " 门";
        return result;
    }

    public static void refresh(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, TodayWidget.class));
        if (ids.length != 0) update(context, manager, ids);
    }

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        update(context, manager, ids);
    }

    private static void update(Context context, AppWidgetManager manager, int[] ids) {
        LocalDate date = LocalDate.now();
        String[] shown = {"今日无课", "", ""};
        try (TimetableStore store = new TimetableStore(context)) {
            for (Timetable.Semester semester : store.semesters()) if (semester.id == store.activeSemesterId()) {
                int week = semester.anchorDate == null ? 1 :
                        WeekMath.weekAt(semester.anchorDate, semester.anchorWeek, date);
                shown = lines(Timetable.entriesForDate(store.courses(semester.id),
                        store.dateExceptions(semester.id, date), date, week), LocalTime.now(),
                        currentSchedule(context));
                break;
            }
        }
        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.today_widget);
            views.setTextViewText(R.id.widget_title, "今日课程 · " + date);
            views.setTextViewText(R.id.widget_first, shown[0]);
            views.setTextViewText(R.id.widget_second, shown[1]);
            views.setTextViewText(R.id.widget_more, shown[2]);
            Intent open = new Intent(context, MainActivity.class);
            views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, 0,
                    open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            manager.updateAppWidget(id, views);
        }
    }
}
