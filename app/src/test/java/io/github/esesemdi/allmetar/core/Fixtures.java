package io.github.esesemdi.allmetar.core;

/** Sample feed answers, shaped like the live services' responses. */
final class Fixtures {
    private Fixtures() {
    }

    /** aviationweather.gov /api/data/metar?ids=CYWG,KGFK&format=json&hours=2 */
    static final String METARS = "["
            + "{\"icaoId\":\"CYWG\",\"receiptTime\":\"2026-11-25T14:08:04.138Z\",\"obsTime\":1795615200,"
            + "\"reportTime\":\"2026-11-25T14:00:00.000Z\",\"temp\":-4,\"dewp\":-5,\"wdir\":190,\"wspd\":11,"
            + "\"visib\":0.5,\"altim\":1029.9,\"slp\":1031.5,\"qcField\":0,\"wxString\":\"FZFG\",\"vertVis\":1,"
            + "\"metarType\":\"METAR\",\"rawOb\":\"METAR CYWG 251400Z 19011KT 1/2SM R36/6000FT/U R13/6000FT/D FZFG "
            + "VV001 M04/M05 A3041 RMK FG8 SLP315\",\"lat\":49.916,\"lon\":-97.249,\"elev\":238,"
            + "\"name\":\"Winnipeg Intl Arpt, MB, CA\",\"cover\":\"OVX\",\"clouds\":[{\"cover\":\"OVX\",\"base\":100}],"
            + "\"fltCat\":\"LIFR\"},"
            + "{\"icaoId\":\"KGFK\",\"receiptTime\":\"2026-11-25T13:56:40.431Z\",\"obsTime\":1795614780,"
            + "\"reportTime\":\"2026-11-25T14:00:00.000Z\",\"temp\":-3.3,\"dewp\":-5,\"wdir\":180,\"wspd\":8,"
            + "\"visib\":\"10+\",\"altim\":1031.2,\"slp\":1032.5,\"qcField\":12,\"metarType\":\"METAR\","
            + "\"rawOb\":\"METAR KGFK 251353Z 18008KT 10SM BKN039 M03/M05 A3045 RMK AO2 SLP325 T10331050 $\","
            + "\"lat\":47.9428,\"lon\":-97.1829,\"elev\":256,\"name\":\"Grand Forks Intl, ND, US\",\"cover\":\"BKN\","
            + "\"clouds\":[{\"cover\":\"BKN\",\"base\":3900}],\"fltCat\":\"VFR\"},"
            + "{\"icaoId\":\"CYWG\",\"obsTime\":1795611600,\"reportTime\":\"2026-11-25T13:00:00.000Z\","
            + "\"metarType\":\"METAR\",\"rawOb\":\"METAR CYWG 251300Z 18011KT 1/2SM R36/5500VP6000FT/U "
            + "R13/5500VP6000FT/U FZFG VV001 M05/M05 A3042 RMK FG8 SLP319\",\"name\":\"Winnipeg Intl Arpt, MB, CA\"},"
            + "{\"icaoId\":\"KGFK\",\"obsTime\":1795611180,\"reportTime\":\"2026-11-25T13:00:00.000Z\","
            + "\"metarType\":\"METAR\",\"rawOb\":\"METAR KGFK 251253Z 17007KT 10SM BKN041 M04/M07 A3045 RMK AO2 "
            + "SLP326 T10391067 $\",\"name\":\"Grand Forks Intl, ND, US\"},"
            + "{\"icaoId\":\"CYWG\",\"obsTime\":1795611060,\"reportTime\":\"2026-11-25T12:51:00.000Z\","
            + "\"metarType\":\"SPECI\",\"rawOb\":\"SPECI CYWG 251251Z 18009KT 1/2SM R36/5500VP6000FT/N "
            + "R13/5500VP6000FT/N FZFG VV001 M05/M05 A3042 RMK FG8 SLP319\",\"name\":\"Winnipeg Intl Arpt, MB, CA\"}"
            + "]";

