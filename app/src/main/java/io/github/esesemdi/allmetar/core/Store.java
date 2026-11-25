package io.github.esesemdi.allmetar.core;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * On-disk storage: one small JSON file per saved location plus an index file.
 * All access goes through one instance per directory and is thread safe.
 */
public final class Store {
    public static final int MAX_STATIONS = 10;

    public interface Change {
        void apply(Station.Edit e);
    }

    private final File dir;
    private final Object lock = new Object();
    private final Map<String, Station> cache = new HashMap<>();
    private boolean loaded;
    private final List<String> order = new ArrayList<>();
    private final Map<String, String> widgets = new LinkedHashMap<>();
    private long lastRefresh;

    public Store(File dir) {
        this.dir = dir;
    }

    // ------------------------------------------------------------ station list

    public List<String> ids() {
        synchronized (lock) {
            load();
            return new ArrayList<>(order);
        }
    }

    public List<Station> stations() {
        synchronized (lock) {
            load();
            List<Station> out = new ArrayList<>();
            for (String id : order) {
                Station s = getLocked(id);
                if (s != null) {
                    out.add(s);
                }
            }
            return out;
        }
    }

    public Station get(String id) {
        synchronized (lock) {
            load();
            return order.contains(id) ? getLocked(id) : null;
        }
    }

    public boolean isFull() {
        synchronized (lock) {
            load();
            return order.size() >= MAX_STATIONS;
        }
    }

    /** Adds a location at the end of the list. Returns false when the list is full or it is already saved. */
    public boolean add(Station s) {
        synchronized (lock) {
            load();
            if (!validId(s.id) || order.contains(s.id) || order.size() >= MAX_STATIONS) {
                return false;
            }
            order.add(s.id);
            cache.put(s.id, s);
            writeStation(s);
            writeIndex();
            return true;
        }
    }

    public void remove(String id) {
        synchronized (lock) {
            load();
            if (!order.remove(id)) {
                return;
            }
            cache.remove(id);
            //noinspection ResultOfMethodCallIgnored
            fileFor(id).delete();
            writeIndex();
        }
    }

    /** Moves a location up (-1) or down (+1) in the list. */
    public void move(String id, int delta) {
        synchronized (lock) {
            load();
            int i = order.indexOf(id);
            int j = i + delta;
            if (i < 0 || j < 0 || j >= order.size()) {
                return;
            }
            order.set(i, order.get(j));
            order.set(j, id);
            writeIndex();
        }
    }

    /** Applies a change to a saved location. Returns the new value, or null if it is no longer saved. */
    public Station update(String id, Change change) {
        synchronized (lock) {
            load();
            if (!order.contains(id)) {
                return null;
            }
            Station cur = getLocked(id);
            if (cur == null) {
                cur = Station.create(id, "", null);
            }
            Station.Edit e = cur.edit();
            change.apply(e);
            e.id = id;
            Station next = e.build();
            cache.put(id, next);
            writeStation(next);
            return next;
        }
    }

    // ------------------------------------------------------------ misc state

    /** Epoch seconds of the last successful weather refresh; 0 = never. */
    public long lastRefresh() {
        synchronized (lock) {
            load();
            return lastRefresh;
        }
    }

    public void setLastRefresh(long sec) {
        synchronized (lock) {
            load();
            lastRefresh = sec;
            writeIndex();
        }
    }

    /** Location shown by a home-screen widget, or null. */
    public String widgetStation(int widgetId) {
        synchronized (lock) {
            load();
            return widgets.get(String.valueOf(widgetId));
        }
    }

    public void setWidgetStation(int widgetId, String stationId) {
        synchronized (lock) {
            load();
            if (stationId == null) {
                widgets.remove(String.valueOf(widgetId));
            } else {
                widgets.put(String.valueOf(widgetId), stationId);
            }
            writeIndex();
        }
    }

    public static boolean validId(String id) {
        if (id == null || id.length() < 3 || id.length() > 8) {
            return false;
        }
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            boolean ok = (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------ files

    private File fileFor(String id) {
        return new File(dir, "st_" + id + ".json");
    }

    private void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        String text = read(new File(dir, "index.json"));
        if (text == null) {
            return;
        }
        try {
            Map<String, Object> m = Json.obj(Json.parse(text));
            for (Object o : Json.arr(m.get("order"))) {
                if (o instanceof String && validId((String) o) && !order.contains(o)
                        && order.size() < MAX_STATIONS) {
                    order.add((String) o);
                }
            }
            for (Map.Entry<String, Object> e : Json.obj(m.get("widgets")).entrySet()) {
                if (e.getValue() instanceof String) {
                    widgets.put(e.getKey(), (String) e.getValue());
                }
            }
            lastRefresh = Json.lng(m, "lastRefresh", 0);
        } catch (RuntimeException e) {
            // Unreadable index: start with an empty list rather than crash.
        }
    }

    private Station getLocked(String id) {
        Station s = cache.get(id);
        if (s != null) {
            return s;
        }
        String text = read(fileFor(id));
        if (text != null) {
            try {
                s = Station.fromJson(Json.obj(Json.parse(text)));
            } catch (RuntimeException e) {
                s = null;
            }
        }
        if (s == null || !id.equals(s.id)) {
            s = Station.create(id, "", null);
        }
        cache.put(id, s);
        return s;
    }

    private void writeIndex() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("order", new ArrayList<Object>(order));
        m.put("widgets", new LinkedHashMap<String, Object>(widgets));
        m.put("lastRefresh", lastRefresh);
        write(new File(dir, "index.json"), Json.write(m));
    }

    private void writeStation(Station s) {
        write(fileFor(s.id), Json.write(s.toJson()));
    }

    private static String read(File f) {
        try {
            if (!f.isFile()) {
                return null;
            }
            return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    private static void write(File f, String text) {
        File tmp = new File(f.getParentFile(), f.getName() + ".tmp");
        try {
            Files.write(tmp.toPath(), text.getBytes(StandardCharsets.UTF_8));
            try {
                Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailed) {
                Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            // Storage full or unavailable: keep the in-memory copy; the next write retries.
        }
    }
}
