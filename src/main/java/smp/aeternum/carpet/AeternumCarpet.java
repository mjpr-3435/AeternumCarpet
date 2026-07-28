package smp.aeternum.carpet;

import carpet.CarpetServer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;

import net.minecraft.block.ChestBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import smp.aeternum.carpet.command.CommandAvailabilityRefresher;
import smp.aeternum.carpet.command.BlockersCommand;
import smp.aeternum.carpet.blockers.BlockerScanner;
import smp.aeternum.carpet.blockers.BlockerScanStore;
import smp.aeternum.carpet.blockers.BlockerBlockList;
import smp.aeternum.carpet.network.BlockerNetworking;
import smp.aeternum.carpet.command.ExplorationCommand;
import smp.aeternum.carpet.command.QuickAccessCommand;
import smp.aeternum.carpet.command.TodoCommand;
import smp.aeternum.carpet.config.AeternumCarpetConfig;
import smp.aeternum.carpet.exploration.PlayerExplorationGuard;
import smp.aeternum.carpet.exploration.ExplorationPregenerator;
import smp.aeternum.carpet.quickaccess.QuickAccessSubscriptionStore;
import smp.aeternum.carpet.todo.TodoListStore;
import smp.aeternum.carpet.finder.FinderScanner;
import smp.aeternum.carpet.finder.FinderStore;
import smp.aeternum.carpet.finder.FinderHints;
import smp.aeternum.carpet.finder.FinderItemNames;
import smp.aeternum.carpet.command.FinderCommand;
import smp.aeternum.carpet.command.ProtectCommand;
import smp.aeternum.carpet.protect.ProtectedBlockStore;
import smp.aeternum.carpet.actions.CommandPackStore;
import smp.aeternum.carpet.actions.CommandRunner;
import smp.aeternum.carpet.command.ActionsCommand;
import smp.aeternum.carpet.network.CommandPacksNetworking;
import smp.aeternum.carpet.command.MotdCommand;
import smp.aeternum.carpet.motd.JoinMotd;
import smp.aeternum.carpet.motd.MotdTaskStore;
import smp.aeternum.carpet.guides.GuideStore;
import smp.aeternum.carpet.network.GuideNetworking;
import smp.aeternum.carpet.network.ExplorationMapNetworking;
import smp.aeternum.carpet.network.MaterialsNetworking;
import smp.aeternum.carpet.command.GuideCommand;
import smp.aeternum.carpet.command.MaterialsCommand;
import smp.aeternum.carpet.materials.MaterialsStore;

public class AeternumCarpet implements ModInitializer {
	public static final String MOD_ID = "aeternum-carpet";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		BlockerNetworking.registerPayloads();
		CommandPacksNetworking.registerPayloads();
		GuideNetworking.registerPayloads();
		ExplorationMapNetworking.registerPayloads();
		MaterialsNetworking.registerPayloads();
		CarpetServer.manageExtension(new AeternumCarpetExtension());
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			AeternumCarpetConfig.load();
			ExplorationPregenerator.load(server);
			TodoListStore.load(server);
			QuickAccessSubscriptionStore.load(server);
			BlockerScanStore.load(server);
			BlockerBlockList.load(server);
			FinderStore.load(server);
			FinderItemNames.load(server);
			ProtectedBlockStore.load(server);
			CommandPackStore.load(server);
			MotdTaskStore.load(server);
			GuideStore.load(server);
			MaterialsStore.load(server);
			CommandAvailabilityRefresher.initialize();
		});
		ServerTickEvents.END_SERVER_TICK.register(PlayerExplorationGuard::tick);
		ServerTickEvents.END_SERVER_TICK.register(ExplorationPregenerator::tick);
		ServerTickEvents.END_SERVER_TICK.register(CommandAvailabilityRefresher::tick);
		ServerTickEvents.END_SERVER_TICK.register(BlockerScanner::tick);
		ServerTickEvents.END_SERVER_TICK.register(FinderScanner::tick);
		ServerTickEvents.END_SERVER_TICK.register(FinderHints::tick);
		ServerTickEvents.END_SERVER_TICK.register(CommandRunner::tick);
		ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, player, params) -> !ActionsCommand.handleChat(player, message.getContent().getString()));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (AeternumCarpetSettings.joinMotd) JoinMotd.send(handler.player);
		});
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
			if (!world.isClient) BlockerScanner.onPlayerBlockBroken((net.minecraft.server.world.ServerWorld) world, pos);
		});
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) ->
			AeternumCarpetSettings.commandProtect && !player.hasPermissionLevel(2) && ProtectedBlockStore.isProtectedInventory(world, pos) ? false : true);
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (world.isClient || !AeternumCarpetSettings.commandProtect || player.hasPermissionLevel(2)) return ActionResult.PASS;
			if (ProtectedBlockStore.isProtectedInventory(world, hit.getBlockPos())) {
				player.sendMessage(net.minecraft.text.Text.literal("[Protect] Solo administradores pueden abrir este inventario.").formatted(net.minecraft.util.Formatting.RED), true);
				return ActionResult.FAIL;
			}
			if (player.getStackInHand(hand).getItem() instanceof BlockItem item && item.getBlock() instanceof ChestBlock) {
				if (ProtectedBlockStore.wouldJoinProtectedChest(world, hit.getBlockPos())
					|| ProtectedBlockStore.wouldJoinProtectedChest(world, hit.getBlockPos().offset(hit.getSide()))) return ActionResult.FAIL;
			}
			return ActionResult.PASS;
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			ExplorationCommand.register(dispatcher);
			QuickAccessCommand.register(dispatcher);
			TodoCommand.register(dispatcher);
			BlockersCommand.register(dispatcher);
			FinderCommand.register(dispatcher);
			ProtectCommand.register(dispatcher);
			ActionsCommand.register(dispatcher);
			MotdCommand.register(dispatcher);
			GuideCommand.register(dispatcher);
			MaterialsCommand.register(dispatcher);
		});
	}

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}
}
