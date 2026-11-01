package cn.edu.swpu.offlinetimetable;

import android.content.Context;
import android.view.View;
import android.widget.DatePicker;
import android.widget.Spinner;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.LocalDate;

import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withHint;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.Matchers.instanceOf;

@RunWith(AndroidJUnit4.class)
public final class TimetableWorkflowTest {
    @Before public void clearLocalData() {
        Context context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase("timetable.db");
    }

    @Test public void currentSemesterAndAnchorSurviveRestart() {
        try (ActivityScenario<MainActivity> app = ActivityScenario.launch(MainActivity.class)) {
            createSemester("秋季学期");
            onView(withText("设置周次锚点")).perform(click());
            onView(withHint("该日期所属学期周次")).perform(replaceText("7"));
            onView(withText("保存")).perform(click());
            onView(withText("第 7 周")).check(matches(isDisplayed()));
            app.recreate();
            onView(withText("第 7 周")).check(matches(isDisplayed()));
            onView(withText("重新校准周次锚点")).check(matches(isDisplayed()));
            onView(withText("添加课程安排")).perform(scrollTo(), click());
            onView(withHint("课程名称")).perform(replaceText("化学"));
            onView(withText("保存")).perform(click());
            onView(withText("1-1 节 · 化学 · 手动修改")).perform(scrollTo()).check(matches(isDisplayed()));
        }
        try (ActivityScenario<MainActivity> reopened = ActivityScenario.launch(MainActivity.class)) {
            onView(withText("第 7 周")).check(matches(isDisplayed()));
            onView(withText("1-1 节 · 化学 · 手动修改")).perform(scrollTo()).check(matches(isDisplayed()));
        }
    }

    @Test public void currentWeekAdvancesAfterCrossingMonday() {
        try (ActivityScenario<MainActivity> app = ActivityScenario.launch(MainActivity.class)) {
            createSemester("跨周学期");
            onView(withText("设置周次锚点")).perform(click());
            onView(withText(startsWith("日期："))).perform(click());
            LocalDate previousWeek = LocalDate.now().minusWeeks(1);
            onView(instanceOf(DatePicker.class)).perform(new ViewAction() {
                @Override public org.hamcrest.Matcher<View> getConstraints() {
                    return instanceOf(DatePicker.class);
                }
                @Override public String getDescription() { return "选择上周同一天"; }
                @Override public void perform(UiController controller, View view) {
                    ((DatePicker) view).updateDate(previousWeek.getYear(),
                            previousWeek.getMonthValue() - 1, previousWeek.getDayOfMonth());
                }
            });
            onView(withId(android.R.id.button1)).perform(click());
            onView(withHint("该日期所属学期周次")).perform(replaceText("7"));
            onView(withText("保存")).perform(click());
            onView(withText("第 8 周")).check(matches(isDisplayed()));
        }
    }

    @Test public void switchingSemestersAndEditingCoursesChangesTheVisibleWeek() {
        try (ActivityScenario<MainActivity> app = ActivityScenario.launch(MainActivity.class)) {
            createSemester("第一学期");
            onView(withText("添加课程安排")).perform(scrollTo(), click());
            onView(withHint("课程名称")).perform(replaceText("数学"));
            onView(withHint("适用周次，如 1-16,18")).perform(replaceText("1,3"));
            onView(withText("保存")).perform(click());
            onView(withText("1-1 节 · 数学 · 手动修改")).perform(scrollTo()).check(matches(isDisplayed()));
            onView(withText("下一周")).perform(scrollTo(), click());
            onView(withText("1-1 节 · 数学 · 手动修改")).check(doesNotExist());
            onView(withText("下一周")).perform(scrollTo(), click());
            onView(withText("1-1 节 · 数学 · 手动修改")).perform(scrollTo()).check(matches(isDisplayed()));
            onView(withText("1-1 节 · 数学 · 手动修改")).perform(scrollTo(), click());
            onView(withText("编辑")).perform(click());
            onView(withHint("课程名称")).perform(replaceText("物理"));
            onView(withText("保存")).perform(click());
            onView(withText("1-1 节 · 物理 · 手动修改")).perform(scrollTo()).check(matches(isDisplayed()));
            onView(withText("新建学期")).perform(scrollTo(), click());
            onView(withHint("学期名称，例如 2026 秋")).perform(replaceText("第二学期"));
            onView(withText("创建")).perform(click());
            onView(withText("1-1 节 · 物理 · 手动修改")).check(doesNotExist());
            onView(withContentDescription("切换学期")).perform(click());
            onData(is("第一学期")).perform(click());
            onView(withText("1-1 节 · 物理 · 手动修改")).perform(scrollTo()).check(matches(isDisplayed()));
            onView(withText("下一周")).perform(scrollTo(), click());
            onView(withText("1-1 节 · 物理 · 手动修改")).check(doesNotExist());
            onView(withText("下一周")).perform(scrollTo(), click());
            onView(withText("1-1 节 · 物理 · 手动修改")).perform(scrollTo()).check(matches(isDisplayed()));
            app.recreate();
            onView(withText("第 1 周")).check(matches(isDisplayed()));
            onView(withText("下一周")).perform(scrollTo(), click());
            onView(withText("下一周")).perform(scrollTo(), click());
            onView(withText("1-1 节 · 物理 · 手动修改")).perform(scrollTo()).check(matches(isDisplayed()));
            onView(withText("1-1 节 · 物理 · 手动修改")).perform(scrollTo(), click());
            onView(withText("删除")).perform(click());
            onView(withText("删除")).perform(click());
            onView(withText("1-1 节 · 物理 · 手动修改")).check(doesNotExist());
            app.recreate();
            onView(withText("下一周")).perform(scrollTo(), click());
            onView(withText("下一周")).perform(scrollTo(), click());
            onView(withText("1-1 节 · 物理 · 手动修改")).check(doesNotExist());
        }
    }

