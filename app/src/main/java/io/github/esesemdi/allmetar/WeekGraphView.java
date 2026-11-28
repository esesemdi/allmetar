package io.github.esesemdi.allmetar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.view.View;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import io.github.esesemdi.allmetar.core.Conditions;
import io.github.esesemdi.allmetar.core.Graph;

/**
 * Ceiling (top) and temperature (bottom) over the past week, with the ceiling the TAF forecasts
 * continuing to the right of "now". The ceiling scale is logarithmic so that low ceilings, the
 * ones that matter, take up most of the height.
 */
final class WeekGraphView extends View {
    private static final int[] CEILING_GRID = {500, 1000, 3000};
    private static final double LOG_MIN = 2; // 100 ft
    private static final double LOG_MAX = 4; // 10,000 ft
    /** Observations further apart than this are not joined by a line (seconds). */
    private static final long MAX_GAP = 2 * 3600 + 900;

    private static final DateTimeFormatter DAY_LONG = DateTimeFormatter.ofPattern("EEE d", Locale.US)
            .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DAY_SHORT = DateTimeFormatter.ofPattern("d", Locale.US)
            .withZone(ZoneOffset.UTC);

    private Graph graph;
    private final float dp;
    private final Paint observed = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint forecast = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint temporary = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint day = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint frame = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nowLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint band = new Paint();
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint title = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    WeekGraphView(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        float sp = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 1f,
                c.getResources().getDisplayMetrics());

        observed.setStyle(Paint.Style.STROKE);
        observed.setStrokeWidth(1.6f * dp);
        observed.setColor(Ui.FG);
        observed.setStrokeJoin(Paint.Join.ROUND);

        forecast.set(observed);
        forecast.setPathEffect(new DashPathEffect(new float[]{5 * dp, 3 * dp}, 0));

        temporary.set(observed);
        temporary.setStrokeWidth(1.2f * dp);
        temporary.setColor(Ui.DIM);
        temporary.setPathEffect(new DashPathEffect(new float[]{1.5f * dp, 2.5f * dp}, 0));

        grid.setStyle(Paint.Style.STROKE);
        grid.setStrokeWidth(Math.max(1f, 0.75f * dp));
        grid.setColor(0xFF4A4A4A);
        grid.setPathEffect(new DashPathEffect(new float[]{2 * dp, 3 * dp}, 0));

        day.setStyle(Paint.Style.STROKE);
        day.setStrokeWidth(Math.max(1f, 0.75f * dp));
        day.setColor(Ui.LINE);

        frame.setStyle(Paint.Style.STROKE);
        frame.setStrokeWidth(Math.max(1f, 0.75f * dp));
        frame.setColor(0xFF4A4A4A);

        nowLine.setStyle(Paint.Style.STROKE);
        nowLine.setStrokeWidth(Math.max(1f, dp));
        nowLine.setColor(Ui.DIM);

        band.setStyle(Paint.Style.FILL);
        band.setColor((ReportStyle.RED & 0x00FFFFFF) | 0x33000000);

        label.setColor(Ui.DIM);
        label.setTextSize(9.5f * sp);
        label.setTypeface(Typeface.MONOSPACE);

