package io.github.esesemdi.allmetar.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Reader for the bundled aerodrome file (see tools/build_airports.py for the format):
 * an "A" line per aerodrome followed by its "R" (runway) and "F" (frequency) lines.
 */
public final class AirportIndex {
    private AirportIndex() {
    }

    /** The part of an aerodrome record needed to search and list it. */
    public static final class Entry {
        public final String id;
        public final String name;
        public final String city;
        public final String region;
        public final String country;
        public final String type;
        public final String others;
        final String haystack;

        Entry(String id, String name, String city, String region, String country, String type, String others) {
            this.id = id;
            this.name = name;
            this.city = city;
            this.region = region;
            this.country = country;
            this.type = type;
            this.others = others;
            this.haystack = (name + " " + city).toLowerCase(Locale.ROOT);
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
    }

    public static List<Entry> loadEntries(InputStream in) throws IOException {
        List<Entry> out = new ArrayList<>(20000);
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8), 64 * 1024);
        String line;
        while ((line = r.readLine()) != null) {
            if (line.length() > 2 && line.charAt(0) == 'A' && line.charAt(1) == '\t') {
                String[] f = line.split("\t", -1);
                if (f.length >= 11) {
                    out.add(new Entry(f[1], f[2], f[3], f[4], f[5], f[9], f[10]));
                }
            }
        }
        return out;
    }

    /** Full record for one aerodrome, or null when it is not in the file. */
    public static Airport find(InputStream in, String id) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8), 64 * 1024);
        String prefix = "A\t" + id + "\t";
        String line;
        while ((line = r.readLine()) != null) {
            if (!line.startsWith(prefix)) {
                continue;
            }
            String[] a = line.split("\t", -1);
            if (a.length < 11) {
                return null;
            }
            List<Runway> runways = new ArrayList<>();
            List<Freq> freqs = new ArrayList<>();
            while ((line = r.readLine()) != null && !line.startsWith("A\t")) {
                String[] f = line.split("\t", -1);
                if (f[0].equals("R") && f.length >= 8) {
                    runways.add(new Runway(f[1], f[2], f[3], f[4], f[5], "1".equals(f[6]), "1".equals(f[7])));
                } else if (f[0].equals("F") && f.length >= 4) {
                    freqs.add(new Freq(f[1], f[2], f[3]));
                }
            }
            return new Airport(a[1], a[2], a[3], a[4], a[5], a[6], a[7], a[8], a[9], a[10], runways, freqs);
        }
        return null;
    }

    /**
     * Matches on identifier, other codes (IATA / local), name and city.
     * Exact and prefix identifier matches come first, then larger aerodromes before smaller ones.
     */
    public static List<Entry> search(List<Entry> all, String query, int limit) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            return new ArrayList<>();
        }
        final String up = q.toUpperCase(Locale.ROOT);
        String low = q.toLowerCase(Locale.ROOT);
        String[] words = low.split("\\s+");
        boolean single = words.length == 1;

        final List<Entry> hits = new ArrayList<>();
        final List<Integer> scores = new ArrayList<>();
        for (Entry e : all) {
            int score = -1;
            if (single) {
                if (e.id.equals(up)) {
                    score = 0;
                } else if (hasCode(e.others, up, true)) {
                    score = 1;
                } else if (e.id.startsWith(up)) {
                    score = 2;
                } else if (hasCode(e.others, up, false)) {
                    score = 3;
                }
            }
            if (score < 0) {
                boolean allWords = true;
                for (String w : words) {
                    if (!e.haystack.contains(w)) {
                        allWords = false;
                        break;
                    }
                }
                if (allWords) {
                    score = (e.haystack.startsWith(low) || e.haystack.contains(" " + low)) ? 4 : 5;
                }
            }
            if (score >= 0) {
                hits.add(e);
                scores.add(score);
            }
        }
        List<Integer> idx = new ArrayList<>(hits.size());
        for (int i = 0; i < hits.size(); i++) {
            idx.add(i);
        }
        Collections.sort(idx, new Comparator<Integer>() {
            @Override
            public int compare(Integer a, Integer b) {
                int c = Integer.compare(scores.get(a), scores.get(b));
                if (c != 0) {
                    return c;
                }
                c = Integer.compare(typeRank(hits.get(a).type), typeRank(hits.get(b).type));
                if (c != 0) {
                    return c;
                }
                return hits.get(a).id.compareTo(hits.get(b).id);
            }
        });
        List<Entry> out = new ArrayList<>();
        for (int i = 0; i < idx.size() && out.size() < limit; i++) {
            out.add(hits.get(idx.get(i)));
        }
        return out;
    }

    private static boolean hasCode(String others, String q, boolean exact) {
        if (others.isEmpty()) {
            return false;
        }
        for (String c : others.split(" ")) {
            if (exact ? c.equals(q) : c.startsWith(q)) {
                return true;
            }
        }
        return false;
    }

    private static int typeRank(String type) {
        switch (type) {
            case "L":
                return 0;
            case "M":
                return 1;
            case "S":
                return 2;
            default:
                return 3;
        }
    }
}
