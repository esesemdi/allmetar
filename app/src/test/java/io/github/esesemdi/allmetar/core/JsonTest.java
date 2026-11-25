package io.github.esesemdi.allmetar.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.Map;

import org.junit.Test;

public class JsonTest {
    @Test
    public void parsesNestedValues() {
        Object v = Json.parse(" {\"a\": [1, 2.5, -3, true, false, null, \"x\"], \"b\": {\"c\": \"d\"}, \"e\": 1795615200} ");
        Map<String, Object> m = Json.obj(v);
        List<Object> a = Json.arr(m.get("a"));
        assertEquals(7, a.size());
        assertEquals(Long.valueOf(1), a.get(0));
        assertEquals(Double.valueOf(2.5), a.get(1));
        assertEquals(Long.valueOf(-3), a.get(2));
        assertEquals(Boolean.TRUE, a.get(3));
        assertEquals(Boolean.FALSE, a.get(4));
        assertNull(a.get(5));
        assertEquals("x", a.get(6));
        assertEquals("d", Json.str(Json.obj(m.get("b")), "c", null));
        assertEquals(1795615200L, Json.lng(m, "e", 0));
    }

    @Test
    public void handlesEscapes() {
        Object v = Json.parse("{\"t\":\"line1\\nline2 \\\"q\\\" \\\\ \\u00e9 \\/\"}");
        assertEquals("line1\nline2 \"q\" \\ é /", Json.str(Json.obj(v), "t", null));
    }

    @Test
    public void roundTrips() {
        String src = "{\"a\":[1,2.5,true,null,\"x\\ny\\t\\\"z\\\"\"],\"b\":{\"c\":-7}}";
        assertEquals(src, Json.write(Json.parse(src)));
    }

    @Test
    public void missingAndNullValuesUseDefaults() {
        Map<String, Object> m = Json.obj(Json.parse("{\"n\":null,\"s\":\"12\"}"));
        assertEquals("def", Json.str(m, "n", "def"));
        assertEquals("def", Json.str(m, "absent", "def"));
        assertEquals(12L, Json.lng(m, "s", 0));
        assertEquals(5L, Json.lng(m, "n", 5));
    }

    @Test
    public void rejectsBrokenInput() {
        for (String bad : new String[]{"", "{", "[1,", "{\"a\" 1}", "nope", "[1] x", "\"abc"}) {
            try {
                Json.parse(bad);
                fail("accepted: " + bad);
            } catch (Json.JsonException expected) {
                assertTrue(true);
            }
        }
    }
}
