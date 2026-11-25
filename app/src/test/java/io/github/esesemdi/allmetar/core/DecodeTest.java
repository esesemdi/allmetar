package io.github.esesemdi.allmetar.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Decodes real reports word by word. */
public class DecodeTest {
    private static String[] words(String text) {
        return text.trim().split("\\s+");
    }

    /** Explanation for the first occurrence of {@code word}. */
    private static String say(int kind, String report, String word) {
        String[] w = words(report);
        for (int i = 0; i < w.length; i++) {
            if (w[i].equals(word)) {
                Decode.Hit h = Decode.decode(kind, w, i);
                return h == null ? null : h.text;
            }
        }
        throw new IllegalArgumentException("no word " + word);
    }

    private static final String WG_FOG =
            "CYWG 251500Z 19013KT 1/2SM R36/P6000FT/D R13/5500VP6000FT/U FZFG VV001 M03/M04 A3042 RMK FG8 SLP319";
    private static final String WG_SPECI =
            "SPECI CYWG 251213Z 18010G16KT 3/4SM R36/P6000FT/N BR VV002 M05/M06 A3040 RMK FG8 SLP314";
    private static final String WG_DRIZZLE =
            "CYWG 190900Z 02007KT 2 1/2SM -DZ BR OVC003 07/07 A2964 RMK ST8 SLP045";
    private static final String WG_LAYERS =
            "CYWG 250600Z 21009KT 10SM BKN007 OVC016 M01/M02 A3038 RMK ST7SC1 PRESRR SLP306";
    private static final String GFK =
            "KGFK 251453Z 18008KT 10SM FEW039 M03/M04 A3045 RMK AO2 SLP328 T10281044 51005 $";

    @Test
    public void metarBody() {
        assertEquals("Station identifier: where the observation was made.", say(Decode.METAR, WG_FOG, "CYWG"));
        assertEquals("Observed on the 25th at 15:00 UTC.", say(Decode.METAR, WG_FOG, "251500Z"));
        assertEquals("Wind from 190° true at 13 kt.", say(Decode.METAR, WG_FOG, "19013KT"));
        assertEquals("Visibility 1/2 statute mile.", say(Decode.METAR, WG_FOG, "1/2SM"));
        assertEquals("Runway visual range, runway 36: more than 6,000 ft, decreasing.",
                say(Decode.METAR, WG_FOG, "R36/P6000FT/D"));
        assertEquals("Runway visual range, runway 13: varying from 5,500 to more than 6,000 ft, increasing.",
                say(Decode.METAR, WG_FOG, "R13/5500VP6000FT/U"));
        assertEquals("Freezing fog. Fog with the temperature below freezing.", say(Decode.METAR, WG_FOG, "FZFG"));
        assertEquals("Sky obscured: vertical visibility 100 ft. Counts as a ceiling.",
                say(Decode.METAR, WG_FOG, "VV001"));
        assertEquals("Temperature −3 °C, dew point −4 °C.", say(Decode.METAR, WG_FOG, "M03/M04"));
        assertEquals("Altimeter setting 30.42 inches of mercury.", say(Decode.METAR, WG_FOG, "A3042"));
        assertTrue(say(Decode.METAR, WG_FOG, "RMK").startsWith("Remarks follow"));
    }

