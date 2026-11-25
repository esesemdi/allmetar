package io.github.esesemdi.allmetar.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Plain-language meaning of one word (group) of a raw METAR, TAF or NOTAM.
 *
 * The report is passed as its whitespace-separated words plus the index of the word that was
 * pressed, because the meaning of a group often depends on where it stands. A {@link Hit} may
 * cover more than one word (for example "2 1/2SM"). Returns null when there is nothing to say.
 */
public final class Decode {
    public static final int METAR = 0;
    public static final int TAF = 1;
    public static final int NOTAM = 2;

    private Decode() {
    }

    public static final class Hit {
        /** First and last word index (inclusive) that the explanation covers. */
        public final int from;
        public final int to;
        public final String text;

        Hit(int from, int to, String text) {
            this.from = from;
            this.to = to;
            this.text = text;
        }
    }

    public static Hit decode(int kind, String[] words, int index) {
        if (words == null || index < 0 || index >= words.length) {
            return null;
        }
        try {
            switch (kind) {
                case METAR:
                    return metar(words, index);
                case TAF:
                    return taf(words, index);
                case NOTAM:
                    return notam(words, index);
                default:
                    return null;
            }
        } catch (RuntimeException e) {
            return null; // an odd group must never break the screen
        }
    }

    /** Start and end offsets of each whitespace-separated word in {@code text}. */
    public static int[][] wordRanges(String text) {
        List<int[]> out = new ArrayList<>();
        int n = text.length();
        int i = 0;
        while (i < n) {
            while (i < n && Character.isWhitespace(text.charAt(i))) {
                i++;
            }
            int start = i;
            while (i < n && !Character.isWhitespace(text.charAt(i))) {
                i++;
            }
            if (i > start) {
                out.add(new int[]{start, i});
            }
        }
        return out.toArray(new int[0][]);
    }

    // ================================================================ METAR

    private static final Pattern STATION = Pattern.compile("[A-Z][A-Z0-9]{3}");
    private static final Pattern DAY_TIME = Pattern.compile("(\\d{2})(\\d{2})(\\d{2})Z");
    private static final Pattern WHOLE = Pattern.compile("\\d{1,2}");
    private static final Pattern FRACTION_SM = Pattern.compile("(\\d)/(\\d{1,2})SM");

    private static Hit metar(String[] w, int i) {
        String t = clean(w[i]);
        int rmk = indexOf(w, "RMK");
        if (i == rmk) {
            return one(i, "Remarks follow: extra detail added by the observer or station.");
        }
        if (rmk >= 0 && i > rmk) {
            return remark(w, i, rmk, false);
        }
        if (t.equals("METAR")) {
            return one(i, "Routine weather report, issued every hour.");
        }
        if (t.equals("SPECI")) {
            return one(i, "Special report, issued between routine reports because conditions changed significantly.");
        }
        if (isStation(w, i)) {
            return one(i, "Station identifier: where the observation was made.");
        }
        Matcher m = DAY_TIME.matcher(t);
        if (m.matches()) {
            return one(i, "Observed on the " + ordinal(num(m.group(1))) + " at " + m.group(2) + ":" + m.group(3) + " UTC.");
        }
        switch (t) {
            case "AUTO":
                return one(i, "Fully automated observation: no human observer checked it.");
            case "COR":
                return one(i, "Corrected report: replaces an earlier one for the same time.");
            case "RTD":
                return one(i, "Routine report sent late.");
            case "NIL":
                return one(i, "No report available.");
            case "NOSIG":
                return one(i, "No significant change expected in the next 2 hours.");
            default:
                break;
        }
        if (t.matches("CC[A-Z]")) {
            int n = t.charAt(2) - 'A' + 1;
            return one(i, "Corrected report: " + ordinal(n) + " correction to the report for this time.");
        }
        // wind shear: WS RWY 36 / WS ALL RWY
        Hit ws = windShearRunway(w, i);
        if (ws != null) {
            return ws;
        }
        Hit h = conditions(w, i, true);
        if (h != null) {
            return h;
        }
        m = Pattern.compile("(M)?(\\d{2})/(?:(M)?(\\d{2}))?").matcher(t);
        if (m.matches()) {
            String s = "Temperature " + celsius(m.group(1) != null, m.group(2));
            if (m.group(4) != null) {
                s += ", dew point " + celsius(m.group(3) != null, m.group(4));
            }
            return one(i, s + ".");
        }
        m = Pattern.compile("A(\\d{2})(\\d{2})").matcher(t);
        if (m.matches()) {
            return one(i, "Altimeter setting " + m.group(1) + "." + m.group(2) + " inches of mercury.");
        }
        m = Pattern.compile("Q(\\d{4})").matcher(t);
        if (m.matches()) {
            return one(i, "Altimeter setting (QNH) " + num(m.group(1)) + " hPa.");
        }
        return null;
    }

    private static boolean isStation(String[] w, int i) {
        String t = clean(w[i]);
        if (!STATION.matcher(t).matches() || isKeyword(t)) {
            return false;
        }
        for (int j = 0; j < i; j++) {
            if (!isKeyword(clean(w[j]))) {
                return false; // something other than METAR / SPECI / TAF / AMD / COR came first
            }
        }
        return true;
    }

