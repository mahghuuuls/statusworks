package com.mahghuuuls.statusworks.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ParticleMathTest {

    private static int over(double perSecond, int ticks) {
        ParticleMath.Rate rate = new ParticleMath.Rate();
        int total = 0;
        for (int i = 0; i < ticks; i++) {
            total += rate.next(perSecond);
        }
        return total;
    }

    @Test
    void ratesSpreadOverTheTicksAndCarryTheRest() {
        assertEquals(4, over(4, 20));
        assertEquals(40, over(4, 200));
        assertEquals(1, over(0.5, 40));
        assertEquals(0, over(0.5, 39));
        assertEquals(100, over(100, 20));
        ParticleMath.Rate fast = new ParticleMath.Rate();
        assertEquals(2, fast.next(40), "two in every tick at 40 per second");
    }

    @Test
    void colorsBecomeRedGreenBlueForTheColoredTypesOnly() {
        assertArrayEquals(new double[]{1, 0, 0}, ParticleMath.colorArguments("mobSpell", 0xFF0000), 1e-9);
        assertArrayEquals(new double[]{0, 0, 1}, ParticleMath.colorArguments("mobSpellAmbient", 0x0000FF), 1e-9);
        assertArrayEquals(new double[]{0.001, 1, 0}, ParticleMath.colorArguments("reddust", 0x00FF00), 1e-9,
                "vanilla would turn a red of 0 into full red");
        assertArrayEquals(new double[]{0, 0, 0}, ParticleMath.colorArguments("flame", 0xFF0000), 1e-9);
        assertArrayEquals(new double[]{1, 1, 1}, ParticleMath.colorArguments("mobSpell", -1), 1e-9,
                "white, not vanilla's black");
        assertArrayEquals(new double[]{0, 0, 0}, ParticleMath.colorArguments("reddust", -1), 1e-9, "its own red");
    }

    @Test
    void theVanillaParticleSettingDecidesBeyond32Blocks() {
        java.util.Random random = new java.util.Random(1);
        int all = 0;
        int decreased = 0;
        int minimal = 0;
        for (int i = 0; i < 3000; i++) {
            all += ParticleMath.allowed(0, random) ? 1 : 0;
            decreased += ParticleMath.allowed(1, random) ? 1 : 0;
            minimal += ParticleMath.allowed(2, random) ? 1 : 0;
        }
        assertEquals(3000, all);
        assertEquals(0, minimal);
        assertEquals(2000, decreased, 100, "about two in three");
    }
}
