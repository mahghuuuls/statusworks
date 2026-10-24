package com.mahghuuuls.statusworks.devtest;

import com.mahghuuuls.statusworks.api.contribution.ContributionType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;

/** A test contribution type for players only; it logs every value and end it is told. */
final class PowerType implements ContributionType {

    @Override
    public String id() {
        return DevTestMod.id("power");
    }

    @Override
    public boolean appliesTo(EntityLivingBase entity) {
        return entity instanceof EntityPlayer;
    }

    @Override
    public void onValue(EntityLivingBase entity, String key, double value) {
        DevTestMod.LOGGER.info("[stw-devtest] power_value entity={} key={} value={}", entity.getName(), key, value);
    }

    @Override
    public void onEnd(EntityLivingBase entity, String key) {
        DevTestMod.LOGGER.info("[stw-devtest] power_end entity={} key={}", entity.getName(), key);
    }
}
