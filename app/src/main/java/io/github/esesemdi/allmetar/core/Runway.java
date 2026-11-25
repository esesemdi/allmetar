package io.github.esesemdi.allmetar.core;

import java.util.LinkedHashMap;
import java.util.Map;

public final class Runway {
    public final String lowEnd;
    public final String highEnd;
    /** Feet as text; empty when unknown. */
    public final String lengthFt;
    public final String widthFt;
    public final String surface;
    public final boolean lighted;
    public final boolean closed;

    public Runway(String lowEnd, String highEnd, String lengthFt, String widthFt, String surface,
                  boolean lighted, boolean closed) {
        this.lowEnd = lowEnd;
        this.highEnd = highEnd;
        this.lengthFt = lengthFt;
        this.widthFt = widthFt;
        this.surface = surface;
        this.lighted = lighted;
        this.closed = closed;
    }

    public String designator() {
        if (highEnd.isEmpty()) {
            return lowEnd;
        }
        if (lowEnd.isEmpty()) {
            return highEnd;
        }
        return lowEnd + "/" + highEnd;
    }

    Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("l", lowEnd);
        m.put("h", highEnd);
        m.put("len", lengthFt);
        m.put("w", widthFt);
        m.put("s", surface);
        m.put("lgt", lighted);
        m.put("c", closed);
        return m;
    }

    static Runway fromJson(Map<String, Object> m) {
        return new Runway(Json.str(m, "l", ""), Json.str(m, "h", ""), Json.str(m, "len", ""), Json.str(m, "w", ""),
                Json.str(m, "s", ""), Json.bool(m, "lgt", false), Json.bool(m, "c", false));
    }
}
