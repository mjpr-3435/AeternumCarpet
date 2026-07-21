package smp.aeternum.carpet.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import smp.aeternum.carpet.AeternumCarpetSettings;
import smp.aeternum.carpet.motd.JoinMotd;
import smp.aeternum.carpet.motd.MotdTaskStore;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class MotdCommand {
    private MotdCommand() { }
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("motd").requires(source -> AeternumCarpetSettings.joinMotd).executes(context -> show(context))
            .then(literal("tasks").executes(context -> showTasks(context)))
            .then(literal("add").requires(source -> source.hasPermissionLevel(2)).then(argument("text",StringArgumentType.greedyString()).executes(MotdCommand::add)))
            .then(literal("delete").requires(source -> source.hasPermissionLevel(2)).then(argument("index",IntegerArgumentType.integer(1)).executes(MotdCommand::delete))));
    }
    private static int show(com.mojang.brigadier.context.CommandContext<ServerCommandSource> context) {
            ServerPlayerEntity player;
            try { player = context.getSource().getPlayerOrThrow(); }
            catch (Exception exception) { context.getSource().sendError(Text.literal("Este comando solo puede usarse dentro del juego.")); return 0; }
            JoinMotd.send(player); return 1;
    }
    private static int showTasks(com.mojang.brigadier.context.CommandContext<ServerCommandSource> context){ServerPlayerEntity player;try{player=context.getSource().getPlayerOrThrow();}catch(Exception e){return 0;}if(MotdTaskStore.all().isEmpty())player.sendMessage(Text.literal("No hay tareas públicas."),false);else JoinMotd.sendTasks(player);return 1;}
    private static int add(com.mojang.brigadier.context.CommandContext<ServerCommandSource> context){String text=StringArgumentType.getString(context,"text");if(!MotdTaskStore.add(text)){context.getSource().sendError(Text.literal("La tarea está vacía, es demasiado larga o se alcanzó el límite."));return 0;}context.getSource().sendFeedback(()->Text.literal("Tarea añadida."),false);return showTasks(context);}
    private static int delete(com.mojang.brigadier.context.CommandContext<ServerCommandSource> context){int index=IntegerArgumentType.getInteger(context,"index")-1;if(!MotdTaskStore.remove(index)){context.getSource().sendError(Text.literal("No existe esa tarea."));return 0;}context.getSource().sendFeedback(()->Text.literal("Tarea eliminada."),false);return showTasks(context);}
}
