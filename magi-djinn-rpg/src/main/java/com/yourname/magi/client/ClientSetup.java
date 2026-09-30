package com.yourname.magi.client;

import com.yourname.magi.MagiMod;
import com.yourname.magi.registry.MagiWeapons;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Client-only. Registers the bow draw predicates vanilla only registers for Items.BOW. */
@Mod.EventBusSubscriber(modid = MagiMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MagiWeapons.bows().forEach(obj -> {
            Item bow = obj.get();
            ItemProperties.register(bow, new ResourceLocation("pull"), (stack, level, entity, seed) -> {
                if (entity == null || entity.getUseItem() != stack) return 0.0F;
                return (float) (stack.getUseDuration() - entity.getUseItemRemainingTicks()) / 20.0F;
            });
            ItemProperties.register(bow, new ResourceLocation("pulling"), (stack, level, entity, seed) ->
                    entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0F : 0.0F);
        }));
    }

    private ClientSetup() {}
}
