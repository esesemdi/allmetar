package io.github.esesemdi.allmetar.core;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Weather watch: targets the user sets (for example "ceiling at or below 3000 ft"). When the watch
 * is on, a report whose conditions meet any one target is flagged. Immutable.
 */
public final class Watch {
    public static final int CEILING = 0;
    public static final int VISIBILITY = 1;
    public static final int TEMPERATURE = 2;
    public static final int WIND = 3;
    public static final int GUST = 4;
    public static final int COUNT = 5;

    private static final String[] KEYS = {"ceiling", "visibility", "temperature", "wind", "gust"};

    public final boolean on;
    private final double[] value;   // NaN = not set
    private final boolean[] below;  // true: "at or below", false: "at or above"

    private Watch(boolean on, double[] value, boolean[] below) {
        this.on = on;
        this.value = value;
        this.below = below;
    }

    /** Watch switched off with no targets. */
    public static Watch none() {
        double[] v = new double[COUNT];
        boolean[] b = new boolean[COUNT];
        for (int i = 0; i < COUNT; i++) {
            v[i] = Double.NaN;
            b[i] = defaultBelow(i);
        }
        return new Watch(false, v, b);
    }

    /** Low ceiling, visibility and temperature are what one usually watches for; high wind and gusts. */
    public static boolean defaultBelow(int parameter) {
        return parameter != WIND && parameter != GUST;
    }

    public boolean isSet(int parameter) {
        return !Double.isNaN(value[parameter]);
    }

    public double value(int parameter) {
        return value[parameter];
    }

    /** True for "at or below", false for "at or above". */
    public boolean below(int parameter) {
        return below[parameter];
    }

    public boolean anySet() {
        for (int i = 0; i < COUNT; i++) {
            if (isSet(i)) {
                return true;
            }
        }
        return false;
    }

    /** True when the watch is on and has at least one target. */
    public boolean active() {
        return on && anySet();
    }

    public Watch withOn(boolean nowOn) {
        return new Watch(nowOn, value.clone(), below.clone());
    }

    public Watch with(int parameter, double target, boolean atOrBelow) {
        double[] v = value.clone();
        boolean[] b = below.clone();
        v[parameter] = target;
        b[parameter] = atOrBelow;
        return new Watch(on, v, b);
    }

    public Watch without(int parameter) {
        double[] v = value.clone();
        v[parameter] = Double.NaN;
        return new Watch(on, v, below.clone());
    }

    /** True when the watch is on and the conditions meet at least one target. */
    public boolean matches(Conditions c) {
        return firstMatch(c) >= 0;
    }

    /** The first target the conditions meet, or -1 (also when the watch is off). */
    public int firstMatch(Conditions c) {
        if (!on || c == null) {
            return -1;
        }
        for (int p = 0; p < COUNT; p++) {
            if (isSet(p) && meets(p, c)) {
                return p;
            }
        }
        return -1;
    }

    private boolean meets(int p, Conditions c) {
        double v = value[p];
        switch (p) {
            case CEILING:
                if (!c.cloudKnown()) {
                    return false;
                }
                if (below[p]) {
                    return c.hasCeiling() && c.ceilingFt <= v;
                }
                return !c.hasCeiling() || c.ceilingFt >= v;
            case VISIBILITY:
                return c.visibilityKnown() && compare(c.visibilitySm, v, below[p]);
            case TEMPERATURE:
                return c.tempC != Conditions.NO_TEMP && compare(c.tempC, v, below[p]);
            case WIND:
                return c.windKt >= 0 && compare(c.windKt, v, below[p]);
            case GUST:
                return c.gustKt >= 0 && compare(c.gustKt, v, below[p]);
            default:
                return false;
        }
    }

    private static boolean compare(double actual, double target, boolean atOrBelow) {
        return atOrBelow ? actual <= target : actual >= target;
    }

    Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("on", on);
        for (int i = 0; i < COUNT; i++) {
            if (isSet(i)) {
                Map<String, Object> t = new LinkedHashMap<>();
                t.put("value", value[i]);
                t.put("below", below[i]);
                m.put(KEYS[i], t);
            }
        }
        return m;
    }

    static Watch fromJson(Map<String, Object> m) {
        Watch w = none().withOn(Json.bool(m, "on", false));
        for (int i = 0; i < COUNT; i++) {
            if (m.get(KEYS[i]) instanceof Map) {
                Map<String, Object> t = Json.obj(m.get(KEYS[i]));
                Object v = t.get("value");
                if (v instanceof Number) {
                    w = w.with(i, ((Number) v).doubleValue(), Json.bool(t, "below", defaultBelow(i)));
                }
            }
        }
        return w;
    }
}
