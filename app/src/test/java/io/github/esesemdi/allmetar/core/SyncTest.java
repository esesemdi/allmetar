package io.github.esesemdi.allmetar.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

public class SyncTest {
    /** 2026-11-25T14:34:00Z */
    private static final long T0 = 1795564800L + 14 * 3600 + 34 * 60;

    /** Stands in for both feeds: every station reports every {@code step} seconds, forever. */
    private static final class FakeFeeds implements Fetcher, Sync.Clock {
        long now = T0;
        boolean down;
        final Map<String, Long> step = new HashMap<>();
        final Set<String> withTaf = new HashSet<>();
        final List<String> urls = new ArrayList<>();

        @Override
        public long nowSec() {
            return now;
        }

        @Override
        public String get(String url) throws IOException {
            urls.add(url);
            if (down) {
                throw new UnknownHostException("aviationweather.gov");
            }
            Map<String, String> q = new HashMap<>();
            for (String kv : url.substring(url.indexOf('?') + 1).split("&")) {
                String[] p = kv.split("=", 2);
                q.put(p[0], p.length > 1 ? p[1] : "");
            }
            if (url.contains("/metar?")) {
                long end = now;
                if (q.containsKey("date")) {
                    end = LocalDateTime.parse(q.get("date"), DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
                            .toEpochSecond(ZoneOffset.UTC);
                }
                long start = end - Long.parseLong(q.get("hours")) * 3600;
                List<String> items = new ArrayList<>();
                // newest first across stations, like the real feed
                for (String id : q.get("ids").split(",")) {
                    Long st = step.get(id);
                    if (st == null) {
                        continue;
                    }
                    for (long t = (Math.min(end, now) / st) * st; t >= start; t -= st) {
                        items.add(Fixtures.metarJson(id, t, "METAR", id + " OBS" + t));
                    }
                }
                if (items.size() > Feeds.AWC_MAX_ENTRIES) {
                    items = items.subList(0, Feeds.AWC_MAX_ENTRIES);
                }
                if (items.isEmpty()) {
                    return "";
                }
                return "[" + String.join(",", items) + "]";
            }
            if (url.contains("/taf?")) {
                List<String> items = new ArrayList<>();
                for (String id : q.get("ids").split(",")) {
                    if (withTaf.contains(id)) {
                        long issued = (now / 21600) * 21600;
                        items.add("{\"icaoId\":\"" + id + "\",\"issueTime\":\""
                                + java.time.Instant.ofEpochSecond(issued) + "\",\"validTimeFrom\":" + issued
                                + ",\"validTimeTo\":" + (issued + 86400) + ",\"rawTAF\":\"TAF " + id + " " + issued
                                + "\"}");
                    }
                }
                return items.isEmpty() ? "" : "[" + String.join(",", items) + "]";
            }
            if (url.contains("navcanada")) {
                return Fixtures.NOTAMS;
            }
            throw new IOException("unexpected url " + url);
        }

        int count(String part) {
            int n = 0;
            for (String u : urls) {
                if (u.contains(part)) {
                    n++;
                }
            }
            return n;
        }
    }

    private static Store newStore() throws Exception {
        File d = Files.createTempDirectory("allmetar-sync").toFile();
        d.deleteOnExit();
        return new Store(d);
    }

    private static void assertHourlyWithoutGaps(Station s, long from, long to) {
        Set<Long> have = new HashSet<>();
        for (Metar m : s.metars) {
            assertTrue("duplicate " + m.obs, have.add(m.obs));
        }
        for (long t = (to / 3600) * 3600; t >= from; t -= 3600) {
            assertTrue("missing report at " + t, have.contains(t));
        }
        for (int i = 1; i < s.metars.size(); i++) {
            assertTrue("newest first", s.metars.get(i - 1).obs >= s.metars.get(i).obs);
        }
    }

