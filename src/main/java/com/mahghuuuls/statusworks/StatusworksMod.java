package com.mahghuuuls.statusworks;

import com.mahghuuuls.statusworks.api.StatusworksApi;
import com.mahghuuuls.statusworks.command.StatusworksCommand;
import com.mahghuuuls.statusworks.config.ServerConfig;
import com.mahghuuuls.statusworks.core.definition.DefinitionRegistry;
import com.mahghuuuls.statusworks.core.state.EntityState;
import com.mahghuuuls.statusworks.core.state.EntityStateProvider;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTBase;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLConstructionEvent;
import net.minecraftforge.fml.common.event.FMLLoadCompleteEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.List;

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
        MinecraftForge.EVENT_BUS.register(this);
        LOGGER.info("{} {} loading (strictMode={}, diagnosticLog={}, diagnosticLogLinesPerSecond={})",
                Tags.MOD_NAME, Tags.VERSION, ServerConfig.strictMode, ServerConfig.diagnosticLog,
                ServerConfig.diagnosticLogLinesPerSecond);
    }

    @Mod.EventHandler
    public void loadComplete(FMLLoadCompleteEvent event) {
        List<DefinitionRegistry.Error> errors = core.registry.freeze();
        for (DefinitionRegistry.Error error : errors) {
            LOGGER.error("Statusworks: invalid definition {}", error);
        }
        LOGGER.info("Statusworks definitions frozen: {} statuses valid, {} invalid, {} errors",
                core.registry.statuses().size(), core.registry.invalid().size(), errors.size());
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
        event.registerServerCommand(new StatusworksCommand(new StatusworksCommand.Context(core.statuses,
                core.registry, core.diagnosticLog, core.timings, core.errorCounts, LOGGER)));
    }

    @SubscribeEvent
    public void attachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        Entity entity = event.getObject();
        // Server side only: clients never hold tracked state, they receive what they may see.
        if (entity instanceof EntityLivingBase && !(entity instanceof FakePlayer)
                && entity.world != null && !entity.world.isRemote) {
            event.addCapability(EntityStateProvider.KEY, new EntityStateProvider(core.codec));
        }
    }
}
