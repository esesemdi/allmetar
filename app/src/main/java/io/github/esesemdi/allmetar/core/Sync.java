package io.github.esesemdi.allmetar.core;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Downloads weather and NOTAMs and writes them to the {@link Store}.
 *
 * METAR history works like this: every refresh asks for the last few hours for all saved
 * locations in one request. A location whose stored history has a gap (just added, or the phone
 * was offline for a while) gets its missing days fetched separately, a day per request, up to a week.
 */
public final class Sync {
    public static final int HISTORY_DAYS = 7;

    public interface Listener {
        void onChanged();
    }

    public interface Clock {
        long nowSec();
    }

    private static final long HOUR = 3600L;
    private static final long DAY = 86400L;
    /** Window of the "latest" request, hours. */
    private static final int LATEST_HOURS = 4;
    /** History is treated as gap-free when the previous fetch is no older than this. */
    private static final long CONTIGUOUS = LATEST_HOURS * HOUR - 1800;
    private static final long KEEP_METARS = (HISTORY_DAYS + 1) * DAY;
    private static final long KEEP_TAFS = 36 * HOUR;

    /** One download at a time for the whole process. */
    private static final Object LOCK = new Object();
    private static final java.util.concurrent.atomic.AtomicInteger BUSY =
            new java.util.concurrent.atomic.AtomicInteger();

    private final Store store;
    private final Fetcher http;
    private final Clock clock;
    private final long pauseMs;
    private volatile Listener listener;

