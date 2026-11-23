package smp.aeternum.carpet.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import smp.aeternum.carpet.AeternumCarpetSettings;
import smp.aeternum.carpet.waypoint.WaypointStore;
import smp.aeternum.carpet.waypoint.WaypointStore.Waypoint;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class WaypointsCommand {
    private WaypointsCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("waypoints")
            .requires(source -> AeternumCarpetSettings.commandWaypoints)
            .then(literal("list").executes(WaypointsCommand::list))
            .then(literal("add")
                .then(argument("nombre", StringArgumentType.word())
                    .executes(ctx -> add(ctx, null))
                    .then(argument("pos", BlockPosArgumentType.blockPos())
                        .executes(ctx -> add(ctx, BlockPosArgumentType.getBlockPos(ctx, "pos"))))))
            .then(literal("del")
                .then(argument("nombre", StringArgumentType.word())
                    .suggests(WaypointsCommand::suggestNames)
                    .executes(WaypointsCommand::del)))
            .then(literal("tp")
                .then(argument("nombre", StringArgumentType.word())
                    .suggests(WaypointsCommand::suggestNames)
                    .executes(WaypointsCommand::tp))));
    }

    private static CompletableFuture<Suggestions> suggestNames(CommandContext<ServerCommandSource> ctx, SuggestionsBuilder builder) {
        return CommandSource.suggestMatching(WaypointStore.all().stream().map(w -> w.name), builder);
    }

    private static int list(CommandContext<ServerCommandSource> ctx) {
        List<Waypoint> all = WaypointStore.all();
        if (all.isEmpty()) {
            feedback(ctx, "No hay waypoints guardados. Usa /waypoints add <nombre>.", Formatting.GRAY);
            return 1;
        }
        feedback(ctx, "Waypoints globales (" + all.size() + "):", Formatting.GRAY);
        for (Waypoint wp : all) {
            String dimShort = shortDim(wp.dimension);
            String coords = String.format("%d, %d, %d", Math.round(wp.x), Math.round(wp.y), Math.round(wp.z));
            ctx.getSource().sendFeedback(() -> Text.empty()
                .append(Text.literal("• ").formatted(Formatting.DARK_GRAY))
                .append(Text.literal(wp.name).formatted(Formatting.AQUA))
                .append(Text.literal(" [" + dimShort + " · " + coords + "] ").formatted(Formatting.GRAY))
                .append(Text.literal("[TP]").setStyle(Style.EMPTY
                    .withColor(Formatting.GREEN)
                    .withClickEvent(new ClickEvent.RunCommand("/waypoints tp " + wp.name))
                    .withHoverEvent(new HoverEvent.ShowText(Text.literal("Teletransportarse a " + wp.name)))))
                .append(Text.literal(" "))
                .append(Text.literal("[X]").setStyle(Style.EMPTY
                    .withColor(Formatting.RED)
                    .withClickEvent(new ClickEvent.SuggestCommand("/waypoints del " + wp.name))
                    .withHoverEvent(new HoverEvent.ShowText(Text.literal("Eliminar " + wp.name))))), false);
        }
        return 1;
    }

    private static int add(CommandContext<ServerCommandSource> ctx, BlockPos explicitPos) {
        String name = StringArgumentType.getString(ctx, "nombre");
        if (!WaypointStore.isValidName(name)) {
            error(ctx, "Nombre inválido: usa solo letras, números, '-' o '_' (máx 32 caracteres).");
            return 0;
        }
        if (!(ctx.getSource().getEntity() instanceof ServerPlayerEntity player)) {
            error(ctx, "Este comando solo pueden usarlo jugadores.");
            return 0;
        }

        Waypoint wp = new Waypoint();
        wp.name = name;
        wp.dimension = player.getWorld().getRegistryKey().getValue().toString();
        if (explicitPos != null) {
            wp.x = explicitPos.getX() + 0.5;
            wp.y = explicitPos.getY();
            wp.z = explicitPos.getZ() + 0.5;
            wp.yaw = 0f;
            wp.pitch = 0f;
        } else {
            wp.x = player.getX();
            wp.y = player.getY();
            wp.z = player.getZ();
            wp.yaw = player.getYaw();
            wp.pitch = player.getPitch();
        }
        wp.createdBy = player.getName().getString();
        boolean existed = WaypointStore.get(name) != null;
        WaypointStore.put(wp);

        String coords = String.format("%d, %d, %d", Math.round(wp.x), Math.round(wp.y), Math.round(wp.z));
        feedback(ctx, (existed ? "Waypoint actualizado: §b" : "Waypoint guardado: §b") + wp.name + " §7[" + shortDim(wp.dimension) + " · " + coords + "]", Formatting.GREEN);
        return 1;
    }

    private static int del(CommandContext<ServerCommandSource> ctx) {
        String name = StringArgumentType.getString(ctx, "nombre");
        if (!WaypointStore.remove(name)) {
            error(ctx, "No existe el waypoint: " + name);
            return 0;
        }
        feedback(ctx, "Waypoint eliminado: §e" + name, Formatting.YELLOW);
        return 1;
    }

    private static int tp(CommandContext<ServerCommandSource> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayerEntity player)) {
            error(ctx, "Este comando solo pueden usarlo jugadores.");
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "nombre");
        Waypoint wp = WaypointStore.get(name);
        if (wp == null) {
            error(ctx, "No existe el waypoint: " + name);
            return 0;
        }
        Identifier dimId = Identifier.tryParse(wp.dimension);
        if (dimId == null) {
            error(ctx, "Dimensión inválida en waypoint: " + wp.dimension);
            return 0;
        }
        RegistryKey<World> worldKey = RegistryKey.of(RegistryKeys.WORLD, dimId);
        ServerWorld targetWorld = ctx.getSource().getServer().getWorld(worldKey);
        if (targetWorld == null) {
            error(ctx, "No se pudo cargar la dimensión: " + wp.dimension);
            return 0;
        }
        if (player.hasVehicle()) {
            player.stopRiding();
        }
        player.teleport(targetWorld, wp.x, wp.y, wp.z, Set.<PositionFlag>of(), wp.yaw, wp.pitch, true);
        player.fallDistance = 0f;
        feedback(ctx, "Teletransportado a §b" + wp.name + " §7[" + shortDim(wp.dimension) + "]", Formatting.GRAY);
        return 1;
    }

    private static String shortDim(String dim) {
        if ("minecraft:overworld".equals(dim)) return "Overworld";
        if ("minecraft:the_nether".equals(dim)) return "Nether";
        if ("minecraft:the_end".equals(dim)) return "End";
        return dim;
    }

    private static void feedback(CommandContext<ServerCommandSource> ctx, String msg, Formatting color) {
        ctx.getSource().sendFeedback(() -> Text.empty()
            .append(Text.literal("[Waypoints] ").formatted(Formatting.DARK_GRAY))
            .append(Text.literal(msg).formatted(color)), false);
    }

    private static void error(CommandContext<ServerCommandSource> ctx, String msg) {
        ctx.getSource().sendError(Text.literal("[Waypoints] " + msg));
    }
}
