package smp.aeternum.carpet.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import smp.aeternum.carpet.AeternumCarpetSettings;
import smp.aeternum.carpet.carefulbreak.CarefulBreakStore;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class CarefulBreakCommand {
    private CarefulBreakCommand() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("carefulbreak")
            .requires(source -> AeternumCarpetSettings.carefulBreak)
            .executes(CarefulBreakCommand::show)
            .then(argument("mode", StringArgumentType.word())
                .suggests((ctx, builder) -> CommandSource.suggestMatching(new String[]{"off", "sneak", "always"}, builder))
                .executes(CarefulBreakCommand::set)));
    }

    private static int show(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        String mode = CarefulBreakStore.getMode(player.getUuid());
        context.getSource().sendFeedback(
            () -> Text.literal("CarefulBreak actual: " + mode + " (usa /carefulbreak <off|sneak|always>)").formatted(Formatting.GRAY),
            false
        );
        return 1;
    }

    private static int set(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        String raw = StringArgumentType.getString(context, "mode");
        String norm = CarefulBreakStore.normalize(raw);
        if (norm == null) {
            context.getSource().sendError(Text.literal("Modo inválido '" + raw + "'. Usa: off, sneak o always."));
            return 0;
        }
        CarefulBreakStore.setMode(player.getUuid(), norm);
        context.getSource().sendFeedback(
            () -> Text.literal("CarefulBreak configurado en: " + norm).formatted(Formatting.GRAY),
            false
        );
        return 1;
    }
}
