package com.example.statusworksexample;

import com.mahghuuuls.statusworks.api.StatusworksApi;
import com.mahghuuuls.statusworks.api.definition.AttributeOperation;
import com.mahghuuuls.statusworks.api.definition.Contribution;
import com.mahghuuuls.statusworks.api.definition.ExpirationMode;
import com.mahghuuuls.statusworks.api.definition.Presentation;
import com.mahghuuuls.statusworks.api.definition.StatusDefinition;
import com.mahghuuuls.statusworks.api.definition.ValueForm;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Statusworks example for a mod written in Java: the stacking counter of {@code stacking_counter.zs}.
 *
 * <p>Each time a player hits a living thing, the player gains one stack of "Rage", up to 5. Each stack adds 1 attack
 * damage, and the stacks fall off one by one, 5 seconds apart.</p>
 *
 * <p>Copy this class into your own mod and change the package, mod id, and definition id. Your mod declares
 * {@code dependencies = "required-after:statusworks"} and compiles against the Statusworks jar. Register definitions
 * during startup (pre-init to post-init); they freeze when loading completes.</p>
 */
@Mod(modid = "statusworksexample", name = "Statusworks Example", version = "1.0",
        dependencies = "required-after:statusworks")
public class JavaExample {

    private static final String RAGE = "statusworksexample:rage";

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        StatusworksApi.registerStatus(StatusDefinition.builder(RAGE)
                .limit(5)
                .expiration(ExpirationMode.GRADUAL)
                .defaultDuration(100)
                .contribute(Contribution.attribute("generic.attackDamage", AttributeOperation.ADD, ValueForm.PER_STACK,
                        1))
                .onApplied(context -> System.out.println("Rage on " + context.entity().getName()))
                .presentation(Presentation.builder().name("Rage").iconItem("minecraft:blaze_powder", 0).build())
                .build());
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onHurt(LivingHurtEvent event) {
        if (event.getSource().getTrueSource() instanceof EntityPlayer && !event.getEntity().world.isRemote) {
            StatusworksApi.statuses().apply(event.getSource().getTrueSource(), RAGE, 1);
        }
    }
}
