package com.mahghuuuls.statusworks.devtest;

import com.mahghuuuls.statusworks.api.MarkService;
import com.mahghuuuls.statusworks.api.StatusService;
import com.mahghuuuls.statusworks.api.StatusworksApi;
import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.EntityHusk;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The REQ-193 budget load: 200 husks without AI carrying 5 timed statuses each, one husk with 1,000 independently
 * timed stacks, and one source husk with 50 marks. None of the load definitions has a callback or a presentation.
 * Husks, because they do not burn in daylight, with water breathing, because a husk placed in water drowns. The
 * area's chunks are force-loaded while the load stands, so the husks keep updating on a server with no player (a
 * world stops updating its entities 300 ticks after its last player leaves, unless chunks are forced).
 */
final class DevTestLoad {

    static final String MOB = "stwload";
    static final String MANY = "stwloadmany";
    static final String SOURCE = "stwloadsrc";
    static final int MOBS = 200;
    static final int STATUSES_EACH = 5;
    static final int STACKS = 1000;
    static final int MARKS = 50;

    /** System property that builds the load at server start and logs three {@code timing} readings. */
    static final String AUTO_PROPERTY = "statusworks.devtest.load";

    private static ForgeChunkManager.Ticket ticket;
    private static final List<EntityHusk> built = new ArrayList<>();

    private DevTestLoad() {
    }

    static String statusId(int i) {
        return DevTestMod.id("load" + i);
    }

    /** Builds the load around the center; returns false, with a log line, when a load already stands. */
    static boolean build(WorldServer world, BlockPos center, Consumer<String> log) {
        if (ticket != null) {
            log.accept("load already built; run /stwdev load clear first");
            return false;
        }
        ticket = ForgeChunkManager.requestTicket(DevTestMod.instance, world, ForgeChunkManager.Type.NORMAL);
        if (ticket == null) {
            log.accept("load not built: no chunk ticket available");
            return false;
        }
        // 20 columns by 10 rows, 2 blocks apart, starting 3 blocks south of the center.
        for (int cx = (center.getX() - 24) >> 4; cx <= (center.getX() + 24) >> 4; cx++) {
            for (int cz = (center.getZ() - 4) >> 4; cz <= (center.getZ() + 28) >> 4; cz++) {
                ForgeChunkManager.forceChunk(ticket, new ChunkPos(cx, cz));
            }
        }
        // Load husks saved by an earlier run that was not cleared would add to this load; remove them first.
        int[] left = scan(world, true);
        if (left[0] + left[1] + left[2] > 0) {
            log.accept("removed a leftover load: " + counts(left) + ", " + left[4] + " marks ended");
        }
        StatusService statuses = StatusworksApi.statuses();
        MarkService marks = StatusworksApi.marks();
        List<EntityHusk> mobs = new ArrayList<>();
        for (int i = 0; i < MOBS; i++) {
            EntityHusk husk = husk(world, center.add((i % 20) * 2 - 20, 0, (i / 20) * 2 + 3), MOB);
            if (world.spawnEntity(husk)) {
                mobs.add(husk);
            }
        }
        built.clear();
        built.addAll(mobs);
        int applied = 0;
        for (EntityHusk husk : mobs) {
            for (int s = 1; s <= STATUSES_EACH; s++) {
                if (statuses.apply(husk, statusId(s), 1).succeeded()) {
                    applied++;
                }
            }
        }
        EntityHusk many = husk(world, center.add(-22, 0, 0), MANY);
        boolean manySpawned = world.spawnEntity(many);
        String manyId = DevTestMod.id("loadmany");
        for (int i = 0; i < STACKS; i++) {
            // A different duration for every stack, so each one has its own timer.
            statuses.apply(many, manyId, 1, null, 36000 + i);
        }
        EntityHusk source = husk(world, center.add(22, 0, 0), SOURCE);
        boolean sourceSpawned = world.spawnEntity(source);
        String markId = DevTestMod.id("loadmark");
        for (int i = 0; i < MARKS && i < mobs.size(); i++) {
            marks.mark(source, mobs.get(i), markId);
        }
        log.accept("load built at " + center.getX() + " " + center.getY() + " " + center.getZ() + ": " + mobs.size()
                + " husks, " + applied + " statuses applied (expected " + MOBS * STATUSES_EACH + "); " + MANY
                + " spawned=" + manySpawned + " stacks=" + statuses.count(many, manyId) + " (expected " + STACKS
                + "); " + SOURCE + " spawned=" + sourceSpawned + " marks=" + marks.targetCount(source, markId)
                + " (expected " + MARKS + "); forced chunks=" + ticket.getChunkList().size());
        return true;
    }

    /** Removes the load husks in the world and releases the forced chunks. */
    static void clear(WorldServer world, Consumer<String> log) {
        int[] removed = scan(world, true);
        if (ticket != null) {
            ForgeChunkManager.releaseTicket(ticket);
            ticket = null;
        }
        log.accept("load cleared: removed " + counts(removed) + ", " + removed[4] + " marks ended; chunks released");
    }

