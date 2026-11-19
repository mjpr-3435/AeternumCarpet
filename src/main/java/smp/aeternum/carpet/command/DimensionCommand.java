package smp.aeternum.carpet.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Locale;
import java.util.Set;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;
import smp.aeternum.carpet.AeternumCarpetSettings;

import static net.minecraft.server.command.CommandManager.literal;

public final class DimensionCommand {
    private static final double MIN_WORLD_COORD = -29_999_872.0;
    private static final double MAX_WORLD_COORD = 29_999_872.0;

    private DimensionCommand() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("nether")
            .requires(source -> AeternumCarpetSettings.commandDimension)
            .executes(ctx -> teleport(ctx, World.NETHER, "Nether")));
        dispatcher.register(literal("overworld")
            .requires(source -> AeternumCarpetSettings.commandDimension)
            .executes(ctx -> teleport(ctx, World.OVERWORLD, "Overworld")));
        dispatcher.register(literal("end")
            .requires(source -> AeternumCarpetSettings.commandDimension)
            .executes(ctx -> teleport(ctx, World.END, "End")));
    }

    private static int teleport(CommandContext<ServerCommandSource> context, RegistryKey<World> targetKey, String targetLabel) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        if (!player.isCreative() && !player.isSpectator()) {
            context.getSource().sendError(Text.literal("Este comando solo se puede usar en modo Creativo o Espectador."));
            return 0;
        }

        ServerWorld fromWorld = player.getWorld();
        RegistryKey<World> fromKey = fromWorld.getRegistryKey();
        if (fromKey.equals(targetKey)) {
            context.getSource().sendError(Text.literal("Ya te encuentras en " + targetLabel + "."));
            return 0;
        }

        ServerWorld destWorld = context.getSource().getServer().getWorld(targetKey);
        if (destWorld == null) {
            context.getSource().sendError(Text.literal("La dimensión " + targetLabel + " no está disponible."));
            return 0;
        }

        double destX;
        double destY;
        double destZ;
        float yaw = player.getYaw();
        float pitch = player.getPitch();

        if (targetKey.equals(World.END)) {
            destX = 100.5;
            destY = 49.0;
            destZ = 0.5;
            yaw = 90.0f;
            pitch = 0.0f;
        } else if (fromKey.equals(World.END)) {
            if (targetKey.equals(World.NETHER)) {
                destX = 0.5;
                destY = 128.0;
                destZ = 0.5;
            } else {
                BlockPos spawn = destWorld.getSpawnPos();
                destX = spawn.getX() + 0.5;
                destY = spawn.getY();
                destZ = spawn.getZ() + 0.5;
            }
        } else {
            double scale = DimensionType.getCoordinateScaleFactor(fromWorld.getDimension(), destWorld.getDimension());
            destX = MathHelper.clamp(player.getX() * scale, MIN_WORLD_COORD, MAX_WORLD_COORD);
            destY = MathHelper.clamp(player.getY(), destWorld.getBottomY(), destWorld.getTopYInclusive() - 1);
            destZ = MathHelper.clamp(player.getZ() * scale, MIN_WORLD_COORD, MAX_WORLD_COORD);
        }

        player.teleport(destWorld, destX, destY, destZ, Set.<PositionFlag>of(), yaw, pitch, false);
        player.fallDistance = 0f;

        String coords = String.format(Locale.ROOT, "(%.1f, %.1f, %.1f)", destX, destY, destZ);
        context.getSource().sendFeedback(
            () -> Text.literal("Teletransportado a " + targetLabel + " en " + coords).formatted(Formatting.GRAY),
            false
        );
        return 1;
    }
}
