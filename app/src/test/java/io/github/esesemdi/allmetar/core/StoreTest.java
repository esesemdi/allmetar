package io.github.esesemdi.allmetar.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class StoreTest {
    private static File tempDir() throws Exception {
        File d = Files.createTempDirectory("allmetar-store").toFile();
        d.deleteOnExit();
        return d;
    }

    @Test
    public void holdsAtMostTenLocations() throws Exception {
        Store s = new Store(tempDir());
        for (int i = 0; i < 10; i++) {
            assertTrue(s.add(Station.create("CYA" + (char) ('A' + i), "n", null)));
        }
        assertTrue(s.isFull());
        assertFalse("eleventh is refused", s.add(Station.create("CYWG", "n", null)));
        assertEquals(10, s.ids().size());
        s.remove("CYAA");
        assertFalse(s.isFull());
        assertTrue(s.add(Station.create("CYWG", "n", null)));
    }

    @Test
    public void refusesDuplicatesAndBadIdentifiers() throws Exception {
        Store s = new Store(tempDir());
        assertTrue(s.add(Station.create("CYWG", "Winnipeg", null)));
        assertFalse(s.add(Station.create("CYWG", "again", null)));
        assertFalse(s.add(Station.create("../x", "bad", null)));
        assertFalse(s.add(Station.create("cywg", "lower case", null)));
        assertFalse(s.add(Station.create("", "empty", null)));
        assertEquals(Collections.singletonList("CYWG"), s.ids());
    }

    @Test
    public void survivesARestart() throws Exception {
        File dir = tempDir();
        Store a = new Store(dir);
        Airport ap = new Airport("CYWG", "Winnipeg Intl", "Winnipeg", "MB", "CA", "783", "49.9100", "-97.2399",
                "L", "YWG", Arrays.asList(new Runway("18", "36", "11000", "200", "ASP", true, false)),
                Arrays.asList(new Freq("TWR", "TWR", "118.3")));
        a.add(Station.create("CYWG", "Winnipeg Intl", ap));
        a.add(Station.create("KGFK", "Grand Forks", null));
        a.update("CYWG", new Store.Change() {
            @Override
            public void apply(Station.Edit e) {
                e.metars.add(new Metar(1795615200L, "CYWG 251400Z 19011KT \"quoted\" \\ back", false));
                e.metars.add(new Metar(1795611060L, "SPECI CYWG 251251Z", true));
                e.tafs.add(new Taf(1795612320L, 1795611600L, 1795694400L, "TAF AMD CYWG"));
                e.notams.add(new Notam("1", "(S0234/26\nA) CYWG)", true));
                e.userFreqs.add(new Freq("ATF", "mine", "123.2"));
                e.histTo = 1795615300L;
                e.metarAt = 1795615301L;
                e.notamError = "Server timed out";
            }
        });
        a.setLastRefresh(1795615302L);
        a.setWidgetStation(42, "KGFK");
        a.move("KGFK", -1);

        Store b = new Store(dir);
        assertEquals(Arrays.asList("KGFK", "CYWG"), b.ids());
        Station s = b.get("CYWG");
        assertEquals("Winnipeg Intl", s.name);
        assertEquals(2, s.metars.size());
        assertEquals("CYWG 251400Z 19011KT \"quoted\" \\ back", s.metars.get(0).raw);
        assertTrue(s.metars.get(1).speci);
        assertEquals(1795612320L, s.tafs.get(0).issued);
        assertEquals("(S0234/26\nA) CYWG)", s.notams.get(0).raw);
        assertTrue(s.notams.get(0).aerodrome);
        assertEquals("123.2", s.userFreqs.get(0).mhz);
        assertEquals(1795615300L, s.histTo);
        assertEquals("Server timed out", s.notamError);
        assertNull(s.wxError);
        assertEquals("118.3", s.airport.freqs.get(0).mhz);
        assertEquals("18/36", s.airport.runways.get(0).designator());
        assertTrue(s.airport.runways.get(0).lighted);
        assertEquals("CA", s.country());
        assertEquals("US", b.get("KGFK").country());
        assertEquals(1795615302L, b.lastRefresh());
        assertEquals("KGFK", b.widgetStation(42));
        assertNull(b.widgetStation(7));
    }

    @Test
    public void aRemovedLocationIsNotBroughtBackByALateUpdate() throws Exception {
        File dir = tempDir();
        Store s = new Store(dir);
        s.add(Station.create("CYWG", "Winnipeg", null));
        s.remove("CYWG");
        Station r = s.update("CYWG", new Store.Change() {
            @Override
            public void apply(Station.Edit e) {
                e.metarAt = 1;
            }
        });
        assertNull(r);
        assertNull(s.get("CYWG"));
        assertFalse(new File(dir, "st_CYWG.json").exists());
        assertTrue(new Store(dir).ids().isEmpty());
    }

    @Test
    public void damagedFilesDoNotCrash() throws Exception {
        File dir = tempDir();
        Store s = new Store(dir);
        s.add(Station.create("CYWG", "Winnipeg", null));
        Files.write(new File(dir, "st_CYWG.json").toPath(), "{not json".getBytes("UTF-8"));
        Store again = new Store(dir);
        assertNotNull(again.get("CYWG"));
        assertEquals(0, again.get("CYWG").metars.size());
        Files.write(new File(dir, "index.json").toPath(), "garbage".getBytes("UTF-8"));
        assertTrue(new Store(dir).ids().isEmpty());
    }

    @Test
    public void countryGuessFromIdentifier() {
        assertEquals("CA", Station.countryOfId("CYWG"));
        assertEquals("CA", Station.countryOfId("CKK7"));
        assertEquals("US", Station.countryOfId("KGFK"));
        assertEquals("US", Station.countryOfId("PANC"));
        assertEquals("", Station.countryOfId("EGLL"));
        assertEquals("", Station.countryOfId("1D2"));
    }
}