    private static boolean isKeyword(String t) {
        return t.equals("METAR") || t.equals("SPECI") || t.equals("TAF") || t.equals("AMD") || t.equals("COR")
                || t.equals("AUTO") || t.equals("TEMPO") || t.equals("BECMG") || t.equals("NSW") || t.equals("RMK");
    }

    private static Hit windShearRunway(String[] w, int i) {
        for (int s = Math.max(0, i - 2); s <= i; s++) {
            if (s + 2 < w.length && clean(w[s]).equals("WS")) {
                String a = clean(w[s + 1]);
                String b = clean(w[s + 2]);
                if (a.equals("ALL") && b.equals("RWY") && i <= s + 2) {
                    return new Hit(s, s + 2, "Low-level wind shear reported on all runways.");
                }
                if (a.equals("RWY") && b.matches("\\d{2}[LCR]?") && i <= s + 2) {
                    return new Hit(s, s + 2, "Low-level wind shear reported on runway " + b + ".");
                }
            }
        }
        return null;
    }

    /** Groups shared by METAR bodies and TAFs: wind, visibility, RVR, weather and cloud. */
    private static Hit conditions(String[] w, int i, boolean metar) {
        String t = clean(w[i]);

        // "2 1/2SM": whole number and fraction are two words but one group
        if (WHOLE.matcher(t).matches() && i + 1 < w.length && FRACTION_SM.matcher(clean(w[i + 1])).matches()) {
            return new Hit(i, i + 1, "Visibility " + t + " " + clean(w[i + 1]).replace("SM", "") + " statute miles.");
        }
        if (FRACTION_SM.matcher(t).matches() && i > 0 && WHOLE.matcher(clean(w[i - 1])).matches()) {
            return new Hit(i - 1, i, "Visibility " + clean(w[i - 1]) + " " + t.replace("SM", "") + " statute miles.");
        }

        Matcher m = Pattern.compile("(\\d{3}|VRB)(\\d{2,3})(?:G(\\d{2,3}))?(KT|MPS|KMH)").matcher(t);
        if (m.matches()) {
            String unit = m.group(4).equals("KT") ? "kt" : m.group(4).equals("MPS") ? "m/s" : "km/h";
            int speed = num(m.group(2));
            if (m.group(1).equals("000") && speed == 0) {
                return one(i, "Wind calm.");
            }
            String s = m.group(1).equals("VRB")
                    ? "Wind variable in direction at " + speed + " " + unit
                    : "Wind from " + m.group(1) + "° true at " + speed + " " + unit;
            if (m.group(3) != null) {
                s += ", gusting to " + num(m.group(3)) + " " + unit;
            }
            return one(i, s + ".");
        }
        m = Pattern.compile("(\\d{3})V(\\d{3})").matcher(t);
        if (m.matches()) {
            return one(i, "Wind direction varying between " + m.group(1) + "° and " + m.group(2) + "° true.");
        }
        m = Pattern.compile("WS(\\d{3})/(\\d{3})(\\d{2,3})KT").matcher(t);
        if (m.matches()) {
            return one(i, "Low-level wind shear at " + feet(num(m.group(1)) * 100) + " above ground: wind from "
                    + m.group(2) + "° true at " + num(m.group(3)) + " kt.");
        }

        m = Pattern.compile("([PM])?(\\d{1,2})SM").matcher(t);
        if (m.matches()) {
            int n = num(m.group(2));
            return one(i, "Visibility " + moreLess(m.group(1)) + n + " statute mile" + (n == 1 ? "" : "s") + ".");
        }
        m = Pattern.compile("([PM])?(\\d)/(\\d{1,2})SM").matcher(t);
        if (m.matches()) {
            return one(i, "Visibility " + moreLess(m.group(1)) + m.group(2) + "/" + m.group(3) + " statute mile.");
        }
        if (t.equals("CAVOK")) {
            return one(i, "Ceiling and visibility OK: visibility 10 km or more, no cloud below 5,000 ft and no significant weather.");
        }

        m = Pattern.compile("R(\\d{2}[LCR]?)/([PM])?(\\d{4})(?:V([PM])?(\\d{4}))?FT(?:/([UDN]))?").matcher(t);
        if (m.matches()) {
            String s = "Runway visual range, runway " + m.group(1) + ": ";
            if (m.group(5) != null) {
                s += "varying from " + moreLess(m.group(2)) + thousands(num(m.group(3))) + " to "
                        + moreLess(m.group(4)) + thousands(num(m.group(5))) + " ft";
            } else {
                s += moreLess(m.group(2)) + thousands(num(m.group(3))) + " ft";
            }
            if (m.group(6) != null) {
                s += m.group(6).equals("U") ? ", increasing" : m.group(6).equals("D") ? ", decreasing" : ", no change";
            }
            return one(i, s + ".");
        }
        m = Pattern.compile("R(\\d{2}[LCR]?)/([PM])?(\\d{4})([UDN])?").matcher(t);
        if (m.matches()) {
            String s = "Runway visual range, runway " + m.group(1) + ": " + moreLess(m.group(2))
                    + thousands(num(m.group(3))) + " m";
            if (m.group(4) != null) {
                s += m.group(4).equals("U") ? ", increasing" : m.group(4).equals("D") ? ", decreasing" : ", no change";
            }
            return one(i, s + ".");
        }

        String sky = sky(t);
        if (sky != null) {
            return one(i, sky);
        }
        if (t.equals("NSW")) {
            return one(i, "No significant weather: the weather forecast earlier ends.");
        }
        String wx = weather(t);
        if (wx != null) {
            return one(i, wx);
        }
        if (metar && t.matches("\\d{4}") && i >= 2 && looksLikeWind(clean(w[i - 1]))) {
            int metres = num(t);
            return one(i, metres == 9999 ? "Visibility 10 km or more." : "Visibility " + thousands(metres) + " m.");
        }
        return null;
    }

