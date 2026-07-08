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
        return Map.of(
            "carpet.category.AETERNUM", "Aeternum",
            "carpet.rule.allowPlayerExploration.name", "Allow Player Exploration",
            "carpet.rule.allowPlayerExploration.desc", "Allow players to explore outside configured generation areas.",
            "carpet.rule.spectatorPortalTravel.name", "Spectator Portal Travel",
            "carpet.rule.spectatorPortalTravel.desc", "Allows spectator players to use portals normally.",
            "carpet.rule.openShulkerInHand.name", "Open Shulker In Hand",
            "carpet.rule.openShulkerInHand.desc", "Allows opening a shulker box directly from the hand when using it in the air.",
            "carpet.rule.commandTodoList.name", "Command Todo List",
            "carpet.rule.commandTodoList.desc", "Enables personal todo notes through the /todo command."
        );
    }
}
