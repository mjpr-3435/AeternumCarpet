package smp.aeternum.carpet;

import carpet.CarpetExtension;
import carpet.CarpetServer;

import java.util.Map;

public class AeternumCarpetExtension implements CarpetExtension {
    @Override
    public void onGameStarted() {
        CarpetServer.settingsManager.parseSettingsClass(AeternumCarpetSettings.class);
    }

    @Override
    public Map<String, String> canHasTranslations(String lang) {
        return Map.ofEntries(
            Map.entry("carpet.category.AETERNUM", "Aeternum"),
            Map.entry("carpet.rule.explorationManager.name", "Exploration Manager"),
            Map.entry("carpet.rule.explorationManager.desc", "Shows or hides the /exploration command. While enabled, exploration restrictions are not enforced."),
            Map.entry("carpet.rule.allowPlayerExploration.name", "Allow Player Exploration"),
            Map.entry("carpet.rule.allowPlayerExploration.desc", "When disabled, players are restricted to configured generation areas."),
            Map.entry("carpet.rule.spectatorPortalTravel.name", "Spectator Portal Travel"),
            Map.entry("carpet.rule.spectatorPortalTravel.desc", "Allows spectator players to use portals normally."),
            Map.entry("carpet.rule.openShulkerInHand.name", "Open Shulker In Hand"),
            Map.entry("carpet.rule.openShulkerInHand.desc", "Allows opening a shulker box directly from the hand when using it in the air."),
            Map.entry("carpet.rule.openEnderChestInHand.name", "Open Ender Chest In Hand"),
            Map.entry("carpet.rule.openEnderChestInHand.desc", "Allows opening an ender chest directly from the hand when using it in the air."),
            Map.entry("carpet.rule.instamineDeepslate.name", "Instamine Deepslate"),
            Map.entry("carpet.rule.instamineDeepslate.desc", "Allows instant mining of deepslate with a netherite pickaxe with Efficiency V and Haste II or higher."),
            Map.entry("carpet.rule.fastObsidian.name", "Fast Obsidian"),
            Map.entry("carpet.rule.fastObsidian.desc", "Obsidian and crying obsidian break at stone speed with a netherite pickaxe with Efficiency V."),
            Map.entry("carpet.rule.breakEndPortalFrames.name", "Break End Portal Frames"),
            Map.entry("carpet.rule.breakEndPortalFrames.desc", "Allows breaking end portal frames with a mace with Breach IV, Haste II, and Strength II after sustained mining."),
            Map.entry("carpet.rule.breakEndPortalsWithWindCharge.name", "Break End Portals With Wind Charge"),
            Map.entry("carpet.rule.breakEndPortalsWithWindCharge.desc", "Allows wind charges to remove end portal blocks in a small area."),
            Map.entry("carpet.rule.allowUnlimitedPlayerSpeed.name", "Allow Unlimited Player Speed"),
            Map.entry("carpet.rule.allowUnlimitedPlayerSpeed.desc", "Disables the vanilla server movement speed check that causes moved too quickly rubberbanding."),
            Map.entry("carpet.rule.naturalIceFormation.name", "Natural Ice Formation"),
            Map.entry("carpet.rule.naturalIceFormation.desc", "Controls whether water freezes into ice naturally."),
            Map.entry("carpet.rule.protectedPots.name", "Protected Pots"),
            Map.entry("carpet.rule.protectedPots.desc", "Prevents decorated pots from being broken by anything except explicit player mining."),
            Map.entry("carpet.rule.phantomSpawning.name", "Phantom Spawning"),
            Map.entry("carpet.rule.phantomSpawning.desc", "Controls phantom spawning behavior: vanilla, mobcap, or disable."),
            Map.entry("carpet.rule.todoList.name", "Todo List"),
            Map.entry("carpet.rule.todoList.desc", "Enables personal todo notes in the compact dock overlay widget and HUD pin."),
            Map.entry("carpet.rule.commandBlockers.name", "Command Blockers"),
            Map.entry("carpet.rule.commandBlockers.desc", "Enables /blockers scans for blocks that can stop piston machines."),
            Map.entry("carpet.rule.commandFinder.name", "Command Finder"),
            Map.entry("carpet.rule.commandFinder.desc", "Enables the persistent /find and /count storage finder commands."),
            Map.entry("carpet.rule.commandProtect.name", "Command Protect"),
            Map.entry("carpet.rule.commandProtect.desc", "Enables OP-managed persistent protection for selected blocks through /protect."),
            Map.entry("carpet.rule.commandActions.name", "Command Actions"),
            Map.entry("carpet.rule.commandActions.desc", "Enables the in-game /actions editor for persistent server command automation."),
            Map.entry("carpet.rule.joinMotd.name", "Join MOTD"),
            Map.entry("carpet.rule.joinMotd.desc", "Shows the Aeternum Network MOTD banner on player join (5s) and in the dock overlay."),
            Map.entry("carpet.rule.quickDock.name", "Quick Dock"),
            Map.entry("carpet.rule.quickDock.desc", "Enables the quick access dock bar above the hotbar when holding the Aeternum Dock key."),
            Map.entry("carpet.rule.commandGuide.name", "Command Guide"),
            Map.entry("carpet.rule.commandGuide.desc", "Enables the server-side markdown guide library through /guide."),
            Map.entry("carpet.rule.commandProjects.name", "Command Projects"),
            Map.entry("carpet.rule.commandProjects.desc", "Enables the /projects command for schematics, subregions, and project material collection."),
            Map.entry("carpet.rule.commandDimension.name", "Command Dimension"),
            Map.entry("carpet.rule.commandDimension.desc", "Enables /nether, /overworld, and /end dimension teleports strictly in Creative or Spectator mode."),
            Map.entry("carpet.rule.editBotInventory.name", "Edit Bot Inventory"),
            Map.entry("carpet.rule.editBotInventory.desc", "Allows opening and editing Carpet fake player inventories, armor, and hands by right-clicking them.")
        );
    }
}
