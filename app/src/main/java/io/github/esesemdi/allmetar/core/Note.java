package io.github.esesemdi.allmetar.core;

import java.util.LinkedHashMap;
import java.util.Map;

/** A field the user added to an aerodrome: a heading and free text. */
public final class Note {
    public final String header;
    public final String text;

    public Note(String header, String text) {
        this.header = header == null ? "" : header;
        this.text = text == null ? "" : text;
    }

    Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("h", header);
        m.put("t", text);
        return m;
    }

    static Note fromJson(Map<String, Object> m) {
        return new Note(Json.str(m, "h", ""), Json.str(m, "t", ""));
    }
}
