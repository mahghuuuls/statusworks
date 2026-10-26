package com.mahghuuuls.statusworks.client;

import com.mahghuuuls.statusworks.network.HudDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumParticleTypes;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Draws Statusworks particles on this client from the entries the server sent for other entities (and for the player
 * itself when it is in a status's audience), at each definition's rate and color, in the entity's box. No particle
 * packet is involved. Particles show only within 32 blocks unless the definition has long distance; the vanilla
 * particle setting (all, decreased, minimal) applies at every distance; the client's Statusworks particle switch
 * turns all of them off for this player.
 */
public final class ParticleEmitter {

    private final ClientStore store;
    private final ClientSettings settings;
    /** Vanilla's particle draw distance without long distance, in blocks. */
    private static final double AUDIENCE_NEAR = 32;
    private final Map<String, ParticleMath.Rate> rates = new HashMap<>();
    private final Random random = new Random();

    ParticleEmitter(ClientStore store, ClientSettings settings) {
        this.store = store;
        this.settings = settings;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.phase != TickEvent.Phase.END || mc.world == null || mc.isGamePaused()) {
            return;
        }
        if (!settings.particlesEnabled()) {
            rates.clear();
            return;
        }
        Set<String> seen = new HashSet<>();
        Entity camera = mc.getRenderViewEntity();
        for (Map.Entry<Integer, List<ClientStore.Shown>> e : store.audience().entrySet()) {
            Entity entity = mc.world.getEntityByID(e.getKey());
            if (entity == null || entity.isDead
                    || (entity instanceof EntityPlayer && ((EntityPlayer) entity).isSpectator())) {
                continue;
            }
            for (ClientStore.Shown s : e.getValue()) {
                HudDefinition row = s.row;
                EnumParticleTypes type = row.particleType == null ? null
                        : EnumParticleTypes.getByName(row.particleType);
                if (type == null) {
                    continue;
                }
                String key = e.getKey() + "/" + s.entry.key;
                seen.add(key);
                int n = rates.computeIfAbsent(key, k -> new ParticleMath.Rate()).next(row.particlesPerSecond);
                double[] color = ParticleMath.colorArguments(row.particleType, row.particleColor);
                // Within 32 blocks vanilla applies the player's particle setting itself; beyond, only long distance
                // draws, and then this applies the same setting.
                boolean beyond = row.particlesFar && camera != null
                        && camera.getDistanceSq(entity) > AUDIENCE_NEAR * AUDIENCE_NEAR;
                for (int i = 0; i < n; i++) {
                    if (beyond && !ParticleMath.allowed(mc.gameSettings.particleSetting, random)) {
                        continue;
                    }
                    double x = entity.posX + (random.nextDouble() - 0.5) * entity.width;
                    double y = entity.posY + random.nextDouble() * entity.height;
                    double z = entity.posZ + (random.nextDouble() - 0.5) * entity.width;
                    mc.world.spawnParticle(type, beyond, x, y, z, color[0], color[1], color[2]);
                }
            }
        }
        rates.keySet().retainAll(seen);
    }
}
