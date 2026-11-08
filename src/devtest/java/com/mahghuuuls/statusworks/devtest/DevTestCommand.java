package com.mahghuuuls.statusworks.devtest;

import com.mahghuuuls.statusworks.api.StackChange;
import com.mahghuuuls.statusworks.api.StatusService;
import com.mahghuuuls.statusworks.api.StatusworksApi;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

import java.util.function.Consumer;

/**
 * {@code /stwdev} checks for the development runtime:
 * <ul>
 *   <li>{@code dead}: applies a status to a zombie that has just died; must fail.</li>
 *   <li>{@code nonliving}: applies a status to a dropped item; must fail.</li>
 *   <li>{@code fakeplayer}: applies a status to Forge's fake player; must fail.</li>
 *   <li>{@code unknown}: uses an unknown id 100 times; the server log must warn once.</li>
 *   <li>{@code spawn <n>}: summons n named zombies without AI around the sender.</li>
 *   <li>{@code bulk <duration>}: applies the shared test status with that duration to every zombie named
 *   {@code stwbulk} within 64 blocks.</li>
 *   <li>{@code bulkpool}: gives the draining speed pool to every zombie named {@code stwbulk} within 64 blocks.</li>
 *   <li>{@code bulkmark}: the sender marks every zombie named {@code stwbulk} within 64 blocks with the unlimited
 *   test mark.</li>
 *   <li>{@code load}: builds the REQ-193 budget load ({@link DevTestLoad}) at the sender, or at the world spawn from
 *   the console; {@code load count} counts it, and {@code load clear} removes it.</li>
 * </ul>
 */
final class DevTestCommand extends CommandBase {