    private static boolean looksLikeWind(String t) {
        return t.matches("(\\d{3}|VRB)\\d{2,3}(G\\d{2,3})?(KT|MPS|KMH)") || t.matches("\\d{3}V\\d{3}");
    }

    private static String sky(String t) {
        switch (t) {
            case "SKC":
                return "Sky clear: no cloud.";
            case "CLR":
                return "No cloud detected by the automated station (it looks up to about 10,000 ft in Canada, 12,000 ft in the US).";
            case "NSC":
                return "No significant cloud.";
            case "NCD":
                return "No cloud detected by the automated station.";
            default:
                break;
        }
        Matcher m = Pattern.compile("(FEW|SCT|BKN|OVC)(\\d{3})(CB|TCU)?").matcher(t);
        if (m.matches()) {
            String amount;
            boolean ceiling = false;
            switch (m.group(1)) {
                case "FEW":
                    amount = "Few clouds (up to 2 eighths of the sky)";
                    break;
                case "SCT":
                    amount = "Scattered cloud (3 to 4 eighths of the sky)";
                    break;
                case "BKN":
                    amount = "Broken cloud (5 to 7 eighths of the sky)";
                    ceiling = true;
                    break;
                default:
                    amount = "Overcast (the whole sky)";
                    ceiling = true;
                    break;
            }
            String s = amount + ", base " + feet(num(m.group(2)) * 100) + " above ground";
            if (m.group(3) != null) {
                s += m.group(3).equals("CB") ? ", cumulonimbus" : ", towering cumulus";
            }
            return s + (ceiling ? ". Counts as a ceiling." : ".");
        }
        m = Pattern.compile("VV(\\d{3})").matcher(t);
        if (m.matches()) {
            return "Sky obscured: vertical visibility " + feet(num(m.group(1)) * 100) + ". Counts as a ceiling.";
        }
        if (t.equals("VV///")) {
            return "Sky obscured: vertical visibility not measured.";
        }
        return null;
    }

    private static final String[] DESCRIPTORS = {"MI", "PR", "BC", "DR", "BL", "SH", "TS", "FZ"};
    private static final String[][] PHENOMENA = {
            {"DZ", "drizzle"}, {"RA", "rain"}, {"SN", "snow"}, {"SG", "snow grains"}, {"IC", "ice crystals"},
            {"PL", "ice pellets"}, {"GR", "hail"}, {"GS", "snow pellets"}, {"UP", "unknown precipitation"},
            {"BR", "mist"}, {"FG", "fog"}, {"FU", "smoke"}, {"VA", "volcanic ash"}, {"DU", "dust"},
            {"SA", "sand"}, {"HZ", "haze"}, {"PY", "spray"}, {"PO", "dust or sand whirls"}, {"SQ", "squalls"},
            {"FC", "funnel cloud"}, {"SS", "sandstorm"}, {"DS", "duststorm"}};
    private static final String PRECIPITATION = "DZ RA SN SG PL GS GR UP";

