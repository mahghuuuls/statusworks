package com.mahghuuuls.statusworks.devtest;

import com.mahghuuuls.statusworks.api.StatusworksApi;
import com.mahghuuuls.statusworks.api.contribution.ContributionGroup;
import com.mahghuuuls.statusworks.api.contribution.ContributionGroupType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Contribution types with group signals for the campaign, all logging every call. {@code living} applies to players
 * and to mobs whose name starts with "stw" (so ordinary mobs do not crowd the log); the others apply to players
 * only:
 * {@code grouped} confirms; {@code flaky} can be told to throw or refuse a number of times, or always;
 * {@code mana} stands for a mana mod: it keeps a saved 110 and a maximum of 120 plus its group, and accepts the saved
 * value only when its group is available; {@code loop}'s value notice removes a stack and its end notice applies the
 * status again, while switched on. Also a switch that cancels the next player join, switches that make the next
 * {@code grouped} settled signal throw or add a stack, a keeper of the last replaced player object, and a switch
 * that applies {@code loopstatus} at the start of the next server tick (before the worlds tick).
 */
final class GroupTestTypes {

    /** How an invalidation answers: confirm, refuse or throw, for a number of times (-1: always). */
    static final class Behavior {
        String mode = "ok";
        int times;

        boolean answer(String type) {
            String now = mode;
            if (!"ok".equals(mode) && times > 0) {
                times--;
                if (times == 0) {
                    mode = "ok";
                }
            }
            if ("throw".equals(now)) {
                throw new IllegalStateException("devtest " + type + " invalidation throws");
            }
            return !"refuse".equals(now);
        }
    }

    static final String[] NAMES = {"grouped", "flaky", "mana", "loop", "living"};
    static final Map<String, Behavior> BEHAVIOR = new HashMap<>();
    static boolean loopOn;
    static boolean cancelNextJoin;
    /** Cancels the next join of a living entity that is not a player (a mob arriving through a portal). */
    static boolean cancelNextMobJoin;

    /** The mana model's state for one player; reset by the command. */
    static double manaSaved = 110;
    static Double manaAccepted;
    static double manaAcceptedMax;
    /** While armed, the mana model tries its first acceptance every tick and at every settled signal. */
    static EntityPlayer manaArmedFor;
    static String manaLastRefusal;
    /** The next {@code grouped} settled signal throws once. */
    static boolean settledThrowArmed;
    /** The next {@code grouped} settled signal applies one stack of {@code groupedstatus} once. */
    static boolean settledAddArmed;
    /** The player object replaced at the last respawn or End exit, kept so it can be read afterwards. */
    static EntityPlayer lastReplaced;
    /** At the start of the next server tick, applies one {@code loopstatus} to this player. */
    static EntityPlayer loopAtTickStart;

    private GroupTestTypes() {
    }

    /** A short identity of the Java object, so two objects with the same entity id can be told apart. */
    static String obj(Object o) {
        return Integer.toHexString(System.identityHashCode(o));
    }

    /** The server's tick number, so lines of one tick can be counted. */
    static int tick() {
        return FMLCommonHandler.instance().getMinecraftServerInstance().getTickCounter();
    }

    /** Whether the settled switches are armed, for the read command. */
    static String switches() {
        return "settledAdd=" + settledAddArmed + " settledThrow=" + settledThrowArmed + " loopOn=" + loopOn;
    }

    static void register() {
        for (String name : NAMES) {
            BEHAVIOR.put(DevTestMod.id(name), new Behavior());
            DevTestMod.LOGGER.info("[stw-devtest] registered group type {}: {}", name,
                    StatusworksApi.registerContributionType(new Type(DevTestMod.id(name), !"living".equals(name))));
        }
    }

    /** Tries the mana model's first acceptance and logs the outcome. */
    static String tryAcceptMana(EntityPlayer player) {
        ContributionGroup g = StatusworksApi.contributions().group(player, DevTestMod.id("mana"));
        String line;
        if (manaAccepted != null) {
            line = "mana_already_accepted value=" + manaAccepted + " max=" + manaAcceptedMax;
        } else if (!g.isAvailable()) {
            line = "mana_refused availability=" + g.availability();
            if (manaArmedFor != null && line.equals(manaLastRefusal)) {
                return line;
            }
            manaLastRefusal = line;
        } else {
            double max = 120;
            for (double v : g.values().values()) {
                max += v;
            }
            manaAccepted = Math.min(manaSaved, max);
            manaAcceptedMax = max;
            line = "mana_accept value=" + manaAccepted + " max=" + max + " group=" + g.values();
            manaArmedFor = null;
        }
        DevTestMod.LOGGER.info("[stw-devtest] {}", line);
        return line;
    }

    static final class Type implements ContributionGroupType {
        private final String id;
        private final boolean playersOnly;

