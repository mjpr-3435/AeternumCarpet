package smp.aeternum.carpet;

import carpet.CarpetExtension;
import carpet.CarpetServer;

public class AeternumCarpetExtension implements CarpetExtension {
    @Override
    public void onGameStarted() {
        CarpetServer.settingsManager.parseSettingsClass(AeternumCarpetSettings.class);
    }
}