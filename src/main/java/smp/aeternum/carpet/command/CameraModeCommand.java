package smp.aeternum.carpet.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Set;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import smp.aeternum.carpet.AeternumCarpetSettings;
import smp.aeternum.carpet.camera.CameraModeStore;

import static net.minecraft.server.command.CommandManager.literal;

public final class CameraModeCommand {
    private CameraModeCommand() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("cs")
            .requires(source -> !"off".equals(AeternumCarpetSettings.commandCameraMode))
            .executes(CameraModeCommand::toggle));
    }

    private static int toggle(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
        String mode = AeternumCarpetSettings.commandCameraMode;
        if ("off".equals(mode)) {
            context.getSource().sendError(Text.literal("El comando /cs está desactivado."));
            return 0;
        }

        if (player.isSpectator()) {
            CameraModeStore.SavedState prev = CameraModeStore.pop(player.getUuid());
            GameMode targetGameMode = GameMode.SURVIVAL;
            if (prev != null && prev.dimension != null) {
                Identifier dimId = Identifier.tryParse(prev.dimension);
                ServerWorld world = dimId == null
                    ? null
                    : context.getSource().getServer().getWorld(RegistryKey.of(RegistryKeys.WORLD, dimId));
                if (world != null) {
                    player.teleport(world, prev.x, prev.y, prev.z, Set.<PositionFlag>of(), prev.yaw, prev.pitch, false);
                }
                if (prev.gameMode != null) {
                    GameMode parsed = GameMode.byId(prev.gameMode, null);
                    if (parsed != null && parsed != GameMode.SPECTATOR) {
                        targetGameMode = parsed;
                    }
                }
            }
            player.changeGameMode(targetGameMode);
            player.fallDistance = 0f;
            context.getSource().sendFeedback(() -> Text.literal("Modo cámara desactivado.").formatted(Formatting.GRAY), false);
            return 1;
        }

        if ("restrictive".equals(mode)) {
            String danger = dangerReason(player);
            if (danger != null) {
                context.getSource().sendError(Text.literal("No puedes entrar a /cs ahora: " + danger + "."));
                return 0;
            }
        }

        if (player.hasVehicle()) {
            player.stopRiding();
        }
        GameMode current = player.interactionManager.getGameMode();
        CameraModeStore.save(player.getUuid(), new CameraModeStore.SavedState(
            player.getWorld().getRegistryKey().getValue().toString(),
            player.getX(),
            player.getY(),
            player.getZ(),
            player.getYaw(),
            player.getPitch(),
            current.getId()
        ));
        player.changeGameMode(GameMode.SPECTATOR);
        context.getSource().sendFeedback(() -> Text.literal("Modo cámara activado.").formatted(Formatting.GRAY), false);
        return 1;
    }

    private static String dangerReason(ServerPlayerEntity player) {
        if (player.isInLava()) return "estás en lava";
        if (player.isOnFire()) return "te estás quemando";
        if (player.isInsideWall()) return "te estás asfixiando";
        if (player.getAir() < player.getMaxAir() / 4) return "te estás quedando sin aire";
        if (!player.isOnGround() && !player.getAbilities().flying && player.fallDistance > 0.5f) {
            return "estás cayendo";
        }
        if (player.hurtTime > 0 || (player.getLastAttackedTime() > 0 && player.age - player.getLastAttackedTime() < 100)) {
            return "recibiste daño recientemente";
        }
        return null;
    }
}
