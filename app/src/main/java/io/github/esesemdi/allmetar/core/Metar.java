package io.github.esesemdi.allmetar.core;

import java.util.LinkedHashMap;
import java.util.Map;

/** One surface observation, kept as issued. */
public final class Metar {
    /** Observation time, epoch seconds (UTC). */
    public final long obs;
    /** Raw report text, without the leading "METAR" keyword; SPECI reports keep theirs. */
    public final String raw;
    public final boolean speci;

    public Metar(long obs, String raw, boolean speci) {
        this.obs = obs;
        this.raw = raw;
        this.speci = speci;
    }

    Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("t", obs);
        m.put("r", raw);
        if (speci) {
            m.put("s", Boolean.TRUE);
        }
        return m;
    }

    static Metar fromJson(Map<String, Object> m) {
        return new Metar(Json.lng(m, "t", 0), Json.str(m, "r", ""), Json.bool(m, "s", false));
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Metar)) {
            return false;
        }
        Metar x = (Metar) o;
        return obs == x.obs && raw.equals(x.raw);
    }

    @Override
    public int hashCode() {
        return Long.hashCode(obs) * 31 + raw.hashCode();
    }
}
