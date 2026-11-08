package com.mahghuuuls.statusworks.core.lifecycle;

import com.mahghuuuls.statusworks.core.contribution.ContributionManager;
import com.mahghuuuls.statusworks.core.definition.DefinitionRegistry;
import com.mahghuuuls.statusworks.core.mark.MarkStore;
import com.mahghuuuls.statusworks.core.rule.DeathRules;
import com.mahghuuuls.statusworks.core.rule.StatusRules;
import com.mahghuuuls.statusworks.core.state.CooldownOperations;
import com.mahghuuuls.statusworks.core.state.EntityState;
import com.mahghuuuls.statusworks.core.state.EntityStateCodec;
import com.mahghuuuls.statusworks.core.state.PoolOperations;
import com.mahghuuuls.statusworks.core.state.StatusOperations;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * What death and player cloning do to state.
 *
 * <ul>
 *   <li>Death (lowest priority, never for a canceled death): every instance ends with reason {@code death},
 *   except on a player the instances whose death rule is {@code keep}; so do the entity's marks, except on a
 *   player the marks kept on that side's death.</li>
 *   <li>Clone (Minecraft builds a new player object on respawn and on leaving the End): after a death, the
 *   kept instances are copied with their count and time; on leaving the End, everything is copied. Forge does
 *   not copy capabilities itself in 1.12.</li>
 * </ul>
 *
 * <p>The copy goes through the save format, so a clone behaves exactly like a save and load.</p>
 */
public final class DeathAndClone {

    private final StatusOperations statuses;
    private final PoolOperations pools;
    private final CooldownOperations cooldowns;
    private final MarkStore marks;
    private final ContributionManager contributions;
    private final EntityStateCodec codec;
    private final DefinitionRegistry registry;

    public DeathAndClone(StatusOperations statuses, PoolOperations pools, CooldownOperations cooldowns,
                         MarkStore marks, ContributionManager contributions, EntityStateCodec codec,
                         DefinitionRegistry registry) {
        this.statuses = statuses;
        this.pools = pools;
        this.cooldowns = cooldowns;
        this.marks = marks;
        this.contributions = contributions;
        this.codec = codec;
        this.registry = registry;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDeath(LivingDeathEvent event) {
        EntityLivingBase entity = event.getEntityLiving();
        if (event.isCanceled() || entity.world.isRemote) {
            return;
        }
        EntityState state = entity.getCapability(EntityState.CAPABILITY, null);
        if (state != null && !state.isEmpty()) {
            statuses.endOnDeath(entity, state, entity instanceof EntityPlayer);
            pools.endOnDeath(entity, state, entity instanceof EntityPlayer);
            cooldowns.endOnDeath(entity, state, entity instanceof EntityPlayer);
        }
        marks.onDeath(entity);
    }

    @SubscribeEvent
    public void onClone(PlayerEvent.Clone event) {
        EntityPlayer original = event.getOriginal();
        EntityPlayer clone = event.getEntityPlayer();
        if (clone.world.isRemote) {
            return;
        }
        // The old object is replaced: its contribution groups end for good; the clone becomes current at its join.
        contributions.retire(original);
        EntityState from = original.getCapability(EntityState.CAPABILITY, null);
        EntityState to = clone.getCapability(EntityState.CAPABILITY, null);
        if (from == null || to == null || (from.hasNothingToSave() && from.newerFormat() == null)) {
            return;
        }
        codec.read(to, codec.write(from));
        for (EntityState.StatusSlot slot : to.statuses()) {
            DefinitionRegistry.Entry entry = registry.status(slot.id);
            if (entry == null || !StatusRules.copiedOnClone(entry.definition(), event.isWasDeath())) {
                to.removeStatus(slot.id);
            }
        }
        for (EntityState.PoolSlot slot : to.pools()) {
            DefinitionRegistry.PoolEntry entry = registry.pool(slot.id);
            if (entry == null || !DeathRules.copiedOnClone(entry.definition().deathRule(), event.isWasDeath())) {
                to.removePool(slot.id);
            }
        }
        for (EntityState.CooldownSlot slot : to.cooldowns()) {
            DefinitionRegistry.CooldownEntry entry = registry.cooldown(slot.id);
            if (entry == null || !DeathRules.copiedOnClone(entry.definition().deathRule(), event.isWasDeath())) {
                to.removeCooldown(slot.id);
            }
        }
        contributions.afterClone(original, clone, to, event.isWasDeath());
    }
}
