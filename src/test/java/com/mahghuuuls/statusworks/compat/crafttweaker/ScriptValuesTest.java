package com.mahghuuuls.statusworks.compat.crafttweaker;

import com.mahghuuuls.statusworks.api.StatusService;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ScriptValuesTest {

    private static final UUID A = new UUID(1, 2);

    @Test
    void aSourceIsAUuidInTextAndNoSourceIsTheEmptyText() {
        assertEquals(A, ScriptValues.uuid(A.toString()));
        assertEquals(StatusService.NO_SOURCE, ScriptValues.uuid(""));
        assertEquals(StatusService.NO_SOURCE, ScriptValues.uuid(null));
        assertNull(ScriptValues.uuid("zombie"), "not a UUID");
        assertNull(ScriptValues.uuid("1-2-3-4-5"), "loose text Java would accept");
        assertEquals(A.toString(), ScriptValues.text(A));
        assertEquals("", ScriptValues.text(StatusService.NO_SOURCE));
        assertEquals("", ScriptValues.text(null));
        assertArrayEquals(new String[]{"", A.toString()},
                ScriptValues.texts(Arrays.asList(StatusService.NO_SOURCE, A)));
        assertEquals("\"zombie\" is not a UUID", ScriptValues.notAUuid("zombie"));
    }
}
