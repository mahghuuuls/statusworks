package com.mahghuuuls.statusworks.config;

import net.minecraftforge.common.config.Config;

/** Server settings, written to {@code config/statusworks.cfg}. Gameplay values live in definitions, not here. */
@Config(modid = "statusworks", name = "statusworks")
public final class ServerConfig {

    @Config.Comment({"Stop the game at startup when any definition is invalid, instead of disabling it.",
            "For pack development. Default: false."})
    @Config.RequiresMcRestart
    public static boolean strictMode = false;

    @Config.Comment({"Write one line to the server log for every change of tracked state.",
            "Can also be switched in game with /statusworks debug on|off. Default: false."})
    public static boolean diagnosticLog = false;

    @Config.Comment({"Most diagnostic log lines per second; extra lines are counted in a summary line. Default: 20."})
    @Config.RangeInt(min = 1)
    public static int diagnosticLogLinesPerSecond = 20;

    private ServerConfig() {
    }
}
