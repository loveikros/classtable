package cn.edu.swpu.offlinetimetable;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Full-width seven-day grid with a separate fixed weekday header. */
final class TimetableGridView extends View {
    interface OnEntryClick { void onClick(int weekday, Timetable.DayEntry entry); }
    interface OnWeekSwipe { void onSwipe(int direction); }
    private static final String[] DAYS = {"一", "二", "三", "四", "五", "六", "日"};
    private static final int[] FILL = {0xFFE7EFF1, 0xFFEEEAF7, 0xFFE8F1E9, 0xFFF5EDE4};
    private static final int[] INK = {0xFF476E78, 0xFF6B5C89, 0xFF4F765A, 0xFF876C4D};
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Hit> hits = new ArrayList<>();
    private final List<Timetable.DayEntry>[] days;
    private final String[] periods;
    private final OnEntryClick listener;
    private final OnWeekSwipe weekSwipe;
    private final boolean headerOnly;
    private final int highlightedDay;
    private final LocalDate monday;
    private final int axis, row, header, gap;
    private float column;
    private float downX, downY;

    @SuppressWarnings("unchecked")
    TimetableGridView(Context context, List<Timetable.DayEntry>[] days, String schedule,
                      LocalDate monday, int highlightedDay, boolean headerOnly,
                      OnEntryClick listener, OnWeekSwipe weekSwipe) {
        super(context);
        this.days = days;
        this.periods = TodayWidget.validateSchedule(schedule).split(",");
        this.listener = listener;
        this.weekSwipe = weekSwipe;
        this.headerOnly = headerOnly;
        this.highlightedDay = highlightedDay;
        this.monday = monday;
        float density = getResources().getDisplayMetrics().density;
        axis = Math.round(38 * density);
        row = Math.round(68 * density);
        header = Math.round(60 * density);
        gap = Math.max(1, Math.round(density));
        setClickable(true);
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), headerOnly ? header : 12 * row);
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        column = (width - axis) / 7f;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.WHITE);
        hits.clear();
        if (headerOnly) drawHeader(canvas);
        else drawCourses(canvas);
    }

    private void drawHeader(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFF0F4F0);
        canvas.drawRect(axis, 0, axis + 7 * column, header, paint);
        if (highlightedDay >= 1 && highlightedDay <= 7) {
            paint.setColor(0xFFCAE2D3);
            float left = axis + (highlightedDay - 1) * column;
            canvas.drawRect(left, 0, left + column, header, paint);
        }
        paint.setTextSize(Math.min(sp(15), column * 0.55f));
        paint.setFakeBoldText(true);
        paint.setColor(0xFF465F59);
        for (int day = 0; day < 7; day++) {
            float centerX = axis + day * column + column / 2f;
            centeredLabel(canvas, DAYS[day], centerX, header * 0.28f);
            paint.setTextSize(Math.min(sp(13), column * 0.40f));
            paint.setFakeBoldText(false);
            centeredLabel(canvas, Integer.toString(monday.plusDays(day).getDayOfMonth()),
                    centerX, header * 0.73f);
            paint.setTextSize(Math.min(sp(15), column * 0.55f));
            paint.setFakeBoldText(true);
        }
        paint.setFakeBoldText(false);
        paint.setColor(0xFFE7EEE8);
        canvas.drawRect(0, 0, axis, header, paint);
        paint.setColor(0xFFDDE8DF);
        canvas.drawLine(axis, 0, axis, header, paint);
        canvas.drawLine(0, header - 1, getWidth(), header - 1, paint);
    }

    private void drawCourses(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFF7F9F6);
        canvas.drawRect(0, 0, axis, 12 * row, paint);
        if (highlightedDay >= 1 && highlightedDay <= 7) {
            paint.setColor(0xFFF1F8F2);
            float left = axis + (highlightedDay - 1) * column;
            canvas.drawRect(left, 0, left + column, 12 * row, paint);
        }
        for (int period = 0; period < 12; period++) {
            int top = period * row;
            paint.setColor(0xFFE7ECE8);
            paint.setStrokeWidth(1);
            canvas.drawLine(0, top, getWidth(), top, paint);
            canvas.save();
            canvas.clipRect(0, top, axis, top + row);
            paint.setColor(0xFF516C65);
            paint.setTextSize(Math.min(sp(15), axis * 0.40f));
            paint.setFakeBoldText(true);
            centeredLabel(canvas, Integer.toString(period + 1), axis / 2f, top + row * 0.25f);
            paint.setFakeBoldText(false);
            paint.setColor(0xFF84918D);
            paint.setTextSize(Math.min(sp(10), axis * 0.25f));
            String[] times = periods[period].split("-");
            centeredLabel(canvas, times[0], axis / 2f, top + row * 0.57f);
            centeredLabel(canvas, times[1], axis / 2f, top + row * 0.80f);
            canvas.restore();
        }
        paint.setColor(0xFFE7ECE8);
        canvas.drawLine(0, 12 * row - 1, getWidth(), 12 * row - 1, paint);
        for (int day = 0; day < 7; day++) {
            List<Timetable.DayEntry> entries = days[day];
            if (entries == null) continue;
            for (Timetable.DayEntry entry : entries) {
                if (entry.exception != null && entry.exception.kind == Timetable.DateException.Kind.CANCEL) continue;
                float left = axis + day * column + gap;
                float top = (entry.startPeriod - 1) * row + gap;
                float right = axis + (day + 1) * column - gap;
                float bottom = entry.endPeriod * row - gap;
                RectF bounds = new RectF(left, top, right, bottom);
                int color = (entry.name.hashCode() & Integer.MAX_VALUE) % FILL.length;
                paint.setColor(FILL[color]);
                canvas.drawRoundRect(bounds, dp(12), dp(12), paint);
                canvas.save();
                canvas.clipRect(bounds);
                paint.setColor(INK[color]);
                paint.setTextSize(sp(10));
                paint.setFakeBoldText(true);
                float textY = top + sp(12);
                textY = drawWrapped(canvas, entry.name, left + gap * 2, textY,
                        right - gap * 2, bottom - sp(5), sp(11));
                paint.setFakeBoldText(false);
                if (!entry.location.isEmpty() && textY + sp(9) < bottom) {
                    paint.setTextSize(sp(8));
                    drawWrapped(canvas, entry.location, left + gap * 2, textY + sp(2),
                            right - gap * 2, bottom - sp(4), sp(9));
                }
                canvas.restore();
                hits.add(new Hit(bounds, day + 1, entry));
            }
        }
        if (highlightedDay >= 1 && highlightedDay <= 7) {
            paint.setColor(0xFF92B9A0);
            paint.setStrokeWidth(dp(2));
            float left = axis + (highlightedDay - 1) * column;
            canvas.drawLine(left, 0, left, 12 * row, paint);
            canvas.drawLine(left + column, 0, left + column, 12 * row, paint);
        }
        paint.setColor(0xFFDDE8DF);
        canvas.drawLine(axis, 0, axis, getHeight(), paint);
    }

    private float drawWrapped(Canvas canvas, String text, float x, float y, float right,
                              float bottom, float lineHeight) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char next = text.charAt(i);
            if (paint.measureText(line.toString() + next) > right - x && line.length() > 0) {
                if (y + lineHeight > bottom && i < text.length()) {
                    String visible = line.toString();
                    while (!visible.isEmpty() && paint.measureText(visible + "…") > right - x)
                        visible = visible.substring(0, visible.length() - 1);
                    canvas.drawText(visible + "…", x, y, paint);
                    return bottom + lineHeight;
                }
                canvas.drawText(line.toString(), x, y, paint);
                y += lineHeight;
                line.setLength(0);
            }
            line.append(next);
        }
        if (line.length() > 0 && y <= bottom) {
            canvas.drawText(line.toString(), x, y, paint);
            y += lineHeight;
        }
        return y;
    }

    private void center(Canvas canvas, String text, float centerX, float baseline) {
        canvas.drawText(text, centerX - paint.measureText(text) / 2, baseline, paint);
    }

    private void centeredLabel(Canvas canvas, String text, float centerX, float centerY) {
        Paint.FontMetrics metrics = paint.getFontMetrics();
        center(canvas, text, centerX, centerY - (metrics.ascent + metrics.descent) / 2);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX(); downY = event.getY();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (Math.abs(event.getX() - downX) > dp(12)
                        && Math.abs(event.getX() - downX) > Math.abs(event.getY() - downY))
                    getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_UP:
                float dx = event.getX() - downX;
                if (Math.abs(dx) > dp(48) && Math.abs(dx) > Math.abs(event.getY() - downY) * 1.2f) {
                    weekSwipe.onSwipe(dx < 0 ? 1 : -1);
                    return true;
                }
                if (Math.abs(event.getX() - downX) < dp(6)
                        && Math.abs(event.getY() - downY) < dp(6)
                        && !headerOnly && event.getX() >= axis) {
                    for (int i = hits.size() - 1; i >= 0; i--) {
                        Hit hit = hits.get(i);
                        if (hit.bounds.contains(event.getX(), event.getY())) {
                            listener.onClick(hit.weekday, hit.entry);
                            performClick();
                            return true;
                        }
                    }
                }
                performClick();
                return true;
            case MotionEvent.ACTION_CANCEL:
                return true;
            default:
                return true;
        }
    }

    @Override public boolean performClick() { super.performClick(); return true; }

    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
    private float sp(float value) { return value * getResources().getDisplayMetrics().scaledDensity; }

    private static final class Hit {
        final RectF bounds;
        final int weekday;
        final Timetable.DayEntry entry;
        Hit(RectF bounds, int weekday, Timetable.DayEntry entry) {
            this.bounds = bounds; this.weekday = weekday; this.entry = entry;
        }
    }
}
