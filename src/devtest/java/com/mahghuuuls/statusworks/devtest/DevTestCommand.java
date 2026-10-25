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
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;

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
 * </ul>
 */
final class DevTestCommand extends CommandBase {

    @Override
    public String getName() {
        return "stwdev";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/stwdev <dead|nonliving|fakeplayer|unknown|spawn <n>|bulk <duration>|bulkpool|bulkmark>";
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
