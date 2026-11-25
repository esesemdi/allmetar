package io.github.esesemdi.allmetar.core;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * URLs and parsers for the two data feeds:
 * the Aviation Weather Center data API (METAR, TAF) and NAV CANADA's CFPS weather API (NOTAM).
 */
public final class Feeds {
    public static final String AWC = "https://aviationweather.gov/api/data/";
    public static final String CFPS = "https://plan.navcanada.ca/weather/api/alpha/";
    /** The AWC API returns at most this many entries per request. */
    public static final int AWC_MAX_ENTRIES = 400;

    private static final DateTimeFormatter AWC_DATE =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmm").withZone(ZoneOffset.UTC);
    private static final Pattern NOTAM_A_FIELD = Pattern.compile("\\bA\\)\\s*([A-Z0-9 ]+?)\\s+B\\)");

    private Feeds() {
    }

    /** A feed answered, but not with data in the expected shape. */
    public static final class FeedException extends IOException {
        public FeedException(String message) {
            super(message);
        }
    }

    // ------------------------------------------------------------ URLs

    /**
     * @param hours  how far back to look
     * @param endSec end of the window in epoch seconds, or 0 for "now"
     */
    public static String metarUrl(List<String> ids, int hours, long endSec) {
        StringBuilder sb = new StringBuilder(AWC).append("metar?ids=").append(join(ids))
                .append("&format=json&hours=").append(hours);
        if (endSec > 0) {
            sb.append("&date=").append(AWC_DATE.format(Instant.ofEpochSecond(endSec)));
        }
        return sb.toString();
    }

    public static String tafUrl(List<String> ids) {
        return AWC + "taf?ids=" + join(ids) + "&format=json";
    }

    public static String notamUrl(String id) {
        return CFPS + "?site=" + id + "&alpha=notam";
    }

    private static String join(List<String> ids) {
        StringBuilder sb = new StringBuilder();
        for (String id : ids) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(id);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------ METAR

    public static final class MetarBatch {
        /** Reports per station identifier, in feed order (newest first). */
        public final Map<String, List<Metar>> byStation = new HashMap<>();
        /** Station names as given by the feed. */
        public final Map<String, String> names = new HashMap<>();
        public int count;
    }

    public static MetarBatch parseMetars(String body) throws FeedException {
        MetarBatch out = new MetarBatch();
        List<Object> items = topLevelArray(body, "METAR");
        for (Object o : items) {
            Map<String, Object> m = Json.obj(o);
            String id = Json.str(m, "icaoId", "").trim().toUpperCase(java.util.Locale.US);
            long obs = Json.lng(m, "obsTime", 0);
            String raw = Json.str(m, "rawOb", "").trim().replaceAll("\\s+", " ");
            if (id.isEmpty() || obs <= 0 || raw.isEmpty()) {
                continue;
            }
            boolean speci = "SPECI".equalsIgnoreCase(Json.str(m, "metarType", "")) || raw.startsWith("SPECI ");
            if (raw.startsWith("METAR ")) {
                raw = raw.substring(6);
            }
            List<Metar> list = out.byStation.get(id);
            if (list == null) {
                list = new ArrayList<>();
                out.byStation.put(id, list);
            }
            list.add(new Metar(obs, raw, speci));
            out.count++;
            String name = Json.str(m, "name", "").trim();
            if (!name.isEmpty()) {
                out.names.put(id, name);
            }
        }
        return out;
    }

    // ------------------------------------------------------------ TAF

    public static Map<String, List<Taf>> parseTafs(String body) throws FeedException {
        Map<String, List<Taf>> out = new HashMap<>();
        for (Object o : topLevelArray(body, "TAF")) {
            Map<String, Object> m = Json.obj(o);
            String id = Json.str(m, "icaoId", "").trim().toUpperCase(java.util.Locale.US);
            String raw = Json.str(m, "rawTAF", "").trim().replaceAll("\\s+", " ");
            if (id.isEmpty() || raw.isEmpty()) {
                continue;
            }
            long from = Json.lng(m, "validTimeFrom", 0);
            long to = Json.lng(m, "validTimeTo", 0);
            long issued = isoToEpoch(Json.str(m, "issueTime", null));
            if (issued <= 0) {
                issued = isoToEpoch(Json.str(m, "bulletinTime", null));
            }
            if (issued <= 0) {
                issued = from;
            }
            List<Taf> list = out.get(id);
            if (list == null) {
                list = new ArrayList<>();
                out.put(id, list);
            }
            list.add(new Taf(issued, from, to, raw));
        }
        return out;
    }

    static long isoToEpoch(String iso) {
        if (iso == null || iso.isEmpty()) {
            return 0;
        }
        try {
            return Instant.parse(iso).getEpochSecond();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    // ------------------------------------------------------------ NOTAM (NAV CANADA)

    /** Parses a CFPS answer for one site. Aerodrome NOTAMs come first, then FIR / area ones. */
    public static List<Notam> parseNotams(String body, String id) throws FeedException {
        if (body == null || body.trim().isEmpty()) {
            throw new FeedException("Empty NOTAM response");
        }
        Object root;
        try {
            root = Json.parse(body);
        } catch (Json.JsonException e) {
            throw new FeedException("Unreadable NOTAM response");
        }
        Map<String, Object> top = Json.obj(root);
        if (!(top.get("data") instanceof List)) {
            throw new FeedException("Unexpected NOTAM response");
        }
        List<Notam> aerodrome = new ArrayList<>();
        List<Notam> area = new ArrayList<>();
        for (Object o : Json.arr(top.get("data"))) {
            Map<String, Object> m = Json.obj(o);
            if (!"notam".equalsIgnoreCase(Json.str(m, "type", "notam"))) {
                continue;
            }
            String text = Json.str(m, "text", "");
            String raw = text;
            if (text.trim().startsWith("{")) {
                try {
                    String r = Json.str(Json.obj(Json.parse(text)), "raw", null);
                    if (r != null) {
                        raw = r;
                    }
                } catch (Json.JsonException ignored) {
                    // keep the text as received
                }
            }
            raw = raw.replace("\r", "").trim();
            if (raw.isEmpty()) {
                continue;
            }
            String location = Json.str(m, "location", "");
            boolean atAerodrome = id.equalsIgnoreCase(location.trim());
            if (!atAerodrome) {
                Matcher a = NOTAM_A_FIELD.matcher(raw);
                if (a.find()) {
                    for (String tok : a.group(1).trim().split("\\s+")) {
                        if (tok.equalsIgnoreCase(id)) {
                            atAerodrome = true;
                            break;
                        }
                    }
                }
            }
            Notam n = new Notam(Json.str(m, "pk", ""), raw, atAerodrome);
            (atAerodrome ? aerodrome : area).add(n);
        }
        aerodrome.addAll(area);
        return aerodrome;
    }

    // ------------------------------------------------------------ shared

    private static List<Object> topLevelArray(String body, String what) throws FeedException {
        if (body == null || body.trim().isEmpty()) {
            return new ArrayList<>();
        }
        Object root;
        try {
            root = Json.parse(body);
        } catch (Json.JsonException e) {
            throw new FeedException("Unreadable " + what + " response");
        }
        if (root instanceof List) {
            return Json.arr(root);
        }
        Map<String, Object> m = Json.obj(root);
        String err = Json.str(m, "error", Json.str(m, "message", null));
        throw new FeedException(err != null ? err : "Unexpected " + what + " response");
    }
}
