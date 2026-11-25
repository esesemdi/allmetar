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
    /** The user's own version of the frequency list; null while the built-in list is unchanged. */
    public final List<Freq> freqsEdit;
    /** The user's own version of the runway list; null while the built-in list is unchanged. */
    public final List<Runway> runwaysEdit;
    /** Elevation in feet as entered by the user; null while the built-in value is unchanged. */
    public final String elevEdit;
    /** Fields the user added (heading + text). */
    public final List<Note> notes;
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
        this.freqsEdit = e.freqsEdit == null ? null : Collections.unmodifiableList(new ArrayList<>(e.freqsEdit));
        this.runwaysEdit = e.runwaysEdit == null ? null
                : Collections.unmodifiableList(new ArrayList<>(e.runwaysEdit));
        this.elevEdit = e.elevEdit;
        this.notes = Collections.unmodifiableList(new ArrayList<>(e.notes));
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
        e.freqsEdit = freqsEdit == null ? null : new ArrayList<>(freqsEdit);
        e.runwaysEdit = runwaysEdit == null ? null : new ArrayList<>(runwaysEdit);
        e.elevEdit = elevEdit;
        e.notes = new ArrayList<>(notes);
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
        public List<Freq> freqsEdit;
        public List<Runway> runwaysEdit;
        public String elevEdit;
        public List<Note> notes = new ArrayList<>();

        /** The frequency list ready to be changed: the user's version, started from the built-in one. */
        public List<Freq> editableFreqs() {
            if (freqsEdit == null) {
                freqsEdit = airport == null ? new ArrayList<Freq>() : new ArrayList<>(airport.freqs);
            }
            return freqsEdit;
        }

        /** The runway list ready to be changed: the user's version, started from the built-in one. */
        public List<Runway> editableRunways() {
            if (runwaysEdit == null) {
                runwaysEdit = airport == null ? new ArrayList<Runway>() : new ArrayList<>(airport.runways);
            }
            return runwaysEdit;
        }

        /** Drops the user's changes to frequencies, runways and elevation. Added fields are kept. */
        public void resetToBuiltIn() {
            freqsEdit = null;
            runwaysEdit = null;
            elevEdit = null;
        }
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

    /** Frequencies to show: the user's version if they changed the list, otherwise the built-in one. */
    public List<Freq> freqs() {
        if (freqsEdit != null) {
            return freqsEdit;
        }
        return airport == null ? Collections.<Freq>emptyList() : airport.freqs;
    }

    /** Runways to show: the user's version if they changed the list, otherwise the built-in one. */
    public List<Runway> runways() {
        if (runwaysEdit != null) {
            return runwaysEdit;
        }
        return airport == null ? Collections.<Runway>emptyList() : airport.runways;
    }

    /** Elevation in feet as text; empty when unknown. */
    public String elevFt() {
        if (elevEdit != null) {
            return elevEdit;
        }
        return airport == null ? "" : airport.elevFt;
    }

    /** True when the user changed frequencies, runways or elevation. */
    public boolean hasEdits() {
        return freqsEdit != null || runwaysEdit != null || elevEdit != null;
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
        if (freqsEdit != null) {
            a = new ArrayList<>();
            for (Freq x : freqsEdit) {
                a.add(x.toJson());
            }
            m.put("freqsEdit", a);
        }
        if (runwaysEdit != null) {
            a = new ArrayList<>();
            for (Runway x : runwaysEdit) {
                a.add(x.toJson());
            }
            m.put("runwaysEdit", a);
        }
        if (elevEdit != null) {
            m.put("elevEdit", elevEdit);
        }
        a = new ArrayList<>();
        for (Note x : notes) {
            a.add(x.toJson());
        }
        m.put("notes", a);
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
        if (m.get("freqsEdit") instanceof List) {
            e.freqsEdit = new ArrayList<>();
            for (Object o : Json.arr(m.get("freqsEdit"))) {
                e.freqsEdit.add(Freq.fromJson(Json.obj(o)));
            }
        }
        if (m.get("runwaysEdit") instanceof List) {
            e.runwaysEdit = new ArrayList<>();
            for (Object o : Json.arr(m.get("runwaysEdit"))) {
                e.runwaysEdit.add(Runway.fromJson(Json.obj(o)));
            }
        }
        e.elevEdit = Json.str(m, "elevEdit", null);
        for (Object o : Json.arr(m.get("notes"))) {
            e.notes.add(Note.fromJson(Json.obj(o)));
        }
        // Version 1.0.4 and earlier kept frequencies added by the user in a separate list.
        for (Object o : Json.arr(m.get("userFreqs"))) {
            Freq f = Freq.fromJson(Json.obj(o));
            e.editableFreqs().add(new Freq(f.type, f.desc, f.mhz, true));
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
