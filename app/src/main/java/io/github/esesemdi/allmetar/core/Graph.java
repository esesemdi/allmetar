package io.github.esesemdi.allmetar.core;

import java.util.ArrayList;
import java.util.List;

/**
 * What the week graph shows: observed ceiling and temperature for the past days (from METARs)
 * and the ceiling the current TAF forecasts for the hours ahead.
 */
public final class Graph {
    public static final int DAYS_BACK = 7;

    /** A stretch of time with one ceiling value. */
    public static final class Span {
        public final long from;
        public final long to;
        /** Feet, or {@link Conditions#NO_CEILING}. */
        public final int ceilingFt;

        Span(long from, long to, int ceilingFt) {
            this.from = from;
            this.to = to;
            this.ceilingFt = ceilingFt;
        }
    }

    /** One observation. */
    public static final class Point {
        public final long time;
        /** Feet, {@link Conditions#NO_CEILING}, or {@link Conditions#UNKNOWN}. */
        public final int ceilingFt;
        /** Degrees C, or {@link Conditions#NO_TEMP}. */
        public final int tempC;

        Point(long time, int ceilingFt, int tempC) {
            this.time = time;
            this.ceilingFt = ceilingFt;
            this.tempC = tempC;
        }
    }

    public final long start;
    public final long now;
    public final long end;
    /** Oldest first. */
    public final List<Point> observed;
    /** Lasting forecast conditions, in time order, from now on. */
    public final List<Span> forecast;
    /** TEMPO / PROB ceilings: may occur within the span. */
    public final List<Span> temporary;

    private Graph(long start, long now, long end, List<Point> observed, List<Span> forecast, List<Span> temporary) {
        this.start = start;
        this.now = now;
        this.end = end;
        this.observed = observed;
        this.forecast = forecast;
        this.temporary = temporary;
    }

    public boolean isEmpty() {
        return observed.isEmpty() && forecast.isEmpty();
    }

    public boolean hasTemperature() {
        for (Point p : observed) {
            if (p.tempC != Conditions.NO_TEMP) {
                return true;
            }
        }
        return false;
    }

    public static Graph of(Station s, long now) {
        long start = now - DAYS_BACK * 86400L;
        List<Point> observed = new ArrayList<>();
        for (int i = s.metars.size() - 1; i >= 0; i--) { // stored newest first
            Metar m = s.metars.get(i);
            if (m.obs < start || m.obs > now + 600) {
                continue;
            }
            Conditions c = Conditions.ofMetar(m.raw);
            if (c.cloudKnown() || c.tempC != Conditions.NO_TEMP) {
                observed.add(new Point(m.obs, c.ceilingFt, c.tempC));
            }
        }

        List<Span> forecast = new ArrayList<>();
        List<Span> temporary = new ArrayList<>();
        long end = now;
        Taf taf = s.latestTaf();
        if (taf != null && taf.validTo > now && taf.validFrom > 0) {
            end = taf.validTo;
            forecast(TafParts.of(taf), now, taf.validTo, forecast, temporary);
        }
        // room to the right of "now" even without a forecast, so the newest reports are not on the edge
        end = Math.max(end, now + 6 * 3600L);
        return new Graph(start, now, end, observed, forecast, temporary);
    }

    /**
     * Lays the TAF groups out on a timeline. A BECMG change is applied at the start of its period
     * when it lowers the ceiling and at the end when it raises it, i.e. the cautious reading.
     */
    static void forecast(List<TafParts.Part> parts, long now, long validTo, List<Span> lasting,
                         List<Span> temporary) {
        // change points of the lasting ceiling: time -> ceiling from then on
        List<long[]> changes = new ArrayList<>();
        int current = Conditions.UNKNOWN;
        for (TafParts.Part p : parts) {
            Conditions c = p.conditions;
            if (p.type == TafParts.BASE || p.type == TafParts.FROM) {
                current = c.cloudKnown() ? c.ceilingFt : Conditions.NO_CEILING;
                // an FM group replaces everything forecast from its time on
                for (int k = changes.size() - 1; k >= 0; k--) {
                    if (changes.get(k)[0] >= p.from) {
                        changes.remove(k);
                    }
                }
                changes.add(new long[]{p.from, current});
            } else if (p.type == TafParts.BECOMING) {
                if (c.cloudKnown()) {
                    boolean lower = rank(c.ceilingFt) < rank(current);
                    current = c.ceilingFt;
                    changes.add(new long[]{lower ? p.from : p.to, current});
                }
            } else if (c.hasCeiling() && p.to > now) {
                temporary.add(new Span(Math.max(p.from, now), Math.min(p.to, validTo), c.ceilingFt));
            }
        }
        for (int i = 0; i < changes.size(); i++) {
            long from = changes.get(i)[0];
            long to = validTo;
            for (int j = i + 1; j < changes.size(); j++) {
                if (changes.get(j)[0] > from) {
                    to = changes.get(j)[0];
                    break;
                }
            }
            // a later change at the same moment replaces this one
            boolean replaced = false;
            for (int j = i + 1; j < changes.size(); j++) {
                if (changes.get(j)[0] <= from) {
                    replaced = true;
                    break;
                }
            }
            if (replaced || to <= now || to <= from) {
                continue;
            }
            int ceiling = (int) changes.get(i)[1];
            if (ceiling == Conditions.UNKNOWN) {
                continue;
            }
            lasting.add(new Span(Math.max(from, now), Math.min(to, validTo), ceiling));
        }
    }

    private static long rank(int ceiling) {
        return ceiling == Conditions.UNKNOWN ? Long.MAX_VALUE : ceiling;
    }
}
