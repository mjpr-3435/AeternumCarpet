package smp.aeternum.carpet.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Optional;
import net.minecraft.command.CommandSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import smp.aeternum.carpet.AeternumCarpetSettings;
import smp.aeternum.carpet.bot.BotInventoryOpener;
import smp.aeternum.carpet.bot.OfflinePlayerSession;

public final class ViewInventoryCommand {
    private ViewInventoryCommand() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("viewinv")
            .requires(src -> AeternumCarpetSettings.commandViewInventory && src.hasPermissionLevel(2))
            .then(CommandManager.argument("player", StringArgumentType.word())
                .suggests((ctx, builder) -> CommandSource.suggestMatching(ctx.getSource().getServer().getPlayerNames(), builder))
                .executes(ViewInventoryCommand::execute)));
    }

    private static int execute(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity viewer = source.getPlayerOrThrow();
        String targetName = StringArgumentType.getString(ctx, "player");
        MinecraftServer server = source.getServer();

        ServerPlayerEntity online = findOnlinePlayer(server, targetName);
        if (online != null) {
            BotInventoryOpener.openOnline(viewer, online);
            return 1;
        }

        if (server.getUserCache() != null) {
            Optional<GameProfile> profile = server.getUserCache().findByName(targetName);
            if (profile.isPresent()) {
                OfflinePlayerSession session = OfflinePlayerSession.load(server, profile.get());
                if (session != null) {
                    BotInventoryOpener.openOffline(viewer, profile.get().getName(), session);
                    return 1;
                }
            }
        }

        source.sendError(Text.literal("No se encontró jugador online ni datos offline para: " + targetName));
        return 0;
    }

    private static ServerPlayerEntity findOnlinePlayer(MinecraftServer server, String name) {
        ServerPlayerEntity exact = server.getPlayerManager().getPlayer(name);
        if (exact != null) return exact;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.getName().getString().equalsIgnoreCase(name)) {
                return p;
            }
        }
        return null;
    }
}
