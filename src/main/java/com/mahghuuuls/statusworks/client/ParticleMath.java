package com.mahghuuuls.statusworks.client;

/** The number rules of client particles: how many to spawn each tick, and how a color becomes spawn arguments. */
final class ParticleMath {

    private ParticleMath() {
    }

    /**
     * Spawns per tick for one rate: the fraction left over carries to the next tick, so 4 per second gives 4 in
     * every 20 ticks and 0.5 per second gives one every 40.
     */
    static final class Rate {
        private double carry;

        int next(double perSecond) {
            carry += perSecond / 20.0;
            int n = (int) carry;
            carry -= n;
            return n;
        }
    }

    /**
     * Whether one particle is drawn under the vanilla particle setting, as vanilla decides it within 32 blocks: all
     * (0) draws every one, decreased (1) two in three, minimal (2) none.
     */
    static boolean allowed(int particleSetting, java.util.Random random) {
        int level = particleSetting;
        if (level == 1 && random.nextInt(3) == 0) {
            level = 2;
        }
        return level <= 1;
    }

    /**
     * The three speed arguments that color a particle: red, green, and blue from 0 to 1 for {@code mobSpell},
     * {@code mobSpellAmbient}, and {@code reddust}; zeros for other types. Without a color, the spell particles are
     * white (vanilla would draw zeros as black) and {@code reddust} is its own red. Vanilla turns a red of exactly 0
     * into full red for {@code reddust}, so a color without red sends a red just above 0.
     */
    static double[] colorArguments(String type, int color) {
        boolean spell = "mobSpell".equals(type) || "mobSpellAmbient".equals(type);
        if (!spell && !"reddust".equals(type)) {
            return new double[]{0, 0, 0};
        }
        if (color < 0) {
            return spell ? new double[]{1, 1, 1} : new double[]{0, 0, 0};
        }
        double r = ((color >> 16) & 0xFF) / 255.0;
        double g = ((color >> 8) & 0xFF) / 255.0;
        double b = (color & 0xFF) / 255.0;
        if ("reddust".equals(type) && r == 0) {
            r = 0.001;
        }
        return new double[]{r, g, b};
    }
}