    /** aviationweather.gov /api/data/taf?ids=CYWG,KGFK&format=json */
    static final String TAFS = "["
            + "{\"icaoId\":\"CYWG\",\"dbPopTime\":\"2026-11-25T13:13:26.748Z\",\"bulletinTime\":\"2026-11-25T11:00:00.000Z\","
            + "\"issueTime\":\"2026-11-25T13:12:00.000Z\",\"validTimeFrom\":1795611600,\"validTimeTo\":1795694400,"
            + "\"rawTAF\":\"TAF AMD CYWG 251312Z 2513/2612 18010KT 1/2SM FZFG VV001 TEMPO 2513/2515 3SM BR OVC003 "
            + "FM251500 19012KT 1SM BR OVC004 PROB30 TEMPO 2515/2518 1/2SM FZFG BECMG 2520/2522 P6SM SCT020 "
            + "RMK NXT FCST BY 251800Z\",\"mostRecent\":1,\"remarks\":\" RMK NXT FCST BY 251800Z\",\"lat\":49.916,"
            + "\"lon\":-97.249,\"elev\":238,\"prior\":0,\"name\":\"Winnipeg Intl Arpt\",\"fcsts\":[{\"timeFrom\":1795611600,"
            + "\"timeTo\":1795618800,\"timeBec\":null,\"fcstChange\":null,\"probability\":null,\"wdir\":180,\"wspd\":10,"
            + "\"wgst\":null,\"visib\":0.5,\"wxString\":\"FZFG\",\"clouds\":[]}]},"
            + "{\"icaoId\":\"KGFK\",\"dbPopTime\":\"2026-11-25T13:35:24.516Z\",\"bulletinTime\":\"2026-11-25T13:35:00.000Z\","
            + "\"issueTime\":\"2026-11-25T13:35:00.000Z\",\"validTimeFrom\":1795615200,\"validTimeTo\":1795694400,"
            + "\"rawTAF\":\"TAF KGFK 251335Z 2514/2612 16006KT P6SM BKN040 FM251600 17007KT P6SM SCT030 "
            + "FM252300 18013KT P6SM SCT050\",\"mostRecent\":1,\"remarks\":\" AMD\",\"prior\":3,"
            + "\"name\":\"Grand Forks Intl\",\"fcsts\":[]}"
            + "]";

    /** plan.navcanada.ca /weather/api/alpha/?site=CYWG&alpha=notam (the "text" field is JSON inside a string) */
    static final String NOTAMS = "{\"meta\": {\"now\": \"2026-11-25T14:41:25.013\", \"count\": {\"notam\": 3}, "
            + "\"messages\": []}, \"data\": ["
            + "{\"type\": \"notam\", \"pk\": \"1822656\", \"location\": null, \"startValidity\": \"2026-11-20T15:52:00\", "
            + "\"endValidity\": null, \"text\": \"{\\\"raw\\\" : \\\"(G4138/26 NOTAMR G4044/26\\\\nQ) CZXX/QXXXX/IV/NBO/E/000/999/"
            + "5245N09040W999\\\\nA) CZWG CZYZ B) 2611201552 C) 2702122359\\\\nE) GNSS UNREL WITHIN AREA)\\\", "
            + "\\\"english\\\" : null, \\\"french\\\" : null}\", \"hasError\": false, "
            + "\"position\": {\"pointReference\": \"CYWG\", \"radialDistance\": 0}},"
            + "{\"type\": \"notam\", \"pk\": \"1827628\", \"location\": \"CYWG\", \"startValidity\": \"2026-11-25T14:08:00\", "
            + "\"endValidity\": \"2026-11-25T22:08:00\", \"text\": \"{\\\"raw\\\" : \\\"(S0234/26 NOTAMR S0205/26\\\\nQ) CZWG/QFAXX/"
            + "IV/NBO/A/000/999/4955N09714W005\\\\nA) CYWG B) 2611251408 C) 2611252208\\\\nE) RSC 18/36 5/5/5 100 PCT "
            + "DRY)\\\", \\\"english\\\" : null, \\\"french\\\" : null}\", \"hasError\": false, "
            + "\"position\": {\"pointReference\": \"CYWG\", \"radialDistance\": 0}},"
            + "{\"type\": \"notam\", \"pk\": \"1800001\", \"location\": null, \"startValidity\": \"2026-11-01T00:00:00\", "
            + "\"endValidity\": null, \"text\": \"{\\\"raw\\\" : \\\"(M1234/26 NOTAMN\\\\nQ) CZWG/QMRLC/IV/NBO/A/000/999/"
            + "4955N09714W005\\\\nA) CYWG B) 2611010000 C) 2612010000\\\\nE) RWY 07/25 CLSD)\\\", \\\"english\\\" : null, "
            + "\\\"french\\\" : null}\", \"hasError\": false, \"position\": {\"pointReference\": \"CYWG\", \"radialDistance\": 0}}"
            + "]}";

    /** A METAR feed entry for building custom answers. */
    static String metarJson(String id, long obs, String type, String rawWithoutKeyword) {
        return "{\"icaoId\":\"" + id + "\",\"obsTime\":" + obs + ",\"metarType\":\"" + type + "\",\"rawOb\":\""
                + type + " " + rawWithoutKeyword + "\",\"name\":\"" + id + " name\"}";
    }
}