    /** Present-weather group such as -SHRA, FZFG, +TSRAGR or VCSH; null when the word is not one. */
    static String weather(String token) {
        String t = token;
        String intensity = "";
        boolean vicinity = false;
        boolean recent = false;
        if (t.equals("+FC")) {
            return "Tornado or waterspout.";
        }
        if (t.startsWith("+")) {
            intensity = "heavy ";
            t = t.substring(1);
        } else if (t.startsWith("-")) {
            intensity = "light ";
            t = t.substring(1);
        } else if (t.startsWith("VC")) {
            vicinity = true;
            t = t.substring(2);
        } else if (t.startsWith("RE") && t.length() >= 4) {
            recent = true;
            t = t.substring(2);
        }
        if (t.isEmpty() || t.length() % 2 != 0) {
            return null;
        }
        List<String> desc = new ArrayList<>();
        List<String> names = new ArrayList<>();
        boolean precipitation = false;
        for (int k = 0; k < t.length(); k += 2) {
            String code = t.substring(k, k + 2);
            boolean found = false;
            if (names.isEmpty()) {
                for (String d : DESCRIPTORS) {
                    if (d.equals(code)) {
                        desc.add(code);
                        found = true;
                        break;
                    }
                }
            }
            if (!found) {
                for (String[] p : PHENOMENA) {
                    if (p[0].equals(code)) {
                        names.add(p[1]);
                        precipitation |= PRECIPITATION.contains(code);
                        found = true;
                        break;
                    }
                }
            }
            if (!found) {
                return null;
            }
        }
        if (intensity.isEmpty() && precipitation && !vicinity && !recent) {
            intensity = "moderate ";
        }
        String what = intensity + join(names);
        String s;
        if (desc.contains("TS")) {
            s = names.isEmpty() ? "thunderstorm" : "thunderstorm with " + what;
        } else if (desc.contains("SH")) {
            s = names.isEmpty() ? "showers" : what + " showers";
        } else if (desc.contains("FZ")) {
            s = intensity + "freezing " + join(names);
        } else if (desc.contains("BL")) {
            s = "blowing " + join(names);
        } else if (desc.contains("DR")) {
            s = "low drifting " + join(names);
        } else if (desc.contains("MI")) {
            s = "shallow " + join(names);
        } else if (desc.contains("BC")) {
            s = "patches of " + join(names);
        } else if (desc.contains("PR")) {
            s = join(names) + " covering part of the aerodrome";
        } else {
            s = what;
        }
        if (names.isEmpty() && !desc.contains("TS") && !desc.contains("SH")) {
            return null;
        }
        if (recent) {
            s = "recent " + s + " (in the past hour, not now)";
        }
        if (vicinity) {
            s += " in the vicinity, not at the aerodrome itself";
        }
        String extra = "";
        if (token.equals("BR")) {
            extra = " Visibility is reduced but still 5/8 statute mile or more.";
        } else if (token.equals("FG")) {
            extra = " Visibility is below 5/8 statute mile.";
        } else if (token.equals("FZFG")) {
            extra = " Fog with the temperature below freezing.";
        }
        return capital(s) + "." + extra;
    }

    // ================================================================ remarks (METAR and TAF)

    private static final String[] LAYER_CODES = {"BLSN", "ACC", "TCU", "AC", "AS", "CB", "CC", "CF", "CI", "CS",
            "CU", "NS", "SC", "SF", "ST", "FG", "BR", "SN", "RA", "DZ", "FU", "HZ", "IC", "PL", "GR", "VA", "DU",
            "SA", "SG"};
    private static final String[] LAYER_NAMES = {"blowing snow", "altocumulus castellanus", "towering cumulus",
            "altocumulus", "altostratus", "cumulonimbus", "cirrocumulus", "cumulus fractus", "cirrus",
            "cirrostratus", "cumulus", "nimbostratus", "stratocumulus", "stratus fractus", "stratus", "fog", "mist",
            "snow", "rain", "drizzle", "smoke", "haze", "ice crystals", "ice pellets", "hail", "volcanic ash",
            "dust", "sand", "snow grains"};
    private static final String[] TENDENCY = {
            "rose, then fell; higher than 3 hours ago",
            "rose, then steadied or rose more slowly",
            "rose steadily",
            "fell or was steady, then rose; higher than 3 hours ago",
            "steady",
            "fell, then rose; lower than 3 hours ago",
            "fell, then steadied or fell more slowly",
            "fell steadily",
            "rose or was steady, then fell; lower than 3 hours ago"};

