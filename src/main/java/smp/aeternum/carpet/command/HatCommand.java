package smp.aeternum.carpet.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.component.EnchantmentEffectComponentTypes;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import smp.aeternum.carpet.AeternumCarpetSettings;

import static net.minecraft.server.command.CommandManager.literal;

public final class HatCommand {
    private HatCommand() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("hat")
            .requires(source -> AeternumCarpetSettings.commandHat)
            .executes(HatCommand::equipHat));
    }

    private static int equipHat(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        if (player.isSpectator()) {
            context.getSource().sendError(Text.literal("No puedes usar /hat en modo espectador."));
            return 0;
        }
        ItemStack handStack = player.getMainHandStack();
        ItemStack headStack = player.getEquippedStack(EquipmentSlot.HEAD);
        if (handStack.isEmpty() && headStack.isEmpty()) {
            context.getSource().sendError(Text.literal("No tienes ningún ítem en la mano ni en la cabeza."));
            return 0;
        }
        if (!player.isCreative() && EnchantmentHelper.hasAnyEnchantmentsWith(headStack, EnchantmentEffectComponentTypes.PREVENT_ARMOR_CHANGE)) {
            context.getSource().sendError(Text.literal("El ítem en tu cabeza tiene Maldición de ligamiento."));
            return 0;
        }

        player.equipStack(EquipmentSlot.HEAD, handStack.copy());
        player.setStackInHand(Hand.MAIN_HAND, headStack.copy());
        player.playerScreenHandler.sendContentUpdates();
        context.getSource().sendFeedback(() -> Text.literal("Ítem equipado en la cabeza.").formatted(Formatting.GRAY), false);
        return 1;
    }
}