        title.setColor(Ui.DIM);
        title.setTextSize(9.5f * sp);
        title.setLetterSpacing(0.12f);
        setContentDescription("Graph of ceiling and temperature");
    }

    void setGraph(Graph g) {
        graph = g;
        requestLayout();
        invalidate();
    }

    private boolean showTemperature() {
        return graph != null && graph.hasTemperature();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        float h = 16 + 132 + 20;       // title, ceiling panel, day labels
        if (showTemperature()) {
            h += 24 + 72;              // title, temperature panel
        }
        setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), widthSpec), Math.round(h * dp));
    }

    @Override
    protected void onDraw(Canvas c) {
        Graph g = graph;
        if (g == null || g.end <= g.start) {
            return;
        }
        float left = 36 * dp;
        float right = getWidth() - 2 * dp;
        float ceilTop = 16 * dp;
        float ceilBottom = ceilTop + 132 * dp;
        boolean temps = showTemperature();
        float tempTop = ceilBottom + 24 * dp;
        float tempBottom = tempTop + 72 * dp;
        float plotBottom = temps ? tempBottom : ceilBottom;

        // ---- days
        float dayWidth = (right - left) * 86400f / (g.end - g.start);
        long firstDay = (g.start / 86400) * 86400;
        for (long d = firstDay; d < g.end; d += 86400) {
            if (d > g.start) {
                float x = x(g, d, left, right);
                c.drawLine(x, ceilTop, x, plotBottom, day);
            }
            long from = Math.max(d, g.start);
            long to = Math.min(d + 86400, g.end);
            String text = (dayWidth > 44 * dp ? DAY_LONG : DAY_SHORT).format(Instant.ofEpochSecond(d))
                    .toUpperCase(Locale.US);
            float w = label.measureText(text);
            float span = x(g, to, left, right) - x(g, from, left, right);
            if (span >= w + 2 * dp) {
                c.drawText(text, x(g, from, left, right) + (span - w) / 2, plotBottom + 14 * dp, label);
            }
        }

        // ---- ceiling panel
        c.drawText("CEILING · FT ABOVE GROUND", left, ceilTop - 5 * dp, title);
        c.drawRect(left, ceilY(500, ceilTop, ceilBottom), right, ceilBottom, band);
        c.drawRect(left, ceilTop, right, ceilBottom, frame);
        for (int ft : CEILING_GRID) {
            float y = ceilY(ft, ceilTop, ceilBottom);
            path.rewind();
            path.moveTo(left, y);
            path.lineTo(right, y);
            c.drawPath(path, grid);
            yLabel(c, String.valueOf(ft), left, y);
        }
        yLabel(c, "NONE", left, ceilTop + 4 * dp);
        yLabel(c, "100", left, ceilBottom - 4 * dp);

        // observed ceiling: steps, each value held until the next report
        path.rewind();
        boolean drawing = false;
        List<Graph.Point> obs = g.observed;
        for (int i = 0; i < obs.size(); i++) {
            Graph.Point p = obs.get(i);
            if (p.ceilingFt == Conditions.UNKNOWN) {
                drawing = false;
                continue;
            }
            float x0 = x(g, p.time, left, right);
            float y = ceilY(p.ceilingFt, ceilTop, ceilBottom);
            if (drawing) {
                path.lineTo(x0, y);
            } else {
                path.moveTo(x0, y);
            }
            long next = i + 1 < obs.size() ? obs.get(i + 1).time : g.now;
            long until = Math.min(next, p.time + MAX_GAP);
            path.lineTo(x(g, Math.min(until, g.now), left, right), y);
            drawing = next - p.time <= MAX_GAP;
        }
        c.drawPath(path, observed);

        // forecast ceiling
        path.rewind();
        for (int i = 0; i < g.forecast.size(); i++) {
            Graph.Span s = g.forecast.get(i);
            float y = ceilY(s.ceilingFt, ceilTop, ceilBottom);
            float x0 = x(g, s.from, left, right);
            if (i == 0) {
                path.moveTo(x0, y);
            } else {
                path.lineTo(x0, y);
            }
            path.lineTo(x(g, s.to, left, right), y);
        }
        c.drawPath(path, forecast);
        for (Graph.Span s : g.temporary) {
            float y = ceilY(s.ceilingFt, ceilTop, ceilBottom);
            path.rewind();
            path.moveTo(x(g, s.from, left, right), y);
            path.lineTo(x(g, s.to, left, right), y);
            c.drawPath(path, temporary);
        }

        // ---- temperature panel
        if (temps) {
            int min = Integer.MAX_VALUE;
            int max = Integer.MIN_VALUE;
            for (Graph.Point p : obs) {
                if (p.tempC != Conditions.NO_TEMP) {
                    min = Math.min(min, p.tempC);
                    max = Math.max(max, p.tempC);
                }
            }
            int lo = (int) Math.floor((min - 1) / 5.0) * 5;
            int hi = (int) Math.ceil((max + 1) / 5.0) * 5;
            if (hi - lo < 10) {
                hi = lo + 10;
            }
            c.drawText("TEMPERATURE · °C", left, tempTop - 5 * dp, title);
            c.drawRect(left, tempTop, right, tempBottom, frame);
            int step = hi - lo <= 10 ? 5 : hi - lo <= 30 ? 10 : 20;
            for (int t = (int) Math.ceil(lo / (double) step) * step; t < hi; t += step) {
                if (t <= lo) {
                    continue;
                }
                float y = tempY(t, lo, hi, tempTop, tempBottom);
                path.rewind();
                path.moveTo(left, y);
                path.lineTo(right, y);
                c.drawPath(path, grid);
                if (y - tempTop > 13 * dp && tempBottom - y > 13 * dp) {
                    yLabel(c, degrees(t), left, y);
                }
            }
            yLabel(c, degrees(hi), left, tempTop + 4 * dp);
            yLabel(c, degrees(lo), left, tempBottom - 4 * dp);
            path.rewind();
            long last = 0;
            for (Graph.Point p : obs) {
                if (p.tempC == Conditions.NO_TEMP) {
                    continue;
                }
                float px = x(g, p.time, left, right);
                float py = tempY(p.tempC, lo, hi, tempTop, tempBottom);
                if (last != 0 && p.time - last <= MAX_GAP) {
                    path.lineTo(px, py);
                } else {
                    path.moveTo(px, py);
                    path.lineTo(px + 0.5f * dp, py); // a lone report still leaves a dot
                }
                last = p.time;
            }
            c.drawPath(path, observed);
        }

        // ---- now
        float xn = x(g, g.now, left, right);
        c.drawLine(xn, ceilTop, xn, plotBottom, nowLine);
        String nowText = "NOW";
        float nw = title.measureText(nowText);
        c.drawText(nowText, Math.min(xn - nw / 2, right - nw), ceilTop - 5 * dp, title);
    }

    private static String degrees(int t) {
        return (t < 0 ? "−" : "") + Math.abs(t);
    }

    private void yLabel(Canvas c, String text, float left, float y) {
        float w = label.measureText(text);
        c.drawText(text, left - w - 4 * dp, y + label.getTextSize() * 0.35f, label);
    }

    private static float x(Graph g, long t, float left, float right) {
        double f = (t - g.start) / (double) (g.end - g.start);
        f = Math.max(0, Math.min(1, f));
        return (float) (left + f * (right - left));
    }

    /** Logarithmic: 100 ft at the bottom, 10,000 ft or no ceiling at the top. */
    private static float ceilY(int ft, float top, float bottom) {
        if (ft == Conditions.NO_CEILING) {
            return top;
        }
        double log = Math.log10(Math.max(100, Math.min(10000, ft)));
        double f = (log - LOG_MIN) / (LOG_MAX - LOG_MIN);
        return (float) (bottom - f * (bottom - top));
    }

    private static float tempY(int t, int lo, int hi, float top, float bottom) {
        double f = (t - lo) / (double) (hi - lo);
        return (float) (bottom - f * (bottom - top));
    }
}
