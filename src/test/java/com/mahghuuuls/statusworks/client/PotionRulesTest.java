package com.mahghuuuls.statusworks.client;

import com.mahghuuuls.statusworks.network.HudDefinition;
import com.mahghuuuls.statusworks.network.HudEntry;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PotionRulesTest {

    private static final HudDefinition SPEED = new HudDefinition(-1, "minecraft:speed", HudDefinition.POTION,
            "effect.moveSpeed", true, null, false, null, 0, null, true, false, true, 0, Integer.MAX_VALUE, 1,
            PotionEntries.TIMER_MODE);

    private static HudEntry effect(int amplifier, int remaining) {
        return new HudEntry("potion|minecraft:speed", -1, HudEntry.OWN, PotionRules.level(amplifier), 0, 0, remaining,
                true, null, Collections.<HudEntry.Group>emptyList());
    }

    @Test
    void anEffectShowsOnlyWhenVanillaWouldShowItAndThePlayerDidNotHideIt() {
        assertTrue(PotionRules.shown(true, true, false));
        assertFalse(PotionRules.shown(false, true, false), "the effect hides its HUD icon");
        assertFalse(PotionRules.shown(true, false, false), "hidden particles: vanilla does not show it either");
        assertFalse(PotionRules.shown(true, true, true), "hidden by the player's id list");
    }

    @Test
    void speedTwoWithNinetySecondsShowsTwoAndTwoMinutes() {
        HudEntry speed2 = effect(1, 90 * 20);
        assertEquals("2", HudText.corner(SPEED, speed2));
        assertEquals(Collections.singletonList("2m"), HudText.under(SPEED, speed2, speed2.remaining, true));
        assertEquals(Arrays.asList("2", "1:30"), HudText.fields(SPEED, speed2, speed2.remaining), "the named list");
        HudEntry speed1 = effect(0, 90 * 20);
        assertEquals("", HudText.corner(SPEED, speed1), "level I shows no number");
        assertEquals(Collections.singletonList("1:30"), HudText.fields(SPEED, speed1, speed1.remaining));
    }

    @Test
    void anEffectWithoutAnEndShowsNoTime() {
        assertEquals(-1, PotionRules.remaining(32767, true));
        assertEquals(400, PotionRules.remaining(400, false));
        assertEquals(0, PotionRules.remaining(-5, false));
        HudEntry endless = effect(0, PotionRules.remaining(32767, true));
        assertTrue(HudText.under(SPEED, endless, endless.remaining, true).isEmpty());
    }
}
