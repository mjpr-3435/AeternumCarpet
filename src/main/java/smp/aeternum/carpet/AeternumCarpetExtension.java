package smp.aeternum.carpet;

import carpet.CarpetExtension;
import carpet.api.settings.SettingsManager;

import java.util.Map;

public class AeternumCarpetExtension implements CarpetExtension {
    private static final SettingsManager SETTINGS_MANAGER = new SettingsManager("1.0.0", AeternumCarpet.MOD_ID, "Aeternum Carpet");

    @Override
    public void onGameStarted() {
        SETTINGS_MANAGER.parseSettingsClass(AeternumCarpetSettings.class);
    }

    @Override
    public SettingsManager extensionSettingsManager() {
        return SETTINGS_MANAGER;
    }

    @Override
    public Map<String, String> canHasTranslations(String lang) {
        return Map.of(
            AeternumCarpet.MOD_ID + ".rule.allowPlayerExploration.name", "Allow Player Exploration",
            AeternumCarpet.MOD_ID + ".rule.allowPlayerExploration.desc", "Allow players to explore outside configured generation areas.",
            AeternumCarpet.MOD_ID + ".category.AETERNUM", "Aeternum"
        );
    }
}
