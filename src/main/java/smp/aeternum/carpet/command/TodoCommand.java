package smp.aeternum.carpet.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
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
            .requires(source -> AeternumCarpetSettings.commandTodoList)
            .executes(TodoCommand::listNotes)
            .then(literal("list")
                .executes(TodoCommand::listNotes))
            .then(literal("add")
                .then(argument("text", StringArgumentType.greedyString())
                    .executes(TodoCommand::addNote)))
            .then(literal("delete")
                .then(argument("index", IntegerArgumentType.integer(1))
                    .executes(TodoCommand::deleteNote))));
    }

    private static int listNotes(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        sendNotesList(context.getSource(), player);
        return 1;
    }

    private static int addNote(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        String note = StringArgumentType.getString(context, "text").trim();
        if (note.isEmpty()) {
            context.getSource().sendError(Text.literal("La nota no puede estar vacia."));
            return 0;
        }

        TodoListStore.addNote(player.getUuid(), capitalizeNote(note));
        context.getSource().sendFeedback(() -> Text.literal("Anadiste una nota a tu lista.").formatted(Formatting.GRAY), false);
        sendNotesList(context.getSource(), player);
        return 1;
    }

    private static int deleteNote(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        int index = IntegerArgumentType.getInteger(context, "index");
        if (!TodoListStore.removeNote(player.getUuid(), index - 1)) {
            context.getSource().sendError(Text.literal("Indice invalido.")); 
            return 0;
        }

        context.getSource().sendFeedback(() -> Text.literal("Eliminaste una nota de tu lista.").formatted(Formatting.GRAY), false);
        sendNotesList(context.getSource(), player);
        return 1;
    }

    private static void sendNotesList(ServerCommandSource source, ServerPlayerEntity player) {
        List<String> notes = TodoListStore.getNotes(player.getUuid());
        if (notes.isEmpty()) {
            source.sendFeedback(() -> Text.literal("No tienes notas. Usa /todo add <texto> para crear una.").formatted(Formatting.GRAY), false);
            return;
        }

        source.sendFeedback(() -> Text.literal("Estas son tus notas:").formatted(Formatting.GRAY), false);
        for (int i = 0; i < notes.size(); i++) {
            int index = i + 1;
            String note = notes.get(i);
            source.sendFeedback(() -> buildNoteLine(index, note), false);
        }
    }

    private static Text buildNoteLine(int index, String note) {
        MutableText deleteButton = Text.literal("[x]")
            .setStyle(Style.EMPTY
                .withColor(Formatting.RED)
                .withClickEvent(new ClickEvent.RunCommand("/todo delete " + index))
                .withHoverEvent(new HoverEvent.ShowText(Text.literal("Borrar nota"))));

        return Text.empty()
            .append(deleteButton)
            .append(Text.literal(" ").formatted(Formatting.DARK_GRAY))
            .append(Text.literal("[" + index + "]").formatted(Formatting.DARK_GRAY))
            .append(Text.literal(" " + capitalizeNote(note)).formatted(Formatting.GRAY));
    }

    private static String capitalizeNote(String note) {
        if (note.isEmpty()) {
            return note;
        }
        return Character.toUpperCase(note.charAt(0)) + note.substring(1);
    }
}
