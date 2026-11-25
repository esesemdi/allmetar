package io.github.esesemdi.allmetar.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class HistoryTest {
    /** 2026-11-25T00:00:00Z */
    private static final long MIDNIGHT = 1795564800L;

    private static List<Metar> hourly(long from, long to, long offset) {
        List<Metar> l = new ArrayList<>();
        for (long t = to; t >= from; t -= 3600) {
            l.add(new Metar(t + offset, "OBS " + (t + offset), false));
        }
        return l;
    }

    @Test
    public void fixtureTimesAreWhatTheySay() {
        assertEquals(0, MIDNIGHT % 86400);
        assertEquals(1795615200L, MIDNIGHT + 14 * 3600); // the 14Z CYWG report in Fixtures
    }

    @Test
    public void eightPerDayForAWeek() {
        long now = MIDNIGHT + 14 * 3600 + 34 * 60; // 14:34Z
        List<Metar> m = hourly(now - 8 * 86400, MIDNIGHT + 14 * 3600, 0);
        List<History.Slot> s = History.threeHourly(m, now, 7);
        // newest slot is 12Z today; oldest is 15Z seven days earlier
        assertEquals(MIDNIGHT + 12 * 3600, s.get(0).time);
        assertEquals(MIDNIGHT - 7 * 86400 + 15 * 3600, s.get(s.size() - 1).time);
        assertEquals(56, s.size());
        for (History.Slot x : s) {
            assertNotNull(x.metar);
            assertEquals(x.time, x.metar.obs);
            assertEquals(0, x.time % (3 * 3600));
        }
    }

    @Test
    public void usStationsReportBeforeTheHour() {
        long now = MIDNIGHT + 14 * 3600 + 34 * 60;
        List<Metar> m = hourly(now - 2 * 86400, MIDNIGHT + 14 * 3600, -7 * 60); // xx:53
        List<History.Slot> s = History.threeHourly(m, now, 1);
        assertEquals(MIDNIGHT + 12 * 3600, s.get(0).time);
        assertEquals(MIDNIGHT + 12 * 3600 - 7 * 60, s.get(0).metar.obs);
    }

    @Test
    public void routineReportBeatsACloserSpeci() {
        long t = MIDNIGHT + 12 * 3600;
        List<Metar> m = new ArrayList<>();
        m.add(new Metar(t + 120, "SPECI later", true));
        m.add(new Metar(t - 7 * 60, "routine", false));
        List<History.Slot> s = History.threeHourly(m, t + 2 * 3600, 1);
        assertEquals(t, s.get(0).time);
        assertEquals("routine", s.get(0).metar.raw);
    }

    @Test
    public void speciIsUsedWhenThereIsNothingElse() {
        long t = MIDNIGHT + 12 * 3600;
        List<Metar> m = new ArrayList<>();
        m.add(new Metar(t + 600, "SPECI only", true));
        List<History.Slot> s = History.threeHourly(m, t + 2 * 3600, 1);
        assertEquals(t, s.get(0).time);
        assertEquals("SPECI only", s.get(0).metar.raw);
    }

    @Test
    public void gapsAreShownAsMissing() {
        long now = MIDNIGHT + 14 * 3600;
        List<Metar> m = new ArrayList<>();
        m.add(new Metar(MIDNIGHT + 12 * 3600, "12Z", false));
        m.add(new Metar(MIDNIGHT + 6 * 3600, "06Z", false));
        List<History.Slot> s = History.threeHourly(m, now, 1);
        assertEquals("12Z", s.get(0).metar.raw);
        assertNull(s.get(1).metar); // 09Z
        assertEquals(MIDNIGHT + 9 * 3600, s.get(1).time);
        assertEquals("06Z", s.get(2).metar.raw);
    }

    @Test
    public void slotWhoseReportIsNotOutYetIsLeftOut() {
        long now = MIDNIGHT + 15 * 3600 + 120; // 15:02Z, the 15Z report has not arrived
        List<Metar> m = hourly(MIDNIGHT, MIDNIGHT + 14 * 3600, 0);
        List<History.Slot> s = History.threeHourly(m, now, 1);
        assertEquals(MIDNIGHT + 12 * 3600, s.get(0).time);
        // ...but an hour later a still-missing 15Z report is shown as a gap
        s = History.threeHourly(m, now + 3600, 1);
        assertEquals(MIDNIGHT + 15 * 3600, s.get(0).time);
        assertNull(s.get(0).metar);
    }

    @Test
    public void latestFive() {
        List<Metar> m = hourly(MIDNIGHT, MIDNIGHT + 10 * 3600, 0);
        List<Metar> five = History.latest(m, 5);
        assertEquals(5, five.size());
        assertEquals(MIDNIGHT + 10 * 3600, five.get(0).obs);
        assertTrue(History.latest(m.subList(0, 2), 5).size() == 2);
    }
}
