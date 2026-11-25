package io.github.esesemdi.allmetar.core;

import java.util.LinkedHashMap;
import java.util.Map;

public final class Freq {
    /** Short type, e.g. TWR, GND, ATIS. */
    public final String type;
    /** Longer description; may equal the type or be empty. */
    public final String desc;
    /** Frequency in MHz, as text. */
    public final String mhz;

    public Freq(String type, String desc, String mhz) {
        this.type = type;
        this.desc = desc;
        this.mhz = mhz;
    }

    /** "118.3" -> "118.300"; values that are not plain numbers are returned unchanged. */
    public String mhzFormatted() {
        try {
            double d = Double.parseDouble(mhz);
            String s = String.format(java.util.Locale.US, "%.3f", d);
            return s;
        } catch (NumberFormatException e) {
            return mhz;
        }
    }

    Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("t", type);
        m.put("d", desc);
        m.put("f", mhz);
        return m;
    }

    static Freq fromJson(Map<String, Object> m) {
        return new Freq(Json.str(m, "t", ""), Json.str(m, "d", ""), Json.str(m, "f", ""));
    }
}