    @Test
    public void metarVariants() {
        assertTrue(say(Decode.METAR, WG_SPECI, "SPECI").startsWith("Special report"));
        assertEquals("Station identifier: where the observation was made.", say(Decode.METAR, WG_SPECI, "CYWG"));
        assertEquals("Wind from 180° true at 10 kt, gusting to 16 kt.", say(Decode.METAR, WG_SPECI, "18010G16KT"));
        assertEquals("Visibility 3/4 statute mile.", say(Decode.METAR, WG_SPECI, "3/4SM"));
        assertEquals("Runway visual range, runway 36: more than 6,000 ft, no change.",
                say(Decode.METAR, WG_SPECI, "R36/P6000FT/N"));
        assertEquals("Mist. Visibility is reduced but still 5/8 statute mile or more.",
                say(Decode.METAR, WG_SPECI, "BR"));
        assertEquals("Wind from 020° true at 7 kt.", say(Decode.METAR, WG_DRIZZLE, "02007KT"));
        assertEquals("Light drizzle.", say(Decode.METAR, WG_DRIZZLE, "-DZ"));
        assertEquals("Overcast (the whole sky), base 300 ft above ground. Counts as a ceiling.",
                say(Decode.METAR, WG_DRIZZLE, "OVC003"));
        assertEquals("Temperature 7 °C, dew point 7 °C.", say(Decode.METAR, WG_DRIZZLE, "07/07"));
        assertEquals("Broken cloud (5 to 7 eighths of the sky), base 700 ft above ground. Counts as a ceiling.",
                say(Decode.METAR, WG_LAYERS, "BKN007"));
        assertEquals("Visibility 10 statute miles.", say(Decode.METAR, WG_LAYERS, "10SM"));
        assertEquals("Few clouds (up to 2 eighths of the sky), base 3,900 ft above ground.",
                say(Decode.METAR, GFK, "FEW039"));
        assertEquals("Wind calm.", say(Decode.METAR, "CYAV 251500Z 00000KT 15SM SKC M04/M06 A3035", "00000KT"));
        assertEquals("Sky clear: no cloud.", say(Decode.METAR, "CYAV 251500Z 00000KT 15SM SKC M04/M06 A3035", "SKC"));
        assertEquals("Wind variable in direction at 3 kt.",
                say(Decode.METAR, "CYAV 251500Z AUTO VRB03KT 9SM CLR 01/M01 A3001", "VRB03KT"));
        assertTrue(say(Decode.METAR, "CYAV 251500Z AUTO VRB03KT 9SM CLR 01/M01 A3001", "AUTO").startsWith("Fully automated"));
        assertTrue(say(Decode.METAR, "CYAV 251500Z AUTO VRB03KT 9SM CLR 01/M01 A3001", "CLR").startsWith("No cloud detected"));
        assertEquals("Temperature 1 °C, dew point −1 °C.",
                say(Decode.METAR, "CYAV 251500Z AUTO VRB03KT 9SM CLR 01/M01 A3001", "01/M01"));
        assertEquals("Wind direction varying between 160° and 220° true.",
                say(Decode.METAR, "CYWG 251500Z 19013KT 160V220 15SM FEW040 M03/M04 A3042", "160V220"));
        assertEquals("Corrected report: 1st correction to the report for this time.",
                say(Decode.METAR, "CYWG 251500Z CCA 19013KT 15SM FEW040 M03/M04 A3042", "CCA"));
    }

    @Test
    public void wholeNumberAndFractionAreOneVisibilityGroup() {
        String[] w = words(WG_DRIZZLE);
        Decode.Hit whole = Decode.decode(Decode.METAR, w, 3);
        Decode.Hit fraction = Decode.decode(Decode.METAR, w, 4);
        assertEquals("Visibility 2 1/2 statute miles.", whole.text);
        assertEquals(whole.text, fraction.text);
        assertEquals(3, whole.from);
        assertEquals(4, whole.to);
        assertEquals(3, fraction.from);
        assertEquals(4, fraction.to);
        // also right after a TAF change period
        String[] t = words("TAF CYWG 251140Z 2512/2612 18010KT P6SM SKC TEMPO 2513/2515 1 1/2SM BR");
        assertEquals("Visibility 1 1/2 statute miles.", Decode.decode(Decode.TAF, t, t.length - 2).text);
        assertEquals("Visibility 1 1/2 statute miles.", Decode.decode(Decode.TAF, t, t.length - 3).text);
    }

    @Test
    public void weatherGroups() {
        assertEquals("Light rain showers.", Decode.weather("-SHRA"));
        assertEquals("Thunderstorm with heavy rain and hail.", Decode.weather("+TSRAGR"));
        assertEquals("Thunderstorm.", Decode.weather("TS"));
        assertEquals("Moderate snow.", Decode.weather("SN"));
        assertEquals("Heavy snow.", Decode.weather("+SN"));
        assertEquals("Light freezing drizzle.", Decode.weather("-FZDZ"));
        assertEquals("Moderate freezing rain.", Decode.weather("FZRA"));
        assertEquals("Blowing snow.", Decode.weather("BLSN"));
        assertEquals("Low drifting snow.", Decode.weather("DRSN"));
        assertEquals("Shallow fog.", Decode.weather("MIFG"));
        assertEquals("Patches of fog.", Decode.weather("BCFG"));
        assertEquals("Showers in the vicinity, not at the aerodrome itself.", Decode.weather("VCSH"));
        assertEquals("Light snow and rain showers.", Decode.weather("-SHSNRA"));
        assertEquals("Fog. Visibility is below 5/8 statute mile.", Decode.weather("FG"));
        assertEquals("Haze.", Decode.weather("HZ"));
        assertEquals("Ice crystals.", Decode.weather("IC"));
        assertEquals("Tornado or waterspout.", Decode.weather("+FC"));
        assertNull(Decode.weather("TEMPO"));
        assertNull(Decode.weather("CYWG"));
        assertNull(Decode.weather("BECMG"));
        assertNull(Decode.weather("A3042"));
        assertNull(Decode.weather("FZ"));
    }