    private static Hit remark(String[] w, int i, int rmk, boolean taf) {
        String t = clean(w[i]);
        Matcher m = Pattern.compile("SLP(\\d{2})(\\d)").matcher(t);
        if (m.matches()) {
            int tens = num(m.group(1));
            String hpa = (tens >= 50 ? "9" : "10") + m.group(1) + "." + m.group(2);
            return one(i, "Sea-level pressure " + hpa + " hPa.");
        }
        m = DAY_TIME.matcher(t);
        if (m.matches()) {
            return one(i, "The " + ordinal(num(m.group(1))) + " at " + m.group(2) + ":" + m.group(3) + " UTC.");
        }
        // PK WND 28045/1955
        for (int s = Math.max(rmk + 1, i - 2); s <= i; s++) {
            if (s + 2 < w.length && clean(w[s]).equals("PK") && clean(w[s + 1]).equals("WND")) {
                Matcher p = Pattern.compile("(\\d{3})(\\d{2,3})/(\\d{2})?(\\d{2})").matcher(clean(w[s + 2]));
                if (p.matches()) {
                    String when = p.group(3) != null ? p.group(3) + ":" + p.group(4) + " UTC"
                            : p.group(4) + " minutes past the hour";
                    return new Hit(s, s + 2, "Peak wind since the last routine report: from " + p.group(1)
                            + "° at " + num(p.group(2)) + " kt, at " + when + ".");
                }
            }
        }
        // WSHFT 1930
        for (int s = Math.max(rmk + 1, i - 1); s <= i; s++) {
            if (s + 1 < w.length && clean(w[s]).equals("WSHFT")) {
                Matcher p = Pattern.compile("(\\d{2})?(\\d{2})").matcher(clean(w[s + 1]));
                if (p.matches()) {
                    String when = p.group(1) != null ? p.group(1) + ":" + p.group(2) + " UTC"
                            : p.group(2) + " minutes past the hour";
                    return new Hit(s, s + 1, "Wind shift (a change in direction of 45° or more) at " + when + ".");
                }
            }
        }
        if (!taf) {
            m = Pattern.compile("T([01])(\\d{2})(\\d)([01])(\\d{2})(\\d)").matcher(t);
            if (m.matches()) {
                return one(i, "Temperature " + tenths(m.group(1), m.group(2), m.group(3)) + " °C, dew point "
                        + tenths(m.group(4), m.group(5), m.group(6)) + " °C, to the nearest tenth.");
            }
            m = Pattern.compile("5([0-8])(\\d{2})(\\d)").matcher(t);
            if (m.matches()) {
                return one(i, "Pressure over the past 3 hours: " + TENDENCY[num(m.group(1))] + ". Net change "
                        + num(m.group(2)) + "." + m.group(3) + " hPa.");
            }
            m = Pattern.compile("([12])([01])(\\d{2})(\\d)").matcher(t);
            if (m.matches()) {
                return one(i, (m.group(1).equals("1") ? "Highest" : "Lowest") + " temperature in the past 6 hours: "
                        + tenths(m.group(2), m.group(3), m.group(4)) + " °C.");
            }
            m = Pattern.compile("4([01])(\\d{2})(\\d)([01])(\\d{2})(\\d)").matcher(t);
            if (m.matches()) {
                return one(i, "Past 24 hours: highest temperature " + tenths(m.group(1), m.group(2), m.group(3))
                        + " °C, lowest " + tenths(m.group(4), m.group(5), m.group(6)) + " °C.");
            }
            m = Pattern.compile("P(\\d{2})(\\d{2})").matcher(t);
            if (m.matches()) {
                return one(i, "Precipitation in the past hour: " + num(m.group(1)) + "." + m.group(2) + " inches.");
            }
            m = Pattern.compile("([67])(\\d{2})(\\d{2})").matcher(t);
            if (m.matches()) {
                return one(i, "Precipitation in the past " + (m.group(1).equals("6") ? "3 or 6" : "24")
                        + " hours: " + num(m.group(2)) + "." + m.group(3) + " inches.");
            }
            m = Pattern.compile("4/(\\d{3})").matcher(t);
            if (m.matches()) {
                return one(i, "Snow depth on the ground: " + num(m.group(1)) + " inches.");
            }
            m = Pattern.compile("(\\d{1,5})FT").matcher(t);
            if (m.matches()) {
                return one(i, feet(num(m.group(1))) + ".");
            }
            String began = beginEnd(t);
            if (began != null) {
                return one(i, began);
            }
            String layers = layers(t);
            if (layers != null) {
                return one(i, layers);
            }
        }
        String a = Abbrev.remark(t);
        return a == null ? null : one(i, a + ".");
    }

    /** Canadian remark such as SF2ST6 or FG8: layer types and how many eighths of the sky each covers. */
    static String layers(String t) {
        List<String> parts = new ArrayList<>();
        int pos = 0;
        boolean cloud = false;
        while (pos < t.length()) {
            boolean found = false;
            for (int k = 0; k < LAYER_CODES.length; k++) {
                String code = LAYER_CODES[k];
                if (t.startsWith(code, pos) && pos + code.length() < t.length()) {
                    char d = t.charAt(pos + code.length());
                    if (d >= '1' && d <= '8') {
                        parts.add(LAYER_NAMES[k] + " " + d + "/8");
                        cloud |= k >= 1 && k <= 14;
                        pos += code.length() + 1;
                        found = true;
                        break;
                    }
                }
            }
            if (!found) {
                return null;
            }
        }
        if (parts.isEmpty()) {
            return null;
        }
        String intro = parts.size() > 1 ? "Layers from the lowest up, with the eighths of the sky each covers: "
                : cloud ? "Cloud type and the eighths of the sky it covers: "
                : "Obscuring layer and the eighths of the sky it hides: ";
        return intro + String.join(", ", parts) + ".";
    }

