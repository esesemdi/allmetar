package io.github.esesemdi.allmetar.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class ConditionsTest {
    /** 2026-11-25T00:00:00Z */
    private static final long MIDNIGHT = 1795564800L;
    private static final long H = 3600L;

    private static void near(double expected, double actual) {
        assertTrue("expected " + expected + " but was " + actual, Math.abs(expected - actual) < 0.02);
    }

    @Test
    public void metarCeilingVisibilityTemperature() {
        Conditions c = Conditions.ofMetar(
                "CYWG 251500Z 19013KT 1/2SM R36/P6000FT/D R13/5500VP6000FT/U FZFG VV001 M03/M04 A3042 RMK FG8 SLP319");
        assertEquals(100, c.ceilingFt);
        assertEquals(7, c.ceilingWord);
        near(0.5, c.visibilitySm);
        assertEquals(3, c.visFrom);
        assertEquals(3, c.visTo);
        assertEquals(-3, c.tempC);
        assertTrue(c.lifr());
        assertEquals("LIFR", c.category());

        c = Conditions.ofMetar("CYWG 190900Z 02007KT 2 1/2SM -DZ BR OVC003 07/07 A2964 RMK ST8 SLP045");
        near(2.5, c.visibilitySm);
        assertEquals(3, c.visFrom);
        assertEquals(4, c.visTo);
        assertEquals(300, c.ceilingFt);
        assertEquals(7, c.ceilingWord);
        assertEquals(7, c.tempC);
        assertTrue("ceiling below 500 ft is LIFR even with 2 1/2 miles", c.lifr());

        c = Conditions.ofMetar("CYWG 250600Z 21009KT 10SM BKN007 OVC016 M01/M02 A3038 RMK ST7SC1 PRESRR SLP306");
        assertEquals("lowest ceiling layer", 700, c.ceilingFt);
        assertEquals(4, c.ceilingWord);
        near(10, c.visibilitySm);
        assertFalse(c.lifr());
        assertEquals("IFR", c.category());
        assertEquals(-1, c.tempC);

        c = Conditions.ofMetar("CYWG 250900Z 18009KT 15SM FEW007 OVC018 M02/M03 A3041 RMK SF2ST6 SLP315");
        assertEquals("a FEW layer is not a ceiling", 1800, c.ceilingFt);
        assertEquals(5, c.ceilingWord);
        assertEquals("MVFR", c.category());

        c = Conditions.ofMetar("CYWG 250300Z 19010KT 15SM FEW080 M04/M05 A3035 RMK AC1 SLP298");
        assertEquals(Conditions.NO_CEILING, c.ceilingFt);
        assertEquals(-1, c.ceilingWord);
        assertFalse(c.hasCeiling());
        assertTrue(c.cloudKnown());
        assertEquals("VFR", c.category());
        assertEquals(-4, c.tempC);

        c = Conditions.ofMetar("SPECI CYWG 251213Z 18010G16KT 3/4SM R36/P6000FT/N BR VV002 M05/M06 A3040 RMK FG8 SLP314");
        near(0.75, c.visibilitySm);
        assertEquals(4, c.visFrom);
        assertEquals(200, c.ceilingFt);
        assertTrue(c.lifr());
    }

    @Test
    public void edgeCases() {
        Conditions c = Conditions.ofMetar("KXYZ 251453Z 00000KT 3/4SM BR CLR 01/01 A3000 RMK AO2");
        assertTrue("visibility below a mile alone is LIFR", c.lifr());
        assertEquals(Conditions.NO_CEILING, c.ceilingFt);

        c = Conditions.ofMetar("KXYZ 251453Z 00000KT 1SM BR OVC005 01/01 A3000");
        assertFalse("exactly 1 mile and 500 ft is IFR, not LIFR", c.lifr());
        assertEquals("IFR", c.category());

        c = Conditions.ofMetar("KXYZ 251453Z 00000KT M1/4SM FG VV000 M00/M01 A3000");
        near(0.24, c.visibilitySm);
        assertEquals(0, c.ceilingFt);
        assertEquals(0, c.tempC);
        assertTrue(c.lifr());

        c = Conditions.ofMetar("EGLL 251450Z 24008KT 9999 SCT030 BKN045 11/07 Q1012");
        near(7, c.visibilitySm);
        assertEquals(4500, c.ceilingFt);
        assertEquals("VFR", c.category());
        c = Conditions.ofMetar("EGLL 251450Z 24008KT 0800 FG VV001 11/11 Q1012");
        near(0.5, c.visibilitySm);
        assertTrue(c.lifr());
        c = Conditions.ofMetar("EGLL 251450Z 24008KT CAVOK 11/07 Q1012");
        assertEquals(Conditions.NO_CEILING, c.ceilingFt);
        assertEquals("VFR", c.category());

        c = Conditions.ofMetar("CYAV 251500Z AUTO 19008KT");
        assertFalse(c.cloudKnown());
        assertFalse(c.visibilityKnown());
        assertFalse(c.lifr());
        assertEquals("", c.category());
        assertEquals(Conditions.NO_TEMP, c.tempC);
        assertEquals(0, Conditions.split("  ").length);
        assertFalse(Conditions.ofMetar("").lifr());

        // remarks are not conditions
        c = Conditions.ofMetar("CYWG 251500Z 19013KT 15SM SKC M03/M04 A3042 RMK VIS 1/2SM OVC002 PAST HR");
        near(15, c.visibilitySm);
        assertEquals(Conditions.NO_CEILING, c.ceilingFt);
    }

    private static final String TAF = "TAF AMD CYWG 251515Z 2515/2612 19012KT 1/2SM FZFG VV001 TEMPO 2515/2517 3SM BR OVC003 "
            + "FM251700 19015KT P6SM SCT008 BECMG 2520/2522 18018G28KT FM252200 18020G30KT P6SM SKC "
            + "PROB30 TEMPO 2602/2606 2SM -SN OVC010 RMK NXT FCST BY 251800Z";
    private static final long VALID_FROM = MIDNIGHT + 15 * H;
    private static final long VALID_TO = MIDNIGHT + 36 * H;

    @Test
    public void tafGroups() {
        String[] w = Conditions.split(TAF);
        List<TafParts.Part> p = TafParts.of(w, VALID_FROM, VALID_TO);
        assertEquals(6, p.size());

        assertEquals(TafParts.BASE, p.get(0).type);
        assertEquals(VALID_FROM, p.get(0).from);
        assertEquals("until the first FM group", MIDNIGHT + 17 * H, p.get(0).to);
        assertEquals("19012KT", w[p.get(0).wordFrom]);
        assertEquals("TEMPO", w[p.get(0).wordTo]);
        assertEquals(100, p.get(0).conditions.ceilingFt);
        assertTrue(p.get(0).conditions.lifr());
        assertEquals("VV001", w[p.get(0).conditions.ceilingWord]);
        assertEquals("1/2SM", w[p.get(0).conditions.visFrom]);

        assertEquals(TafParts.TEMPORARY, p.get(1).type);
        assertEquals(MIDNIGHT + 15 * H, p.get(1).from);
        assertEquals(MIDNIGHT + 17 * H, p.get(1).to);
        assertEquals("TEMPO", w[p.get(1).wordFrom]);
        assertEquals(300, p.get(1).conditions.ceilingFt);
        assertTrue(p.get(1).conditions.lifr());

        assertEquals(TafParts.FROM, p.get(2).type);
        assertEquals(MIDNIGHT + 17 * H, p.get(2).from);
        assertEquals(MIDNIGHT + 22 * H, p.get(2).to);
        assertEquals("FM251700", w[p.get(2).wordFrom]);
        assertEquals(Conditions.NO_CEILING, p.get(2).conditions.ceilingFt);
        assertFalse(p.get(2).conditions.lifr());

        assertEquals(TafParts.BECOMING, p.get(3).type);
        assertEquals(MIDNIGHT + 20 * H, p.get(3).from);
        assertEquals(MIDNIGHT + 22 * H, p.get(3).to);
        assertFalse("wind-only change says nothing about cloud", p.get(3).conditions.cloudKnown());

        assertEquals(TafParts.FROM, p.get(4).type);
        assertEquals(MIDNIGHT + 22 * H, p.get(4).from);
        assertEquals(VALID_TO, p.get(4).to);

        assertEquals(TafParts.TEMPORARY, p.get(5).type);
        assertEquals("PROB30", w[p.get(5).wordFrom]);
        assertEquals("RMK", w[p.get(5).wordTo]);
        assertEquals("the 26th", MIDNIGHT + 26 * H, p.get(5).from);
        assertEquals(MIDNIGHT + 30 * H, p.get(5).to);
        assertEquals(1000, p.get(5).conditions.ceilingFt);
        assertEquals("IFR", p.get(5).conditions.category());
    }

    @Test
    public void tafDatesAcrossMonthEnd() {
        // valid 30 Nov 18Z to 1 Dec 18Z
        long from = MIDNIGHT + 5 * 86400 + 18 * H;
        long to = from + 24 * H;
        List<TafParts.Part> p = TafParts.of(Conditions.split(
                "TAF CYWG 301740Z 3018/0118 18010KT P6SM BKN020 FM010300 20010KT P6SM OVC008 TEMPO 0106/0110 1SM -SN"),
                from, to);
        assertEquals(3, p.size());
        assertEquals(from + 9 * H, p.get(1).from); // 1 Dec 03Z
        assertEquals(from + 9 * H, p.get(0).to);
        assertEquals(from + 12 * H, p.get(2).from);
        assertEquals(from + 16 * H, p.get(2).to);
        // hour 24 means the end of that day
        assertEquals(MIDNIGHT + 24 * H, TafParts.resolve(MIDNIGHT + 12 * H, MIDNIGHT + 36 * H, 25, 24, 0));
    }

    @Test
    public void graphTimeline() {
        long now = MIDNIGHT + 15 * H + 1800; // 15:30Z
        List<Graph.Span> lasting = new ArrayList<>();
        List<Graph.Span> temporary = new ArrayList<>();
        Graph.forecast(TafParts.of(Conditions.split(TAF), VALID_FROM, VALID_TO), now, VALID_TO, lasting, temporary);
        assertEquals(3, lasting.size());
        assertEquals(now, lasting.get(0).from);
        assertEquals(MIDNIGHT + 17 * H, lasting.get(0).to);
        assertEquals(100, lasting.get(0).ceilingFt);
        assertEquals(MIDNIGHT + 17 * H, lasting.get(1).from);
        assertEquals(MIDNIGHT + 22 * H, lasting.get(1).to);
        assertEquals(Conditions.NO_CEILING, lasting.get(1).ceilingFt);
        assertEquals(VALID_TO, lasting.get(2).to);
        assertEquals(2, temporary.size());
        assertEquals(now, temporary.get(0).from);
        assertEquals(300, temporary.get(0).ceilingFt);
        assertEquals(MIDNIGHT + 26 * H, temporary.get(1).from);
        assertEquals(1000, temporary.get(1).ceilingFt);
    }

    @Test
    public void becomingIsReadCautiously() {
        long from = MIDNIGHT + 12 * H;
        long to = MIDNIGHT + 36 * H;
        List<Graph.Span> lasting = new ArrayList<>();
        Graph.forecast(TafParts.of(Conditions.split(
                "TAF CYWG 251140Z 2512/2612 18010KT P6SM OVC008 BECMG 2514/2516 BKN030 BECMG 2520/2522 OVC004"),
                from, to), from, to, lasting, new ArrayList<Graph.Span>());
        assertEquals(3, lasting.size());
        assertEquals(800, lasting.get(0).ceilingFt);
        assertEquals("an improvement counts only once the period is over", MIDNIGHT + 16 * H, lasting.get(0).to);
        assertEquals(3000, lasting.get(1).ceilingFt);
        assertEquals("a deterioration counts from the start of the period", MIDNIGHT + 20 * H, lasting.get(1).to);
        assertEquals(400, lasting.get(2).ceilingFt);
        assertEquals(to, lasting.get(2).to);
    }

    @Test
    public void graphOfAStation() {
        long now = MIDNIGHT + 15 * H + 1800;
        Station.Edit e = Station.create("CYWG", "", null).edit();
        e.metars.add(new Metar(MIDNIGHT + 15 * H, "CYWG 251500Z 19013KT 1/2SM FZFG VV001 M03/M04 A3042 RMK FG8", false));
        e.metars.add(new Metar(MIDNIGHT + 14 * H, "CYWG 251400Z 19011KT 15SM FEW040 M04/M05 A3041", false));
        e.metars.add(new Metar(MIDNIGHT - 8 * 86400, "CYWG 170000Z 19011KT 15SM OVC010 M10/M12 A3041", false));
        e.tafs.add(new Taf(MIDNIGHT + 15 * H + 900, VALID_FROM, VALID_TO, TAF));
        Graph g = Graph.of(e.build(), now);
        assertEquals(now - 7 * 86400, g.start);
        assertEquals(now, g.now);
        assertEquals(VALID_TO, g.end);
        assertEquals("reports older than a week are left out", 2, g.observed.size());
        assertEquals("oldest first", MIDNIGHT + 14 * H, g.observed.get(0).time);
        assertEquals(Conditions.NO_CEILING, g.observed.get(0).ceilingFt);
        assertEquals(-4, g.observed.get(0).tempC);
        assertEquals(100, g.observed.get(1).ceilingFt);
        assertEquals(3, g.forecast.size());
        assertEquals(2, g.temporary.size());
        assertTrue(g.hasTemperature());
        assertFalse(g.isEmpty());

        // no TAF: still a little room after "now"; expired TAF is ignored
        Station.Edit bare = Station.create("CYAV", "", null).edit();
        bare.tafs.add(new Taf(MIDNIGHT - 86400, MIDNIGHT - 86400, MIDNIGHT - 3600, "TAF CYAV 240000Z 2400/2423 18010KT P6SM OVC008"));
        Graph empty = Graph.of(bare.build(), now);
        assertTrue(empty.isEmpty());
        assertEquals(now + 6 * H, empty.end);
        assertFalse(empty.hasTemperature());
    }
}
