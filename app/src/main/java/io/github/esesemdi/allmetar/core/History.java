package io.github.esesemdi.allmetar.core;

import java.util.ArrayList;
import java.util.List;

/** Picks the observations shown on the METAR screen. */
public final class History {
    public static final long SLOT = 3 * 3600L;

    private History() {
    }

    public static final class Slot {
        /** Nominal time of the slot (00, 03, ... 21 UTC), epoch seconds. */
        public final long time;
        /** The report for that time, or null when none was received. */
        public final Metar metar;

        Slot(long time, Metar metar) {
            this.time = time;
            this.metar = metar;
        }
    }

    /** The {@code n} most recent reports, newest first (SPECI included). */
    public static List<Metar> latest(List<Metar> newestFirst, int n) {
        return new ArrayList<>(newestFirst.subList(0, Math.min(n, newestFirst.size())));
    }

    /**
     * One report per 3-hour slot (00, 03, ... 21 UTC) going back {@code days} days, newest first.
     * For each slot the routine report closest to the hour is used (US stations report a few
     * minutes before the hour); a SPECI is used only if there is no routine report.
     */
    public static List<Slot> threeHourly(List<Metar> metars, long nowSec, int days) {
        List<Slot> out = new ArrayList<>();
        long newest = (nowSec / SLOT) * SLOT;
        long oldest = nowSec - days * 86400L;
        for (long t = newest; t >= oldest; t -= SLOT) {
            Metar best = pick(metars, t, 2400, 1200, false);
            if (best == null) {
                best = pick(metars, t, 1800, 1800, true);
            }
            if (best == null && nowSec - t < 3600) {
                continue; // the report for the newest slot may simply not be out yet
            }
            out.add(new Slot(t, best));
        }
        return out;
    }

    private static Metar pick(List<Metar> metars, long t, long before, long after, boolean allowSpeci) {
        Metar best = null;
        long bestDist = Long.MAX_VALUE;
        for (Metar m : metars) {
            if (m.speci && !allowSpeci) {
                continue;
            }
            if (m.obs < t - before || m.obs > t + after) {
                continue;
            }
            long d = Math.abs(m.obs - t);
            if (d < bestDist) {
                best = m;
                bestDist = d;
            }
        }
        return best;
    }
}
