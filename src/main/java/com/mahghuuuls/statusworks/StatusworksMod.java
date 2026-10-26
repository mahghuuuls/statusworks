package com.mahghuuuls.statusworks;

import com.mahghuuuls.statusworks.api.StatusworksApi;
import com.mahghuuuls.statusworks.command.StatusworksCommand;
import com.mahghuuuls.statusworks.config.ServerConfig;
import com.mahghuuuls.statusworks.core.definition.DefinitionRegistry;
import com.mahghuuuls.statusworks.core.state.EntityState;
import com.mahghuuuls.statusworks.core.state.EntityStateProvider;
import com.mahghuuuls.statusworks.network.StatusworksNetwork;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.nbt.NBTBase;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLConstructionEvent;
import net.minecraftforge.fml.common.event.FMLLoadCompleteEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Mod entry point. CraftTweaker is optional and declared with an inclusive minimum and no
 * upper bound; nothing is required beyond Forge.
 */
@Mod(
        modid = Tags.MOD_ID,
        name = Tags.MOD_NAME,
        version = Tags.VERSION,
        acceptedMinecraftVersions = "[1.12.2]",
        dependencies = "after:crafttweaker@[4.1.20,)"
)
public class StatusworksMod {

    public static final Logger LOGGER = LogManager.getLogger(Tags.MOD_NAME);

    @SidedProxy(clientSide = "com.mahghuuuls.statusworks.ClientProxy",
            serverSide = "com.mahghuuuls.statusworks.CommonProxy")
    public static CommonProxy proxy;

    private StatusworksCore core;

    @Mod.EventHandler
    public void construct(FMLConstructionEvent event) {
        // Installed before any mod's pre-init, so every registration gets an answer instead of an exception.
        core = new StatusworksCore(LOGGER, false, ServerConfig.diagnosticLogLinesPerSecond);
        StatusworksApi.install(core);
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ConfigManager.sync(Tags.MOD_ID, Config.Type.INSTANCE);
        core.diagnosticLog.configure(ServerConfig.diagnosticLog, null, null);
        core.diagnosticLog.setLinesPerSecond(ServerConfig.diagnosticLogLinesPerSecond);
        CapabilityManager.INSTANCE.register(EntityState.class, new Capability.IStorage<EntityState>() {
            @Nullable
            @Override
            public NBTBase writeNBT(Capability<EntityState> capability, EntityState instance, EnumFacing side) {
                return null;
            }

            @Override
            public void readNBT(Capability<EntityState> capability, EntityState instance, EnumFacing side,
                                NBTBase nbt) {
            }
        }, EntityState::new);
        MinecraftForge.EVENT_BUS.register(core.tickDriver);
        MinecraftForge.EVENT_BUS.register(core.deathAndClone);
        MinecraftForge.EVENT_BUS.register(core.ownEntries);
        MinecraftForge.EVENT_BUS.register(core.audience);
        StatusworksNetwork.register();
        proxy.preInit(event);
        MinecraftForge.EVENT_BUS.register(this);
        LOGGER.info("{} {} loading (strictMode={}, diagnosticLog={}, diagnosticLogLinesPerSecond={})",
                Tags.MOD_NAME, Tags.VERSION, ServerConfig.strictMode, ServerConfig.diagnosticLog,
                ServerConfig.diagnosticLogLinesPerSecond);
    }

    @Mod.EventHandler
    public void loadComplete(FMLLoadCompleteEvent event) {
        core.contributionTypes.freeze();
        List<DefinitionRegistry.Error> errors = core.registry.freeze();
        for (DefinitionRegistry.Error error : errors) {
            LOGGER.error("Statusworks: invalid definition {}", error);
        }
        LOGGER.info("Statusworks definitions frozen: {} statuses valid, {} invalid; {} pools valid, {} invalid; "
                        + "{} marks valid, {} invalid; {} cooldowns valid, {} invalid; {} errors",
                core.registry.statuses().size(), core.registry.invalid().size(), core.registry.pools().size(),
                core.registry.invalidPools().size(), core.registry.marks().size(), core.registry.invalidMarks().size(),
                core.registry.cooldowns().size(), core.registry.invalidCooldowns().size(), errors.size());
        if (ServerConfig.strictMode && !errors.isEmpty()) {
            StringBuilder message = new StringBuilder("Statusworks strict mode: ").append(errors.size())
                    .append(" invalid definition error(s)");
            for (DefinitionRegistry.Error error : errors) {
                message.append("; ").append(error);
            }
            throw new IllegalStateException(message.toString());
        }
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        checkModdedAttributes(event);
        core.ownEntries.buildTable();
        event.registerServerCommand(new StatusworksCommand(new StatusworksCommand.Context(core.statuses,
                core.pools, core.cooldowns, core.marks, core.registry, core.diagnosticLog, core.timings,
                core.errorCounts, LOGGER)));
    }

    /**
     * Attribute ids from other mods cannot be known when definitions freeze, since 1.12 keeps no list of
     * attributes. Once the overworld exists, a test player and a test zombie show which attributes this server
     * has; a definition that names any other non-vanilla attribute is disabled now, like any invalid definition.
     */
    private void checkModdedAttributes(FMLServerStartingEvent event) {
        net.minecraft.world.WorldServer overworld = event.getServer().getWorld(0);
        Set<String> known = new HashSet<>();
        FakePlayer player = FakePlayerFactory.getMinecraft(overworld);
        for (IAttributeInstance attribute : player.getAttributeMap().getAllAttributes()) {
            known.add(attribute.getAttribute().getName());
        }
        for (IAttributeInstance attribute : new EntityZombie(overworld).getAttributeMap().getAllAttributes()) {
            known.add(attribute.getAttribute().getName());
        }
        List<DefinitionRegistry.Error> errors = core.registry.checkLateAttributes(known::contains);
        for (DefinitionRegistry.Error error : errors) {
            LOGGER.error("Statusworks: invalid definition {}", error);
        }
        if (!errors.isEmpty()) {
            LOGGER.info("Statusworks: {} definition(s) disabled for attributes this server does not have",
                    errors.size());
            // Entities in always-loaded chunks were restored before this check; end their now-invalid instances.
            core.tickDriver.forEachTracked((entity, state) -> core.statuses.endOrphans(entity, state, core.registry));
        }
        if (ServerConfig.strictMode && !errors.isEmpty()) {
            throw new IllegalStateException("Statusworks strict mode: " + errors.size()
                    + " definition(s) name attributes this server does not have; first: " + errors.get(0));
        }
    }

    @SubscribeEvent
    public void attachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        Entity entity = event.getObject();
        // Server side only: clients never hold tracked state, they receive what they may see.
        if (entity instanceof EntityLivingBase && !(entity instanceof FakePlayer)
                && entity.world != null && !entity.world.isRemote) {
            event.addCapability(EntityStateProvider.KEY, new EntityStateProvider(core.codec, core.contributions,
                    (EntityLivingBase) entity));
        }
    }
}
