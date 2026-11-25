package io.github.esesemdi.allmetar.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Aerodrome reference data from the bundled OurAirports extract. */
public final class Airport {
    public final String id;
    public final String name;
    public final String city;
    public final String region;
    public final String country;
    /** Feet as text; empty when unknown. */
    public final String elevFt;
    public final String lat;
    public final String lon;
    /** L, M, S (airport size) or W (water aerodrome). */
    public final String type;
    /** Other codes the aerodrome is known by (IATA, local), space separated. */
    public final String others;
    public final List<Runway> runways;
    public final List<Freq> freqs;

    public Airport(String id, String name, String city, String region, String country, String elevFt,
                   String lat, String lon, String type, String others, List<Runway> runways, List<Freq> freqs) {
        this.id = id;
        this.name = name;
        this.city = city;
        this.region = region;
        this.country = country;
        this.elevFt = elevFt;
        this.lat = lat;
        this.lon = lon;
        this.type = type;
        this.others = others;
        this.runways = Collections.unmodifiableList(new ArrayList<>(runways));
        this.freqs = Collections.unmodifiableList(new ArrayList<>(freqs));
    }

    /** "Winnipeg, MB" style place line; may be empty. */
    public String place() {
        StringBuilder sb = new StringBuilder();
        if (!city.isEmpty()) {
            sb.append(city);
        }
        if (!region.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(region);
        }
        return sb.toString();
    }

    Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("city", city);
        m.put("region", region);
        m.put("country", country);
        m.put("elev", elevFt);
        m.put("lat", lat);
        m.put("lon", lon);
        m.put("type", type);
        m.put("others", others);
        List<Object> r = new ArrayList<>();
        for (Runway x : runways) {
            r.add(x.toJson());
        }
        m.put("runways", r);
        List<Object> f = new ArrayList<>();
        for (Freq x : freqs) {
            f.add(x.toJson());
        }
        m.put("freqs", f);
        return m;
    }

    static Airport fromJson(Map<String, Object> m) {
        List<Runway> r = new ArrayList<>();
        for (Object o : Json.arr(m.get("runways"))) {
            r.add(Runway.fromJson(Json.obj(o)));
        }
        List<Freq> f = new ArrayList<>();
        for (Object o : Json.arr(m.get("freqs"))) {
            f.add(Freq.fromJson(Json.obj(o)));
        }
        return new Airport(Json.str(m, "id", ""), Json.str(m, "name", ""), Json.str(m, "city", ""),
                Json.str(m, "region", ""), Json.str(m, "country", ""), Json.str(m, "elev", ""),
                Json.str(m, "lat", ""), Json.str(m, "lon", ""), Json.str(m, "type", ""),
                Json.str(m, "others", ""), r, f);
    }
}