    @Test
    public void addingALocationLoadsAWeekOfHistory() throws Exception {
        FakeFeeds f = new FakeFeeds();
        f.step.put("CYWG", 3600L);
        f.withTaf.add("CYWG");
        Store store = newStore();
        store.add(Station.create("CYWG", "", null));
        Sync sync = new Sync(store, f, f, 0);

        assertTrue(sync.refreshStation("CYWG"));

        Station s = store.get("CYWG");
        assertHourlyWithoutGaps(s, T0 - 7 * 86400, T0);
        assertEquals(T0, s.histTo);
        assertEquals(T0, s.metarAt);
        assertNull(s.wxError);
        assertEquals("CYWG name", s.name);
        assertEquals(1, s.tafs.size());
        assertEquals(3, s.notams.size());
        assertEquals(T0, s.notamAt);
        assertEquals(T0, store.lastRefresh());
        assertEquals("1 latest + 7 daily history requests", 8, f.count("/metar?"));
        for (History.Slot slot : History.threeHourly(s.metars, T0, 7)) {
            assertNotNull("slot " + slot.time, slot.metar);
        }
        assertEquals(56, History.threeHourly(s.metars, T0, 7).size());
        assertEquals(T0 / 3600 * 3600, History.latest(s.metars, 5).get(0).obs);
    }

    @Test
    public void hourlyRefreshIsOneWeatherRequestForAllLocations() throws Exception {
        FakeFeeds f = new FakeFeeds();
        f.step.put("CYWG", 3600L);
        f.step.put("KGFK", 3600L);
        f.withTaf.add("CYWG");
        Store store = newStore();
        store.add(Station.create("CYWG", "", null));
        store.add(Station.create("KGFK", "", null));
        Sync sync = new Sync(store, f, f, 0);
        assertTrue(sync.refreshAll());

        f.urls.clear();
        f.now += 3600;
        assertTrue(sync.refreshAll());

        assertEquals(1, f.count("/metar?"));
        assertTrue(f.urls.get(0).contains("ids=CYWG,KGFK"));
        assertFalse("no history request needed", f.urls.get(0).contains("date="));
        assertEquals(1, f.count("/taf?"));
        assertEquals("NOTAMs only for the Canadian aerodrome", 1, f.count("navcanada"));
        assertTrue(f.urls.toString().contains("site=CYWG"));
        assertEquals(f.now, store.get("CYWG").histTo);
        assertEquals(f.now, store.get("KGFK").histTo);
        assertHourlyWithoutGaps(store.get("KGFK"), f.now - 7 * 86400, f.now);
        assertEquals(0, store.get("KGFK").notams.size());
        assertEquals(0, store.get("KGFK").tafs.size());
        assertEquals(f.now, store.get("KGFK").tafAt);
    }

    @Test
    public void aGapAfterBeingOfflineIsFilledIn() throws Exception {
        FakeFeeds f = new FakeFeeds();
        f.step.put("CYWG", 3600L);
        Store store = newStore();
        store.add(Station.create("CYWG", "", null));
        Sync sync = new Sync(store, f, f, 0);
        assertTrue(sync.refreshAll());

        f.urls.clear();
        f.now += 10 * 3600; // ten hours with no updates
        assertTrue(sync.refreshAll());

        assertEquals("latest + one request for the gap", 2, f.count("/metar?"));
        assertTrue(f.urls.toString(), f.urls.toString().contains("hours=11&date="));
        assertHourlyWithoutGaps(store.get("CYWG"), f.now - 7 * 86400, f.now);
        assertEquals(f.now, store.get("CYWG").histTo);

        // three days offline: three daily requests, and reports older than 8 days are dropped
        f.urls.clear();
        f.now += 3 * 86400;
        assertTrue(sync.refreshAll());
        assertEquals(1 + 4, f.count("/metar?"));
        Station s = store.get("CYWG");
        assertHourlyWithoutGaps(s, f.now - 7 * 86400, f.now);
        assertTrue(s.metars.get(s.metars.size() - 1).obs >= f.now - 8 * 86400);
    }

