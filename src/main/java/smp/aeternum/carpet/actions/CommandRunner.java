package smp.aeternum.carpet.actions;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/** Runs at most one step per tick; waits never sleep or stall the server thread. */
public final class CommandRunner {
    private static final ArrayDeque<Job> JOBS = new ArrayDeque<>();
    private static final Set<String> RUNNING = new HashSet<>();
    private CommandRunner() { }

    public static boolean start(MinecraftServer server, ServerPlayerEntity requester, String packId) {
        CommandPack pack = CommandPackStore.get(packId);
        if (pack == null || !RUNNING.add(packId)) return false;
        JOBS.add(new Job(server, requester, packId, pack));
        announce(server, Text.literal("[Actions] ").formatted(Formatting.DARK_GRAY)
            .append(Text.literal("Ejecutando: " + pack.name).formatted(Formatting.AQUA)));
        return true;
    }
    public static void tick(MinecraftServer server) {
        if (JOBS.isEmpty()) return;
        Job job = JOBS.removeFirst();
        if (job.waitTicks-- > 0) { JOBS.addLast(job); return; }
        if (job.index >= job.pack.commands.size()) { finish(job, true); return; }
        CommandStep step = job.pack.commands.get(job.index++);
        if (step.type == CommandStep.Type.WAIT) job.waitTicks = Integer.parseInt(step.value) * 20;
        else {
            String command = step.value.startsWith("/") ? step.value.substring(1) : step.value;
            // Commands such as Carpet's /player can schedule and await server work.  Running
            // them directly from END_SERVER_TICK can deadlock the server watchdog; defer them
            // to the normal server task queue instead.
            server.execute(() -> runCommand(job, command));
            return;
        }
        JOBS.addLast(job);
    }
    private static void runCommand(Job job, String command) {
        boolean success;
        try {
            ServerCommandSource source = job.server.getCommandSource().withSilent();
            success = job.server.getCommandManager().getDispatcher().execute(command, source) > 0;
        } catch (Exception ignored) {
            success = false;
        }
        Text marker = Text.literal(success ? "✓ " : "✕ ").formatted(success ? Formatting.GREEN : Formatting.RED);
        announce(job.server, Text.literal("[Actions] ").formatted(Formatting.DARK_GRAY)
            .append(marker).append(Text.literal("/" + command).formatted(Formatting.DARK_GRAY)));
        JOBS.addLast(job);
    }
    private static void finish(Job job, boolean success) {
        RUNNING.remove(job.key);
        announce(job.server, Text.literal("[Actions] ").formatted(Formatting.DARK_GRAY)
            .append(Text.literal(success ? "Acción terminada." : "Acción cancelada.").formatted(success ? Formatting.GREEN : Formatting.RED)));
    }
    private static void announce(MinecraftServer server, Text message) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) player.sendMessage(message, false);
    }
    private static final class Job {
        final MinecraftServer server; final ServerPlayerEntity requester; final String key; final CommandPack pack; int index, waitTicks;
        Job(MinecraftServer server, ServerPlayerEntity requester, String key, CommandPack pack) { this.server=server; this.requester=requester; this.key=key; this.pack=pack; }
    }
}
