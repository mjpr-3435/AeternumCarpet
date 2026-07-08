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
            "carpet.rule.allowPlayerExploration.name", "Allow Player Exploration",
            "carpet.rule.allowPlayerExploration.desc", "Allow players to explore outside configured generation areas.",
            "carpet.category.AETERNUM", "Aeternum"
        );
    }
}