    @Test public void dateExceptionsPersistAndLeaveNextWeekUnchanged() {
        LocalDate today = LocalDate.now();
        try (ActivityScenario<MainActivity> app = ActivityScenario.launch(MainActivity.class)) {
            createSemester("日期例外学期");
            onView(withText("设置周次锚点")).perform(click());
            onView(withText("保存")).perform(click());
            addCourseForToday("数学", "原教室", today, 1);
            addCourseForToday("物理", "旧教室", today, 3);
            onView(withText("选择日期")).perform(scrollTo(), click());
            onView(withId(android.R.id.button1)).perform(click());
            onView(withText("1-1 节 · 数学 · 原教室 · 手动修改"))
                    .perform(scrollTo(), click());
            onView(withText("设为当天停课")).perform(click());
            onView(withText("保存")).perform(click());
            onView(withText("3-3 节 · 物理 · 旧教室 · 手动修改"))
                    .perform(scrollTo(), click());
            onView(withText("修改当天信息")).perform(click());
            onView(withHint("地点（可留空）")).perform(replaceText("新教室"));
            onView(withText("保存")).perform(click());
            onView(withText("新增当天补课")).perform(scrollTo(), click());
            onView(withHint("课程名称")).perform(replaceText("化学补课"));
            onView(withText("保存")).perform(click());
            onView(withText("1-1 节 · 数学 · 原教室 · 停课 · 日期例外"))
                    .perform(scrollTo()).check(matches(isDisplayed()));
            onView(withText("3-3 节 · 物理 · 新教室 · 信息变更 · 日期例外"))
                    .perform(scrollTo()).check(matches(isDisplayed()));
            onView(withText("3-3 节 · 物理 · 新教室 · 信息变更 · 日期例外"))
                    .perform(scrollTo(), click());
            onView(withText("编辑日期例外")).perform(click());
            onView(withHint("地点（可留空）")).perform(replaceText("更新教室"));
            onView(withText("保存")).perform(click());
            onView(withText("3-3 节 · 物理 · 更新教室 · 信息变更 · 日期例外"))
                    .perform(scrollTo()).check(matches(isDisplayed()));
            onView(withText("1-1 节 · 化学补课 · 补课 · 日期例外"))
                    .perform(scrollTo()).check(matches(isDisplayed()));
            onView(withText("返回整周")).perform(scrollTo(), click());
            onView(withText("3-3 节 · 物理 · 更新教室 · 信息变更 · 日期例外"))
                    .perform(scrollTo()).check(matches(isDisplayed()));
            onView(withText("选择日期")).perform(scrollTo(), click());
            onView(withId(android.R.id.button1)).perform(click());
            app.recreate();
            onView(withText("选择日期")).perform(scrollTo(), click());
            onView(withId(android.R.id.button1)).perform(click());
            onView(withText("1-1 节 · 数学 · 原教室 · 停课 · 日期例外"))
                    .perform(scrollTo()).check(matches(isDisplayed()));
            onView(withText("1-1 节 · 数学 · 原教室 · 停课 · 日期例外"))
                    .perform(scrollTo(), click());
            onView(withText("删除日期例外")).perform(click());
            onView(withText("删除")).perform(click());
            onView(withText("1-1 节 · 数学 · 原教室 · 手动修改"))
                    .perform(scrollTo()).check(matches(isDisplayed()));
            onView(withText("选择日期")).perform(scrollTo(), click());
            onView(instanceOf(DatePicker.class)).perform(new ViewAction() {
                @Override public org.hamcrest.Matcher<View> getConstraints() {
                    return instanceOf(DatePicker.class);
                }
                @Override public String getDescription() { return "选择下周同一天"; }
                @Override public void perform(UiController controller, View view) {
                    LocalDate nextWeek = today.plusWeeks(1);
                    ((DatePicker) view).updateDate(nextWeek.getYear(),
                            nextWeek.getMonthValue() - 1, nextWeek.getDayOfMonth());
                }
            });
            onView(withId(android.R.id.button1)).perform(click());
            onView(withText("3-3 节 · 物理 · 旧教室 · 手动修改"))
                    .perform(scrollTo()).check(matches(isDisplayed()));
            onView(withText("1-1 节 · 化学补课 · 补课 · 日期例外"))
                    .check(doesNotExist());
        }
    }

    private void addCourseForToday(String name, String location, LocalDate today, int period) {
        onView(withText("添加课程安排")).perform(scrollTo(), click());
        onView(withHint("课程名称")).perform(replaceText(name));
        onView(withContentDescription("星期")).perform(new ViewAction() {
            @Override public org.hamcrest.Matcher<View> getConstraints() {
                return instanceOf(Spinner.class);
            }
            @Override public String getDescription() { return "选择当天星期"; }
            @Override public void perform(UiController controller, View view) {
                ((Spinner) view).setSelection(today.getDayOfWeek().getValue() - 1);
            }
        });
        onView(withHint("开始节次 1-12")).perform(replaceText(Integer.toString(period)));
        onView(withHint("结束节次 1-12")).perform(replaceText(Integer.toString(period)));
        onView(withHint("适用周次，如 1-16,18")).perform(replaceText("1,2"));
        onView(withHint("地点（可留空）")).perform(replaceText(location));
        onView(withText("保存")).perform(click());
    }

    private void createSemester(String name) {
        onView(withText("新建学期")).perform(click());
        onView(withHint("学期名称，例如 2026 秋")).perform(replaceText(name));
        onView(withText("创建")).perform(click());
    }
}
