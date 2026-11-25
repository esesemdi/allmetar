package io.github.esesemdi.allmetar.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TafFormatTest {
    @Test
    public void oneChangeGroupPerLine() {
        String raw = "TAF AMD CYWG 251312Z 2513/2612 18010KT 1/2SM FZFG VV001 TEMPO 2513/2515 3SM BR OVC003 "
                + "FM251500 19012KT 1SM BR OVC004 PROB30 TEMPO 2515/2518 1/2SM FZFG BECMG 2520/2522 P6SM SCT020 "
                + "RMK NXT FCST BY 251800Z";
        String expected = "TAF AMD CYWG 251312Z 2513/2612 18010KT 1/2SM FZFG VV001\n"
                + "  TEMPO 2513/2515 3SM BR OVC003\n"
                + "  FM251500 19012KT 1SM BR OVC004\n"
                + "  PROB30 TEMPO 2515/2518 1/2SM FZFG\n"
                + "  BECMG 2520/2522 P6SM SCT020\n"
                + "  RMK NXT FCST BY 251800Z";
        assertEquals(expected, TafFormat.lines(raw));
    }

    @Test
    public void onlyWhitespaceChanges() {
        String raw = "TAF KGFK 251335Z 2514/2612 16006KT P6SM BKN040 FM251600 17007KT P6SM SCT030 PROB40 2518/2520 2SM BR";
        assertEquals(raw, TafFormat.lines(raw).replaceAll("\\s+", " "));
        assertEquals("", TafFormat.lines(null));
    }
}
