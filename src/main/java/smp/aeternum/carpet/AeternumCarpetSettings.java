package smp.aeternum.carpet;

import carpet.api.settings.Rule;
import carpet.api.settings.RuleCategory;

public class AeternumCarpetSettings {
    @Rule(categories = {"AETERNUM", RuleCategory.COMMAND})
    public static boolean explorationManager = false;

    @Rule(categories = {"AETERNUM", RuleCategory.FEATURE})
    public static boolean allowPlayerExploration = true;

    @Rule(categories = {"AETERNUM", RuleCategory.FEATURE})
    public static boolean spectatorPortalTravel = false;

    @Rule(categories = {"AETERNUM", RuleCategory.FEATURE})
    public static boolean openShulkerInHand = false;

    @Rule(categories = {"AETERNUM", RuleCategory.FEATURE})
    public static boolean openEnderChestInHand = false;

    @Rule(categories = {"AETERNUM", RuleCategory.FEATURE})
    public static boolean instamineDeepslate = false;

    @Rule(categories = {"AETERNUM", RuleCategory.FEATURE})
    public static boolean breakEndPortalFrames = false;

    @Rule(categories = {"AETERNUM", RuleCategory.FEATURE})
    public static boolean breakEndPortalsWithWindCharge = false;

    @Rule(categories = {"AETERNUM", RuleCategory.FEATURE})
    public static boolean allowUnlimitedPlayerSpeed = false;

    @Rule(categories = {"AETERNUM", RuleCategory.FEATURE})
    public static boolean naturalIceFormation = true;

    @Rule(categories = {"AETERNUM", RuleCategory.FEATURE}, options = {"vanilla", "mobcap", "disable"})
    public static String phantomSpawning = "vanilla";

    @Rule(categories = {"AETERNUM", RuleCategory.COMMAND})
    public static boolean commandTodoList = false;

    @Rule(categories = {"AETERNUM", RuleCategory.COMMAND})
    public static boolean commandBlockers = false;

    @Rule(categories = {"AETERNUM", RuleCategory.COMMAND})
    public static boolean commandFinder = false;

    @Rule(categories = {"AETERNUM", RuleCategory.COMMAND})
    public static boolean commandProtect = false;

    @Rule(categories = {"AETERNUM", RuleCategory.COMMAND})
    public static boolean commandActions = false;

    @Rule(categories = {"AETERNUM", RuleCategory.FEATURE})
    public static boolean joinMotd = true;

    @Rule(categories = {"AETERNUM", RuleCategory.COMMAND})
    public static boolean commandGuide = false;

}
