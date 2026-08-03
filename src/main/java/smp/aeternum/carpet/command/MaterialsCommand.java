package smp.aeternum.carpet.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import smp.aeternum.carpet.AeternumCarpetSettings;
import smp.aeternum.carpet.materials.MaterialsStore;
import smp.aeternum.carpet.network.MaterialsNetworking;
import java.util.List;
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
				.then(literal("list").executes(MaterialsCommand::listRegions))
				.then(literal("del").then(argument("name", StringArgumentType.word()).then(argument("index", IntegerArgumentType.integer(1)).executes(MaterialsCommand::deleteRegion))))
				.then(literal("add").then(argument("name", StringArgumentType.word())
					.then(argument("pos1", BlockPosArgumentType.blockPos())
						.then(argument("pos2", BlockPosArgumentType.blockPos())
							.executes(MaterialsCommand::addRegion)))))));
	}

	private static int addRegion(com.mojang.brigadier.context.CommandContext<ServerCommandSource> c) {
		String name = StringArgumentType.getString(c, "name");
		if (MaterialsStore.get(name) == null) {
			c.getSource().sendError(Text.literal("No existe una lista con el nombre '" + name + "'."));
			return 0;
		}
		if (!MaterialsStore.getRegions(name).isEmpty()) {
			c.getSource().sendError(Text.literal("La lista '" + name + "' ya tiene un almacén asignado. Elimínalo antes de asignar otro."));
			return 0;
		}
		BlockPos p1 = BlockPosArgumentType.getBlockPos(c, "pos1");
		BlockPos p2 = BlockPosArgumentType.getBlockPos(c, "pos2");
		String dim = c.getSource().getWorld().getRegistryKey().getValue().toString();
		MaterialsStore.addStorageRegion(name, dim, p1, p2);
		c.getSource().sendFeedback(() -> Text.literal("Almacén asociado a " + name).formatted(Formatting.GRAY), false);
		listRegions(c);
		return 1;
	}

	private static int listRegions(com.mojang.brigadier.context.CommandContext<ServerCommandSource> c) {
		boolean found = false;
		for (var list : MaterialsStore.all()) {
			List<MaterialsStore.StorageRegion> regions = MaterialsStore.getRegions(list.name);
			if (regions.isEmpty()) continue;
			found = true;
			c.getSource().sendFeedback(() -> Text.literal(list.name + ":").formatted(Formatting.GOLD), false);
			for (int i = 0; i < regions.size(); i++) {
				var region = regions.get(i);
				final int idx = i;
				String delCmd = "/materials storage del " + list.name + " " + (idx + 1);
				c.getSource().sendFeedback(() -> Text.empty()
					.append(Text.literal("[x]").setStyle(Style.EMPTY.withColor(Formatting.RED)
						.withClickEvent(new ClickEvent.RunCommand(delCmd))
						.withHoverEvent(new HoverEvent.ShowText(Text.literal("Eliminar almacén")))))
					.append(Text.literal(" ").formatted(Formatting.DARK_GRAY))
					.append(Text.literal("[" + (idx + 1) + "] " + region.dimension + " (" + region.x1 + "," + region.y1 + "," + region.z1 + ")-(" + region.x2 + "," + region.y2 + "," + region.z2 + ")").formatted(Formatting.GRAY)), false);
			}
		}
		if (!found) c.getSource().sendFeedback(() -> Text.literal("No hay almacenes registrados.").formatted(Formatting.GRAY), false);
		return 1;
	}

	private static int deleteRegion(com.mojang.brigadier.context.CommandContext<ServerCommandSource> c) {
		String name = StringArgumentType.getString(c, "name");
		int idx = IntegerArgumentType.getInteger(c, "index") - 1;
		List<MaterialsStore.StorageRegion> regions = MaterialsStore.getRegions(name);
		if (idx < 0 || idx >= regions.size()) {
			c.getSource().sendError(Text.literal("Índice inválido."));
			return 0;
		}
		MaterialsStore.removeStorageRegion(name, idx);
		c.getSource().sendFeedback(() -> Text.literal("Almacén eliminado de " + name).formatted(Formatting.GRAY), false);
		listRegions(c);
		return 1;
	}
}
