package io.github.esesemdi.allmetar.core;

import java.util.LinkedHashMap;
import java.util.Map;

/** One terminal forecast, kept as issued. */
public final class Taf {
    /** Issue time, epoch seconds (UTC). */
    public final long issued;
    public final long validFrom;
    public final long validTo;
    public final String raw;

    public Taf(long issued, long validFrom, long validTo, String raw) {
        this.issued = issued;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.raw = raw;
    }

    Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("i", issued);
        m.put("f", validFrom);
        m.put("e", validTo);
        m.put("r", raw);
        return m;
    }

    static Taf fromJson(Map<String, Object> m) {
        return new Taf(Json.lng(m, "i", 0), Json.lng(m, "f", 0), Json.lng(m, "e", 0), Json.str(m, "r", ""));
    }
}