    @Override
    public String getName() {
        return "stwdev";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/stwdev <dead|nonliving|fakeplayer|unknown|spawn <n>|bulk <duration>|bulkpool|bulkmark"
                + "|load [clear|count]|group <type> <ok|throw|refuse> [times]|group read|mana <reset|accept|arm>"
                + "|loop <on|off>|canceljoin [mob]|deepchain>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 1) {
            throw new WrongUsageException(getUsage(sender));
        }
        World world = sender.getEntityWorld();
        BlockPos pos = sender.getPosition();
        StatusService statuses = StatusworksApi.statuses();
        String shared = DevTestMod.id("shared");
        switch (args[0]) {
            case "dead": {
                EntityZombie zombie = zombie(world, pos, "stwdead");
                world.spawnEntity(zombie);
                zombie.setHealth(0.0F);
                StackChange r = statuses.apply(zombie, shared, 1);
                log(sender, "dead apply result: " + r + " alive=" + zombie.isEntityAlive());
                zombie.setDead();
                break;
            }
            case "nonliving": {
                EntityItem item = new EntityItem(world, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5,
                        new ItemStack(Items.STICK));
                world.spawnEntity(item);
                StackChange r = statuses.apply(item, shared, 1);
                log(sender, "nonliving apply result: " + r);
                item.setDead();
                break;
            }
            case "fakeplayer": {
                FakePlayer fake = FakePlayerFactory.getMinecraft((WorldServer) world);
                StackChange r = statuses.apply(fake, shared, 1);
                log(sender, "fakeplayer apply result: " + r + " has=" + statuses.has(fake, shared));
                break;
            }
            case "unknown": {
                Entity self = sender.getCommandSenderEntity();
                if (self == null) {
                    throw new CommandException("run this as a player");
                }
                StackChange last = null;
                for (int i = 0; i < 100; i++) {
                    last = statuses.apply(self, DevTestMod.id("no_such_status"), 1);
                }
                log(sender, "unknown id called 100 times; last result: " + last);
                break;
            }
            case "spawn": {
                int n = args.length > 1 ? parseInt(args[1], 1, 1000) : 1;
                for (int i = 0; i < n; i++) {
                    BlockPos p = pos.add((i % 20) * 2 - 20, 0, (i / 20) * 2 + 3);
                    world.spawnEntity(zombie(world, p, "stwbulk"));
                }
                log(sender, "spawned " + n + " zombies named stwbulk");
                break;
            }
            case "bulk": {
                int duration = args.length > 1 ? parseInt(args[1], 1) : 20;
                int applied = 0;
                for (EntityZombie z : world.getEntitiesWithinAABB(EntityZombie.class,
                        new net.minecraft.util.math.AxisAlignedBB(pos).grow(64))) {
                    if ("stwbulk".equals(z.getCustomNameTag())
                            && statuses.apply(z, shared, 1, null, duration).succeeded()) {
                        applied++;
                    }
                }
                log(sender, "bulk applied " + shared + " for " + duration + " ticks to " + applied + " zombies");
                break;
            }
            case "bulkpool": {
                String pool = DevTestMod.id("pspeed");
                int created = 0;
                for (EntityZombie z : world.getEntitiesWithinAABB(EntityZombie.class,
                        new net.minecraft.util.math.AxisAlignedBB(pos).grow(64))) {
                    if ("stwbulk".equals(z.getCustomNameTag())
                            && StatusworksApi.pools().add(z, pool, 0).succeeded()) {
                        created++;
                    }
                }
                log(sender, "bulkpool gave " + pool + " to " + created + " zombies");
                break;
            }
            case "bulkmark": {
                String mark = DevTestMod.id("mnone");
                Entity source = sender.getCommandSenderEntity();
                int created = 0;
                for (EntityZombie z : world.getEntitiesWithinAABB(EntityZombie.class,
                        new net.minecraft.util.math.AxisAlignedBB(pos).grow(64))) {
                    if ("stwbulk".equals(z.getCustomNameTag())
                            && StatusworksApi.marks().mark(source, z, mark).succeeded()) {
                        created++;
                    }
                }
                log(sender, "bulkmark marked " + created + " zombies with " + mark);
                break;
            }
            case "load": {
                Consumer<String> out = line -> log(sender, line);
                if (args.length > 1 && "clear".equals(args[1])) {
                    DevTestLoad.clear((WorldServer) world, out);
                } else if (args.length > 1 && "count".equals(args[1])) {
                    DevTestLoad.count((WorldServer) world, out);
                } else if (args.length > 1) {
                    throw new WrongUsageException(getUsage(sender));
                } else {
                    Entity self = sender.getCommandSenderEntity();
                    DevTestLoad.build((WorldServer) world, self != null ? pos : world.getSpawnPoint(), out);
                }
                break;
            }
            case "group": {
                EntityPlayer player = getCommandSenderAsPlayer(sender);
                if (args.length >= 2 && "read".equals(args[1])) {
                    for (String name : GroupTestTypes.NAMES) {
                        log(sender, "group_read type=" + name + " " + StatusworksApi.contributions()
                                .group(player, DevTestMod.id(name)));
                    }
                    break;
                }
                if (args.length < 3) {
                    throw new WrongUsageException(getUsage(sender));
                }
                GroupTestTypes.Behavior b = GroupTestTypes.BEHAVIOR.get(DevTestMod.id(args[1]));
                if (b == null || !java.util.Arrays.asList("ok", "throw", "refuse").contains(args[2])) {
                    throw new WrongUsageException(getUsage(sender));
                }
                int times = args.length > 3 ? parseInt(args[3], 1) : -1;
                b.mode = args[2];
                b.times = times;
                log(sender, "group_behavior type=" + args[1] + " mode=" + b.mode + " times=" + b.times);
                break;
            }
            case "mana": {
                EntityPlayer player = getCommandSenderAsPlayer(sender);
                if (args.length >= 2 && "reset".equals(args[1])) {
                    GroupTestTypes.manaSaved = 110;
                    GroupTestTypes.manaAccepted = null;
                    GroupTestTypes.manaArmedFor = null;
                    GroupTestTypes.manaLastRefusal = null;
                    log(sender, "mana_reset saved=110 base_max=120");
                } else if (args.length >= 2 && "arm".equals(args[1])) {
                    GroupTestTypes.manaArmedFor = player;
                    log(sender, "mana_armed: tries its first acceptance every tick and at every settled signal");
                } else if (args.length >= 2 && "accept".equals(args[1])) {
                    log(sender, GroupTestTypes.tryAcceptMana(player));
                } else {
                    throw new WrongUsageException(getUsage(sender));
                }
                break;
            }
            case "loop": {
                if (args.length < 2 || !("on".equals(args[1]) || "off".equals(args[1]))) {
                    throw new WrongUsageException(getUsage(sender));
                }
                GroupTestTypes.loopOn = "on".equals(args[1]);
                log(sender, "loop " + args[1]);
                break;
            }
            case "canceljoin": {
                if (args.length > 1 && "mob".equals(args[1])) {
                    GroupTestTypes.cancelNextMobJoin = true;
                    log(sender, "canceljoin armed: the next mob join is canceled");
                } else {
                    GroupTestTypes.cancelNextJoin = true;
                    log(sender, "canceljoin armed: the next player join is canceled");
                }
                break;
            }
            case "deepchain": {
                EntityPlayer player = getCommandSenderAsPlayer(sender);
                statuses.clear(player, DevTestMod.id("deepchain"));
                statuses.apply(player, DevTestMod.id("deepchain"), 1);
                log(sender, "deepchain started; count=" + statuses.count(player, DevTestMod.id("deepchain")));
                break;
            }
            default:
                throw new WrongUsageException(getUsage(sender));
        }
    }

    private static EntityZombie zombie(World world, BlockPos pos, String name) {
        EntityZombie zombie = new EntityZombie(world);
        zombie.setLocationAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        zombie.setCustomNameTag(name);
        zombie.setNoAI(true);
        zombie.enablePersistence();
        return zombie;
    }

    private static void log(ICommandSender sender, String line) {
        sender.sendMessage(new net.minecraft.util.text.TextComponentString(line));
        DevTestMod.LOGGER.info("[stw-devtest] {}", line);
    }
}
