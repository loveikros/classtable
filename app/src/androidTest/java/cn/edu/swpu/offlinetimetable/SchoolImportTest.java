package cn.edu.swpu.offlinetimetable;

import android.webkit.WebView;
import android.view.WindowManager;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.instanceOf;

@RunWith(AndroidJUnit4.class)
public final class SchoolImportTest {
    @Test public void importDialogShowsSchoolWebPageArea() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withText("从学校网页导入")).perform(scrollTo(), click());
            onView(withText("抓取课表并预览")).check(
                    androidx.test.espresso.assertion.ViewAssertions.matches(
                            androidx.test.espresso.matcher.ViewMatchers.isDisplayed()));
            onView(instanceOf(WebView.class)).check((view, error) -> {
                if (error != null) throw error;
                assertTrue("学校网页区域必须可见", view.getHeight() > 200);
                int flags = ((WindowManager.LayoutParams) view.getRootView().getLayoutParams()).flags;
                assertEquals("学校网页输入框必须能唤起键盘", 0,
                        flags & WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
            });
        }
    }
    // De-identified excerpt of the saved #wdkbTable popup: two courses in one cell,
    // repeated across consecutive periods, and a course without a location.
    private static final String HTML = "<table id='wdkbTable'><tr>"
            + "<td data-role='item' data-week='2' data-begin-unit='6' data-end-unit='6'>"
            + block("3-12周", "数值分析(数值分析05)", "教师甲", "教室A")
            + block("13-15周", "论文写作(论文写作02)", "教师乙", "教室B")
            + "</td></tr><tr><td data-role='item' data-week='2' data-begin-unit='7' data-end-unit='7'>"
            + block("3-12周", "数值分析(数值分析05)", "教师甲", "教室A")
            + block("13-15周", "论文写作(论文写作02)", "教师乙", "教室B")
            + "</td>"
            + cell(6, 7, "1周", "入学教育(班级一)", "教师丙", "")
            + "</tr></table>";

    private static String cell(int day, int period, String weeks, String name, String teacher, String location) {
        return "<td data-role='item' data-week='" + day + "' data-begin-unit='" + period
                + "' data-end-unit='" + period + "'>" + block(weeks, name, teacher, location) + "</td>";
    }

    private static String block(String weeks, String name, String teacher, String location) {
        return "<div><div>" + weeks + "</div><div><b>" + name
                + "</b></div><div>" + teacher + "</div><div>" + location + "</div></div>";
    }

    @Test public void renderedPopupImportsSeparateWeeksAndMergesPeriods() throws Exception {
        ApplicationProvider.getApplicationContext().deleteDatabase("timetable.db");
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            AtomicReference<WebView> web = new AtomicReference<>();
            AtomicReference<String> extracted = new AtomicReference<>();
            CountDownLatch ready = new CountDownLatch(1);
            scenario.onActivity(activity -> {
                WebView view = new WebView(activity);
                view.getSettings().setJavaScriptEnabled(true);
                web.set(view);
                view.setWebViewClient(new android.webkit.WebViewClient() {
                    @Override public void onPageFinished(WebView v, String url) {
                        v.evaluateJavascript(SchoolImport.EXTRACT, value -> {
                            extracted.set(value);
                            ready.countDown();
                        });
                    }
                });
                view.loadDataWithBaseURL("https://yjs.swpu.edu.cn/", HTML, "text/html", "UTF-8", null);
            });
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            String json = new JSONArray("[" + extracted.get() + "]").getString(0);
            List<Timetable.Course> courses = SchoolImport.parse(json);
            assertEquals(3, courses.size());
            assertEquals(6, courses.get(0).startPeriod);
            assertEquals(7, courses.get(0).endPeriod);
            assertEquals("3,4,5,6,7,8,9,10,11,12", courses.get(0).weeks);
            assertEquals("", courses.get(2).location);
            scenario.onActivity(activity -> {
                try (TimetableStore store = new TimetableStore(activity)) {
                    long id = store.importSemester("样本学期", courses);
                    assertEquals(id, store.activeSemesterId());
                    assertEquals(3, store.courses(id).size());
                    assertFalse(store.courses(id).get(0).manuallyModified);
                }
            });
            scenario.onActivity(activity -> web.get().destroy());
        }
    }
}
