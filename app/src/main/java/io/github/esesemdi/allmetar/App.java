package io.github.esesemdi.allmetar;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.esesemdi.allmetar.core.Airport;
import io.github.esesemdi.allmetar.core.AirportIndex;
import io.github.esesemdi.allmetar.core.HttpFetcher;
import io.github.esesemdi.allmetar.core.Store;
import io.github.esesemdi.allmetar.core.Sync;

/** Process-wide singletons: storage, downloader, background thread and change notifications. */
public final class App {
    static final String USER_AGENT = "AllMetar/1 (Android; +https://github.com/esesemdi/allmetar)";
    static final String AIRPORTS_ASSET = "airports.tsv";

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService BG = Executors.newSingleThreadExecutor();
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    private static Store store;
    private static Sync sync;
    private static List<AirportIndex.Entry> airports;

    private App() {
    }

    public static synchronized Store store(Context c) {
        if (store == null) {
            store = new Store(new File(c.getApplicationContext().getFilesDir(), "data"));
        }
        return store;
    }

    public static synchronized Sync sync(Context c) {
        if (sync == null) {
            final Context app = c.getApplicationContext();
            sync = new Sync(store(app), new HttpFetcher(USER_AGENT, 10000, 20000),
                    () -> System.currentTimeMillis() / 1000L, 150);
            sync.setListener(() -> notifyChanged(app));
        }
        return sync;
    }

    /** Runs work on the single background thread. */
    public static void run(Runnable r) {
        BG.execute(() -> {
            try {
                r.run();
            } catch (RuntimeException ignored) {
                // never let a background failure take the app down
            }
        });
    }

    /** Updates everything in the background; if that fails, tries again once the connection is back. */
    public static void refreshAll(Context c) {
        final Context app = c.getApplicationContext();
        run(() -> {
            long started = now();
            sync(app).refreshAll();
            retryWhenOnlineIfFailed(app, started);
        });
    }

    /** Same as {@link #refreshAll(Context)} for a single location. */
    public static void refreshStation(Context c, final String id) {
        final Context app = c.getApplicationContext();
        run(() -> {
            long started = now();
            sync(app).refreshStation(id);
            retryWhenOnlineIfFailed(app, started);
        });
    }

    private static void retryWhenOnlineIfFailed(Context app, long started) {
        Store s = store(app);
        if (!s.ids().isEmpty() && s.lastRefresh() < started) {
            try {
                Scheduler.whenOnline(app);
            } catch (RuntimeException ignored) {
                // the hourly update will catch up
            }
        }
    }

    public static void ui(Runnable r) {
        MAIN.post(r);
    }

    public static void addListener(Runnable r) {
        LISTENERS.add(r);
    }

    public static void removeListener(Runnable r) {
        LISTENERS.remove(r);
    }

    /** Tells open screens and home-screen widgets that stored data changed. Callable from any thread. */
    public static void notifyChanged(Context c) {
        final Context app = c.getApplicationContext();
        MAIN.post(() -> {
            for (Runnable r : LISTENERS) {
                r.run();
            }
            MetarWidget.updateAll(app);
        });
    }

    public static long now() {
        return System.currentTimeMillis() / 1000L;
    }

    /** Bundled aerodrome list (search fields only). Slow on first call: use off the main thread. */
    public static synchronized List<AirportIndex.Entry> airports(Context c) throws IOException {
        if (airports == null) {
            try (InputStream in = c.getApplicationContext().getAssets().open(AIRPORTS_ASSET)) {
                airports = AirportIndex.loadEntries(in);
            }
        }
        return airports;
    }

    /** Full bundled record for one aerodrome, or null. Use off the main thread. */
    public static Airport airport(Context c, String id) {
        try (InputStream in = c.getApplicationContext().getAssets().open(AIRPORTS_ASSET)) {
            return AirportIndex.find(in, id);
        } catch (IOException e) {
            return null;
        }
    }
}
