package cn.edu.swpu.offlinetimetable;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import java.util.List;
import java.util.function.Consumer;

/** Full-screen, user-driven browser; reads page structure, never login field values. */
final class SchoolImportBrowser extends Dialog {
    static final String SCHOOL_URL = "https://yjs.swpu.edu.cn/yjsxkapp/sys/xsxkapp/index.html";
    private static final String VISIBLE = "function visible(e){return !!e&&e.getClientRects().length>0"
            + "&&getComputedStyle(e).visibility!=='hidden';}";
    static final String PAGE_STATE = "(function(){" + VISIBLE
            + "if(visible(document.querySelector('#wdkbTable')))return 'timetable';"
            + "if(visible(document.querySelector('#loginName')))return 'login';"
            + "return 'browse';})()";
    static final String LOCATE_LOGIN = "(function(){" + VISIBLE
            + "var e=document.querySelector('#loginName');if(!visible(e))return false;"
            + "e.scrollIntoView({block:'center',inline:'center'});return true;})()";

    private final WebView web;
    private final TextView instruction;
    private final Button locate;
    private final Button capture;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean disposed;
    private boolean loading;
    private boolean loadFailed;
    private boolean capturing;
    private final Runnable inspect = this::inspectPage;

    SchoolImportBrowser(Activity activity, Consumer<List<Timetable.Course>> onImport) {
        super(activity, android.R.style.Theme_Material_Light_NoActionBar);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundColor(Color.WHITE);
        content.setPadding(dp(8), 0, dp(8), 0);
        web = new WebView(activity);
        TextView title = new TextView(activity);
        title.setText("西柚课表导入");
        title.setTextSize(20);
        content.addView(title);
        LinearLayout toolbar = new LinearLayout(activity);
        content.addView(toolbar);
        action(toolbar, "关闭", this::dismiss);
        action(toolbar, "返回", () -> { if (web.canGoBack()) web.goBack(); });
        action(toolbar, "刷新", () -> web.reload());
        instruction = new TextView(activity);
        instruction.setTextSize(15);
        instruction.setPadding(dp(4), dp(6), dp(4), dp(6));
        instruction.setText("正在打开西柚选课网页…");
        content.addView(instruction);
        locate = action(content, "定位账号登录", this::locateLogin);
        locate.setVisibility(android.view.View.GONE);
        web.setContentDescription("西柚选课网页");
        web.setFocusableInTouchMode(true);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        content.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        capture = action(content, "抓取课表并预览", () -> capture(onImport));
        capture.setVisibility(android.view.View.GONE);
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageStarted(WebView view, String url,
                                                 android.graphics.Bitmap favicon) {
                loading = true;
                loadFailed = false;
                handler.removeCallbacks(inspect);
                locate.setVisibility(android.view.View.GONE);
                capture.setVisibility(android.view.View.GONE);
                instruction.setText("网页加载中…");
            }

            @Override public void onPageFinished(WebView view, String url) {
                loading = false;
                handler.removeCallbacks(inspect);
                if (!loadFailed) inspectPage();
            }

            @Override public void onReceivedError(WebView view, WebResourceRequest request,
                                                   WebResourceError error) {
                if (!request.isForMainFrame()) return;
                loadFailed = true;
                handler.removeCallbacks(inspect);
                instruction.setText("网页加载失败，请检查网络后点“刷新”。");
            }
        });
        setContentView(content);
        setOnDismissListener(ignored -> {
            disposed = true;
            handler.removeCallbacksAndMessages(null);
            web.stopLoading();
            web.destroy();
        });
    }

    @Override protected void onStart() {
        super.onStart();
        Window window = getWindow();
        window.setLayout(-1, -1);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        web.loadUrl(SCHOOL_URL);
    }

    @Override public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else dismiss();
    }

    private void inspectPage() {
        if (disposed || loading || loadFailed || !isShowing()) return;
        String url = web.getUrl();
        if (!isSchoolPage(url)) {
            locate.setVisibility(android.view.View.GONE);
            capture.setVisibility(android.view.View.GONE);
            instruction.setText("步骤 1 · 请在网页完成登录，再返回西柚选课页面。");
            handler.postDelayed(inspect, 1000);
            return;
        }
        web.evaluateJavascript(PAGE_STATE, state -> {
            if (disposed || loading || !url.equals(web.getUrl())) return;
            boolean login = "\"login\"".equals(state);
            boolean timetable = "\"timetable\"".equals(state);
            locate.setVisibility(login ? android.view.View.VISIBLE : android.view.View.GONE);
            capture.setVisibility(timetable ? android.view.View.VISIBLE : android.view.View.GONE);
            instruction.setText(login
                    ? "步骤 1 · 点“定位账号登录”，双指放大后输入学号、密码和验证码。"
                    : timetable ? "步骤 3 · 课表已打开，请抓取并核对预览。"
                    : "步骤 2 · 请在网页中打开“我的课表”弹窗。网页可双指缩放。");
            handler.postDelayed(inspect, 1000);
        });
    }

    private void locateLogin() {
        if (!isSchoolPage(web.getUrl())) return;
        String url = web.getUrl();
        // Bring a desktop form close to its natural size before centering it.
        float naturalScale = getContext().getResources().getDisplayMetrics().density;
        float factor = naturalScale / web.getScale();
        if (factor > 1.1f) web.zoomBy(Math.min(4f, factor));
        handler.postDelayed(() -> {
            if (disposed || loading || !url.equals(web.getUrl())) return;
            web.evaluateJavascript(LOCATE_LOGIN, found -> {
                if (!disposed && !"true".equals(found)) {
                    toast("未找到账号登录区域，请在网页中查找或刷新后重试。");
                }
            });
        }, 250);
    }

    private void capture(Consumer<List<Timetable.Course>> onImport) {
        if (capturing || !isSchoolPage(web.getUrl())) return;
        String url = web.getUrl();
        capturing = true;
        capture.setEnabled(false);
        web.evaluateJavascript(SchoolImport.EXTRACT, value -> {
            if (disposed) return;
            capturing = false;
            capture.setEnabled(true);
            if (loading || !url.equals(web.getUrl())) return;
            try {
                String json = new JSONArray("[" + value + "]").getString(0);
                List<Timetable.Course> courses = SchoolImport.parse(json);
                dismiss();
                onImport.accept(courses);
            } catch (Exception ex) { toast(ex.getMessage()); }
        });
    }

    private static boolean isSchoolPage(String url) {
        if (url == null) return false;
        Uri uri = Uri.parse(url);
        return "https".equals(uri.getScheme()) && "yjs.swpu.edu.cn".equals(uri.getHost())
                && (uri.getPort() == -1 || uri.getPort() == 443);
    }

    private Button action(LinearLayout parent, String text, Runnable run) {
        Button button = new Button(getContext());
        button.setText(text);
        button.setAllCaps(false);
        boolean horizontal = parent.getOrientation() == LinearLayout.HORIZONTAL;
        parent.addView(button, horizontal ? new LinearLayout.LayoutParams(0, -2, 1)
                : new LinearLayout.LayoutParams(-1, -2));
        button.setOnClickListener(ignored -> run.run());
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getContext().getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
    }
}