    @Test
    public void aFailedRefreshKeepsWhatWasThereAndSaysWhy() throws Exception {
        FakeFeeds f = new FakeFeeds();
        f.step.put("CYWG", 3600L);
        f.withTaf.add("CYWG");
        Store store = newStore();
        store.add(Station.create("CYWG", "", null));
        Sync sync = new Sync(store, f, f, 0);
        assertTrue(sync.refreshAll());
        Station before = store.get("CYWG");

        f.down = true;
        f.now += 3600;
        assertFalse(sync.refreshAll());
        assertFalse(sync.refreshLatest());

        Station s = store.get("CYWG");
        assertEquals("No internet connection", s.wxError);
        assertEquals("No internet connection", s.notamError);
        assertEquals(before.metars.size(), s.metars.size());
        assertEquals(before.notams.size(), s.notams.size());
        assertEquals(before.histTo, s.histTo);
        assertEquals(before.notamAt, s.notamAt);
        assertEquals(T0, store.lastRefresh());

        f.down = false;
        assertTrue(sync.refreshAll());
        s = store.get("CYWG");
        assertNull(s.wxError);
        assertNull(s.notamError);
        assertEquals(f.now, s.histTo);
        assertFalse(Sync.isBusy());
    }

    @Test
    public void aLocationWithoutWeatherReportsIsNotAskedForAWeekOfThem() throws Exception {
        FakeFeeds f = new FakeFeeds();
        Store store = newStore();
        store.add(Station.create("CKK7", "Steinbach (South)", null));
        Sync sync = new Sync(store, f, f, 0);

        assertTrue(sync.refreshStation("CKK7"));

        assertEquals("latest + one day", 2, f.count("/metar?"));
        Station s = store.get("CKK7");
        assertEquals(0, s.metars.size());
        assertEquals(T0, s.histTo);
        assertEquals("Steinbach (South)", s.name);
        assertEquals(3, s.notams.size());

        f.urls.clear();
        f.now += 3600;
        assertTrue(sync.refreshAll());
        assertEquals(1, f.count("/metar?"));
    }

    @Test
    public void veryFrequentReportsBeyondTheFeedLimitAreFetchedInSmallerWindows() throws Exception {
        FakeFeeds f = new FakeFeeds();
        f.step.put("KXYZ", 180L); // every 3 minutes = 480 a day, above the 400-entry cap
        Store store = newStore();
        store.add(Station.create("KXYZ", "", null));
        Sync sync = new Sync(store, f, f, 0);

        assertTrue(sync.refreshStation("KXYZ"));

        Station s = store.get("KXYZ");
        Set<Long> have = new HashSet<>();
        for (Metar m : s.metars) {
            have.add(m.obs);
        }
        for (long t = (T0 / 180) * 180; t >= T0 - 7 * 86400; t -= 180) {
            assertTrue("missing " + t, have.contains(t));
        }
        assertEquals(0, f.count("navcanada"));
    }

    @Test
    public void forecastsAccumulateAndOldOnesAreDropped() throws Exception {
        FakeFeeds f = new FakeFeeds();
        f.step.put("CYWG", 3600L);
        f.withTaf.add("CYWG");
        Store store = newStore();
        store.add(Station.create("CYWG", "", null));
        Sync sync = new Sync(store, f, f, 0);
        sync.refreshAll();
        sync.refreshAll();
        assertEquals("same forecast is stored once", 1, store.get("CYWG").tafs.size());

        f.now += 6 * 3600;
        sync.refreshAll();
        Station s = store.get("CYWG");
        assertEquals(2, s.tafs.size());
        assertTrue(s.tafs.get(0).issued > s.tafs.get(1).issued);
        assertEquals(s.tafs.get(0), s.latestTaf());

        f.now += 40 * 3600;
        sync.refreshAll();
        s = store.get("CYWG");
        assertEquals(1, s.tafs.size());
        assertEquals((f.now / 21600) * 21600, s.tafs.get(0).issued);
    }

    @Test
    public void listenerHearsAboutChanges() throws Exception {
        FakeFeeds f = new FakeFeeds();
        f.step.put("CYWG", 3600L);
        Store store = newStore();
        store.add(Station.create("CYWG", "", null));
        Sync sync = new Sync(store, f, f, 0);
        final int[] calls = {0};
        sync.setListener(new Sync.Listener() {
            @Override
            public void onChanged() {
                calls[0]++;
            }
        });
        sync.refreshAll();
        assertTrue(calls[0] >= 2);
    }
}
