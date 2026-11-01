package cn.edu.swpu.offlinetimetable;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.view.Gravity;
import android.view.animation.DecelerateInterpolator;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import org.json.JSONArray;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;

public final class MainActivity extends Activity {
    private static final String[] DAYS = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
    private TimetableStore store;
    private static final int EXPORT_REQUEST = 42, RESTORE_REQUEST = 43;
    private Integer selectedWeek;
    private LocalDate selectedDate;
    private boolean viewingCurrentWeek = true;
    private TimetableGridView weekdayHeaderView;
    private TimetableGridView timetableGridView;
    private ScrollView timetableScroll;
    private final Handler clock = new Handler(Looper.getMainLooper());
    private final Runnable midnightRefresh = () -> {
        if (viewingCurrentWeek) {
            selectedWeek = null;
            selectedDate = null;
            render();
        }
        scheduleMidnightRefresh();
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        removeLegacyCredentials();
        store = new TimetableStore(this);
        render();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null && data.getData() != null) {
            try {
                if (requestCode == EXPORT_REQUEST) {
                    try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                        if (out == null) throw new IllegalArgumentException("无法写入文件");
                        out.write(LocalBackup.export(store).getBytes(StandardCharsets.UTF_8));
                    }
                    error("备份已保存");
                } else if (requestCode == RESTORE_REQUEST) {
                    String text;
                    try (InputStream in = getContentResolver().openInputStream(data.getData())) {
                        if (in == null) throw new IllegalArgumentException("无法读取文件");
                        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                        byte[] chunk = new byte[8192];
                        int count;
                        while ((count = in.read(chunk)) != -1 && buffer.size() <= 5_000_000)
                            buffer.write(chunk, 0, count);
                        byte[] bytes = buffer.toByteArray();
                        if (bytes.length > 5_000_000) throw new IllegalArgumentException("备份文件过大");
                        text = new String(bytes, StandardCharsets.UTF_8);
                    }
                    LocalBackup.Preview preview = LocalBackup.inspect(text);
                    new AlertDialog.Builder(this).setTitle("恢复预览")
                            .setMessage(preview.summary + "\n将替换本机课表，教务密码不会从备份恢复。")
                            .setNegativeButton("取消", null)
                            .setPositiveButton("确认恢复", (dialog, which) -> {
                                try {
                                    LocalBackup.restore(store, preview);
                                    selectedDate = null; selectedWeek = null; viewingCurrentWeek = true;
                                    render(); error("课表已恢复");
                                } catch (Exception ex) { error(ex.getMessage()); }
                            }).show();
                }
            } catch (Exception ex) { error(ex.getMessage()); }
        }
    }

    private void removeLegacyCredentials() {
        getSharedPreferences("school_credentials", MODE_PRIVATE).edit().clear().apply();
        try {
            java.security.KeyStore keys = java.security.KeyStore.getInstance("AndroidKeyStore");
            keys.load(null);
            if (keys.containsAlias("school_password_v1")) keys.deleteEntry("school_password_v1");
        } catch (Exception ignored) { }
    }

    @Override protected void onDestroy() {
        store.close();
        super.onDestroy();
    }

    @Override protected void onResume() {
        super.onResume();
        if (store != null && viewingCurrentWeek) {
            selectedWeek = null;
            render();
        }
        scheduleMidnightRefresh();
    }

    @Override protected void onPause() {
        clock.removeCallbacks(midnightRefresh);
        super.onPause();
    }

    private void scheduleMidnightRefresh() {
        clock.removeCallbacks(midnightRefresh);
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime nextMidnight = now.toLocalDate().plusDays(1)
                .atStartOfDay(ZoneId.systemDefault());
        long delay = java.time.Duration.between(now, nextMidnight).toMillis();
        clock.postDelayed(midnightRefresh, Math.max(1, delay));
    }

    private int currentWeek(Timetable.Semester semester) {
        return semester.anchorDate == null ? 1 : WeekMath.weekAt(semester.anchorDate,
                semester.anchorWeek, LocalDate.now());
    }

    private void render() {
        List<Timetable.Semester> semesters = store.semesters();
        long selectedId = store.activeSemesterId();
        Timetable.Semester semester = null;
        int activeIndex = 0;
        for (int i = 0; i < semesters.size(); i++) {
            if (semesters.get(i).id == selectedId) {
                semester = semesters.get(i);
                activeIndex = i;
                break;
            }
        }
        if (semester == null && !semesters.isEmpty()) {
            semester = semesters.get(0);
            store.selectSemester(semester.id);
        }
        TodayWidget.refresh(this);

        LinearLayout root = column();
        root.setBackgroundColor(Color.rgb(247, 248, 245));
        LinearLayout top = column();
        top.setPadding(dp(12), dp(12), dp(12), 0);
        root.addView(top);
        ScrollView scroll = new ScrollView(this);
        LinearLayout body = column();
        body.setPadding(dp(10), 0, dp(10), dp(32));
        scroll.addView(body);
        setContentView(root);
        LinearLayout hero = card(top, Color.rgb(222, 235, 226));
        label(hero, "西柚课程表", 28);

        if (!semesters.isEmpty()) {
            Spinner picker = new Spinner(this);
            List<String> names = new ArrayList<>();
            for (Timetable.Semester item : semesters) names.add(item.name);
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_item, names);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            picker.setAdapter(adapter);
            picker.setSelection(activeIndex);
            picker.setContentDescription("切换学期");
            picker.setBackground(roundedSurface(Color.rgb(237, 243, 239)));
            picker.setPadding(14, 8, 14, 8);
            hero.addView(picker);
            picker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    long chosenId = semesters.get(position).id;
                    if (chosenId != store.activeSemesterId()) {
                        store.selectSemester(chosenId);
                        selectedWeek = null;
                        selectedDate = null;
                        viewingCurrentWeek = true;
                        render();
                    }
                }
                @Override public void onNothingSelected(AdapterView<?> parent) { }
            });
        }
        if (semester == null) {
            root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
            LinearLayout tools = managementCard(body);
            label(tools, "请先新建学期，再设置周次锚点和课程安排。", 16);
            return;
        }

        Timetable.Semester active = semester;
        LinearLayout weekCard = card(top, Color.WHITE);
        if (selectedWeek == null) selectedWeek = currentWeek(active);
        Spinner weekPicker = new Spinner(this);
        String[] weekNames = new String[99];
        for (int i = 0; i < weekNames.length; i++) weekNames[i] = "第 " + (i + 1) + " 周";
        ArrayAdapter<String> weekAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, weekNames) {
            @Override public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView title = (TextView) super.getView(position, convertView, parent);
                title.setTextSize(23);
                title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                title.setTextColor(Color.rgb(43, 64, 62));
                title.setGravity(Gravity.CENTER);
                return title;
            }
        };
        weekAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        weekPicker.setAdapter(weekAdapter);
        weekPicker.setSelection(Math.max(0, Math.min(98, selectedWeek - 1)));
        weekPicker.setContentDescription("选择周次");
        weekPicker.setBackgroundColor(Color.TRANSPARENT);
        weekPicker.setPadding(0, 0, 0, 0);
        FrameLayout weekPickerFrame = new FrameLayout(this);
        weekPickerFrame.setBackground(roundedSurface(Color.rgb(237, 243, 239)));
        weekCard.addView(weekPickerFrame, new LinearLayout.LayoutParams(-1, dp(62)));
        weekPickerFrame.addView(weekPicker, new FrameLayout.LayoutParams(-1, -1));
        TextView weekArrow = new TextView(this);
        weekArrow.setText("▾");
        weekArrow.setTextSize(14);
        weekArrow.setTextColor(Color.rgb(86, 112, 102));
        weekArrow.setGravity(Gravity.CENTER);
        weekArrow.setOnClickListener(view -> weekPicker.performClick());
        FrameLayout.LayoutParams arrowParams = new FrameLayout.LayoutParams(dp(18), dp(22),
                Gravity.END | Gravity.BOTTOM);
        arrowParams.setMargins(0, 0, dp(5), dp(2));
        weekPickerFrame.addView(weekArrow, arrowParams);
        weekPicker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position + 1 != selectedWeek) selectWeek(position + 1);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        LinearLayout dateActions = row();
        weekCard.addView(dateActions);
        button(dateActions, "回到当前周", () -> { viewingCurrentWeek = true; selectedDate = null; selectedWeek = currentWeek(active); render(); });
        button(dateActions, "选择日期", () -> chooseDate(active));
        if (selectedDate != null) {
            label(weekCard, "已选 " + selectedDate + " · " + DAYS[selectedDate.getDayOfWeek().getValue() - 1], 15);
            LinearLayout selectedActions = row();
            weekCard.addView(selectedActions);
            button(selectedActions, "当天补课", () -> editDateException(active, selectedDate, null, null,
                    Timetable.DateException.Kind.EXTRA));
            button(selectedActions, "取消高亮", () -> { selectedDate = null; render(); });
        }
        List<Timetable.Course> allCourses = store.courses(active.id);
        LocalDate firstDay = active.anchorDate == null ? null : active.anchorDate
                .minusDays(active.anchorDate.getDayOfWeek().getValue() - 1)
                .plusWeeks(selectedWeek - active.anchorWeek);
        if (firstDay == null) firstDay = LocalDate.now()
                .minusDays(LocalDate.now().getDayOfWeek().getValue() - 1);
        final LocalDate gridMonday = firstDay;
        @SuppressWarnings("unchecked") List<Timetable.DayEntry>[] days = new List[7];
        for (int day = 0; day < 7; day++) {
            LocalDate date = gridMonday.plusDays(day);
            days[day] = Timetable.entriesForDate(allCourses,
                    store.dateExceptions(active.id, date), date, selectedWeek);
        }
        String schedule = TodayWidget.currentSchedule(this);
        int highlightedDay = selectedDate == null ? 0 : selectedDate.getDayOfWeek().getValue();
        TimetableGridView.OnWeekSwipe swipe = direction -> {
            int target = Math.max(1, Math.min(99, selectedWeek + direction));
            if (target == selectedWeek) return;
            int scrollY = scroll.getScrollY();
            selectWeek(target);
            ScrollView updated = timetableScroll;
            if (updated != null) updated.post(() -> updated.scrollTo(0, scrollY));
        };
        LinearLayout headerBar = column();
        headerBar.setBackgroundColor(Color.WHITE);
        headerBar.setPadding(dp(10), 0, dp(10), 0);
        root.addView(headerBar);
        TimetableGridView weekdayHeader = new TimetableGridView(this, days, schedule,
                gridMonday, highlightedDay, true, (weekday, entry) -> { }, swipe);
        weekdayHeaderView = weekdayHeader;
        headerBar.addView(weekdayHeader, new LinearLayout.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        timetableScroll = scroll;
        body.setBackgroundColor(Color.WHITE);
        TimetableGridView grid = new TimetableGridView(this, days, schedule, gridMonday, highlightedDay,
                false, (weekday, entry) -> {
            LocalDate date = gridMonday.plusDays(weekday - 1);
            if (selectedDate != null) showDayActions(active, date, entry);
            else if (entry.exception == null) showCourseActions(active, entry.course);
            else showDayActions(active, date, entry);
        }, swipe);
        timetableGridView = grid;
        body.addView(grid, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout tools = managementCard(body);
        Button add = button(tools, "＋ 添加课程安排", () -> editCourse(active, null));
        add.setTextColor(Color.WHITE);
        add.setBackground(roundedSurface(Color.rgb(95, 131, 119)));
        if (active.anchorDate == null)
            label(tools, "尚未确认周次锚点 · 当前显示第 1 周", 15);
        else
            label(tools, "周次锚点 · " + active.anchorDate + " 为第 " + active.anchorWeek + " 周", 15);
        button(tools, active.anchorDate == null ? "设置周次锚点" : "重新校准周次锚点",
                () -> editAnchor(active));
    }

    private LinearLayout managementCard(LinearLayout body) {
        LinearLayout tools = card(body, Color.WHITE);
        label(tools, "课表管理", 20);
        LinearLayout firstActions = row();
        tools.addView(firstActions);
        button(firstActions, "新建学期", this::createSemester);
        button(firstActions, "从学校网页导入", this::openSchoolImport);
        LinearLayout secondActions = row();
        tools.addView(secondActions);
        button(secondActions, "导出备份", this::exportBackup);
        button(secondActions, "恢复备份", this::chooseBackup);
        button(tools, "调整节次时间", this::editPeriodTimes);
        return tools;
    }

    private void selectWeek(int week) {
        if (week == selectedWeek) return;
        int direction = week > selectedWeek ? 1 : -1;
        selectedWeek = week;
        selectedDate = null;
        viewingCurrentWeek = false;
        render();
        animateWeek(weekdayHeaderView, direction);
        animateWeek(timetableGridView, direction);
    }

    private void animateWeek(View view, int direction) {
        if (view == null) return;
        view.setTranslationX(dp(28) * direction);
        view.setAlpha(0.45f);
        view.animate().translationX(0).alpha(1).setDuration(220)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    private void exportBackup() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "离线课程表备份.json");
        startActivityForResult(intent, EXPORT_REQUEST);
    }

    private void chooseBackup() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        startActivityForResult(intent, RESTORE_REQUEST);
    }

    private void editPeriodTimes() {
        String current = TodayWidget.currentSchedule(this);
        EditText input = field("12 个时间段，以逗号分隔", current);
        input.setSingleLine(false);
        new AlertDialog.Builder(this).setTitle("节次时间")
                .setMessage("请按学校铃声时间调整，格式如 08:00-08:45。")
                .setView(wrap(input)).setNegativeButton("取消", null)
                .setPositiveButton("保存", (dialog, which) -> {
                    try {
                        String schedule = TodayWidget.validateSchedule(input.getText().toString());
                        getSharedPreferences("widget", MODE_PRIVATE).edit().putString("schedule", schedule).apply();
                        TodayWidget.refresh(this);
                    } catch (Exception ex) { error(ex.getMessage()); }
                }).show();
    }

    private void chooseDate(Timetable.Semester semester) {
        if (semester.anchorDate == null) {
            error("请先设置周次锚点");
            return;
        }
        LocalDate initial = selectedDate == null ? LocalDate.now() : selectedDate;
        new DatePickerDialog(this, (view, year, month, day) -> {
            LocalDate picked = LocalDate.of(year, month + 1, day);
            int week = WeekMath.weekAt(semester.anchorDate, semester.anchorWeek, picked);
            if (week < 1 || week > 99) {
                error("所选日期不在第 1 至 99 周内");
                return;
            }
            selectedDate = picked;
            selectedWeek = week;
            viewingCurrentWeek = false;
            render();
        }, initial.getYear(), initial.getMonthValue() - 1, initial.getDayOfMonth()).show();
    }

    private void showDayActions(Timetable.Semester semester, LocalDate date, Timetable.DayEntry entry) {
        if (entry.exception != null) {
            boolean extra = entry.exception.kind == Timetable.DateException.Kind.EXTRA;
            boolean cancelled = entry.exception.kind == Timetable.DateException.Kind.CANCEL;
            List<String> options = new ArrayList<>();
            options.add("编辑日期例外");
            options.add("删除日期例外");
            if (!extra) options.add(cancelled ? "改为当天信息变更" : "改为当天停课");
            if (entry.course != null) options.add("编辑常规安排");
            String[] actions = options.toArray(new String[0]);
            new AlertDialog.Builder(this).setTitle(entry.name).setItems(actions, (dialog, which) -> {
                if (which == 0) editDateException(semester, date, entry.course,
                        entry.exception, entry.exception.kind);
                else if (which == 2) editDateException(semester, date, entry.course,
                        entry.exception, cancelled ? Timetable.DateException.Kind.CHANGE :
                                Timetable.DateException.Kind.CANCEL);
                else if (entry.course != null && which == options.size() - 1)
                    editCourse(semester, entry.course);
                else new AlertDialog.Builder(this).setMessage("删除这条日期例外？")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("删除", (confirm, answer) -> {
                            store.deleteDateException(semester.id, entry.exception.id);
                            render();
                        }).show();
            }).show();
            return;
        }
        String[] actions = {"设为当天停课", "修改当天信息", "编辑常规安排"};
        new AlertDialog.Builder(this).setTitle(entry.name).setItems(actions, (dialog, which) -> {
            if (which == 0) editDateException(semester, date, entry.course, null,
                    Timetable.DateException.Kind.CANCEL);
            else if (which == 1) editDateException(semester, date, entry.course, null,
                    Timetable.DateException.Kind.CHANGE);
            else editCourse(semester, entry.course);
        }).show();
    }

    private void editDateException(Timetable.Semester semester, LocalDate date,
                                   Timetable.Course course, Timetable.DateException original,
                                   Timetable.DateException.Kind kind) {
        LinearLayout form = column();
        label(form, "仅影响 " + date, 16);
        String priorName = original == null ? (course == null ? "" : course.name) : original.name;
        String priorClass = original == null ? (course == null ? "" : course.className) : original.className;
        String priorTeacher = original == null ? (course == null ? "" : course.teacher) : original.teacher;
        String priorLocation = original == null ? (course == null ? "" : course.location) : original.location;
        int priorStart = original == null ? (course == null ? 1 : course.startPeriod) : original.startPeriod;
        int priorEnd = original == null ? (course == null ? 1 : course.endPeriod) : original.endPeriod;
        EditText name = addField(form, "课程名称", priorName);
        EditText start = addField(form, "开始节次 1-12", Integer.toString(priorStart));
        EditText end = addField(form, "结束节次 1-12", Integer.toString(priorEnd));
        start.setInputType(InputType.TYPE_CLASS_NUMBER);
        end.setInputType(InputType.TYPE_CLASS_NUMBER);
        EditText className = addField(form, "班级（可留空）", priorClass);
        EditText teacher = addField(form, "教师（可留空）", priorTeacher);
        EditText location = addField(form, "地点（可留空）", priorLocation);
        boolean cancelled = kind == Timetable.DateException.Kind.CANCEL;
        name.setEnabled(!cancelled);
        start.setEnabled(!cancelled);
        end.setEnabled(!cancelled);
        className.setEnabled(!cancelled);
        teacher.setEnabled(!cancelled);
        location.setEnabled(!cancelled);
        String title = cancelled ? "当天停课" : kind == Timetable.DateException.Kind.EXTRA ?
                "当天补课" : "修改当天信息";
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(title)
                .setView(wrap(form)).setNegativeButton("取消", null)
                .setPositiveButton("保存", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    try {
                        Timetable.DateException edited = new Timetable.DateException(
                                original == null ? 0 : original.id, semester.id, date, kind,
                                original != null ? original.courseId : course == null ? 0 : course.id,
                                name.getText().toString(),
                                className.getText().toString(), teacher.getText().toString(),
                                location.getText().toString(), number(start, "开始节次"),
                                number(end, "结束节次"));
                        store.saveDateException(edited);
                        dialog.dismiss();
                        render();
                    } catch (IllegalArgumentException ex) { error(ex.getMessage()); }
                }));
        dialog.show();
    }

    private void openSchoolImport() {
        LinearLayout content = column();
        content.setPadding(dp(12), dp(4), dp(12), dp(4));
        label(content, "登录后打开“我的课表”弹窗，再点下方按钮。", 15);
        Button capture = button(content, "抓取课表并预览", () -> { });
        capture.setTextSize(16);
        capture.setMinHeight(dp(56));
        capture.setMaxLines(2);
        capture.setTextColor(Color.WHITE);
        capture.setBackground(roundedSurface(Color.rgb(95, 131, 119)));
        WebView web = new WebView(this);
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false);
        web.getSettings().setAllowContentAccess(false);
        web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.setWebViewClient(new WebViewClient());
        int browserHeight = (int) (getResources().getDisplayMetrics().heightPixels * 0.62f);
        content.addView(web, new LinearLayout.LayoutParams(-1, browserHeight));
        AlertDialog browser = new AlertDialog.Builder(this).setTitle("学校选课网页")
                .setView(content).setNegativeButton("取消", null).create();
        browser.setOnDismissListener(ignored -> web.destroy());
        capture.setOnClickListener(view -> {
            String url = web.getUrl();
            if (url == null || !url.startsWith("https://yjs.swpu.edu.cn/")) {
                error("请在学校选课网页打开课表");
                return;
            }
            web.evaluateJavascript(SchoolImport.EXTRACT, value -> {
                try {
                    String json = new JSONArray("[" + value + "]").getString(0);
                    List<Timetable.Course> courses = SchoolImport.parse(json);
                    browser.setOnDismissListener(null);
                    browser.dismiss();
                    web.destroy();
                    previewImport(courses);
                } catch (Exception ex) { error(ex.getMessage()); }
            });
        });
        browser.show();
        Window window = browser.getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        window.setLayout(-1, -1);
        web.loadUrl("https://yjs.swpu.edu.cn/yjsxkapp/sys/xsxkapp/index.html");
    }

    private void previewImport(List<Timetable.Course> courses) {
        LinearLayout content = column();
        label(content, "解析到 " + courses.size() + " 条课程安排，请核对后保存", 18);
        label(content, "选择学期（可上下滑动）", 16);
        String[] semesterNames = SemesterChoices.forDate(LocalDate.now());
        NumberPicker semesterPicker = new NumberPicker(this);
        semesterPicker.setMinValue(0);
        semesterPicker.setMaxValue(semesterNames.length - 1);
        semesterPicker.setDisplayedValues(semesterNames);
        semesterPicker.setWrapSelectorWheel(false);
        semesterPicker.setValue(0);
        content.addView(semesterPicker);
        for (Timetable.Course course : courses) {
            label(content, DAYS[course.weekday - 1] + " " + course.startPeriod + "-"
                    + course.endPeriod + " 节 · " + course.weeks + " 周 · " + course.name
                    + " · " + course.className + " · " + course.teacher + " · "
                    + course.location, 14);
        }
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("导入预览")
                .setView(wrap(content)).setNegativeButton("取消", null)
                .setPositiveButton("保存离线课表", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    try {
                        store.importSemester(semesterNames[semesterPicker.getValue()], courses);
                        selectedWeek = null;
                        selectedDate = null;
                        viewingCurrentWeek = true;
                        dialog.dismiss();
                        render();
                    } catch (Exception ex) { error(ex.getMessage()); }
                }));
        dialog.show();
    }

    private void createSemester() {
        EditText name = field("学期名称，例如 2026 秋", "");
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("新建学期")
                .setView(wrap(name)).setNegativeButton("取消", null)
                .setPositiveButton("创建", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    try {
                        store.createSemester(name.getText().toString());
                        selectedWeek = null;
                        selectedDate = null;
                        viewingCurrentWeek = true;
                        dialog.dismiss();
                        render();
                    } catch (IllegalArgumentException ex) { error(ex.getMessage()); }
                }));
        dialog.show();
    }

    private void editAnchor(Timetable.Semester semester) {
        LocalDate initial = semester.anchorDate == null ? LocalDate.now() : semester.anchorDate;
        LocalDate[] picked = {initial};
        LinearLayout content = column();
        Button date = button(content, "日期：" + initial, () -> {
            DatePickerDialog picker = new DatePickerDialog(this, (view, year, month, day) -> {
                picked[0] = LocalDate.of(year, month + 1, day);
                dateLabel(content, picked[0]);
            }, picked[0].getYear(), picked[0].getMonthValue() - 1, picked[0].getDayOfMonth());
            picker.show();
        });
        EditText week = field("该日期所属学期周次", semester.anchorDate == null ? "1" :
                Integer.toString(semester.anchorWeek));
        week.setInputType(InputType.TYPE_CLASS_NUMBER);
        content.addView(week);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("周次锚点")
                .setView(wrap(content)).setNegativeButton("取消", null)
                .setPositiveButton("保存", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    try {
                        store.setAnchor(semester.id, picked[0], number(week, "周次"));
                        selectedWeek = null;
                        selectedDate = null;
                        viewingCurrentWeek = true;
                        dialog.dismiss();
                        render();
                    } catch (IllegalArgumentException ex) { error(ex.getMessage()); }
                }));
        dialog.show();
    }

    private void dateLabel(LinearLayout content, LocalDate date) {
        ((Button) content.getChildAt(0)).setText("日期：" + date);
    }

    private void showCourseActions(Timetable.Semester semester, Timetable.Course course) {
        new AlertDialog.Builder(this).setTitle(course.name)
                .setMessage("第 " + course.startPeriod + "-" + course.endPeriod + " 节\n"
                        + "适用周次：" + course.weeks + "\n班级：" + course.className
                        + "\n教师：" + course.teacher + "\n地点：" + course.location)
                .setPositiveButton("编辑", (dialog, which) -> editCourse(semester, course))
                .setNeutralButton("删除", (dialog, which) -> new AlertDialog.Builder(this)
                        .setMessage("删除这条课程安排？")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("删除", (confirm, answer) -> {
                            store.deleteCourse(semester.id, course.id);
                            render();
                        }).show())
                .setNegativeButton("关闭", null).show();
    }

    private void editCourse(Timetable.Semester semester, Timetable.Course original) {
        LinearLayout form = column();
        EditText name = addField(form, "课程名称", original == null ? "" : original.name);
        String[] dayNames = DAYS;
        Spinner weekday = new Spinner(this);
        ArrayAdapter<String> days = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, dayNames);
        days.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        weekday.setAdapter(days);
        weekday.setSelection(original == null ? 0 : original.weekday - 1);
        weekday.setContentDescription("星期");
        form.addView(weekday);
        EditText start = addField(form, "开始节次 1-12", original == null ? "1" : Integer.toString(original.startPeriod));
        EditText end = addField(form, "结束节次 1-12", original == null ? "1" : Integer.toString(original.endPeriod));
        start.setInputType(InputType.TYPE_CLASS_NUMBER);
        end.setInputType(InputType.TYPE_CLASS_NUMBER);
        EditText weeks = addField(form, "适用周次，如 1-16,18", original == null ?
                Integer.toString(Math.max(1, selectedWeek)) : original.weeks.replace(',', '，'));
        EditText className = addField(form, "班级（可留空）", original == null ? "" : original.className);
        EditText teacher = addField(form, "教师（可留空）", original == null ? "" : original.teacher);
        EditText location = addField(form, "地点（可留空）", original == null ? "" : original.location);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(original == null ? "添加课程安排" : "编辑课程安排")
                .setView(wrap(form)).setNegativeButton("取消", null)
                .setPositiveButton("保存", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    try {
                        Timetable.Course edited = new Timetable.Course(original == null ? 0 : original.id,
                                semester.id, name.getText().toString(), className.getText().toString(),
                                teacher.getText().toString(), location.getText().toString(),
                                weekday.getSelectedItemPosition() + 1, number(start, "开始节次"),
                                number(end, "结束节次"), weeks.getText().toString(), true);
                        store.saveCourse(edited);
                        dialog.dismiss();
                        render();
                    } catch (IllegalArgumentException ex) { error(ex.getMessage()); }
                }));
        dialog.show();
    }

    private int number(EditText field, String label) {
        try { return Integer.parseInt(field.getText().toString().trim()); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException("请输入有效的" + label); }
    }

    private EditText field(String hint, String value) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setSingleLine(true);
        edit.setText(value);
        edit.setTextColor(Color.rgb(43, 64, 62));
        edit.setHintTextColor(Color.rgb(120, 137, 133));
        edit.setBackground(roundedSurface(Color.rgb(237, 243, 239)));
        edit.setPadding(16, 12, 16, 12);
        return edit;
    }

    private GradientDrawable roundedSurface(int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(18 * getResources().getDisplayMetrics().density);
        return shape;
    }

    private EditText addField(LinearLayout container, String hint, String value) {
        EditText result = field(hint, value);
        container.addView(result);
        return result;
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        return layout;
    }

    private LinearLayout card(LinearLayout parent, int color) {
        LinearLayout card = column();
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable shape = roundedSurface(color);
        shape.setCornerRadius(dp(24));
        shape.setStroke(dp(1), Color.rgb(221, 231, 223));
        card.setBackground(shape);
        card.setElevation(dp(2));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(14));
        parent.addView(card, params);
        return card;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private View wrap(View view) {
        ScrollView scroll = new ScrollView(this);
        scroll.setPadding(24, 8, 24, 8);
        scroll.addView(view);
        return scroll;
    }

    private void label(LinearLayout parent, String value, int sp) {
        TextView label = new TextView(this);
        label.setText(value);
        label.setTextSize(sp);
        label.setTextColor(sp >= 18 ? Color.rgb(43, 64, 62) : Color.rgb(93, 112, 108));
        if (sp >= 18) label.setTypeface(null, Typeface.BOLD);
        label.setPadding(8, 12, 8, 12);
        parent.addView(label);
    }

    private Button button(LinearLayout parent, String title, Runnable action) {
        Button button = new Button(this);
        button.setText(title);
        button.setAllCaps(false);
        button.setTextColor(Color.rgb(48, 76, 72));
        button.setBackground(roundedSurface(Color.rgb(231, 239, 234)));
        LinearLayout.LayoutParams params = parent.getOrientation() == LinearLayout.HORIZONTAL
                ? new LinearLayout.LayoutParams(0, -2, 1) : new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(2, 5, 2, 5);
        button.setLayoutParams(params);
        button.setOnClickListener(view -> action.run());
        parent.addView(button);
        return button;
    }

    private void error(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
}
