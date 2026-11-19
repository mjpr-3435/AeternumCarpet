package smp.aeternum.carpet.bot;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import smp.aeternum.carpet.AeternumCarpet;

public final class ModScreenHandlers {
    public static final ExtendedScreenHandlerType<BotInventoryScreenHandler, BotOpenData> BOT_INVENTORY =
        Registry.register(
            Registries.SCREEN_HANDLER,
            AeternumCarpet.id("bot_inventory"),
            new ExtendedScreenHandlerType<>(BotInventoryScreenHandler::new, BotOpenData.CODEC)
        );

    private ModScreenHandlers() {}

    public static void register() {
        // Forces static initialization.
    }
}
