package io.github.esesemdi.allmetar;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Small helpers for building the black-and-white screens in code. */
final class Ui {
    static final int BG = 0xFF000000;
    static final int FG = 0xFFFFFFFF;
    static final int DIM = 0xFF9E9E9E;
    static final int LINE = 0xFF2B2B2B;

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm'Z'", Locale.US)
            .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)
            .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DAY_HM = DateTimeFormatter.ofPattern("d MMM HH:mm'Z'", Locale.US)
            .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HHmm'Z'", Locale.US)
            .withZone(ZoneOffset.UTC);

    private Ui() {
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static TextView text(Context c, CharSequence s, float sp, int color, boolean mono) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (mono) {
            t.setTypeface(Typeface.MONOSPACE);
        }
        t.setLineSpacing(0f, 1.15f);
        return t;
    }

    /** Small spaced-out section heading. */
    static TextView heading(Context c, String s) {
        TextView t = text(c, s.toUpperCase(Locale.US), 11, DIM, false);
        t.setLetterSpacing(0.14f);
        t.setPadding(0, dp(c, 22), 0, dp(c, 8));
        return t;
    }

    static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(LINE);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 1))));
        return v;
    }

    /** Text-only button with a thin white outline. */
    static TextView button(Context c, String s, View.OnClickListener l) {
        TextView t = text(c, s.toUpperCase(Locale.US), 12, FG, false);
        t.setLetterSpacing(0.12f);
        t.setGravity(Gravity.CENTER);
        int h = dp(c, 14);
        int v = dp(c, 9);
        t.setPadding(h, v, h, v);
        GradientDrawable d = new GradientDrawable();
        d.setColor(BG);
        d.setStroke(Math.max(1, dp(c, 1)), FG);
        t.setBackground(d);
        t.setOnClickListener(l);
        return t;
    }

    /** Borderless tappable text for the header bar. */
    static TextView action(Context c, String s, View.OnClickListener l) {
        TextView t = text(c, s, 13, FG, false);
        t.setLetterSpacing(0.12f);
        t.setGravity(Gravity.CENTER);
        int p = dp(c, 12);
        t.setPadding(p, p, p, p);
        t.setMinWidth(dp(c, 48));
        t.setMinHeight(dp(c, 48));
        t.setOnClickListener(l);
        ripple(t);
        return t;
    }

    static void ripple(View v) {
        TypedValue out = new TypedValue();
        if (v.getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, out, true)
                && out.resourceId != 0) {
            v.setBackgroundResource(out.resourceId);
        }
    }

    static TextView oneLine(TextView t) {
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static LinearLayout.LayoutParams fill() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    static LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    /** Keeps content clear of the status bar, navigation bar, display cut-out and keyboard. */
    @SuppressWarnings("deprecation")
    static void fitSystemBars(View root) {
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int l;
            int t;
            int r;
            int b;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets i = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                l = i.left;
                t = i.top;
                r = i.right;
                b = i.bottom;
            } else {
                l = insets.getSystemWindowInsetLeft();
                t = insets.getSystemWindowInsetTop();
                r = insets.getSystemWindowInsetRight();
                b = insets.getSystemWindowInsetBottom();
            }
            v.setPadding(l, t, r, b);
            return insets;
        });
    }

    // ------------------------------------------------------------ time text (always UTC)

    static String hm(long sec) {
        return HM.format(Instant.ofEpochSecond(sec));
    }

    static String hhmm(long sec) {
        return HHMM.format(Instant.ofEpochSecond(sec));
    }

    static String day(long sec) {
        return DAY.format(Instant.ofEpochSecond(sec)).toUpperCase(Locale.US);
    }

    static String dayHm(long sec) {
        return DAY_HM.format(Instant.ofEpochSecond(sec)).toUpperCase(Locale.US);
    }

    /** "35 MIN", "3 H", "2 D" */
    static String age(long sec, long now) {
        long d = Math.max(0, now - sec);
        if (d < 5400) {
            return (d / 60) + " MIN";
        }
        if (d < 2 * 86400) {
            return (d / 3600) + " H";
        }
        return (d / 86400) + " D";
    }
}
