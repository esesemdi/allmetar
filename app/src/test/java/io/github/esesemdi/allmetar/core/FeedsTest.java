package io.github.esesemdi.allmetar.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class FeedsTest {
    @Test
    public void metarsAreGroupedByStationAndKeptRaw() throws Exception {
        Feeds.MetarBatch b = Feeds.parseMetars(Fixtures.METARS);
        assertEquals(5, b.count);
        List<Metar> wg = b.byStation.get("CYWG");
        assertEquals(3, wg.size());
        assertEquals("CYWG 251400Z 19011KT 1/2SM R36/6000FT/U R13/6000FT/D FZFG VV001 M04/M05 A3041 RMK FG8 SLP315",
                wg.get(0).raw);
        assertEquals(1795615200L, wg.get(0).obs);
        assertFalse(wg.get(0).speci);
        assertTrue(wg.get(2).speci);
        assertTrue(wg.get(2).raw.startsWith("SPECI CYWG 251251Z"));
        List<Metar> gfk = b.byStation.get("KGFK");
        assertEquals(2, gfk.size());
        assertEquals("KGFK 251353Z 18008KT 10SM BKN039 M03/M05 A3045 RMK AO2 SLP325 T10331050 $", gfk.get(0).raw);
        assertEquals("Winnipeg Intl Arpt, MB, CA", b.names.get("CYWG"));
    }

    @Test
    public void emptyMetarAnswerMeansNoReports() throws Exception {
        assertEquals(0, Feeds.parseMetars("").count);
        assertEquals(0, Feeds.parseMetars("  \n").count);
        assertEquals(0, Feeds.parseMetars("[]").count);
    }

    @Test
    public void errorAnswersAreNotMistakenForData() {
        for (String bad : new String[]{"{\"error\":\"Invalid parameter\"}", "<html>Service unavailable</html>"}) {
            try {
                Feeds.parseMetars(bad);
                fail("accepted: " + bad);
            } catch (Feeds.FeedException expected) {
                assertTrue(true);
            }
        }
    }

    @Test
    public void tafs() throws Exception {
        Map<String, List<Taf>> t = Feeds.parseTafs(Fixtures.TAFS);
        Taf wg = t.get("CYWG").get(0);
        assertTrue(wg.raw.startsWith("TAF AMD CYWG 251312Z 2513/2612 18010KT"));
        assertEquals(1795612320L, wg.issued); // 2026-11-25T13:12:00Z
        assertEquals(1795611600L, wg.validFrom);
        assertEquals(1795694400L, wg.validTo);
        assertEquals(1, t.get("KGFK").size());
    }

    @Test
    public void notamsAreUnwrappedAndAerodromeOnesComeFirst() throws Exception {
        List<Notam> n = Feeds.parseNotams(Fixtures.NOTAMS, "CYWG");
        assertEquals(3, n.size());
        assertTrue(n.get(0).aerodrome);
        assertEquals("(S0234/26 NOTAMR S0205/26\nQ) CZWG/QFAXX/IV/NBO/A/000/999/4955N09714W005\n"
                + "A) CYWG B) 2611251408 C) 2611252208\nE) RSC 18/36 5/5/5 100 PCT DRY)", n.get(0).raw);
        assertTrue("A) field match counts as aerodrome", n.get(1).aerodrome);
        assertTrue(n.get(1).raw.contains("RWY 07/25 CLSD"));
        assertFalse(n.get(2).aerodrome);
        assertTrue(n.get(2).raw.startsWith("(G4138/26"));
    }

    @Test
    public void noNotamsIsDifferentFromAFailedAnswer() throws Exception {
        assertEquals(0, Feeds.parseNotams("{\"meta\":{\"count\":{\"notam\":0}},\"data\":[]}", "CYWG").size());
        for (String bad : new String[]{"", "{\"meta\":{}}", "<html>", "[]"}) {
            try {
                Feeds.parseNotams(bad, "CYWG");
                fail("accepted: " + bad);
            } catch (Feeds.FeedException expected) {
                assertTrue(true);
            }
        }
    }

    @Test
    public void urls() {
        assertEquals("https://aviationweather.gov/api/data/metar?ids=CYWG,KGFK&format=json&hours=4",
                Feeds.metarUrl(Arrays.asList("CYWG", "KGFK"), 4, 0));
        // 1795305600 = 2026-11-22T00:00:00Z
        assertEquals("https://aviationweather.gov/api/data/metar?ids=CYWG&format=json&hours=24&date=20261122_0000",
                Feeds.metarUrl(Arrays.asList("CYWG"), 24, 1795305600L));
        assertEquals("https://aviationweather.gov/api/data/taf?ids=CYWG&format=json",
                Feeds.tafUrl(Arrays.asList("CYWG")));
        assertEquals("https://plan.navcanada.ca/weather/api/alpha/?site=CYWG&alpha=notam", Feeds.notamUrl("CYWG"));
    }
}