    @Test
    public void canadianRemarks() {
        assertEquals("Obscuring layer and the eighths of the sky it hides: fog 8/8.", say(Decode.METAR, WG_FOG, "FG8"));
        assertEquals("Sea-level pressure 1031.9 hPa.", say(Decode.METAR, WG_FOG, "SLP319"));
        assertEquals("Sea-level pressure 1004.5 hPa.", say(Decode.METAR, WG_DRIZZLE, "SLP045"));
        assertEquals("Sea-level pressure 998.2 hPa.", say(Decode.METAR, "CYWG 251500Z 19013KT 15SM SKC 01/00 A2950 RMK SLP982", "SLP982"));
        assertEquals("Cloud type and the eighths of the sky it covers: stratus 8/8.", say(Decode.METAR, WG_DRIZZLE, "ST8"));
        assertEquals("Layers from the lowest up, with the eighths of the sky each covers: stratus 7/8, stratocumulus 1/8.",
                say(Decode.METAR, WG_LAYERS, "ST7SC1"));
        assertEquals("Pressure rising rapidly.", say(Decode.METAR, WG_LAYERS, "PRESRR"));
        assertEquals("Layers from the lowest up, with the eighths of the sky each covers: stratus fractus 2/8, stratus 6/8.",
                Decode.layers("SF2ST6"));
        assertEquals("Cloud type and the eighths of the sky it covers: altocumulus castellanus 3/8.", Decode.layers("ACC3"));
        assertNull(Decode.layers("SLP319"));
        assertNull(Decode.layers("AO2"));
        assertNull(Decode.layers("ST"));
    }

    @Test
    public void usRemarks() {
        assertTrue(say(Decode.METAR, GFK, "AO2").startsWith("Automated station with a precipitation discriminator"));
        assertEquals("Sea-level pressure 1032.8 hPa.", say(Decode.METAR, GFK, "SLP328"));
        assertEquals("Temperature −2.8 °C, dew point −4.4 °C, to the nearest tenth.", say(Decode.METAR, GFK, "T10281044"));
        assertEquals("Pressure over the past 3 hours: rose, then steadied or rose more slowly. Net change 0.5 hPa.",
                say(Decode.METAR, GFK, "51005"));
        assertEquals("Station flagged as needing maintenance.", say(Decode.METAR, GFK, "$"));
        String pk = "KMSP 251453Z 28020G35KT 10SM FEW050 05/M02 A2990 RMK AO2 PK WND 28045/1425 WSHFT 1410 RAB05E30 P0003 60012 SLP130";
        String[] w = words(pk);
        assertEquals("28045/1425", w[11]);
        Decode.Hit h = Decode.decode(Decode.METAR, w, 11);
        assertEquals("Peak wind since the last routine report: from 280° at 45 kt, at 14:25 UTC.", h.text);
        assertEquals(9, h.from);
        assertEquals(11, h.to);
        assertEquals(h.text, Decode.decode(Decode.METAR, w, 9).text);
        assertEquals(h.text, Decode.decode(Decode.METAR, w, 10).text);
        Decode.Hit shift = Decode.decode(Decode.METAR, w, 13);
        assertEquals(12, shift.from);
        assertEquals(13, shift.to);
        assertEquals("Wind shift (a change in direction of 45° or more) at 14:10 UTC.", say(Decode.METAR, pk, "WSHFT"));
        assertEquals("Moderate rain began at 05 minutes past the hour, ended at 30 minutes past the hour.",
                say(Decode.METAR, pk, "RAB05E30"));
        assertEquals("Precipitation in the past hour: 0.03 inches.", say(Decode.METAR, pk, "P0003"));
        assertEquals("Precipitation in the past 3 or 6 hours: 0.12 inches.", say(Decode.METAR, pk, "60012"));
    }