        Type(String id, boolean playersOnly) {
            this.id = id;
            this.playersOnly = playersOnly;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public boolean appliesTo(EntityLivingBase entity) {
            if (entity instanceof EntityPlayer) {
                return true;
            }
            return !playersOnly && entity.hasCustomName() && entity.getCustomNameTag().startsWith("stw");
        }

        @Override
        public boolean invalidate(EntityLivingBase entity) {
            Behavior b = BEHAVIOR.get(id);
            String mode = b.mode;
            DevTestMod.LOGGER.info("[stw-devtest] group_invalidate_call type={} entity={} obj={} mode={} tick={}", id,
                    entity.getName(), obj(entity), mode, tick());
            boolean confirmed = b.answer(id);
            DevTestMod.LOGGER.info("[stw-devtest] group_invalidate type={} entity={} obj={} mode={} confirmed={}", id,
                    entity.getName(), obj(entity), mode, confirmed);
            return confirmed;
        }

        @Override
        public void settled(EntityLivingBase entity, Map<String, Double> group) {
            ContributionGroup read = StatusworksApi.contributions().group(entity, id);
            DevTestMod.LOGGER.info("[stw-devtest] group_settled type={} entity={} obj={} tick={} group={} read={}", id,
                    entity.getName(), obj(entity), tick(), group, read);
            if (id.equals(DevTestMod.id("grouped"))) {
                if (settledThrowArmed) {
                    settledThrowArmed = false;
                    DevTestMod.LOGGER.info("[stw-devtest] settled_throw type={}", id);
                    throw new IllegalStateException("devtest grouped settled signal throws");
                }
                if (settledAddArmed) {
                    settledAddArmed = false;
                    DevTestMod.LOGGER.info("[stw-devtest] settled_add applies one groupedstatus");
                    StatusworksApi.statuses().apply(entity, DevTestMod.id("groupedstatus"), 1);
                    DevTestMod.LOGGER.info("[stw-devtest] settled_add returned");
                }
            }
            if (entity == manaArmedFor && id.equals(DevTestMod.id("mana"))) {
                tryAcceptMana(manaArmedFor);
            }
        }

        @Override
        public void onValue(EntityLivingBase entity, String key, double value) {
            DevTestMod.LOGGER.info("[stw-devtest] group_value type={} entity={} key={} value={}", id,
                    entity.getName(), key, value);
            if (loopOn && id.equals(DevTestMod.id("loop"))) {
                StatusworksApi.statuses().remove(entity, DevTestMod.id("loopstatus"), 1);
            }
        }

        @Override
        public void onEnd(EntityLivingBase entity, String key) {
            DevTestMod.LOGGER.info("[stw-devtest] group_end type={} entity={} key={}", id, entity.getName(), key);
            if (loopOn && id.equals(DevTestMod.id("loop"))) {
                StatusworksApi.statuses().apply(entity, DevTestMod.id("loopstatus"), 1);
            }
        }
    }

    /**
     * While the mana model is armed, tries its first acceptance every server tick; when the loop is armed for the
     * tick start, applies {@code loopstatus} there, so its chain starts before the worlds tick.
     */
    static final class ManaTicker {
        @SubscribeEvent
        public void onTick(TickEvent.ServerTickEvent event) {
            if (event.phase == TickEvent.Phase.START && loopAtTickStart != null) {
                EntityPlayer player = loopAtTickStart;
                loopAtTickStart = null;
                DevTestMod.LOGGER.info("[stw-devtest] loop_tick_start applies loopstatus tick={}", tick());
                StatusworksApi.statuses().apply(player, DevTestMod.id("loopstatus"), 1);
            }
            if (event.phase == TickEvent.Phase.END && manaArmedFor != null) {
                tryAcceptMana(manaArmedFor);
            }
        }
    }

    /** Keeps the player object a respawn or End exit replaces, so a command can read it afterwards. */
    static final class CloneKeeper {
        @SubscribeEvent
        public void onClone(PlayerEvent.Clone event) {
            lastReplaced = event.getOriginal();
            DevTestMod.LOGGER.info("[stw-devtest] clone old_obj={} new_obj={} wasDeath={}", obj(event.getOriginal()),
                    obj(event.getEntityPlayer()), event.isWasDeath());
        }
    }

    /** Cancels the next player join when switched on, the way another mod might. */
    static final class JoinCanceler {
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void onJoin(EntityJoinWorldEvent event) {
            if (event.getWorld().isRemote || !(event.getEntity() instanceof EntityLivingBase)) {
                return;
            }
            boolean player = event.getEntity() instanceof EntityPlayer;
            if ((player && cancelNextJoin) || (!player && cancelNextMobJoin)) {
                if (player) {
                    cancelNextJoin = false;
                } else {
                    cancelNextMobJoin = false;
                }
                event.setCanceled(true);
                DevTestMod.LOGGER.info("[stw-devtest] canceled_join entity={} forceSpawn={}",
                        event.getEntity().getName(), event.getEntity().forceSpawn);
            }
        }
    }
}
