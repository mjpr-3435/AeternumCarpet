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
            Map.entry("carpet.rule.allowPlayerExploration.name", "Allow Player Exploration"),
            Map.entry("carpet.rule.allowPlayerExploration.desc", "Allow players to explore outside configured generation areas."),
            Map.entry("carpet.rule.spectatorPortalTravel.name", "Spectator Portal Travel"),
            Map.entry("carpet.rule.spectatorPortalTravel.desc", "Allows spectator players to use portals normally."),
            Map.entry("carpet.rule.openShulkerInHand.name", "Open Shulker In Hand"),
            Map.entry("carpet.rule.openShulkerInHand.desc", "Allows opening a shulker box directly from the hand when using it in the air."),
            Map.entry("carpet.rule.openEnderChestInHand.name", "Open Ender Chest In Hand"),
            Map.entry("carpet.rule.openEnderChestInHand.desc", "Allows opening an ender chest directly from the hand when using it in the air."),
            Map.entry("carpet.rule.instamineDeepslate.name", "Instamine Deepslate"),
            Map.entry("carpet.rule.instamineDeepslate.desc", "Allows instant mining of deepslate with a netherite pickaxe and Haste II or higher."),
            Map.entry("carpet.rule.breakEndPortalFrames.name", "Break End Portal Frames"),
            Map.entry("carpet.rule.breakEndPortalFrames.desc", "Allows breaking end portal frames with a mace, Haste II, and Strength II after sustained mining."),
            Map.entry("carpet.rule.breakEndPortalsWithWindCharge.name", "Break End Portals With Wind Charge"),
            Map.entry("carpet.rule.breakEndPortalsWithWindCharge.desc", "Allows wind charges to remove end portal blocks in a small area."),
            Map.entry("carpet.rule.allowUnlimitedPlayerSpeed.name", "Allow Unlimited Player Speed"),
            Map.entry("carpet.rule.allowUnlimitedPlayerSpeed.desc", "Disables the vanilla server movement speed check that causes moved too quickly rubberbanding."),
            Map.entry("carpet.rule.phantomSpawning.name", "Phantom Spawning"),
            Map.entry("carpet.rule.phantomSpawning.desc", "Controls phantom spawning behavior: vanilla, mobcap, or disable."),
            Map.entry("carpet.rule.commandTodoList.name", "Command Todo List"),
            Map.entry("carpet.rule.commandTodoList.desc", "Enables personal todo notes through the /todo command.")
        );
    }
}