    @Test
    public void taf() {
        String t = "TAF AMD CYWG 251312Z 2513/2612 18010KT 1/2SM FZFG VV001 TEMPO 2513/2515 3SM BR OVC003 "
                + "FM251500 19012KT 1SM BR OVC002 BECMG 2520/2522 18018G28KT PROB30 2522/2524 P6SM -SHRA "
                + "WS015/27045KT RMK NXT FCST BY 251800Z";
        assertTrue(say(Decode.TAF, t, "TAF").startsWith("Terminal aerodrome forecast"));
        assertEquals("Amended forecast: replaces the one issued earlier.", say(Decode.TAF, t, "AMD"));
        assertEquals("Station identifier: the aerodrome the forecast is for.", say(Decode.TAF, t, "CYWG"));
        assertEquals("Issued on the 25th at 13:12 UTC.", say(Decode.TAF, t, "251312Z"));
        assertEquals("Forecast valid from the 25th at 13:00 UTC to the 26th at 12:00 UTC.", say(Decode.TAF, t, "2513/2612"));
        assertTrue(say(Decode.TAF, t, "TEMPO").startsWith("Temporary"));
        assertEquals("Period the change applies to: from the 25th at 13:00 UTC to the 25th at 15:00 UTC.",
                say(Decode.TAF, t, "2513/2515"));
        assertEquals("From the 25th at 15:00 UTC: a lasting change to the conditions that follow.",
                say(Decode.TAF, t, "FM251500"));
        assertTrue(say(Decode.TAF, t, "BECMG").startsWith("Becoming"));
        assertEquals("Period the change applies to: from the 25th at 20:00 UTC to the 25th at 22:00 UTC.",
                say(Decode.TAF, t, "2520/2522"));
        assertEquals("30% probability of the conditions that follow.", say(Decode.TAF, t, "PROB30"));
        assertEquals("Period the change applies to: from the 25th at 22:00 UTC to the 25th at 24:00 UTC.",
                say(Decode.TAF, t, "2522/2524"));
        assertEquals("Visibility more than 6 statute miles.", say(Decode.TAF, t, "P6SM"));
        assertEquals("Visibility 1 statute mile.", say(Decode.TAF, t, "1SM"));
        assertEquals("Light rain showers.", say(Decode.TAF, t, "-SHRA"));
        assertEquals("Low-level wind shear at 1,500 ft above ground: wind from 270° true at 45 kt.",
                say(Decode.TAF, t, "WS015/27045KT"));
        assertEquals("Wind from 180° true at 18 kt, gusting to 28 kt.", say(Decode.TAF, t, "18018G28KT"));
        assertEquals("Next.", say(Decode.TAF, t, "NXT"));
        assertEquals("Forecast.", say(Decode.TAF, t, "FCST"));
        assertNull(say(Decode.TAF, t, "BY"));
        assertEquals("The 25th at 18:00 UTC.", say(Decode.TAF, t, "251800Z"));
        assertEquals("No significant weather: the weather forecast earlier ends.",
                say(Decode.TAF, "TAF CYWG 251140Z 2512/2612 18010KT P6SM -SN BKN030 FM251500 18010KT P6SM NSW SKC", "NSW"));
    }

    private static final String NOTAM_OBST = "(U1651/26 NOTAMN\nQ) CZWG/QOLAS/IV/M/AE/000/010/4954N09711W005\n"
            + "A) CYWG B) 2609270942 C) 2611272359\nE) OBST LGT U/S TOWER 495422N 0971119W (APRX 2NM E AD) 155FT AGL,\n"
            + "921FT AMSL.)";
    private static final String NOTAM_FIR = "(G4138/26 NOTAMR G4044/26\nQ) CZXX/QXXXX/IV/NBO/E/000/999/5245N09040W999\n"
            + "A) CZWG CZYZ B) 2611201552 C) 2612111700EST\nE) UPPER WIND ABV FL180 NOT AVBL)";
    private static final String NOTAM_RWY = "(M1234/26 NOTAMN\nQ) CZWG/QMRLC/IV/NBO/A/000/999/4955N09714W005\n"
            + "A) CYWG B) 2611010000 C) PERM\nD) MON-FRI 1200-2200\nE) RWY 07/25 CLSD. ILS RWY 36 LOC/DME U/S. "
            + "RSC 18/36 5/5/5 100 PCT DRY. CTC 2045551234.)";

