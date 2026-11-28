package io.github.esesemdi.allmetar.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import org.junit.Test;

public class WatchTest {
    private static final Conditions LOW = Conditions.ofMetar(
            "CYWG 221500Z 35020G28KT 3SM -SN OVC009 M04/M05 A3007 RMK ST8 SLP199");
    private static final Conditions CLEAR = Conditions.ofMetar(
            "CYWG 272300Z 21011KT 15SM SKC 06/04 A2946 RMK SLP987");
    private static final Conditions COLD = Conditions.ofMetar(
            "CYWG 230900Z 00000KT 15SM FEW250 M27/M30 A3050 RMK CI1 SLP400");

    @Test
    public void windAndGustAreRead() {
        assertEquals(20, LOW.windKt);
        assertEquals(28, LOW.gustKt);
        assertEquals(11, CLEAR.windKt);
        assertEquals(-1, CLEAR.gustKt);
        assertEquals(0, COLD.windKt);
        assertEquals(-27, COLD.tempC);
        Conditions metric = Conditions.ofMetar("UUEE 251500Z 24005G10MPS 9999 BKN030 01/M01 Q1012");
        assertEquals(10, metric.windKt);
        assertEquals(19, metric.gustKt);
        assertEquals(-1, Conditions.ofMetar("CYWG 251500Z 15SM SKC").windKt);
    }

    @Test
    public void offOrEmptyNeverMatches() {
        Watch w = Watch.none();
        assertFalse(w.on);
        assertFalse(w.anySet());
        assertFalse(w.matches(LOW));
        w = w.with(Watch.CEILING, 3000, true);
        assertFalse("targets set but the watch is off", w.matches(LOW));
        assertFalse(w.active());
        assertTrue(w.withOn(true).matches(LOW));
        assertTrue(w.withOn(true).active());
        assertFalse(Watch.none().withOn(true).matches(LOW));
        assertFalse(Watch.none().withOn(true).active());
    }

    @Test
    public void ceilingTargets() {
        Watch below = Watch.none().withOn(true).with(Watch.CEILING, 1000, true);
        assertTrue("900 ft is at or below 1000", below.matches(LOW));
        assertFalse("no ceiling is not a low ceiling", below.matches(CLEAR));
        assertTrue(Watch.none().withOn(true).with(Watch.CEILING, 900, true).matches(LOW));
        assertFalse(Watch.none().withOn(true).with(Watch.CEILING, 800, true).matches(LOW));

        Watch above = Watch.none().withOn(true).with(Watch.CEILING, 3000, false);
        assertFalse(above.matches(LOW));
        assertTrue("no ceiling counts as above any height", above.matches(CLEAR));
        assertFalse("nothing known about cloud", above.matches(Conditions.ofMetar("CYAV 251500Z AUTO 19008KT 9SM")));
    }

    @Test
    public void anyOneTargetIsEnough() {
        Watch w = Watch.none().withOn(true)
                .with(Watch.CEILING, 500, true)
                .with(Watch.VISIBILITY, 5, true)
                .with(Watch.TEMPERATURE, -25, true)
                .with(Watch.WIND, 25, false)
                .with(Watch.GUST, 25, false);
        assertEquals("visibility 3 SM", Watch.VISIBILITY, w.firstMatch(LOW));
        assertEquals(Watch.TEMPERATURE, w.firstMatch(COLD));
        assertEquals(-1, w.firstMatch(CLEAR));
        assertTrue(w.without(Watch.VISIBILITY).matches(LOW)); // gust 28
        assertEquals(Watch.GUST, w.without(Watch.VISIBILITY).firstMatch(LOW));
        assertFalse(w.without(Watch.VISIBILITY).without(Watch.GUST).matches(LOW));

        Watch warm = Watch.none().withOn(true).with(Watch.TEMPERATURE, 5, false);
        assertTrue(warm.matches(CLEAR));
        assertFalse(warm.matches(COLD));
        Watch calm = Watch.none().withOn(true).with(Watch.WIND, 5, true);
        assertTrue(calm.matches(COLD));
        assertFalse(calm.matches(CLEAR));
        Watch good = Watch.none().withOn(true).with(Watch.VISIBILITY, 6, false);
        assertTrue("P6SM is more than 6", good.matches(Conditions.of(Conditions.split("19012KT P6SM SKC"), 0, 3)));
        assertFalse(Watch.none().withOn(true).with(Watch.VISIBILITY, 6, true)
                .matches(Conditions.of(Conditions.split("19012KT P6SM SKC"), 0, 3)));
    }

    @Test
    public void tafGroupsAreJudgedOnWhatTheySay() {
        Watch w = Watch.none().withOn(true).with(Watch.CEILING, 1000, true).with(Watch.GUST, 25, false);
        List<TafParts.Part> p = TafParts.of(Conditions.split(
                "TAF CYWG 251140Z 2512/2612 18010KT P6SM SCT030 TEMPO 2513/2515 3SM BR OVC008 "
                        + "FM251800 20015G27KT P6SM SKC BECMG 2520/2522 22010KT"),
                1795564800L + 12 * 3600, 1795564800L + 36 * 3600);
        assertEquals(4, p.size());
        assertFalse(w.matches(p.get(0).conditions));
        assertTrue("TEMPO ceiling 800", w.matches(p.get(1).conditions));
        assertTrue("gust 27", w.matches(p.get(2).conditions));
        assertFalse("wind-only change without a gust", w.matches(p.get(3).conditions));
        assertFalse("a TAF has no temperature", Watch.none().withOn(true).with(Watch.TEMPERATURE, 50, true)
                .matches(p.get(0).conditions));
    }

    @Test
    public void isRemembered() throws Exception {
        File dir = Files.createTempDirectory("allmetar-watch").toFile();
        dir.deleteOnExit();
        Store a = new Store(dir);
        assertFalse(a.watch().on);
        a.setWatch(Watch.none().withOn(true).with(Watch.CEILING, 2500, true).with(Watch.WIND, 15, false)
                .with(Watch.TEMPERATURE, -20.5, false));
        Watch w = new Store(dir).watch();
        assertTrue(w.on);
        assertTrue(w.isSet(Watch.CEILING));
        assertEquals(2500, (int) w.value(Watch.CEILING));
        assertTrue(w.below(Watch.CEILING));
        assertFalse(w.below(Watch.WIND));
        assertFalse(w.below(Watch.TEMPERATURE));
        assertTrue(Math.abs(w.value(Watch.TEMPERATURE) + 20.5) < 0.001);
        assertFalse(w.isSet(Watch.VISIBILITY));
        assertFalse(w.isSet(Watch.GUST));
        assertTrue(Watch.defaultBelow(Watch.CEILING));
        assertFalse(Watch.defaultBelow(Watch.GUST));
    }
}
