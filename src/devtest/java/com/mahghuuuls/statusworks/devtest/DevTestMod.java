package com.mahghuuuls.statusworks.devtest;

import com.mahghuuuls.statusworks.api.RegistrationResult;
import com.mahghuuuls.statusworks.api.StatusworksApi;
import com.mahghuuuls.statusworks.api.definition.DeathRule;
import com.mahghuuuls.statusworks.api.definition.ExpirationMode;
import com.mahghuuuls.statusworks.api.definition.Ownership;
import com.mahghuuuls.statusworks.api.definition.RefreshRule;
import com.mahghuuuls.statusworks.api.definition.StatusDefinition;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Development test addon for Statusworks. Lives in its own source set, runs only in the development client and
 * server, and never ships. It registers test definitions through the public API exactly as an addon would, and
 * offers {@code /stwdev} for checks that no ordinary command can reach.
 */
@Mod(modid = DevTestMod.MOD_ID, name = "Statusworks Dev Test", version = "dev",
        dependencies = "required-after:statusworks")
public class DevTestMod {

    public static final String MOD_ID = "statusworks_devtest";
    public static final Logger LOGGER = LogManager.getLogger("stw-devtest");

    static String id(String path) {
        return MOD_ID + ":" + path;
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        register(logged(StatusDefinition.builder(id("shared"))).limit(5).defaultDuration(200).build());
        register(logged(StatusDefinition.builder(id("gradual"))).limit(3).defaultDuration(100)
                .expiration(ExpirationMode.GRADUAL).build());
        register(logged(StatusDefinition.builder(id("independent"))).limit(10).defaultDuration(200)
                .expiration(ExpirationMode.INDEPENDENT).build());
        register(logged(StatusDefinition.builder(id("none"))).limit(3).expiration(ExpirationMode.NONE).build());
        register(logged(StatusDefinition.builder(id("extend"))).limit(5).defaultDuration(100)
                .refresh(RefreshRule.EXTEND).maximumRemaining(300).build());
        register(logged(StatusDefinition.builder(id("preserve"))).limit(5).defaultDuration(100)
                .refresh(RefreshRule.PRESERVE).build());
        register(logged(StatusDefinition.builder(id("noreject"))).limit(2).defaultDuration(100)
                .refreshOnReject(false).build());
        register(logged(StatusDefinition.builder(id("persource"))).limit(3).defaultDuration(200)
                .ownership(Ownership.PER_SOURCE).build());
        register(StatusDefinition.builder(id("long")).limit(5).defaultDuration(6000).build());
        register(logged(StatusDefinition.builder(id("interval"))).limit(3).defaultDuration(60)
                .onInterval(20, c -> LOGGER.info("[stw-devtest] interval {} count={} ticksExisted={}",
                        c.definitionId(), c.countAfter(), c.entity().ticksExisted)).build());
        register(logged(StatusDefinition.builder(id("throwing"))).defaultDuration(200)
                .onInterval(1, c -> {
                    throw new IllegalStateException("devtest: this interval callback always fails");
                }).build());
        register(StatusDefinition.builder(id("selffeed")).limit(100).expiration(ExpirationMode.NONE)
                .onQuantityChanged(c -> {
                    LOGGER.info("[stw-devtest] quantity_changed {}", c);
                    if (c.countAfter() > c.countBefore()) {
                        StatusworksApi.statuses().apply(c.entity(), c.definitionId(), 1);
                    }
                }).build());
        register(logged(StatusDefinition.builder(id("bonus"))).limit(5).defaultDuration(200)
                .onApplied(c -> {
                    LOGGER.info("[stw-devtest] applied {} (adds 1 bonus stack)", c);
                    StatusworksApi.statuses().apply(c.entity(), c.definitionId(), 1);
                }).build());
        // chaina is registered before chainb, so it is processed first; its interval callback ends chainb and
        // applies it again. The new chainb must survive the rest of that tick.
        register(logged(StatusDefinition.builder(id("chaina"))).defaultDuration(200)
                .onInterval(20, c -> {
                    String b = id("chainb");
                    LOGGER.info("[stw-devtest] interval {} clears and re-applies {}", c.definitionId(), b);
                    StatusworksApi.statuses().clear(c.entity(), b);
                    StatusworksApi.statuses().apply(c.entity(), b, 1);
                }).build());
        register(logged(StatusDefinition.builder(id("chainb"))).defaultDuration(200).build());
        register(logged(StatusDefinition.builder(id("keep"))).limit(3).defaultDuration(6000)
                .deathRule(DeathRule.KEEP).build());
        // fitme: limit 10 and 6000 ticks normally. -Dstatusworks.devtest.fitme=removed leaves it unregistered,
        // =lowlimit lowers its limit to 5, and =<mode> (gradual, independent, none) changes its expiration.
        String fitme = System.getProperty("statusworks.devtest.fitme", "");
        if (!"removed".equals(fitme)) {
            StatusDefinition.Builder b = logged(StatusDefinition.builder(id("fitme"))).limit(10).defaultDuration(6000);
            if ("lowlimit".equals(fitme)) {
                b.limit(5);
            } else if (ExpirationMode.fromText(fitme) != null) {
                b.expiration(ExpirationMode.fromText(fitme));
            }
            register(b.build());
        }
        if (Boolean.getBoolean("statusworks.devtest.invalid")) {
            register(StatusDefinition.builder(id("invalid")).limit(0).build());
            register(StatusDefinition.builder(id("badmode")).expiration("sometimes").build());
        }
        if (event.getSide().isClient()) {
            MinecraftForge.EVENT_BUS.register(new DevTestClientCheck());
        }
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        RegistrationResult late = StatusworksApi.registerStatus(StatusDefinition.builder(id("late")).build());
        LOGGER.info("[stw-devtest] late registration result: {}", late);
        event.registerServerCommand(new DevTestCommand());
    }

    /** Adds callbacks that log each status point as {@code [stw-devtest] <point> <context>}. */
    private static StatusDefinition.Builder logged(StatusDefinition.Builder builder) {
        return builder
                .onApplied(c -> LOGGER.info("[stw-devtest] applied {}{}", c,
                        c.sourceEntity() == null ? "" : " sourceEntity=" + c.sourceEntity().getName()))
                .onQuantityChanged(c -> LOGGER.info("[stw-devtest] quantity_changed {}", c))
                .onRemoved(c -> LOGGER.info("[stw-devtest] removed {}", c));
    }

    private static void register(StatusDefinition definition) {
        RegistrationResult result = StatusworksApi.registerStatus(definition);
        LOGGER.info("[stw-devtest] registered {}: {}", definition.id(), result);
    }
}