    @Test
    public void notamHeaderAndFields() {
        assertEquals("NOTAM number 1651 of 2026, series U.", say(Decode.NOTAM, NOTAM_OBST, "(U1651/26"));
        assertEquals("New NOTAM.", say(Decode.NOTAM, NOTAM_OBST, "NOTAMN"));
        assertEquals("Replaces the NOTAM whose number follows.", say(Decode.NOTAM, NOTAM_FIR, "NOTAMR"));
        assertEquals("The NOTAM being replaced: number 4044 of 2026, series G.", say(Decode.NOTAM, NOTAM_FIR, "G4044/26"));
        assertTrue(say(Decode.NOTAM, NOTAM_OBST, "Q)").startsWith("Qualifier line"));
        assertTrue(say(Decode.NOTAM, NOTAM_OBST, "A)").startsWith("Location"));
        assertEquals("Location indicator of the aerodrome or FIR.", say(Decode.NOTAM, NOTAM_OBST, "CYWG"));
        assertEquals("Winnipeg FIR.", say(Decode.NOTAM, NOTAM_FIR, "CZWG"));
        assertEquals("Toronto FIR.", say(Decode.NOTAM, NOTAM_FIR, "CZYZ"));
        assertEquals("Takes effect 27 Sep 2026 at 09:42 UTC.", say(Decode.NOTAM, NOTAM_OBST, "2609270942"));
        assertEquals("Ends 27 Nov 2026 at 23:59 UTC.", say(Decode.NOTAM, NOTAM_OBST, "2611272359"));
        assertEquals("Ends 11 Dec 2026 at 17:00 UTC (estimated).", say(Decode.NOTAM, NOTAM_FIR, "2612111700EST"));
        assertEquals("Permanent: no end date.", say(Decode.NOTAM, NOTAM_RWY, "PERM"));
        assertTrue(say(Decode.NOTAM, NOTAM_RWY, "D)").startsWith("Schedule"));
        assertEquals("Monday to friday.", say(Decode.NOTAM, NOTAM_RWY, "MON-FRI"));
        assertEquals("From 12:00 to 22:00 UTC.", say(Decode.NOTAM, NOTAM_RWY, "1200-2200"));
        assertTrue(say(Decode.NOTAM, NOTAM_OBST, "E)").startsWith("Text"));
    }

    @Test
    public void notamQualifierLine() {
        assertEquals("FIR: CZWG (Winnipeg FIR)\n"
                        + "Subject (OL): obstacle lights\n"
                        + "Condition (AS): unserviceable\n"
                        + "Traffic: IFR and VFR\n"
                        + "Purpose: miscellaneous, not in briefings unless requested\n"
                        + "Scope: aerodrome and en route\n"
                        + "Vertical limits: surface to FL010\n"
                        + "Centre: 49°54'N 097°11'W, radius 5 NM",
                say(Decode.NOTAM, NOTAM_OBST, "CZWG/QOLAS/IV/M/AE/000/010/4954N09711W005"));
        String fir = say(Decode.NOTAM, NOTAM_FIR, "CZXX/QXXXX/IV/NBO/E/000/999/5245N09040W999");
        assertTrue(fir, fir.contains("FIR: CZXX (More than one Canadian FIR)"));
        assertTrue(fir, fir.contains("Subject (XX): not coded: see the plain-language text"));
        assertTrue(fir, fir.contains("Purpose: immediate attention of flight crews; included in pre-flight briefings; "
                + "significant for flight operations"));
        assertTrue(fir, fir.contains("Scope: en route"));
        assertTrue(fir, fir.contains("Vertical limits: surface to unlimited"));
        assertTrue(fir, fir.contains("whole area"));
        String rwy = Decode.qualifier("CZWG/QMRLC/IV/NBO/A/000/999/4955N09714W005");
        assertTrue(rwy, rwy.contains("Subject (MR): runway\nCondition (LC): closed"));
        assertTrue(Decode.qualifier("CZWG/QZZZZ/IV/NBO/A/000/999/4955N09714W005").contains("not in the decoder's table"));
        assertNull(Decode.qualifier("garbage"));
    }