    /** Forgets the load when the server stops; Forge drops the world's tickets with the world. */
    static void reset() {
        ticket = null;
        built.clear();
    }

    /** Logs how many load husks are loaded, and how many of the 200 carry all 5 load statuses. */
    static void count(WorldServer world, Consumer<String> log) {
        int[] n = scan(world, false);
        log.accept("load count: " + counts(n) + "; " + MOB + " with all " + STATUSES_EACH + " statuses: " + n[3]);
        Set<Entity> loaded = Collections.newSetFromMap(new IdentityHashMap<>());
        loaded.addAll(world.loadedEntityList);
        for (EntityHusk husk : built) {
            if (!loaded.contains(husk)) {
                BlockPos at = husk.getPosition();
                log.accept("load husk not loaded: #" + husk.getEntityId() + " at " + at.getX() + " " + at.getY() + " "
                        + at.getZ() + " dead=" + husk.isDead + " health=" + husk.getHealth() + " blockLoaded="
                        + world.isBlockLoaded(at) + " chunk=" + (at.getX() >> 4) + "," + (at.getZ() >> 4)
                        + " inWater=" + husk.isInWater() + " motionY=" + husk.motionY);
            }
        }
    }

    /**
     * Counts the loaded load husks by name, and the {@link #MOB} husks with all load statuses; kills them when
     * {@code kill} is set. A killed source's marks are ended first: ended by its death they would end only a tick
     * later, after a save in the same tick has written them.
     */
    private static int[] scan(WorldServer world, boolean kill) {
        StatusService statuses = StatusworksApi.statuses();
        int mobs = 0;
        int many = 0;
        int sources = 0;
        int full = 0;
        int marksEnded = 0;
        for (Entity e : new ArrayList<>(world.loadedEntityList)) {
            if (!(e instanceof EntityHusk)) {
                continue;
            }
            String name = e.getCustomNameTag();
            if (MOB.equals(name)) {
                mobs++;
                boolean all = true;
                for (int s = 1; s <= STATUSES_EACH; s++) {
                    all &= statuses.has(e, statusId(s));
                }
                full += all ? 1 : 0;
            } else if (MANY.equals(name)) {
                many++;
            } else if (SOURCE.equals(name)) {
                sources++;
            } else {
                continue;
            }
            if (kill) {
                if (SOURCE.equals(name)) {
                    marksEnded += StatusworksApi.marks().clearSource(e, DevTestMod.id("loadmark"));
                }
                e.setDead();
            }
        }
        return new int[]{mobs, many, sources, full, marksEnded};
    }

    private static String counts(int[] n) {
        return n[0] + " " + MOB + ", " + n[1] + " " + MANY + ", " + n[2] + " " + SOURCE;
    }

    private static EntityHusk husk(WorldServer world, BlockPos pos, String name) {
        EntityHusk husk = new EntityHusk(world);
        BlockPos ground = world.getTopSolidOrLiquidBlock(pos);
        husk.setLocationAndAngles(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, 0, 0);
        husk.setCustomNameTag(name);
        husk.setNoAI(true);
        husk.enablePersistence();
        husk.addPotionEffect(new PotionEffect(MobEffects.WATER_BREATHING, 1000000, 0, false, false));
        return husk;
    }

    /**
     * With {@link #AUTO_PROPERTY} set, the server builds the load at its world spawn 100 ticks after start, takes
     * {@code /statusworks timing} three times, 200 ticks apart, after 400 ticks of settling, then clears the load.
     * The readings go to the server log, as console command output.
     */
    static final class Auto {

        private static final int BUILD_AT = 100;
        private static final int[] READ_AT = {BUILD_AT + 400, BUILD_AT + 600, BUILD_AT + 800};
        private static final int CLEAR_AT = BUILD_AT + 820;
        private static final int SAVE_AT = CLEAR_AT + 5;

        private final MinecraftServer server;
        private int ticks;

        Auto(MinecraftServer server) {
            this.server = server;
        }

        @SubscribeEvent
        public void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            ticks++;
            if (ticks == BUILD_AT) {
                run("stwdev load");
            }
            if (ticks == BUILD_AT + 1) {
                run("stwdev load count");
            }
            for (int i = 0; i < READ_AT.length; i++) {
                if (ticks == READ_AT[i]) {
                    DevTestMod.LOGGER.info("[stw-devtest] load reading {} of {}", i + 1, READ_AT.length);
                    run("statusworks timing");
                    run("stwdev load count");
                    // One load husk's remaining times fall between readings only while the husks are updated.
                    run("statusworks inspect @e[type=husk,name=" + MOB + ",c=1]");
                    run("statusworks inspect @e[type=husk,name=" + MANY + ",c=1]");
                }
            }
            if (ticks == CLEAR_AT) {
                run("stwdev load clear");
            }
            if (ticks == SAVE_AT) {
                // The server is ended by stopping its process; save once the removal has settled, so the world
                // keeps no load.
                run("save-all flush");
                DevTestMod.LOGGER.info("[stw-devtest] load run done");
            }
        }

        private void run(String command) {
            server.getCommandManager().executeCommand(server, command);
        }
    }
}