    /** US remark such as RAB05E30: when precipitation or a thunderstorm began and ended. */
    private static String beginEnd(String t) {
        Matcher m = Pattern.compile("([A-Z]{2,6}?)((?:[BE]\\d{2,4})+)").matcher(t);
        if (!m.matches()) {
            return null;
        }
        String wx = weather(m.group(1));
        if (wx == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(wx.substring(0, wx.indexOf('.')));
        Matcher e = Pattern.compile("([BE])(\\d{2})?(\\d{2})").matcher(m.group(2));
        while (e.find()) {
            sb.append(e.group(1).equals("B") ? " began at " : " ended at ");
            sb.append(e.group(2) != null ? e.group(2) + ":" + e.group(3) + " UTC"
                    : e.group(3) + " minutes past the hour");
            sb.append(',');
        }
        sb.setLength(sb.length() - 1);
        return sb + ".";
    }

    // ================================================================ TAF

    private static final Pattern PERIOD = Pattern.compile("(\\d{2})(\\d{2})/(\\d{2})(\\d{2})");

    private static Hit taf(String[] w, int i) {
        String t = clean(w[i]);
        int rmk = indexOf(w, "RMK");
        if (i == rmk) {
            return one(i, "Remarks follow.");
        }
        if (rmk >= 0 && i > rmk) {
            return remark(w, i, rmk, true);
        }
        switch (t) {
            case "TAF":
                return one(i, "Terminal aerodrome forecast: expected conditions within 5 nautical miles of the aerodrome.");
            case "AMD":
                return one(i, "Amended forecast: replaces the one issued earlier.");
            case "COR":
                return one(i, "Corrected forecast.");
            case "TEMPO":
                return one(i, "Temporary: the conditions that follow are expected for less than an hour at a time and less than half of the period.");
            case "BECMG":
                return one(i, "Becoming: a gradual, lasting change to the conditions that follow, during the period given.");
            default:
                break;
        }
        if (isStation(w, i)) {
            return one(i, "Station identifier: the aerodrome the forecast is for.");
        }
        Matcher m = DAY_TIME.matcher(t);
        if (m.matches()) {
            return one(i, "Issued on the " + ordinal(num(m.group(1))) + " at " + m.group(2) + ":" + m.group(3) + " UTC.");
        }
        m = PERIOD.matcher(t);
        if (m.matches()) {
            String span = "the " + ordinal(num(m.group(1))) + " at " + m.group(2) + ":00 UTC to the "
                    + ordinal(num(m.group(3))) + " at " + m.group(4) + ":00 UTC";
            String prev = i > 0 ? clean(w[i - 1]) : "";
            if (prev.equals("TEMPO") || prev.equals("BECMG") || prev.matches("PROB\\d{2}")) {
                return one(i, "Period the change applies to: from " + span + ".");
            }
            return one(i, "Forecast valid from " + span + ".");
        }
        m = Pattern.compile("FM(\\d{2})(\\d{2})(\\d{2})").matcher(t);
        if (m.matches()) {
            return one(i, "From the " + ordinal(num(m.group(1))) + " at " + m.group(2) + ":" + m.group(3)
                    + " UTC: a lasting change to the conditions that follow.");
        }
        m = Pattern.compile("PROB(\\d{2})").matcher(t);
        if (m.matches()) {
            return one(i, m.group(1) + "% probability of the conditions that follow.");
        }
        return conditions(w, i, false);
    }

    // ================================================================ NOTAM

    private static final Pattern NOTAM_ID = Pattern.compile("([A-Z])(\\d{4})/(\\d{2})");
    private static final String[] MONTHS = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct",
            "Nov", "Dec"};

