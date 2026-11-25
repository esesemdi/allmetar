package io.github.esesemdi.allmetar.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Everything stored for one saved location. Immutable; change it through {@link #edit()}. */
public final class Station {
    public final String id;
    public final String name;
    /** May be null when the identifier is not in the bundled aerodrome data. */
    public final Airport airport;
    /** Newest first. */
    public final List<Metar> metars;
    /** Newest first. */
    public final List<Taf> tafs;
    public final List<Notam> notams;
    public final List<Freq> userFreqs;
    /** Epoch seconds of the last successful fetch of each product; 0 = never. */
    public final long metarAt;
    public final long tafAt;
    public final long notamAt;
    /** METAR history is complete up to this time (epoch seconds); 0 = week of history not loaded yet. */
    public final long histTo;
    /** Last weather fetch problem, or null. */
    public final String wxError;
    /** Last NOTAM fetch problem, or null. */
    public final String notamError;

    private Station(Edit e) {
        this.id = e.id;
        this.name = e.name == null ? "" : e.name;
        this.airport = e.airport;
        this.metars = Collections.unmodifiableList(new ArrayList<>(e.metars));
        this.tafs = Collections.unmodifiableList(new ArrayList<>(e.tafs));
        this.notams = Collections.unmodifiableList(new ArrayList<>(e.notams));
        this.userFreqs = Collections.unmodifiableList(new ArrayList<>(e.userFreqs));
        this.metarAt = e.metarAt;
        this.tafAt = e.tafAt;
        this.notamAt = e.notamAt;
        this.histTo = e.histTo;
        this.wxError = e.wxError;
        this.notamError = e.notamError;
    }

    public static Station create(String id, String name, Airport airport) {
        Edit e = new Edit();
        e.id = id;
        e.name = name;
        e.airport = airport;
        return e.build();
    }

    public Edit edit() {
        Edit e = new Edit();
        e.id = id;
        e.name = name;
        e.airport = airport;
        e.metars = new ArrayList<>(metars);
        e.tafs = new ArrayList<>(tafs);
        e.notams = new ArrayList<>(notams);
        e.userFreqs = new ArrayList<>(userFreqs);
        e.metarAt = metarAt;
        e.tafAt = tafAt;
        e.notamAt = notamAt;
        e.histTo = histTo;
        e.wxError = wxError;
        e.notamError = notamError;
        return e;
    }

    public static final class Edit {
        public String id;
        public String name;
        public Airport airport;
        public List<Metar> metars = new ArrayList<>();
        public List<Taf> tafs = new ArrayList<>();
        public List<Notam> notams = new ArrayList<>();
        public List<Freq> userFreqs = new ArrayList<>();
        public long metarAt;
        public long tafAt;
        public long notamAt;
        public long histTo;
        public String wxError;
        public String notamError;

        public Station build() {
            return new Station(this);
        }
    }

    /** Most recent observation, or null. */
    public Metar latestMetar() {
        return metars.isEmpty() ? null : metars.get(0);
    }

    /** Most recently issued forecast, or null. */
    public Taf latestTaf() {
        return tafs.isEmpty() ? null : tafs.get(0);
    }

    /** "CA", "US" or "" (other / unknown). */
    public String country() {
        if (airport != null && !airport.country.isEmpty()) {
            return airport.country;
        }
        return countryOfId(id);
    }

    /** Best guess of the country from an ICAO location indicator. */
    public static String countryOfId(String id) {
        if (id == null || id.length() != 4) {
            return "";
        }
        char c = id.charAt(0);
        if (c == 'C') {
            return "CA";
        }
        if (c == 'K') {
            return "US";
        }
        if (id.startsWith("PA") || id.startsWith("PH") || id.startsWith("PF") || id.startsWith("PO")
                || id.startsWith("PP")) {
            return "US";
        }
        return "";
    }

    Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        if (airport != null) {
            m.put("airport", airport.toJson());
        }
        List<Object> a = new ArrayList<>();
        for (Metar x : metars) {
            a.add(x.toJson());
        }
        m.put("metars", a);
        a = new ArrayList<>();
        for (Taf x : tafs) {
            a.add(x.toJson());
        }
        m.put("tafs", a);
        a = new ArrayList<>();
        for (Notam x : notams) {
            a.add(x.toJson());
        }
        m.put("notams", a);
        a = new ArrayList<>();
        for (Freq x : userFreqs) {
            a.add(x.toJson());
        }
        m.put("userFreqs", a);
        m.put("metarAt", metarAt);
        m.put("tafAt", tafAt);
        m.put("notamAt", notamAt);
        m.put("histTo", histTo);
        if (wxError != null) {
            m.put("wxError", wxError);
        }
        if (notamError != null) {
            m.put("notamError", notamError);
        }
        return m;
    }

    static Station fromJson(Map<String, Object> m) {
        Edit e = new Edit();
        e.id = Json.str(m, "id", "");
        e.name = Json.str(m, "name", "");
        if (m.get("airport") instanceof Map) {
            e.airport = Airport.fromJson(Json.obj(m.get("airport")));
        }
        for (Object o : Json.arr(m.get("metars"))) {
            e.metars.add(Metar.fromJson(Json.obj(o)));
        }
        for (Object o : Json.arr(m.get("tafs"))) {
            e.tafs.add(Taf.fromJson(Json.obj(o)));
        }
        for (Object o : Json.arr(m.get("notams"))) {
            e.notams.add(Notam.fromJson(Json.obj(o)));
        }
        for (Object o : Json.arr(m.get("userFreqs"))) {
            e.userFreqs.add(Freq.fromJson(Json.obj(o)));
        }
        e.metarAt = Json.lng(m, "metarAt", 0);
        e.tafAt = Json.lng(m, "tafAt", 0);
        e.notamAt = Json.lng(m, "notamAt", 0);
        e.histTo = Json.lng(m, "histTo", 0);
        e.wxError = Json.str(m, "wxError", null);
        e.notamError = Json.str(m, "notamError", null);
        return e.build();
    }
}
