package io.github.esesemdi.allmetar.core;

/** Lays a raw TAF out one change group per line. Only whitespace is changed. */
public final class TafFormat {
    private TafFormat() {
    }

    public static String lines(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim().replaceAll("\\s+", " ");
        s = s.replaceAll(" (?=(FM\\d{6}\\b|BECMG |TEMPO |PROB\\d{2} |RMK ))", "\n  ");
        // "PROB30 TEMPO ..." is one group
        s = s.replaceAll("(PROB\\d{2})\n {2}(TEMPO )", "$1 $2");
        return s;
    }
}
