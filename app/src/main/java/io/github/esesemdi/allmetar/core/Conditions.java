package io.github.esesemdi.allmetar.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ceiling, visibility and temperature read from one stretch of a raw report: a METAR body or one
 * group of a TAF. Also remembers which words carried the ceiling and the visibility.
 */
public final class Conditions {
    /** {@link #ceilingFt} when cloud was reported but no layer is a ceiling. */
    public static final int NO_CEILING = Integer.MAX_VALUE;
    /** {@link #ceilingFt} when the words say nothing about cloud. */
    public static final int UNKNOWN = -1;
    /** {@link #tempC} when no temperature group was found. */
    public static final int NO_TEMP = Integer.MIN_VALUE;

    /** Height of the lowest broken / overcast layer or vertical visibility, in feet above ground. */
    public final int ceilingFt;
    /** Statute miles; NaN when the words say nothing about visibility. */
    public final double visibilitySm;
    public final int tempC;
    /** Sustained wind speed in knots, or -1 when no wind group was found. */
    public final int windKt;
    /** Gust speed in knots, or -1 when no gust was reported. */
    public final int gustKt;
    /** Index of the word that is the ceiling, or -1. */
    public final int ceilingWord;
    /** First and last index of the visibility word(s), or -1. */
    public final int visFrom;
    public final int visTo;

    private Conditions(int ceilingFt, double visibilitySm, int tempC, int windKt, int gustKt, int ceilingWord,
                       int visFrom, int visTo) {
        this.ceilingFt = ceilingFt;
        this.visibilitySm = visibilitySm;
        this.tempC = tempC;
        this.windKt = windKt;
        this.gustKt = gustKt;
        this.ceilingWord = ceilingWord;
        this.visFrom = visFrom;
        this.visTo = visTo;
    }

    public boolean hasCeiling() {
        return ceilingFt != NO_CEILING && ceilingFt != UNKNOWN;
    }

    public boolean cloudKnown() {
        return ceilingFt != UNKNOWN;
    }

    public boolean visibilityKnown() {
        return !Double.isNaN(visibilitySm);
    }

    /** Low IFR: ceiling below 500 ft and/or visibility below 1 statute mile. */
    public boolean lifr() {
        return (hasCeiling() && ceilingFt < 500) || (visibilityKnown() && visibilitySm < 1.0);
    }

    /** LIFR, IFR, MVFR or VFR; empty when neither cloud nor visibility is known. */
    public String category() {
        if (!cloudKnown() && !visibilityKnown()) {
            return "";
        }
        int c = hasCeiling() ? ceilingFt : NO_CEILING;
        double v = visibilityKnown() ? visibilitySm : 99;
        if (c < 500 || v < 1) {
            return "LIFR";
        }
        if (c < 1000 || v < 3) {
            return "IFR";
        }
        if (c <= 3000 || v <= 5) {
            return "MVFR";
        }
        return "VFR";
    }

    private static final Pattern LAYER = Pattern.compile("(FEW|SCT|BKN|OVC)(\\d{3})(CB|TCU)?");
    private static final Pattern VERTICAL = Pattern.compile("VV(\\d{3}|///)");
    private static final Pattern WHOLE_SM = Pattern.compile("([PM])?(\\d{1,2})SM");
    private static final Pattern FRACTION_SM = Pattern.compile("([PM])?(\\d)/(\\d{1,2})SM");
    private static final Pattern WHOLE = Pattern.compile("\\d{1,2}");
    private static final Pattern TEMP = Pattern.compile("(M)?(\\d{2})/(?:(M)?(\\d{2}))?");
    private static final Pattern WIND = Pattern.compile("(\\d{3}|VRB)(\\d{2,3})(?:G(\\d{2,3}))?(KT|MPS|KMH)");