    private static Hit notam(String[] w, int i) {
        String raw = w[i];
        // field markers
        String marker = stripOpen(raw);
        switch (marker) {
            case "Q)":
                return one(i, "Qualifier line. In order: FIR, NOTAM code, traffic, purpose, scope, lower limit, upper limit, centre and radius.");
            case "A)":
                return one(i, "Location: the aerodrome or FIR the NOTAM applies to.");
            case "B)":
                return one(i, "Start: when the NOTAM takes effect.");
            case "C)":
                return one(i, "End: when the NOTAM stops applying.");
            case "D)":
                return one(i, "Schedule: the times within the period when it applies.");
            case "E)":
                return one(i, "Text: what the NOTAM is about.");
            case "F)":
                return one(i, "Lower limit of the affected airspace.");
            case "G)":
                return one(i, "Upper limit of the affected airspace.");
            default:
                break;
        }
        String field = "";
        for (int j = 0; j < i; j++) {
            String f = stripOpen(w[j]);
            if (f.length() == 2 && f.charAt(1) == ')' && "QABCDEFG".indexOf(f.charAt(0)) >= 0) {
                field = f.substring(0, 1);
            }
        }
        String t = trimPunctuation(raw);
        if (t.isEmpty()) {
            return null;
        }
        if (t.matches("([A-Z]\\.){2,}[A-Z]?")) {
            t = t.replace(".", ""); // C.R.F.I. is CRFI
        }

        Matcher m = NOTAM_ID.matcher(t);
        if (m.matches() && (field.isEmpty() || field.equals("E"))) {
            String prev = i > 0 ? trimPunctuation(w[i - 1]) : "";
            String what = prev.equals("NOTAMR") ? "The NOTAM being replaced: "
                    : prev.equals("NOTAMC") ? "The NOTAM being cancelled: " : "NOTAM ";
            return one(i, what + "number " + m.group(2) + " of 20" + m.group(3) + ", series " + m.group(1) + ".");
        }
        switch (t) {
            case "NOTAMN":
                return one(i, "New NOTAM.");
            case "NOTAMR":
                return one(i, "Replaces the NOTAM whose number follows.");
            case "NOTAMC":
                return one(i, "Cancels the NOTAM whose number follows.");
            default:
                break;
        }
        if (t.matches("[A-Z]{4}/Q[A-Z]{4}/.*")) {
            String q = qualifier(t);
            if (q != null) {
                return one(i, q);
            }
        }
        if (field.equals("A") && t.matches("[A-Z]{4}")) {
            String fir = QCode.fir(t);
            return one(i, fir != null ? fir + "." : "Location indicator of the aerodrome or FIR.");
        }

        String before = i > 0 ? trimPunctuation(w[i - 1]) : "";
        boolean phone = before.equals("CTC") || before.equals("TEL") || before.equals("PHONE");
        m = Pattern.compile("(\\d{10})(EST)?").matcher(t);
        if (m.matches() && !phone) {
            String when = dateTime("20" + m.group(1));
            if (when != null) {
                String lead = field.equals("B") ? "Takes effect " : field.equals("C") ? "Ends " : "";
                String s = lead.isEmpty() ? capital(when) : lead + when;
                return one(i, s + (m.group(2) != null ? " (estimated)." : "."));
            }
        }
        if (t.matches("\\d{12}")) {
            String when = dateTime(t);
            if (when != null) {
                return one(i, capital(when) + ".");
            }
        }
        if (t.equals("PERM")) {
            return one(i, "Permanent: no end date.");
        }
        m = Pattern.compile("(\\d{2})(\\d{2})-(\\d{2})(\\d{2})").matcher(t);
        if (m.matches() && num(m.group(1)) <= 24 && num(m.group(3)) <= 24 && num(m.group(2)) < 60 && num(m.group(4)) < 60) {
            return one(i, "From " + m.group(1) + ":" + m.group(2) + " to " + m.group(3) + ":" + m.group(4) + " UTC.");
        }
        m = Pattern.compile("(\\d{1,6})FT").matcher(t);
        if (m.matches()) {
            return one(i, feet(num(m.group(1))) + ".");
        }
        m = Pattern.compile("(\\d{1,4}(?:\\.\\d+)?)NM").matcher(t);
        if (m.matches()) {
            return one(i, m.group(1) + " nautical miles.");
        }
        m = Pattern.compile("FL(\\d{3})").matcher(t);
        if (m.matches()) {
            return one(i, "Flight level " + m.group(1) + ": " + feet(num(m.group(1)) * 100)
                    + " on the standard pressure setting.");
        }
        m = Pattern.compile("(\\d{2,3}(?:\\.\\d{1,3})?)(MHZ|KHZ)").matcher(t);
        if (m.matches()) {
            return one(i, "Frequency " + m.group(1) + (m.group(2).equals("MHZ") ? " MHz." : " kHz."));
        }
        m = Pattern.compile("(\\d{2})(\\d{2})(\\d{2})?N(\\d{3})(\\d{2})(\\d{2})?W").matcher(t);
        if (m.matches()) {
            return one(i, "Position " + dms(m.group(1), m.group(2), m.group(3)) + " north, "
                    + dms(m.group(4), m.group(5), m.group(6)) + " west.");
        }
        m = Pattern.compile("(\\d{2})(\\d{2})(\\d{2})?N").matcher(t);
        if (m.matches() && num(m.group(1)) <= 90 && num(m.group(2)) < 60) {
            return one(i, "Latitude " + dms(m.group(1), m.group(2), m.group(3)) + " north.");
        }
        m = Pattern.compile("(\\d{3})(\\d{2})(\\d{2})?W").matcher(t);
        if (m.matches() && num(m.group(1)) <= 180 && num(m.group(2)) < 60) {
            return one(i, "Longitude " + dms(m.group(1), m.group(2), m.group(3)) + " west.");
        }
        if (t.matches("\\d{2}[LCR]?(/\\d{2}[LCR]?)?") && (before.equals("RWY") || before.equals("RSC"))) {
            return one(i, "Runway " + t + ".");
        }
        if (t.matches("[0-6]/[0-6]/[0-6]")) {
            return one(i, "Runway condition code for each third of the runway, from 6 (dry) down to 0 (nil braking).");
        }
        String a = Abbrev.general(t);
        if (a != null) {
            return one(i, a + ".");
        }
        // MON-FRI, SR-SS, LOC/DME and the like: explain the parts when every part is known
        if (t.contains("-") || t.contains("/")) {
            boolean range = t.contains("-") && !t.contains("/");
            String[] parts = t.split("[-/]");
            List<String> meanings = new ArrayList<>();
            for (String part : parts) {
                String x = Abbrev.general(part);
                if (x == null) {
                    meanings.clear();
                    break;
                }
                meanings.add(meanings.isEmpty() ? x : x.toLowerCase(Locale.US));
            }
            if (meanings.size() >= 2) {
                return one(i, String.join(range ? " to " : " / ", meanings) + ".");
            }
        }
        return null;
    }

