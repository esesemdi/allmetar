package io.github.esesemdi.allmetar.core;

import java.util.List;

/** Decides whether this hour's routine reports have all arrived. */
public final class Freshness {
    /** A routine report this close before the hour counts as that hour's report (US stations report at :5x). */
    private static final long EARLY = 600;

    private Freshness() {
    }

    /**
     * True when a location that reported for the previous hour has no routine report for the
     * current hour yet, i.e. it is worth asking again in a few minutes. Locations that have
     * stopped reporting (or never did) are not waited for.
     */
    public static boolean awaitingHourly(List<Station> stations, long nowSec) {
        long hour = (nowSec / 3600) * 3600;
        for (Station s : stations) {
            Metar routine = null;
            for (Metar m : s.metars) { // newest first
                if (!m.speci) {
                    routine = m;
                    break;
                }
            }
            if (routine == null) {
                continue;
            }
            boolean hasThisHour = routine.obs >= hour - EARLY;
            boolean hadLastHour = routine.obs >= hour - 3600 - EARLY;
            if (!hasThisHour && hadLastHour) {
                return true;
            }
        }
        return false;
    }
}
