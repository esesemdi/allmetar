package io.github.esesemdi.allmetar.core;

import java.util.LinkedHashMap;
import java.util.Map;

/** One NOTAM, kept as issued. */
public final class Notam {
    public final String key;
    public final String raw;
    /** True when the NOTAM is filed against the aerodrome itself rather than its FIR / area. */
    public final boolean aerodrome;

    public Notam(String key, String raw, boolean aerodrome) {
        this.key = key;
        this.raw = raw;
        this.aerodrome = aerodrome;
    }

    Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("k", key);
        m.put("r", raw);
        m.put("a", aerodrome);
        return m;
    }

    static Notam fromJson(Map<String, Object> m) {
        return new Notam(Json.str(m, "k", ""), Json.str(m, "r", ""), Json.bool(m, "a", false));
    }
}