    @Test
    public void notamText() {
        assertEquals("Obstacle.", say(Decode.NOTAM, NOTAM_OBST, "OBST"));
        assertEquals("Light / lighting.", say(Decode.NOTAM, NOTAM_OBST, "LGT"));
        assertEquals("Unserviceable.", say(Decode.NOTAM, NOTAM_OBST, "U/S"));
        assertNull(say(Decode.NOTAM, NOTAM_OBST, "TOWER"));
        assertEquals("Latitude 49°54'22\" north.", say(Decode.NOTAM, NOTAM_OBST, "495422N"));
        assertEquals("Longitude 97°11'19\" west.", say(Decode.NOTAM, NOTAM_OBST, "0971119W"));
        assertEquals("Approximately.", say(Decode.NOTAM, NOTAM_OBST, "(APRX"));
        assertEquals("2 nautical miles.", say(Decode.NOTAM, NOTAM_OBST, "2NM"));
        assertEquals("Aerodrome.", say(Decode.NOTAM, NOTAM_OBST, "AD)"));
        assertEquals("155 ft.", say(Decode.NOTAM, NOTAM_OBST, "155FT"));
        assertEquals("Above ground level.", say(Decode.NOTAM, NOTAM_OBST, "AGL,"));
        assertEquals("Above mean sea level.", say(Decode.NOTAM, NOTAM_OBST, "AMSL.)"));
        assertEquals("Above.", say(Decode.NOTAM, NOTAM_FIR, "ABV"));
        assertEquals("Flight level 180: 18,000 ft on the standard pressure setting.", say(Decode.NOTAM, NOTAM_FIR, "FL180"));
        assertEquals("Available.", say(Decode.NOTAM, NOTAM_FIR, "AVBL)"));
        assertEquals("Runway.", say(Decode.NOTAM, NOTAM_RWY, "RWY"));
        assertEquals("Runway 07/25.", say(Decode.NOTAM, NOTAM_RWY, "07/25"));
        assertEquals("Closed.", say(Decode.NOTAM, NOTAM_RWY, "CLSD."));
        assertEquals("Instrument landing system.", say(Decode.NOTAM, NOTAM_RWY, "ILS"));
        assertEquals("Localizer / distance measuring equipment.", say(Decode.NOTAM, NOTAM_RWY, "LOC/DME"));
        assertEquals("Runway surface condition.", say(Decode.NOTAM, NOTAM_RWY, "RSC"));
        assertEquals("Runway 18/36.", say(Decode.NOTAM, NOTAM_RWY, "18/36"));
        assertTrue(say(Decode.NOTAM, NOTAM_RWY, "5/5/5").startsWith("Runway condition code for each third"));
        assertEquals("Percent.", say(Decode.NOTAM, NOTAM_RWY, "PCT"));
        assertNull("a phone number is not a date", say(Decode.NOTAM, NOTAM_RWY, "2045551234.)"));
        assertNull(say(Decode.NOTAM, NOTAM_RWY, "DRY."));
    }

    @Test
    public void wordRangesAndOddInput() {
        int[][] r = Decode.wordRanges("TAF CYWG\n  FM251500 19012KT ");
        assertEquals(4, r.length);
        assertEquals(0, r[0][0]);
        assertEquals(3, r[0][1]);
        assertEquals(11, r[2][0]);
        assertEquals(19, r[2][1]);
        assertEquals(0, Decode.wordRanges("   ").length);
        assertNull(Decode.decode(Decode.METAR, words(WG_FOG), 99));
        assertNull(Decode.decode(Decode.METAR, null, 0));
        assertNull(Decode.decode(7, words(WG_FOG), 0));
        // every word of every sample decodes without throwing
        for (String s : new String[]{WG_FOG, WG_SPECI, WG_DRIZZLE, WG_LAYERS, GFK, NOTAM_OBST, NOTAM_FIR, NOTAM_RWY}) {
            String[] w = words(s);
            for (int kind = 0; kind < 3; kind++) {
                for (int i = 0; i < w.length; i++) {
                    Decode.Hit h = Decode.decode(kind, w, i);
                    if (h != null) {
                        assertNotNull(h.text);
                        assertTrue(h.from <= i && i <= h.to && h.to < w.length);
                    }
                }
            }
        }
    }
}
