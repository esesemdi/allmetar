package io.github.esesemdi.allmetar;

import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;

import java.util.List;

import io.github.esesemdi.allmetar.core.Conditions;
import io.github.esesemdi.allmetar.core.Decode;
import io.github.esesemdi.allmetar.core.Metar;
import io.github.esesemdi.allmetar.core.TafParts;
import io.github.esesemdi.allmetar.core.Watch;

/**
 * How reports are emphasised.
 *
 * - LIFR (ceiling below 500 ft and/or visibility below 1 statute mile): light red text, nothing else.
 * - Otherwise the ceiling and the visibility are shown black on white.
 * - Meets one of the user's weather watch targets (and is not LIFR): yellow text.
 *
 * For a METAR the colour applies to the whole report; in a TAF to each group on its own.
 */
final class ReportStyle {
    /** Light red: LIFR. */
    static final int RED = 0xFFFF8A80;
    /** Yellow: meets a weather watch target. */
    static final int YELLOW = 0xFFFFEB3B;

    private ReportStyle() {
    }

    /** Text colour for a METAR: red for LIFR, yellow for a watch match, otherwise {@code normal}. */
    static int color(Metar m, int normal, Watch watch) {
        try {
            Conditions c = Conditions.ofMetar(m.raw);
            if (c.lifr()) {
                return RED;
            }
            if (watch != null && watch.matches(c)) {
                return YELLOW;
            }
        } catch (RuntimeException ignored) {
            // an odd report is simply shown plain
        }
        return normal;
    }

    /** A METAR's text with ceiling and visibility marked (unless it is LIFR). */
    static CharSequence metar(String raw) {
        SpannableString s = new SpannableString(raw);
        markMetar(s, raw);
        return s;
    }

    static void markMetar(Spannable s, String text) {
        try {
            int[][] ranges = Decode.wordRanges(text);
            Conditions c = Conditions.ofMetar(words(text, ranges));
            if (!c.lifr()) {
                mark(s, ranges, c);
            }
        } catch (RuntimeException ignored) {
            // see color()
        }
    }

    /** Styles every group of a TAF by the rules above. */
    static void markTaf(Spannable s, String text, long validFrom, long validTo, Watch watch) {
        try {
            int[][] ranges = Decode.wordRanges(text);
            List<TafParts.Part> parts = TafParts.of(words(text, ranges), validFrom, validTo);
            for (TafParts.Part p : parts) {
                boolean hasWords = p.wordTo > p.wordFrom && p.wordTo <= ranges.length;
                if (p.conditions.lifr()) {
                    if (hasWords) {
                        colour(s, ranges, p, RED);
                    }
                    continue;
                }
                if (hasWords && watch != null && watch.matches(p.conditions)) {
                    colour(s, ranges, p, YELLOW);
                }
                mark(s, ranges, p.conditions); // after the colour, so the marked words stay black
            }
        } catch (RuntimeException ignored) {
            // see color()
        }
    }

    private static void colour(Spannable s, int[][] ranges, TafParts.Part p, int color) {
        s.setSpan(new ForegroundColorSpan(color), ranges[p.wordFrom][0], ranges[p.wordTo - 1][1],
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
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

    /** White background, black text. */
    private static void mark(Spannable s, int start, int end) {
        s.setSpan(new BackgroundColorSpan(Ui.FG), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.setSpan(new ForegroundColorSpan(Ui.BG), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
}
