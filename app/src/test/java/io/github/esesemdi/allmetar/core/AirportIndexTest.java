package io.github.esesemdi.allmetar.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;

import org.junit.Test;

/** Runs against the real bundled file. */
public class AirportIndexTest {
    private static File asset() {
        for (String p : new String[]{"src/main/assets/airports.tsv", "app/src/main/assets/airports.tsv"}) {
            File f = new File(p);
            if (f.isFile()) {
                return f;
            }
        }
        throw new IllegalStateException("airports.tsv not found from " + new File(".").getAbsolutePath());
    }

    private static Airport find(String id) throws Exception {
        try (InputStream in = new FileInputStream(asset())) {
            return AirportIndex.find(in, id);
        }
    }

    private static List<AirportIndex.Entry> all() throws Exception {
        try (InputStream in = new FileInputStream(asset())) {
            return AirportIndex.loadEntries(in);
        }
    }

    @Test
    public void winnipegHasItsRunwaysAndFrequencies() throws Exception {
        Airport a = find("CYWG");
        assertNotNull(a);
        assertEquals("CA", a.country);
        assertEquals("Winnipeg, MB", a.place());
        assertEquals("783", a.elevFt);
        boolean rwy1836 = false;
        for (Runway r : a.runways) {
            if (r.designator().equals("18/36")) {
                rwy1836 = true;
                assertEquals("11000", r.lengthFt);
                assertFalse(r.closed);
            }
        }
        assertTrue(rwy1836);
        boolean tower = false;
        for (Freq f : a.freqs) {
            if (f.type.equals("TWR") && f.mhzFormatted().equals("118.300")) {
                tower = true;
            }
        }
        assertTrue(tower);
    }

    @Test
    public void smallAndUsAerodromesAreThere() throws Exception {
        Airport ckk7 = find("CKK7");
        assertNotNull(ckk7);
        assertEquals(2, ckk7.runways.size());
        Airport gfk = find("KGFK");
        assertNotNull(gfk);
        assertEquals("US", gfk.country);
        assertTrue(gfk.freqs.size() > 3);
        assertNull(find("ZZZZ"));
        assertNull("no partial identifier matches", find("CYW"));
    }

    @Test
    public void searchByCodeNameAndCity() throws Exception {
        List<AirportIndex.Entry> all = all();
        assertTrue(all.size() > 15000);
        assertEquals("CYWG", AirportIndex.search(all, "cywg", 10).get(0).id);
        assertEquals("IATA code", "CYWG", AirportIndex.search(all, "YWG", 10).get(0).id);
        assertEquals("largest aerodrome in the city first", "CYWG", AirportIndex.search(all, "winnipeg", 10).get(0).id);
        assertEquals("CKK7", AirportIndex.search(all, "steinbach south", 10).get(0).id);
        boolean gfk = false; // Grand Forks BC (CZGF) and Grand Forks ND (KGFK) both match
        for (AirportIndex.Entry e : AirportIndex.search(all, "grand forks", 3)) {
            gfk |= e.id.equals("KGFK");
        }
        assertTrue(gfk);
        List<AirportIndex.Entry> cy = AirportIndex.search(all, "CYW", 50);
        assertTrue(cy.size() > 3);
        int prefixed = 0; // an exact match on another code (e.g. local code CYW) may come first
        for (AirportIndex.Entry e : cy.subList(0, 4)) {
            if (e.id.startsWith("CYW")) {
                prefixed++;
            }
        }
        assertTrue(prefixed >= 3);
        assertTrue(AirportIndex.search(all, "   ", 10).isEmpty());
        assertTrue(AirportIndex.search(all, "qqqqzzzz", 10).isEmpty());
        assertEquals(5, AirportIndex.search(all, "lake", 5).size());
    }

    @Test
    public void identifiersAreUniqueAndStorable() throws Exception {
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (AirportIndex.Entry e : all()) {
            assertTrue("duplicate " + e.id, seen.add(e.id));
            assertTrue("not storable: " + e.id, Store.validId(e.id));
        }
    }
}
