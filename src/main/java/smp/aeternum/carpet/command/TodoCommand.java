package smp.aeternum.carpet.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import smp.aeternum.carpet.AeternumCarpetSettings;
import smp.aeternum.carpet.todo.TodoListStore;

import java.util.List;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class TodoCommand {
    private TodoCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("todo")
            .executes(TodoCommand::listNotes)
            .then(literal("list")
                .executes(TodoCommand::listNotes))
            .then(literal("add")
                .then(argument("text", StringArgumentType.greedyString())
                    .executes(TodoCommand::addNote)))
            .then(literal("delete")
                .then(argument("index", IntegerArgumentType.integer(1))
                    .executes(TodoCommand::deleteNote)))
            .then(literal("clear")
                .executes(TodoCommand::clearNotes)));
    }

    private static int listNotes(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = requirePlayerAndRule(context);
        if (player == null) {
            return 0;
        }
        List<String> notes = TodoListStore.getNotes(player.getUuid());
        if (notes.isEmpty()) {
            context.getSource().sendFeedback(() -> Text.literal("No tienes notas. Usa /todo add <texto> para crear una."), false);
            return 1;
        }

        for (int i = 0; i < notes.size(); i++) {
            int index = i + 1;
            String note = notes.get(i);
            context.getSource().sendFeedback(() -> Text.literal("[" + index + "] " + note), false);
        }
        return 1;
    }

    private static int addNote(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = requirePlayerAndRule(context);
        if (player == null) {
            return 0;
        }
        String note = StringArgumentType.getString(context, "text").trim();
        if (note.isEmpty()) {
            context.getSource().sendError(Text.literal("La nota no puede estar vacia."));
            return 0;
        }

        TodoListStore.addNote(player.getUuid(), note);
        context.getSource().sendFeedback(() -> Text.literal("Nota agregada."), false);
        return 1;
    }

    private static int deleteNote(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = requirePlayerAndRule(context);
        if (player == null) {
            return 0;
        }
        int index = IntegerArgumentType.getInteger(context, "index");
        if (!TodoListStore.removeNote(player.getUuid(), index - 1)) {
            context.getSource().sendError(Text.literal("Indice invalido.")); 
            return 0;
        }

        context.getSource().sendFeedback(() -> Text.literal("Nota eliminada."), false);
        return 1;
    }

    private static int clearNotes(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = requirePlayerAndRule(context);
        if (player == null) {
            return 0;
        }
        TodoListStore.clearNotes(player.getUuid());
        context.getSource().sendFeedback(() -> Text.literal("Notas eliminadas."), false);
        return 1;
    }

    private static ServerPlayerEntity requirePlayerAndRule(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        if (!AeternumCarpetSettings.commandTodoList) {
            context.getSource().sendError(Text.literal("La regla commandTodoList esta desactivada."));
            return null;
        }
        return context.getSource().getPlayerOrThrow();
    }
}
