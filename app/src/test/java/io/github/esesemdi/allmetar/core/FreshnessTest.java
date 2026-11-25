package io.github.esesemdi.allmetar.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class FreshnessTest {
    /** 2026-11-25T15:00:00Z */
    private static final long H15 = 1795564800L + 15 * 3600;

    private static Station with(String id, Metar... newestFirst) {
        Station.Edit e = Station.create(id, id, null).edit();
        e.metars.addAll(Arrays.asList(newestFirst));
        return e.build();
    }

    @Test
    public void canadianReportNotInYet() {
        // 15:10Z, newest routine report is still the 14Z one
        List<Station> s = Collections.singletonList(with("CYWG", new Metar(H15 - 3600, "14Z", false)));
        assertTrue(Freshness.awaitingHourly(s, H15 + 600));
        // once the 15Z report is in there is nothing to wait for
        s = Collections.singletonList(with("CYWG", new Metar(H15, "15Z", false), new Metar(H15 - 3600, "14Z", false)));
        assertFalse(Freshness.awaitingHourly(s, H15 + 600));
    }

    @Test
    public void usReportJustBeforeTheHourCounts() {
        List<Station> s = Collections.singletonList(with("KGFK", new Metar(H15 - 7 * 60, "1453Z", false)));
        assertFalse(Freshness.awaitingHourly(s, H15 + 600));
        s = Collections.singletonList(with("KGFK", new Metar(H15 - 3600 - 7 * 60, "1353Z", false)));
        assertTrue(Freshness.awaitingHourly(s, H15 + 600));
    }

    @Test
    public void aSpeciDoesNotStandInForTheHourlyReport() {
        List<Station> s = Collections.singletonList(with("CYWG",
                new Metar(H15 - 300, "SPECI 1455Z", true), new Metar(H15 - 3600, "14Z", false)));
        assertTrue(Freshness.awaitingHourly(s, H15 + 600));
    }

    @Test
    public void silentLocationsAreNotWaitedFor() {
        Station none = with("CKK7");
        Station stopped = with("CYAV", new Metar(H15 - 3 * 3600, "12Z", false));
        assertFalse(Freshness.awaitingHourly(Arrays.asList(none, stopped), H15 + 600));
        assertFalse(Freshness.awaitingHourly(Collections.<Station>emptyList(), H15 + 600));
        // ...but one late location among several is enough
        Station late = with("CYWG", new Metar(H15 - 3600, "14Z", false));
        assertTrue(Freshness.awaitingHourly(Arrays.asList(none, stopped, late), H15 + 600));
    }
}
