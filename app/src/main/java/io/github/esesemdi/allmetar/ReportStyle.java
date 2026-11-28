package io.github.esesemdi.allmetar;

import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;

import java.util.List;

import io.github.esesemdi.allmetar.core.Conditions;
import io.github.esesemdi.allmetar.core.Decode;
import io.github.esesemdi.allmetar.core.Metar;
import io.github.esesemdi.allmetar.core.TafParts;

/**
 * How reports are emphasised: the ceiling and the visibility stand out, and a report is shown in
 * light red when it is a SPECI or the conditions are low IFR (ceiling below 500 ft and/or
 * visibility below 1 statute mile).
 */
final class ReportStyle {
    /** Light red for SPECI and LIFR. */
    static final int RED = 0xFFFF8A80;
    /** Dark grey patch behind the ceiling and visibility groups. */
    static final int MARK = 0xFF3D3D3D;

    private ReportStyle() {
    }

    static boolean isRed(Metar m) {
        return m.speci || Conditions.ofMetar(m.raw).lifr();
    }

    /** Text colour for a METAR: light red when {@link #isRed}, otherwise {@code normal}. */
    static int color(Metar m, int normal) {
        return isRed(m) ? RED : normal;
    }

    /** A METAR's text with ceiling and visibility marked. */
    static CharSequence metar(String raw) {
        SpannableString s = new SpannableString(raw);
        markMetar(s, raw);
        return s;
    }

    static void markMetar(Spannable s, String text) {
        try {
            int[][] ranges = Decode.wordRanges(text);
            String[] words = words(text, ranges);
            mark(s, ranges, Conditions.ofMetar(words));
        } catch (RuntimeException ignored) {
            // an odd report is simply shown without emphasis
        }
    }

    /** Marks ceiling and visibility in every group of a TAF and turns LIFR groups light red. */
    static void markTaf(Spannable s, String text, long validFrom, long validTo) {
        try {
            int[][] ranges = Decode.wordRanges(text);
            String[] words = words(text, ranges);
            List<TafParts.Part> parts = TafParts.of(words, validFrom, validTo);
            for (TafParts.Part p : parts) {
                mark(s, ranges, p.conditions);
                if (p.conditions.lifr() && p.wordTo > p.wordFrom && p.wordTo <= ranges.length) {
                    s.setSpan(new ForegroundColorSpan(RED), ranges[p.wordFrom][0], ranges[p.wordTo - 1][1],
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
        } catch (RuntimeException ignored) {
            // see markMetar
        }
    }

    private static String[] words(String text, int[][] ranges) {
        String[] words = new String[ranges.length];
        for (int i = 0; i < ranges.length; i++) {
            words[i] = text.substring(ranges[i][0], ranges[i][1]);
        }
        return words;
    }

    private static void mark(Spannable s, int[][] ranges, Conditions c) {
        if (c.ceilingWord >= 0 && c.ceilingWord < ranges.length) {
            mark(s, ranges[c.ceilingWord][0], ranges[c.ceilingWord][1]);
        }
        if (c.visFrom >= 0 && c.visTo >= c.visFrom && c.visTo < ranges.length) {
            mark(s, ranges[c.visFrom][0], ranges[c.visTo][1]);
        }
    }

    private static void mark(Spannable s, int start, int end) {
        s.setSpan(new BackgroundColorSpan(MARK), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
}
