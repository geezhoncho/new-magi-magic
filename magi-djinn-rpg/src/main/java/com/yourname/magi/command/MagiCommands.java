package com.yourname.magi.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.yourname.magi.MagiMod;
import com.yourname.magi.capability.MagiCapabilities;
import com.yourname.magi.djinn.DjinnEquipmentHandler;
import com.yourname.magi.djinn.DjinnManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Debug/admin commands (op level 2): /magi djinn grant|equip|unequip|list */
@Mod.EventBusSubscriber(modid = MagiMod.MODID)
public final class MagiCommands {
    private static final SuggestionProvider<CommandSourceStack> DJINN_IDS =
            (ctx, builder) -> SharedSuggestionProvider.suggestResource(DjinnManager.ids(), builder);

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("magi")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("djinn")
                        .then(Commands.literal("grant")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("djinn", ResourceLocationArgument.id())
                                                .suggests(DJINN_IDS)
                                                .executes(MagiCommands::grant))))
                        .then(Commands.literal("equip")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("djinn", ResourceLocationArgument.id())
                                                .suggests(DJINN_IDS)
                                                .executes(MagiCommands::equip))))
                        .then(Commands.literal("unequip")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(MagiCommands::unequip)))
                        .then(Commands.literal("list")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(MagiCommands::list)))));
    }

    private static int grant(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "djinn");
        if (DjinnManager.get(id) == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown Djinn: " + id));
            return 0;
        }
        boolean added = DjinnEquipmentHandler.grant(target, id);
        if (!added) {
            ctx.getSource().sendFailure(Component.literal(target.getName().getString() + " already owns " + id));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Bound " + id + " to " + target.getName().getString()), true);
        return 1;
    }

    private static int equip(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "djinn");
        if (!DjinnEquipmentHandler.equip(target, id)) {
            ctx.getSource().sendFailure(Component.literal("Cannot equip " + id + " (unknown or not owned)"));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Equipped " + id), true);
        return 1;
    }

    private static int unequip(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        DjinnEquipmentHandler.equip(target, null);
        ctx.getSource().sendSuccess(() -> Component.literal("Unequipped Djinn"), true);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        MagiCapabilities.get(target).ifPresent(data -> {
            String owned = data.ownedIds().isEmpty() ? "(none)" : data.ownedIds().stream()
                    .map(id -> id + " Lv" + data.level(id)).reduce((a, b) -> a + ", " + b).orElse("(none)");
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Owned: " + owned + " | Equipped: " + data.getEquipped()), false);
        });
        return 1;
    }

    private MagiCommands() {}
}
