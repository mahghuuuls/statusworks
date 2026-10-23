package com.mahghuuuls.statusworks.core.rule;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RemovalReasonTest {

    @Test
    void reasonTextsAreTheDocumentedOnes() {
        List<String> texts = new ArrayList<>();
        for (RemovalReason r : RemovalReason.values()) {
            texts.add(r.text());
        }
        assertEquals(Arrays.asList("expired", "removed", "replaced", "death", "gone", "inactive_timeout", "invalid"),
                texts);
    }
}
