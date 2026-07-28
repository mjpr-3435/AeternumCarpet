package smp.aeternum.carpet.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import smp.aeternum.carpet.AeternumCarpetSettings;
import smp.aeternum.carpet.materials.MaterialsStore;
import smp.aeternum.carpet.network.MaterialsNetworking;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class MaterialsCommand {
	private MaterialsCommand() {}

	public static void register(CommandDispatcher<ServerCommandSource> d) {
		d.register(literal("materials")
			.requires(s -> AeternumCarpetSettings.commandMaterials)
			.executes(c -> { try { MaterialsNetworking.send(c.getSource().getPlayerOrThrow()); return 1; } catch (Exception e) { c.getSource().sendError(Text.literal("La lista de materiales se abre en el juego.")); return 0; } })
			.then(literal("storage")
				.requires(s -> s.hasPermissionLevel(2))
				.then(argument("name", StringArgumentType.word())
					.then(argument("pos1", BlockPosArgumentType.blockPos())
						.then(argument("pos2", BlockPosArgumentType.blockPos())
							.executes(c -> {
								String name = StringArgumentType.getString(c, "name");
								BlockPos p1 = BlockPosArgumentType.getBlockPos(c, "pos1");
								BlockPos p2 = BlockPosArgumentType.getBlockPos(c, "pos2");
								String dim = c.getSource().getWorld().getRegistryKey().getValue().toString();
								MaterialsStore.addStorageRegion(name, dim, p1, p2);
								c.getSource().sendFeedback(() -> Text.literal("Almacén asociado a " + name).formatted(Formatting.GRAY), false);
								return 1;
							}))))));
	}
}
