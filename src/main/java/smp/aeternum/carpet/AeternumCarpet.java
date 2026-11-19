package smp.aeternum.carpet;

import carpet.CarpetServer;
import carpet.patches.EntityPlayerMPFake;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;

import net.minecraft.block.ChestBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import smp.aeternum.carpet.command.CommandAvailabilityRefresher;
import smp.aeternum.carpet.command.PerimeterCommand;
import smp.aeternum.carpet.perimeters.PerimeterScanner;
import smp.aeternum.carpet.perimeters.PerimeterStore;
import smp.aeternum.carpet.perimeters.PerimeterPresetStore;
import smp.aeternum.carpet.perimeters.WorldEaterMonitor;
import smp.aeternum.carpet.network.PerimeterNetworking;
import smp.aeternum.carpet.command.ExplorationCommand;
import smp.aeternum.carpet.command.QuickAccessCommand;
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
import smp.aeternum.carpet.motd.MotdTaskStore;
import smp.aeternum.carpet.guides.GuideStore;
import smp.aeternum.carpet.network.GuideNetworking;
import smp.aeternum.carpet.network.ExplorationMapNetworking;
import smp.aeternum.carpet.network.MaterialsNetworking;
import smp.aeternum.carpet.network.ScreenOpenNetworking;
import smp.aeternum.carpet.network.CarpetRulesNetworking;
import smp.aeternum.carpet.command.GuideCommand;
import smp.aeternum.carpet.command.MaterialsCommand;
import smp.aeternum.carpet.command.RulesCommand;
import smp.aeternum.carpet.command.SitBehaviourCommand;
import smp.aeternum.carpet.command.ViewInventoryCommand;
import smp.aeternum.carpet.command.DimensionCommand;
import smp.aeternum.carpet.command.CameraModeCommand;
import smp.aeternum.carpet.command.HatCommand;
import smp.aeternum.carpet.command.CarefulBreakCommand;
import smp.aeternum.carpet.camera.CameraModeStore;
import smp.aeternum.carpet.carefulbreak.CarefulBreakStore;
import smp.aeternum.carpet.bot.BotInventoryOpener;
import smp.aeternum.carpet.bot.FakePlayerReloadManager;
import smp.aeternum.carpet.util.PlayerSitManager;
import smp.aeternum.carpet.bot.ModScreenHandlers;
import smp.aeternum.carpet.materials.MaterialsStore;

public class AeternumCarpet implements ModInitializer {
	public static final String MOD_ID = "aeternum-carpet";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModScreenHandlers.register();
		PerimeterNetworking.registerPayloads();
		CommandPacksNetworking.registerPayloads();
		GuideNetworking.registerPayloads();
		ExplorationMapNetworking.registerPayloads();
		MaterialsNetworking.registerPayloads();
		ScreenOpenNetworking.registerPayloads();
		CarpetRulesNetworking.registerPayloads();
		CarpetServer.manageExtension(new AeternumCarpetExtension());
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			AeternumCarpetConfig.load();
			ExplorationPregenerator.load(server);
			TodoListStore.load(server);
			QuickAccessSubscriptionStore.load(server);
			PerimeterPresetStore.load(server);
			PerimeterStore.load(server);
			WorldEaterMonitor.clearAll();
			FinderStore.load(server);
			FinderItemNames.load(server);
			ProtectedBlockStore.load(server);
			CommandPackStore.load(server);
			MotdTaskStore.load(server);
			GuideStore.load(server);
			MaterialsStore.load(server);
			CameraModeStore.load(server);
			CarefulBreakStore.load(server);
			FakePlayerReloadManager.load(server);
			CommandAvailabilityRefresher.initialize();
		});
		ServerLifecycleEvents.SERVER_STARTED.register(server ->
			CameraModeCommand.attachAfterModRegistration(server.getCommandManager().getDispatcher())
		);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			FakePlayerReloadManager.onServerStopping(server);
			PlayerSitManager.onServerStopping();
		});
		ServerTickEvents.END_SERVER_TICK.register(PlayerExplorationGuard::tick);
		ServerTickEvents.END_SERVER_TICK.register(ExplorationPregenerator::tick);
		ServerTickEvents.END_SERVER_TICK.register(CommandAvailabilityRefresher::tick);
		ServerTickEvents.END_SERVER_TICK.register(PerimeterScanner::tick);
		ServerTickEvents.END_SERVER_TICK.register(WorldEaterMonitor::tick);
		ServerTickEvents.END_SERVER_TICK.register(FinderScanner::tick);
		ServerTickEvents.END_SERVER_TICK.register(FinderHints::tick);
		ServerTickEvents.END_SERVER_TICK.register(CommandRunner::tick);
		ServerTickEvents.END_SERVER_TICK.register(FakePlayerReloadManager::tick);
		ServerTickEvents.END_SERVER_TICK.register(PlayerSitManager::tickServer);
		ServerTickEvents.END_SERVER_TICK.register(server -> MaterialsStore.tick());
		ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, player, params) -> !ActionsCommand.handleChat(player, message.getContent().getString()));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ScreenOpenNetworking.sendDockState(handler.player, AeternumCarpetSettings.joinMotd);
			PerimeterNetworking.sendHud(handler.player);
			if (handler.player instanceof EntityPlayerMPFake bot) FakePlayerReloadManager.onFakePlayerJoin(bot);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			PlayerSitManager.onPlayerDisconnect(handler.player);
			if (handler.player instanceof EntityPlayerMPFake) FakePlayerReloadManager.onFakePlayerDisconnect(server);
		});
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
			if (!world.isClient) PerimeterScanner.onPlayerBlockBroken((net.minecraft.server.world.ServerWorld) world, pos, state);
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
		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (!world.isClient
				&& AeternumCarpetSettings.editBotInventory
				&& hand == net.minecraft.util.Hand.MAIN_HAND
				&& !player.isSpectator()
				&& entity instanceof EntityPlayerMPFake bot
				&& player instanceof net.minecraft.server.network.ServerPlayerEntity viewer) {
				BotInventoryOpener.open(viewer, bot);
				return ActionResult.SUCCESS;
			}
			return ActionResult.PASS;
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			ExplorationCommand.register(dispatcher);
			QuickAccessCommand.register(dispatcher);
			PerimeterCommand.register(dispatcher);
			FinderCommand.register(dispatcher);
			ProtectCommand.register(dispatcher);
			ActionsCommand.register(dispatcher);
			GuideCommand.register(dispatcher);
			MaterialsCommand.register(dispatcher);
			RulesCommand.register(dispatcher);
			DimensionCommand.register(dispatcher);
			CameraModeCommand.register(dispatcher);
			HatCommand.register(dispatcher);
			CarefulBreakCommand.register(dispatcher);
			SitBehaviourCommand.register(dispatcher);
			ViewInventoryCommand.register(dispatcher);
		});
	}

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}
}
