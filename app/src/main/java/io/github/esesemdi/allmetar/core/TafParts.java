package io.github.esesemdi.allmetar.core;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Splits a raw TAF into its groups (initial conditions, FM, BECMG, TEMPO, PROB) with their times. */
public final class TafParts {
    public static final int BASE = 0;
    public static final int FROM = 1;
    public static final int BECOMING = 2;
    public static final int TEMPORARY = 3;

    private TafParts() {
    }

    public static final class Part {
        public final int type;
        /** Epoch seconds. For BASE and FROM, {@code to} is when the next lasting change begins. */
        public final long from;
        public final long to;
        /** Word range of the group in the TAF, first inclusive, last exclusive. */
        public final int wordFrom;
        public final int wordTo;
        public final Conditions conditions;

        Part(int type, long from, long to, int wordFrom, int wordTo, Conditions conditions) {
            this.type = type;
            this.from = from;
            this.to = to;
            this.wordFrom = wordFrom;
            this.wordTo = wordTo;
            this.conditions = conditions;
        }
    }

    private static final Pattern PERIOD = Pattern.compile("(\\d{2})(\\d{2})/(\\d{2})(\\d{2})");
    private static final Pattern FM = Pattern.compile("FM(\\d{2})(\\d{2})(\\d{2})");

    public static List<Part> of(Taf taf) {
        return of(Conditions.split(taf.raw), taf.validFrom, taf.validTo);
    }

    public static List<Part> of(String[] w, long validFrom, long validTo) {
        List<Part> out = new ArrayList<>();
        if (w.length == 0 || validFrom <= 0 || validTo <= validFrom) {
            return out;
        }
        // header: TAF [AMD|COR] ICAO DDHHMMZ DDHH/DDHH
        int i = 0;
        while (i < w.length && !PERIOD.matcher(Conditions.clean(w[i])).matches()) {
            if (isChange(Conditions.clean(w[i]))) {
                break;
            }
            i++;
        }
        if (i < w.length && PERIOD.matcher(Conditions.clean(w[i])).matches()) {
            i++;
        }
        int start = i;
        int type = BASE;
        long from = validFrom;
        long to = validTo;
        int head = i; // first word of the current group (its keyword)
        while (i <= w.length) {
            String t = i < w.length ? Conditions.clean(w[i]) : "RMK";
            if (i == w.length || isChange(t) || t.equals("RMK")) {
                if (i > head || type == BASE) {
                    out.add(new Part(type, from, to, head, i, Conditions.of(w, start, i)));
                }
                if (i == w.length || t.equals("RMK")) {
                    break;
                }
                head = i;
                Matcher fm = FM.matcher(t);
                if (fm.matches()) {
                    type = FROM;
                    from = resolve(validFrom, validTo, num(fm.group(1)), num(fm.group(2)), num(fm.group(3)));
                    to = validTo;
                    i++;
                } else {
                    type = t.equals("BECMG") ? BECOMING : TEMPORARY;
                    i++;
                    if (i < w.length && Conditions.clean(w[i]).equals("TEMPO")) {
                        i++; // PROB30 TEMPO
                    }
                    from = validFrom;
                    to = validTo;
                    if (i < w.length) {
                        Matcher p = PERIOD.matcher(Conditions.clean(w[i]));
                        if (p.matches()) {
                            from = resolve(validFrom, validTo, num(p.group(1)), num(p.group(2)), 0);
                            to = resolve(validFrom, validTo, num(p.group(3)), num(p.group(4)), 0);
                            i++;
                        }
                    }
                }
                start = i;
                continue;
            }
            i++;
        }
        // a lasting group ends where the next FM group begins
        List<Part> fixed = new ArrayList<>();
        for (int k = 0; k < out.size(); k++) {
            Part p = out.get(k);
            if (p.type == BASE || p.type == FROM) {
                long end = validTo;
                for (int j = k + 1; j < out.size(); j++) {
                    if (out.get(j).type == FROM) {
                        end = out.get(j).from;
                        break;
                    }
                }
                p = new Part(p.type, p.from, Math.max(p.from, end), p.wordFrom, p.wordTo, p.conditions);
            }
            fixed.add(p);
        }
        return fixed;
    }

    private static boolean isChange(String t) {
        return FM.matcher(t).matches() || t.equals("BECMG") || t.equals("TEMPO") || t.matches("PROB\\d{2}");
    }

    private static int num(String s) {
        return Integer.parseInt(s);
    }

    /** Turns a day-of-month and time into epoch seconds, choosing the date that falls in the TAF's period. */
    static long resolve(long validFrom, long validTo, int day, int hour, int minute) {
        ZonedDateTime base = Instant.ofEpochSecond(validFrom).atZone(ZoneOffset.UTC);
        long best = validFrom;
        long bestDistance = Long.MAX_VALUE;
        for (int shift = -1; shift <= 1; shift++) {
            ZonedDateTime month = base.withDayOfMonth(1).plusMonths(shift);
            if (day < 1 || day > month.toLocalDate().lengthOfMonth()) {
                continue;
            }
            long t = month.withDayOfMonth(day).withHour(0).withMinute(0).withSecond(0).withNano(0)
                    .toEpochSecond() + hour * 3600L + minute * 60L;
            long distance = t < validFrom ? validFrom - t : t > validTo ? t - validTo : 0;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = t;
            }
        }
        return best;
    }
}
