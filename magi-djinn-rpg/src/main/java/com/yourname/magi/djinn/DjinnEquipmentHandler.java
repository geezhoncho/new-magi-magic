package com.yourname.magi.djinn;

import com.yourname.magi.capability.MagiCapabilities;
import com.yourname.magi.capability.PlayerDjinnData;
import com.yourname.magi.networking.MagiNetwork;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Server-only mutation point for Djinn state. Every state change goes through here so that
 * attribute modifiers and client sync can never drift apart.
 */
public final class DjinnEquipmentHandler {

    /** Re-applies attribute modifiers and pushes a full sync. Call on login/respawn/dimension change/reload. */
    public static void refresh(ServerPlayer player) {
        MagiCapabilities.get(player).ifPresent(data -> {
            applyModifiers(player, data);
            MagiNetwork.syncDjinnData(player, data);
        });
    }

    /** Binds a Djinn to the player. @return true if newly bound; false if unknown or already owned. */
    public static boolean grant(ServerPlayer player, ResourceLocation id) {
        if (DjinnManager.get(id) == null) return false;
        return MagiCapabilities.get(player).map(data -> {
            boolean added = data.unlock(id);
            if (added) {
                if (data.getEquipped() == null) data.equip(id);
                refresh(player);
            }
            return added;
        }).orElse(false);
    }

    /** Equip (or unequip when id == null). Ownership is validated here, never trusted from the client. */
    public static boolean equip(ServerPlayer player, @Nullable ResourceLocation id) {
        return MagiCapabilities.get(player).map(data -> {
            boolean ok;
            if (id == null) {
                data.unequip();
                ok = true;
            } else {
                ok = DjinnManager.get(id) != null && data.equip(id);
            }
            if (ok) refresh(player);
            return ok;
        }).orElse(false);
    }

    private static void applyModifiers(ServerPlayer player, PlayerDjinnData data) {
        // 1) strip modifiers of every known Djinn (cheap: a handful of entries, only on state change)
        DjinnManager.entries().forEach((djinnId, def) -> {
            for (DjinnDefinition.AttributeBonus bonus : def.attributes()) {
                AttributeInstance inst = instance(player, bonus.attribute());
                if (inst != null) inst.removeModifier(modifierId(djinnId, bonus.attribute()));
            }
        });

        // 2) apply the equipped Djinn
        ResourceLocation eq = data.getEquipped();
        if (eq == null) return;
        DjinnDefinition def = DjinnManager.get(eq);
        if (def == null) return;
        int level = Math.max(1, data.level(eq));
        for (DjinnDefinition.AttributeBonus bonus : def.attributes()) {
            AttributeInstance inst = instance(player, bonus.attribute());
            if (inst == null) continue; // attribute missing (optional mod absent)
            double amount = bonus.base() + bonus.perLevel() * (level - 1);
            inst.addTransientModifier(new AttributeModifier(
                    modifierId(eq, bonus.attribute()), "Djinn " + eq, amount, bonus.operation()));
        }
    }

    @Nullable
    private static AttributeInstance instance(ServerPlayer player, ResourceLocation attributeId) {
        Attribute attr = ForgeRegistries.ATTRIBUTES.getValue(attributeId);
        return attr == null ? null : player.getAttribute(attr);
    }

    private static UUID modifierId(ResourceLocation djinn, ResourceLocation attribute) {
        return UUID.nameUUIDFromBytes(("magi_djinn|" + djinn + "|" + attribute).getBytes(StandardCharsets.UTF_8));
    }

    private DjinnEquipmentHandler() {}
}