    /** "CZWG/QOLAS/IV/M/AE/000/010/4954N09711W005" spelled out line by line. */
    static String qualifier(String q) {
        String[] p = q.split("/");
        if (p.length < 5 || p[1].length() != 5) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        String fir = QCode.fir(p[0]);
        sb.append("FIR: ").append(p[0]).append(fir != null ? " (" + fir + ")" : "");
        String subjectCode = p[1].substring(1, 3);
        String conditionCode = p[1].substring(3, 5);
        String subject = QCode.subject(subjectCode);
        String condition = QCode.condition(conditionCode);
        sb.append("\nSubject (").append(subjectCode).append("): ")
                .append(subject != null ? subject : "not in the decoder's table");
        sb.append("\nCondition (").append(conditionCode).append("): ")
                .append(condition != null ? condition : "not in the decoder's table");
        String traffic = p[2].equals("IV") ? "IFR and VFR" : p[2].equals("I") ? "IFR only"
                : p[2].equals("V") ? "VFR only" : p[2].equals("K") ? "checklist" : p[2];
        sb.append("\nTraffic: ").append(traffic);
        List<String> purpose = new ArrayList<>();
        for (char c : p[3].toCharArray()) {
            switch (c) {
                case 'N':
                    purpose.add("immediate attention of flight crews");
                    break;
                case 'B':
                    purpose.add("included in pre-flight briefings");
                    break;
                case 'O':
                    purpose.add("significant for flight operations");
                    break;
                case 'M':
                    purpose.add("miscellaneous, not in briefings unless requested");
                    break;
                case 'K':
                    purpose.add("checklist");
                    break;
                default:
                    break;
            }
        }
        sb.append("\nPurpose: ").append(purpose.isEmpty() ? p[3] : String.join("; ", purpose));
        List<String> scope = new ArrayList<>();
        for (char c : p[4].toCharArray()) {
            switch (c) {
                case 'A':
                    scope.add("aerodrome");
                    break;
                case 'E':
                    scope.add("en route");
                    break;
                case 'W':
                    scope.add("navigation warning");
                    break;
                case 'K':
                    scope.add("checklist");
                    break;
                default:
                    break;
            }
        }
        sb.append("\nScope: ").append(scope.isEmpty() ? p[4] : String.join(" and ", scope));
        if (p.length >= 7 && p[5].matches("\\d{3}") && p[6].matches("\\d{3}")) {
            String lower = p[5].equals("000") ? "surface" : "FL" + p[5];
            String upper = p[6].equals("999") ? "unlimited" : "FL" + p[6];
            sb.append("\nVertical limits: ").append(lower).append(" to ").append(upper);
        }
        if (p.length >= 8) {
            Matcher m = Pattern.compile("(\\d{2})(\\d{2})([NS])(\\d{3})(\\d{2})([EW])(\\d{3})").matcher(p[7]);
            if (m.matches()) {
                int radius = num(m.group(7));
                sb.append("\nCentre: ").append(m.group(1)).append("°").append(m.group(2)).append("'")
                        .append(m.group(3)).append(" ").append(m.group(4)).append("°").append(m.group(5)).append("'")
                        .append(m.group(6)).append(radius == 999 ? ", whole area" : ", radius " + radius + " NM");
            }
        }
        return sb.toString();
    }

    /** "202611251408" -> "25 Nov 2026 at 14:08 UTC"; null when it is not a plausible date. */
    private static String dateTime(String d) {
        int year = num(d.substring(0, 4));
        int month = num(d.substring(4, 6));
        int day = num(d.substring(6, 8));
        int hour = num(d.substring(8, 10));
        int minute = num(d.substring(10, 12));
        if (month < 1 || month > 12 || day < 1 || day > 31 || hour > 24 || minute > 59) {
            return null;
        }
        return day + " " + MONTHS[month - 1] + " " + year + " at " + d.substring(8, 10) + ":" + d.substring(10, 12)
                + " UTC";
    }

    // ================================================================ helpers

    private static Hit one(int i, String text) {
        return new Hit(i, i, text);
    }

    /** METAR / TAF word without the end-of-report "=". */
    private static String clean(String w) {
        String t = w.trim();
        while (t.length() > 1 && t.endsWith("=")) {
            t = t.substring(0, t.length() - 1);
        }
        return t;
    }

    private static String stripOpen(String w) {
        String t = w.trim();
        while (t.startsWith("(")) {
            t = t.substring(1);
        }
        return t;
    }

    /** NOTAM word without surrounding brackets and sentence punctuation. */
    private static String trimPunctuation(String w) {
        int a = 0;
        int b = w.length();
        while (a < b && "(\"'".indexOf(w.charAt(a)) >= 0) {
            a++;
        }
        while (b > a && ").,;:\"'".indexOf(w.charAt(b - 1)) >= 0) {
            b--;
        }
        return w.substring(a, b).toUpperCase(Locale.US);
    }

    private static int indexOf(String[] w, String word) {
        for (int k = 0; k < w.length; k++) {
            if (clean(w[k]).equals(word)) {
                return k;
            }
        }
        return -1;
    }

    private static int num(String s) {
        return Integer.parseInt(s);
    }

    private static String ordinal(int n) {
        int mod100 = n % 100;
        int mod10 = n % 10;
        String suffix = (mod100 >= 11 && mod100 <= 13) ? "th" : mod10 == 1 ? "st" : mod10 == 2 ? "nd"
                : mod10 == 3 ? "rd" : "th";
        return n + suffix;
    }

    private static String thousands(int n) {
        return String.format(Locale.US, "%,d", n);
    }

    private static String feet(int n) {
        return thousands(n) + " ft";
    }

    private static String celsius(boolean minus, String digits) {
        int v = num(digits);
        return (minus && v != 0 ? "−" : "") + v + " °C";
    }

    private static String tenths(String sign, String whole, String tenth) {
        boolean minus = sign.equals("1");
        return (minus ? "−" : "") + num(whole) + "." + tenth;
    }

    private static String moreLess(String pm) {
        return pm == null ? "" : pm.equals("P") ? "more than " : "less than ";
    }

    private static String dms(String d, String m, String s) {
        return num(d) + "°" + m + "'" + (s != null ? s + "\"" : "");
    }

    private static String join(List<String> names) {
        if (names.isEmpty()) {
            return "";
        }
        if (names.size() == 1) {
            return names.get(0);
        }
        return String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
    }

    private static String capital(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
