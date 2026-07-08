package smp.aeternum.carpet;

import carpet.CarpetServer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import net.minecraft.util.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import smp.aeternum.carpet.command.CommandAvailabilityRefresher;
import smp.aeternum.carpet.command.ExplorationCommand;
import smp.aeternum.carpet.command.TodoCommand;
import smp.aeternum.carpet.config.AeternumCarpetConfig;
import smp.aeternum.carpet.exploration.PlayerExplorationGuard;
import smp.aeternum.carpet.todo.TodoListStore;

public class AeternumCarpet implements ModInitializer {
	public static final String MOD_ID = "aeternum-carpet";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		CarpetServer.manageExtension(new AeternumCarpetExtension());
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			AeternumCarpetConfig.load();
			TodoListStore.load(server);
			CommandAvailabilityRefresher.initialize();
		});
		ServerTickEvents.END_SERVER_TICK.register(PlayerExplorationGuard::tick);
		ServerTickEvents.END_SERVER_TICK.register(CommandAvailabilityRefresher::tick);
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			ExplorationCommand.register(dispatcher);
			TodoCommand.register(dispatcher);
		});
		LOGGER.info("Hello Fabric world!");
	}

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}
}
