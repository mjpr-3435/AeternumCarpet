package smp.aeternum.carpet;

import carpet.api.settings.Rule;
import carpet.api.settings.RuleCategory;

public class AeternumCarpetSettings {
    @Rule(categories = {"AETERNUM", RuleCategory.FEATURE})
    public static boolean allowPlayerExploration = true;

    @Rule(categories = {"AETERNUM", RuleCategory.FEATURE})
    public static boolean spectatorPortalTravel = false;

    @Rule(categories = {"AETERNUM", RuleCategory.FEATURE})
    public static boolean openShulkerInHand = false;

    @Rule(categories = {"AETERNUM", RuleCategory.COMMAND})
    public static boolean commandTodoList = false;
}