    public Sync(Store store, Fetcher http, Clock clock, long pauseMs) {
        this.store = store;
        this.http = http;
        this.clock = clock;
        this.pauseMs = pauseMs;
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    public static boolean isBusy() {
        return BUSY.get() > 0;
    }

    // ------------------------------------------------------------ entry points

    /** Latest METARs for every saved location, in a single request. */
    public boolean refreshLatest() {
        BUSY.incrementAndGet();
        changed();
        try {
            synchronized (LOCK) {
                List<String> ids = store.ids();
                return ids.isEmpty() || latest(ids);
            }
        } finally {
            BUSY.decrementAndGet();
            changed();
        }
    }

    /** METARs (with any missing history), TAFs and NOTAMs for every saved location. */
    public boolean refreshAll() {
        BUSY.incrementAndGet();
        changed();
        try {
            synchronized (LOCK) {
                List<String> ids = store.ids();
                if (ids.isEmpty()) {
                    return true;
                }
                boolean ok = latest(ids);
                changed();
                ok &= tafs(ids);
                changed();
                for (String id : ids) {
                    ok &= history(id);
                }
                changed();
                for (String id : ids) {
                    ok &= notams(id);
                }
                return ok;
            }
        } finally {
            BUSY.decrementAndGet();
            changed();
        }
    }

    /** Everything for one location. */
    public boolean refreshStation(String id) {
        BUSY.incrementAndGet();
        changed();
        try {
            synchronized (LOCK) {
                if (store.get(id) == null) {
                    return true;
                }
                List<String> ids = Collections.singletonList(id);
                boolean ok = latest(ids);
                changed();
                ok &= tafs(ids);
                changed();
                ok &= notams(id);
                changed();
                ok &= history(id);
                return ok;
            }
        } finally {
            BUSY.decrementAndGet();
            changed();
        }
    }

    // ------------------------------------------------------------ METAR

    private boolean latest(List<String> ids) {
        final long now = clock.nowSec();
        try {
            final Feeds.MetarBatch b = Feeds.parseMetars(http.get(Feeds.metarUrl(ids, LATEST_HOURS, 0)));
            for (final String id : ids) {
                store.update(id, new Store.Change() {
                    @Override
                    public void apply(Station.Edit e) {
                        merge(e, b.byStation.get(id));
                        prune(e, now);
                        e.metarAt = now;
                        e.wxError = null;
                        if (e.histTo > 0 && e.histTo >= now - CONTIGUOUS) {
                            e.histTo = now;
                        }
                        String name = b.names.get(id);
                        if ((e.name == null || e.name.isEmpty()) && name != null) {
                            e.name = name;
                        }
                    }
                });
            }
            store.setLastRefresh(now);
            return true;
        } catch (IOException | RuntimeException ex) {
            wxFailed(ids, describe(ex));
            return false;
        }
    }

    /** Fills the gap between what is stored and now, at most a week back. */
    private boolean history(final String id) {
        Station st = store.get(id);
        if (st == null) {
            return true;
        }
        final long now = clock.nowSec();
        if (st.histTo > 0 && st.histTo >= now - CONTIGUOUS) {
            return true;
        }
        final boolean firstLoad = st.histTo == 0;
        long from = now - HISTORY_DAYS * DAY;
        if (!firstLoad) {
            from = Math.max(from, st.histTo - HOUR);
        }
        final List<Metar> got = new ArrayList<>();
        boolean ok = true;
        String error = null;
        try {
            long end = now;
            boolean first = true;
            while (end > from) {
                int hours = (int) Math.min(24, (end - from + HOUR - 1) / HOUR);
                if (!first) {
                    pause();
                }
                int before = got.size();
                window(id, end, hours, got, 0);
                if (first && firstLoad && got.size() == before) {
                    break; // nothing in the last day: this location does not report METARs
                }
                first = false;
                end -= hours * HOUR;
            }
        } catch (IOException | RuntimeException ex) {
            ok = false;
            error = describe(ex);
        }
        final boolean complete = ok;
        final String err = error;
        store.update(id, new Store.Change() {
            @Override
            public void apply(Station.Edit e) {
                merge(e, got);
                prune(e, now);
                if (complete) {
                    e.histTo = now;
                    e.wxError = null;
                } else {
                    e.wxError = err;
                }
            }
        });
        changed();
        return ok;
    }

    /** One history request; splits the window when the feed's entry limit may have cut it short. */
    private void window(String id, long end, int hours, List<Metar> out, int depth) throws IOException {
        Feeds.MetarBatch b = Feeds.parseMetars(
                http.get(Feeds.metarUrl(Collections.singletonList(id), hours, end)));
        if (b.count >= Feeds.AWC_MAX_ENTRIES && hours > 1 && depth < 5) {
            int half = hours / 2;
            pause();
            window(id, end, half, out, depth + 1);
            pause();
            window(id, end - half * HOUR, hours - half, out, depth + 1);
            return;
        }
        List<Metar> list = b.byStation.get(id);
        if (list != null) {
            out.addAll(list);
        }
    }

    private void wxFailed(List<String> ids, final String message) {
        for (String id : ids) {
            store.update(id, new Store.Change() {
                @Override
                public void apply(Station.Edit e) {
                    e.wxError = message;
                }
            });
        }
    }

    static void merge(Station.Edit e, List<Metar> more) {
        if (more == null || more.isEmpty()) {
            return;
        }
        LinkedHashSet<Metar> set = new LinkedHashSet<>(e.metars);
        set.addAll(more);
        List<Metar> all = new ArrayList<>(set);
        Collections.sort(all, new Comparator<Metar>() {
            @Override
            public int compare(Metar a, Metar b) {
                return Long.compare(b.obs, a.obs);
            }
        });
        e.metars = all;
    }

    private static void prune(Station.Edit e, long now) {
        long cutoff = now - KEEP_METARS;
        List<Metar> keep = new ArrayList<>(e.metars.size());
        for (Metar m : e.metars) {
            if (m.obs >= cutoff) {
                keep.add(m);
            }
        }
        e.metars = keep;
    }

    // ------------------------------------------------------------ TAF

    private boolean tafs(List<String> ids) {
        final long now = clock.nowSec();
        try {
            final Map<String, List<Taf>> all = Feeds.parseTafs(http.get(Feeds.tafUrl(ids)));
            for (final String id : ids) {
                store.update(id, new Store.Change() {
                    @Override
                    public void apply(Station.Edit e) {
                        List<Taf> list = new ArrayList<>(e.tafs);
                        List<Taf> got = all.get(id);
                        if (got != null) {
                            for (Taf t : got) {
                                boolean known = false;
                                for (Taf x : list) {
                                    if (x.issued == t.issued && x.raw.equals(t.raw)) {
                                        known = true;
                                        break;
                                    }
                                }
                                if (!known) {
                                    list.add(t);
                                }
                            }
                        }
                        Collections.sort(list, new Comparator<Taf>() {
                            @Override
                            public int compare(Taf a, Taf b) {
                                int c = Long.compare(b.issued, a.issued);
                                return c != 0 ? c : Long.compare(b.validFrom, a.validFrom);
                            }
                        });
                        List<Taf> keep = new ArrayList<>();
                        for (int i = 0; i < list.size(); i++) {
                            if (i == 0 || list.get(i).issued >= now - KEEP_TAFS) {
                                keep.add(list.get(i));
                            }
                        }
                        e.tafs = keep;
                        e.tafAt = now;
                    }
                });
            }
            return true;
        } catch (IOException | RuntimeException ex) {
            wxFailed(ids, describe(ex));
            return false;
        }
    }

    // ------------------------------------------------------------ NOTAM

    /** NOTAMs come from NAV CANADA, so only Canadian aerodromes are fetched. */
    private boolean notams(final String id) {
        Station st = store.get(id);
        if (st == null || !"CA".equals(st.country())) {
            return true;
        }
        final long now = clock.nowSec();
        try {
            pause();
            final List<Notam> list = Feeds.parseNotams(http.get(Feeds.notamUrl(id)), id);
            store.update(id, new Store.Change() {
                @Override
                public void apply(Station.Edit e) {
                    e.notams = list;
                    e.notamAt = now;
                    e.notamError = null;
                }
            });
            return true;
        } catch (IOException | RuntimeException ex) {
            final String msg = describe(ex);
            store.update(id, new Store.Change() {
                @Override
                public void apply(Station.Edit e) {
                    e.notamError = msg;
                }
            });
            return false;
        }
    }

    // ------------------------------------------------------------ helpers

    private void pause() {
        if (pauseMs <= 0) {
            return;
        }
        try {
            Thread.sleep(pauseMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void changed() {
        Listener l = listener;
        if (l != null) {
            try {
                l.onChanged();
            } catch (RuntimeException ignored) {
                // a display problem must never break a download
            }
        }
    }

    static String describe(Exception ex) {
        if (ex instanceof UnknownHostException || ex instanceof java.net.ConnectException
                || ex instanceof java.net.NoRouteToHostException) {
            return "No internet connection";
        }
        if (ex instanceof SocketTimeoutException) {
            return "Server timed out";
        }
        if (ex instanceof javax.net.ssl.SSLException) {
            return "Secure connection failed";
        }
        String m = ex.getMessage();
        if (m == null || m.isEmpty()) {
            m = ex.getClass().getSimpleName();
        }
        return m.length() > 120 ? m.substring(0, 120) : m;
    }
}
