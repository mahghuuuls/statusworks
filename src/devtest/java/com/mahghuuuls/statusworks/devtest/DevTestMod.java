package com.mahghuuuls.statusworks.devtest;

import com.mahghuuuls.statusworks.api.RegistrationResult;
import com.mahghuuuls.statusworks.api.StatusworksApi;
import com.mahghuuuls.statusworks.api.definition.AttributeOperation;
import com.mahghuuuls.statusworks.api.definition.Contribution;
import com.mahghuuuls.statusworks.api.definition.CooldownDefinition;
import com.mahghuuuls.statusworks.api.definition.DeathRule;
import com.mahghuuuls.statusworks.api.definition.MarkDefinition;
import com.mahghuuuls.statusworks.api.definition.MarkSide;
import com.mahghuuuls.statusworks.api.definition.Overflow;
import com.mahghuuuls.statusworks.api.definition.ParticleAudience;
import com.mahghuuuls.statusworks.api.definition.Particles;
import com.mahghuuuls.statusworks.api.definition.Direction;
import com.mahghuuuls.statusworks.api.definition.PoolDefinition;
import com.mahghuuuls.statusworks.api.definition.PoolStart;
import com.mahghuuuls.statusworks.api.definition.Presentation;
import com.mahghuuuls.statusworks.api.definition.ExpirationMode;
import com.mahghuuuls.statusworks.api.definition.Ownership;
import com.mahghuuuls.statusworks.api.definition.RefreshRule;
import com.mahghuuuls.statusworks.api.definition.StatusDefinition;
import com.mahghuuuls.statusworks.api.definition.ValueForm;
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
        register(logged(StatusDefinition.builder(id("shared"))).limit(5).defaultDuration(200)
                .presentation(Presentation.builder().name("Shared").description("Test status: its stacks share one timer.")
                        .iconItem("minecraft:blaze_powder", 0).priority(1).build()).build());
        register(logged(StatusDefinition.builder(id("gradual"))).limit(3).defaultDuration(100)
                .presentation(Presentation.builder().name("Gradual").iconItem("minecraft:sugar", 0).priority(2).build())
                .expiration(ExpirationMode.GRADUAL).build());
        register(logged(StatusDefinition.builder(id("independent"))).limit(10).defaultDuration(200)
                .expiration(ExpirationMode.INDEPENDENT).build());
        register(logged(StatusDefinition.builder(id("none"))).limit(3).expiration(ExpirationMode.NONE)
                .presentation(Presentation.builder().nameKey("statusworks_devtest.hud.none")
                        .descriptionKey("statusworks_devtest.hud.none.desc").iconItem("minecraft:dye", 4).build())
                .build());
        register(logged(StatusDefinition.builder(id("extend"))).limit(5).defaultDuration(100)
                .refresh(RefreshRule.EXTEND).maximumRemaining(300).build());
        register(logged(StatusDefinition.builder(id("preserve"))).limit(5).defaultDuration(100)
                .refresh(RefreshRule.PRESERVE).build());
        register(logged(StatusDefinition.builder(id("noreject"))).limit(2).defaultDuration(100)
                .refreshOnReject(false).build());
        register(logged(StatusDefinition.builder(id("persource"))).limit(3).defaultDuration(200)
                .presentation(Presentation.builder().name("Per source").iconItem("minecraft:redstone", 0).priority(3)
                        .build())
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
        // Pools, one per progression rule and option, each with logging callbacks.
        registerPool(loggedPool(PoolDefinition.builder(id("pfixed"))).capacity(10).start(PoolStart.FULL)
                .fixed(Direction.DRAIN, 2).build());
        registerPool(loggedPool(PoolDefinition.builder(id("pfill"))).capacity(10).fixed(Direction.FILL, 2)
                .presentation(Presentation.builder().name("Filling").decimals(1)
                        .iconTexture("minecraft:textures/items/bucket_water.png").build()).build());
        registerPool(loggedPool(PoolDefinition.builder(id("pcap"))).capacity(50)
                .percentOfCapacity(Direction.FILL, 10).build());
        registerPool(loggedPool(PoolDefinition.builder(id("pcur"))).capacity(10).startAmount(8)
                .percentOfCurrent(Direction.DRAIN, 50).build());
        registerPool(loggedPool(PoolDefinition.builder(id("pdefaultcut"))).capacity(100).start(PoolStart.FULL)
                .percentOfCurrent(Direction.DRAIN, 50).build());
        registerPool(loggedPool(PoolDefinition.builder(id("pcutoff"))).capacity(100).start(PoolStart.FULL)
                .percentOfCurrent(Direction.DRAIN, 50).cutoff(5).build());
        registerPool(loggedPool(PoolDefinition.builder(id("ptarget"))).capacity(20).startAmount(10)
                .toTarget(0, 100).build());
        registerPool(loggedPool(PoolDefinition.builder(id("plife"))).capacity(10).lifetime(100).build());
        registerPool(loggedPool(PoolDefinition.builder(id("pkeep"))).capacity(10).startAmount(5)
                .deathRule(DeathRule.KEEP).build());
        // pplain's capacity can be lowered for a restart card: -Dstatusworks.devtest.pplaincap=<capacity>.
        registerPool(loggedPool(PoolDefinition.builder(id("pplain")))
                .capacity(Double.parseDouble(System.getProperty("statusworks.devtest.pplaincap", "10"))).build());
        // Contributions: maximum health per stack, a fixed +10 maximum health, two statuses on armor, movement
        // speed by pool fill, and a players-only contribution type.
        LOGGER.info("[stw-devtest] registered contribution type power: {}",
                StatusworksApi.registerContributionType(new PowerType()));
        register(logged(StatusDefinition.builder(id("hpstack"))).limit(5).defaultDuration(6000)
                .contribute(Contribution.attribute("generic.maxHealth", AttributeOperation.ADD, ValueForm.PER_STACK, 4))
                .build());
        register(logged(StatusDefinition.builder(id("hpkeep"))).defaultDuration(6000).deathRule(DeathRule.KEEP)
                .contribute(Contribution.attribute("generic.maxHealth", AttributeOperation.ADD, ValueForm.FIXED, 10))
                .build());
        register(logged(StatusDefinition.builder(id("hp10"))).defaultDuration(6000)
                .contribute(Contribution.attribute("generic.maxHealth", AttributeOperation.ADD, ValueForm.FIXED, 10))
                .build());
        register(logged(StatusDefinition.builder(id("armora"))).defaultDuration(6000)
                .contribute(Contribution.attribute("generic.armor", AttributeOperation.ADD, ValueForm.FIXED, 2))
                .build());
        register(logged(StatusDefinition.builder(id("armorb"))).defaultDuration(6000)
                .contribute(Contribution.attribute("generic.armor", AttributeOperation.ADD, ValueForm.FIXED, 3))
                .build());
        register(logged(StatusDefinition.builder(id("slowstack"))).limit(5).defaultDuration(6000)
                .contribute(Contribution.attribute("generic.movementSpeed", AttributeOperation.MULTIPLY_TOTAL,
                        ValueForm.PER_STACK, 0.05)).build());
        register(logged(StatusDefinition.builder(id("powerstatus"))).limit(5).defaultDuration(6000)
                .contribute(Contribution.type(id("power"), ValueForm.PER_STACK, 1)).build());
        registerPool(loggedPool(PoolDefinition.builder(id("pspeed"))).capacity(100).start(PoolStart.FULL)
                .fixed(Direction.DRAIN, 5)
                .contribute(Contribution.attribute("generic.movementSpeed", AttributeOperation.MULTIPLY_TOTAL,
                        ValueForm.PER_FILL, 0.5)).build());
        registerPool(loggedPool(PoolDefinition.builder(id("phealth"))).capacity(20).startAmount(5)
                .contribute(Contribution.attribute("generic.maxHealth", AttributeOperation.ADD, ValueForm.PER_FILL, 10))
                .build());
        if (Boolean.getBoolean("statusworks.devtest.invalid")) {
            register(StatusDefinition.builder(id("badattr"))
                    .contribute(Contribution.attribute("generic.maxHelth", AttributeOperation.ADD, ValueForm.FIXED, 1))
                    .build());
            register(StatusDefinition.builder(id("badtype"))
                    .contribute(Contribution.type(id("nope"), ValueForm.FIXED, 1)).build());
            register(StatusDefinition.builder(id("moddedattr"))
                    .contribute(Contribution.attribute("somemod.mana", AttributeOperation.ADD, ValueForm.FIXED, 1))
                    .build());
            register(StatusDefinition.builder(id("badform"))
                    .contribute(Contribution.attribute("generic.armor", AttributeOperation.ADD, ValueForm.PER_FILL, 1))
                    .build());
        }
        // Cooldowns: kept through a player's death (the default) and cleared on death.
        registerCooldown(CooldownDefinition.builder(id("ckeep")).defaultDuration(600)
                .presentation(Presentation.builder().name("Cooldown").iconItem("minecraft:clock", 0).build())
                .onEnded(c -> LOGGER.info("[stw-devtest] cooldown_ended {}", c)).build());
        registerCooldown(CooldownDefinition.builder(id("cclear")).defaultDuration(600).deathRule(DeathRule.CLEAR)
                .onEnded(c -> LOGGER.info("[stw-devtest] cooldown_ended {}", c)).build());
        registerCooldown(CooldownDefinition.builder(id("cshort")).defaultDuration(40)
                .onEnded(c -> LOGGER.info("[stw-devtest] cooldown_ended {}", c)).build());
        if (Boolean.getBoolean("statusworks.devtest.invalid")) {
            register(StatusDefinition.builder(id("invalid")).limit(0).build());
            register(StatusDefinition.builder(id("badmode")).expiration("sometimes").build());
        }
        // Marks: limits and overflow, refresh rules, no duration, distance, inactive timeout, a source-side armor
        // contribution, and a mark kept through the source player's death. Every point is logged.
        registerMark(loggedMark(MarkDefinition.builder(id("mone"))).defaultDuration(600)
                .presentation(Presentation.builder().name("Hunt").iconItem("minecraft:bow", 0).build())
                .onInterval(100, c -> LOGGER.info("[stw-devtest] mark_interval {}", c)).build());
        registerMark(loggedMark(MarkDefinition.builder(id("mthree"))).targetLimit(3)
                .overflow(Overflow.REPLACE_OLDEST).defaultDuration(1200).build());
        registerMark(loggedMark(MarkDefinition.builder(id("mtwo"))).targetLimit(2).overflow(Overflow.REJECT)
                .defaultDuration(1200).build());
        registerMark(loggedMark(MarkDefinition.builder(id("mextend"))).defaultDuration(100)
                .refresh(RefreshRule.EXTEND).maximumRemaining(250).build());
        registerMark(loggedMark(MarkDefinition.builder(id("mpreserve"))).defaultDuration(100)
                .refresh(RefreshRule.PRESERVE).build());
        registerMark(loggedMark(MarkDefinition.builder(id("mnone"))).unlimitedTargets().noDuration().build());
        registerMark(loggedMark(MarkDefinition.builder(id("mdist"))).noDuration().maximumDistance(32).build());
        registerMark(loggedMark(MarkDefinition.builder(id("mtimeout"))).noDuration().maximumInactive(200).build());
        registerMark(loggedMark(MarkDefinition.builder(id("marmor"))).unlimitedTargets().noDuration()
                .contribute(MarkSide.SOURCE, Contribution.attribute("generic.armor", AttributeOperation.ADD,
                        ValueForm.FIXED, 4)).build());
        registerMark(loggedMark(MarkDefinition.builder(id("mkeep"))).defaultDuration(6000).keepOnSourceDeath(true)
                .build());
        // Maximum health on both sides, so a reload, relog, or restart card can see any dip as a health clamp.
        registerMark(loggedMark(MarkDefinition.builder(id("mhp"))).unlimitedTargets().noDuration()
                .contribute(MarkSide.SOURCE, Contribution.attribute("generic.maxHealth", AttributeOperation.ADD,
                        ValueForm.FIXED, 4))
                .contribute(MarkSide.TARGET, Contribution.attribute("generic.maxHealth", AttributeOperation.ADD,
                        ValueForm.FIXED, 10)).build());
        // HUD tests: a pool of 50 with one decimal, an author-hidden status that still gives armor, a mark shown on
        // both sides, three plain fillers.
        registerPool(PoolDefinition.builder(id("phud")).capacity(50)
                .presentation(Presentation.builder().name("Mana").decimals(1).iconItem("minecraft:glowstone_dust", 0)
                        .build()).build());
        register(logged(StatusDefinition.builder(id("secret"))).defaultDuration(6000)
                .presentation(Presentation.builder().hidden().build())
                .contribute(Contribution.attribute("generic.armor", AttributeOperation.ADD, ValueForm.FIXED, 1)).build());
        registerMark(loggedMark(MarkDefinition.builder(id("mboth"))).noDuration()
                .presentation(Presentation.builder().name("Linked").iconItem("minecraft:lead", 0).markHud(true, true)
                        .build()).build());
        for (String filler : new String[]{"fill1", "fill2", "fill3"}) {
            register(StatusDefinition.builder(id(filler)).defaultDuration(6000)
                    .presentation(Presentation.builder().priority(9).build()).build());
        }
        // Particle tests: red spell particles at 4 per second within 32 blocks; green dust with long distance; a
        // pool with happy-villager particles; a mark's yellow particles for its source only; hearts for a mark's
        // target only; an author-hidden status with flames, which must never reach a client.
        register(logged(StatusDefinition.builder(id("pspark"))).defaultDuration(6000)
                .presentation(Presentation.builder().name("Sparks").iconItem("minecraft:redstone_torch", 0)
                        .particles(Particles.builder("mobSpell").color(255, 0, 0).perSecond(4).build()).build())
                .build());
        register(logged(StatusDefinition.builder(id("pfar"))).defaultDuration(6000)
                .presentation(Presentation.builder().name("Far glow").iconItem("minecraft:emerald", 0)
                        .particles(Particles.builder("reddust").color(0x00FF00).perSecond(8).longDistance().build())
                        .build()).build());
        registerPool(PoolDefinition.builder(id("pmist")).capacity(10)
                .presentation(Presentation.builder().name("Mist").iconItem("minecraft:slime_ball", 0)
                        .particles(Particles.builder("happyVillager").perSecond(2).build()).build()).build());
        registerMark(loggedMark(MarkDefinition.builder(id("pmark"))).defaultDuration(2400)
                .presentation(Presentation.builder().name("Tagged").iconItem("minecraft:gold_nugget", 0)
                        .particles(Particles.builder("mobSpellAmbient").color(255, 255, 0).perSecond(4).build())
                        .build()).build());
        registerMark(loggedMark(MarkDefinition.builder(id("pmarkt"))).defaultDuration(2400)
                .presentation(Presentation.builder().name("Loved").iconItem("minecraft:poppy", 0)
                        .particles(Particles.builder("heart").perSecond(2).audience(ParticleAudience.TARGET).build())
                        .build()).build());
        // For the 32-block edge: the same red particles, 20 per second, so they are easy to see at 30 blocks. And a
        // mark that turns inactive beyond 8 blocks, with yellow particles for its source.
        register(logged(StatusDefinition.builder(id("pedge"))).defaultDuration(12000)
                .presentation(Presentation.builder().name("Edge").iconItem("minecraft:redstone_torch", 0)
                        .particles(Particles.builder("mobSpell").color(255, 0, 0).perSecond(20).build()).build())
                .build());
        registerMark(loggedMark(MarkDefinition.builder(id("pmarkd"))).noDuration().maximumDistance(8)
                .presentation(Presentation.builder().name("Close").iconItem("minecraft:string", 0)
                        .particles(Particles.builder("mobSpellAmbient").color(255, 255, 0).perSecond(8).build())
                        .build()).build());
        register(logged(StatusDefinition.builder(id("psecret"))).defaultDuration(6000)
                .presentation(Presentation.builder().hidden().particles(Particles.builder("flame").build()).build())
                .build());
        if (Boolean.getBoolean("statusworks.devtest.invalid")) {
            registerMark(MarkDefinition.builder(id("badmark")).targetLimit(0).build());
            registerMark(MarkDefinition.builder(id("badmarkform")).contribute(MarkSide.TARGET,
                    Contribution.attribute("generic.armor", AttributeOperation.ADD, ValueForm.PER_STACK, 1)).build());
        }
        if (event.getSide().isClient()) {
            MinecraftForge.EVENT_BUS.register(new DevTestClientCheck());
            MinecraftForge.EVENT_BUS.register(new DevTestClientView());
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

    /** Adds callbacks that log each pool point as {@code [stw-devtest] pool_<point> <context>}. */
    private static PoolDefinition.Builder loggedPool(PoolDefinition.Builder builder) {
        return builder
                .onAmountChanged(c -> LOGGER.info("[stw-devtest] pool_amount_changed {}", c))
                .onEmpty(c -> LOGGER.info("[stw-devtest] pool_empty {}", c))
                .onFull(c -> LOGGER.info("[stw-devtest] pool_full {}", c))
                .onTargetReached(c -> LOGGER.info("[stw-devtest] pool_target_reached {}", c))
                .onRemoved(c -> LOGGER.info("[stw-devtest] pool_removed {}", c));
    }

    /** Adds callbacks that log each mark point as {@code [stw-devtest] mark_<point> <context>}. */
    private static MarkDefinition.Builder loggedMark(MarkDefinition.Builder builder) {
        return builder
                .onApplied(c -> LOGGER.info("[stw-devtest] mark_applied {}", c))
                .onInactive(c -> LOGGER.info("[stw-devtest] mark_inactive {}", c))
                .onActive(c -> LOGGER.info("[stw-devtest] mark_active {}", c))
                .onRemoved(c -> LOGGER.info("[stw-devtest] mark_removed {}", c));
    }

    private static void registerMark(MarkDefinition definition) {
        RegistrationResult result = StatusworksApi.registerMark(definition);
        LOGGER.info("[stw-devtest] registered mark {}: {}", definition.id(), result);
    }

    private static void registerCooldown(CooldownDefinition definition) {
        RegistrationResult result = StatusworksApi.registerCooldown(definition);
        LOGGER.info("[stw-devtest] registered cooldown {}: {}", definition.id(), result);
    }

    private static void registerPool(PoolDefinition definition) {
        RegistrationResult result = StatusworksApi.registerPool(definition);
        LOGGER.info("[stw-devtest] registered pool {}: {}", definition.id(), result);
    }

    private static void register(StatusDefinition definition) {
        RegistrationResult result = StatusworksApi.registerStatus(definition);
        LOGGER.info("[stw-devtest] registered {}: {}", definition.id(), result);
    }
}