    /** Reads words[from] up to (not including) words[to]. */
    public static Conditions of(String[] words, int from, int to) {
        int ceiling = UNKNOWN;
        int ceilingWord = -1;
        double vis = Double.NaN;
        int visFrom = -1;
        int visTo = -1;
        int temp = NO_TEMP;
        int wind = -1;
        int gust = -1;
        for (int i = Math.max(0, from); i < to && i < words.length; i++) {
            String t = clean(words[i]);
            Matcher m = WIND.matcher(t);
            if (m.matches()) {
                if (wind < 0) {
                    double factor = m.group(4).equals("KT") ? 1 : m.group(4).equals("MPS") ? 1.943844 : 0.539957;
                    wind = (int) Math.round(Integer.parseInt(m.group(2)) * factor);
                    if (m.group(3) != null) {
                        gust = (int) Math.round(Integer.parseInt(m.group(3)) * factor);
                    }
                }
                continue;
            }
            m = LAYER.matcher(t);
            if (m.matches()) {
                int height = Integer.parseInt(m.group(2)) * 100;
                boolean isCeiling = m.group(1).equals("BKN") || m.group(1).equals("OVC");
                if (isCeiling && (ceiling == UNKNOWN || ceiling == NO_CEILING || height < ceiling)) {
                    ceiling = height;
                    ceilingWord = i;
                } else if (ceiling == UNKNOWN) {
                    ceiling = NO_CEILING;
                }
                continue;
            }
            m = VERTICAL.matcher(t);
            if (m.matches()) {
                int height = m.group(1).equals("///") ? 0 : Integer.parseInt(m.group(1)) * 100;
                if (ceiling == UNKNOWN || ceiling == NO_CEILING || height < ceiling) {
                    ceiling = height;
                    ceilingWord = i;
                }
                continue;
            }
            if (t.equals("SKC") || t.equals("CLR") || t.equals("NSC") || t.equals("NCD")) {
                if (ceiling == UNKNOWN) {
                    ceiling = NO_CEILING;
                }
                continue;
            }
            if (t.equals("CAVOK")) {
                if (ceiling == UNKNOWN) {
                    ceiling = NO_CEILING;
                }
                if (Double.isNaN(vis)) {
                    vis = 7;
                    visFrom = i;
                    visTo = i;
                }
                continue;
            }
            if (!Double.isNaN(vis)) {
                // visibility already found; only the temperature is still of interest
                m = TEMP.matcher(t);
                if (m.matches() && temp == NO_TEMP) {
                    temp = (m.group(1) != null ? -1 : 1) * Integer.parseInt(m.group(2));
                }
                continue;
            }
            m = FRACTION_SM.matcher(t);
            if (m.matches()) {
                double f = Integer.parseInt(m.group(2)) / (double) Math.max(1, Integer.parseInt(m.group(3)));
                visFrom = i;
                visTo = i;
                if (i > from && WHOLE.matcher(clean(words[i - 1])).matches()) {
                    f += Integer.parseInt(clean(words[i - 1]));
                    visFrom = i - 1;
                }
                vis = adjust(f, m.group(1));
                continue;
            }
            m = WHOLE_SM.matcher(t);
            if (m.matches()) {
                vis = adjust(Integer.parseInt(m.group(2)), m.group(1));
                visFrom = i;
                visTo = i;
                continue;
            }
            if (t.matches("\\d{4}") && i > from && WIND.matcher(clean(words[i - 1])).matches()) {
                int metres = Integer.parseInt(t);
                vis = metres >= 9999 ? 7 : metres / 1609.344;
                visFrom = i;
                visTo = i;
                continue;
            }
            m = TEMP.matcher(t);
            if (m.matches() && temp == NO_TEMP) {
                temp = (m.group(1) != null ? -1 : 1) * Integer.parseInt(m.group(2));
            }
        }
        return new Conditions(ceiling, vis, temp, wind, gust, ceilingWord, visFrom, visTo);
    }

    /** Conditions of a METAR: everything before the remarks. */
    public static Conditions ofMetar(String[] words) {
        int end = words.length;
        for (int i = 0; i < words.length; i++) {
            if (clean(words[i]).equals("RMK")) {
                end = i;
                break;
            }
        }
        return of(words, 0, end);
    }

    public static Conditions ofMetar(String raw) {
        return ofMetar(split(raw));
    }

    /** "More than" reads as a little above the number, "less than" as a little below. */
    private static double adjust(double value, String pm) {
        if (pm == null) {
            return value;
        }
        return pm.equals("P") ? value + 0.01 : Math.max(0, value - 0.01);
    }

    static String clean(String w) {
        String t = w.trim();
        while (t.length() > 1 && t.endsWith("=")) {
            t = t.substring(0, t.length() - 1);
        }
        return t;
    }

    public static String[] split(String raw) {
        String t = raw == null ? "" : raw.trim();
        return t.isEmpty() ? new String[0] : t.split("\\s+");
    }
}
