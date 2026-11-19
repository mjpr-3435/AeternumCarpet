package smp.aeternum.carpet.network;

import carpet.CarpetServer;
import carpet.api.settings.CarpetRule;
import carpet.api.settings.InvalidRuleValueException;
import carpet.api.settings.RuleHelper;
import carpet.api.settings.SettingsManager;
import carpet.settings.ParsedRule;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import smp.aeternum.carpet.AeternumCarpet;

public final class CarpetRulesNetworking {
    private static final Gson GSON = new Gson();

    private CarpetRulesNetworking() {
    }

    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(CarpetRulesDataPayload.ID, CarpetRulesDataPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(CarpetRuleSetPayload.ID, CarpetRuleSetPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(CarpetRuleSetPayload.ID, (payload, context) ->
            context.server().execute(() -> handleSet(context.player(), payload)));
    }

    public static void sendSnapshot(ServerPlayerEntity player, boolean openScreen) {
        sendSnapshot(player, openScreen, "", false);
    }

    private static void sendSnapshot(ServerPlayerEntity player, boolean openScreen, String status, boolean error) {
        if (!player.hasPermissionLevel(2)) return;
        List<RuleSnapshot> rules = new ArrayList<>();
        CarpetServer.forEachManager(manager -> {
            String managerId = manager.identifier();
            for (CarpetRule<?> rule : manager.getCarpetRules()) {
                rules.add(toSnapshot(managerId, rule));
            }
        });
        rules.sort(Comparator.comparing((RuleSnapshot r) -> r.managerId, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(r -> r.name, String.CASE_INSENSITIVE_ORDER));
        ServerPlayNetworking.send(player, new CarpetRulesDataPayload(GSON.toJson(rules), openScreen, status, error));
    }

    private static void handleSet(ServerPlayerEntity player, CarpetRuleSetPayload payload) {
        if (!player.hasPermissionLevel(2)) return;
        SettingsManager targetManager = findManager(payload.managerId());
        if (targetManager == null) {
            sendSnapshot(player, false, "Manager no encontrado: " + payload.managerId(), true);
            return;
        }
        CarpetRule<?> rule = targetManager.getCarpetRule(payload.ruleName());
        if (rule == null) {
            sendSnapshot(player, false, "Regla no encontrada: " + payload.ruleName(), true);
            return;
        }

        ServerCommandSource source = player.getCommandSource().withSilent();
        try {
            if (payload.resetToDefault()) {
                RuleHelper.resetToDefault(rule, source);
                if (payload.saveDefault()) {
                    runSilent(player, targetManager.identifier() + " removeDefault " + rule.name());
                }
                sendSnapshot(player, false, rule.name() + " -> " + RuleHelper.toRuleString(rule.value()) + " (default)", false);
            } else {
                rule.set(source, payload.value());
                if (payload.saveDefault()) {
                    runSilent(player, targetManager.identifier() + " setDefault " + rule.name() + " " + RuleHelper.toRuleString(rule.value()));
                }
                String suffix = payload.saveDefault() ? " (guardado)" : "";
                sendSnapshot(player, false, rule.name() + " -> " + RuleHelper.toRuleString(rule.value()) + suffix, false);
            }
        } catch (InvalidRuleValueException e) {
            sendSnapshot(player, false, "Valor inválido para " + rule.name() + ": " + payload.value(), true);
        } catch (RuntimeException e) {
            AeternumCarpet.LOGGER.error("Failed to update rule {}", rule.name(), e);
            sendSnapshot(player, false, "Error al actualizar " + rule.name(), true);
        }
    }

    private static void runSilent(ServerPlayerEntity player, String command) {
        if (player.getServer() == null) return;
        player.getServer().getCommandManager().executeWithPrefix(player.getServer().getCommandSource().withSilent(), command);
    }

    private static SettingsManager findManager(String id) {
        final SettingsManager[] found = new SettingsManager[1];
        CarpetServer.forEachManager(manager -> {
            if (found[0] == null && manager.identifier().equals(id)) {
                found[0] = manager;
            }
        });
        return found[0];
    }

    private static RuleSnapshot toSnapshot(String managerId, CarpetRule<?> rule) {
        RuleSnapshot s = new RuleSnapshot();
        s.managerId = managerId;
        s.sourceMod = resolveSourceMod(managerId, rule);
        s.name = rule.name();
        s.displayName = RuleHelper.translatedName(rule);
        s.description = RuleHelper.translatedDescription(rule);
        s.categories = new ArrayList<>(rule.categories());
        s.value = RuleHelper.toRuleString(rule.value());
        s.defaultValue = RuleHelper.toRuleString(rule.defaultValue());
        s.isDefault = RuleHelper.isInDefaultValue(rule);
        s.isBoolean = rule.type() == Boolean.class || rule.type() == boolean.class;
        s.suggestions = new ArrayList<>(rule.suggestions());
        s.strict = rule.strict();
        return s;
    }

    private static String resolveSourceMod(String managerId, CarpetRule<?> rule) {
        String className = rule.getClass().getName();
        if (rule instanceof ParsedRule<?> parsed && parsed.field != null) {
            className = parsed.field.getDeclaringClass().getName();
        }
        String pkg = className.toLowerCase(Locale.ROOT);
        if (pkg.startsWith("smp.aeternum.")) return "Aeternum";
        if (pkg.startsWith("carpetextra.")) return "Carpet Extra";
        if (pkg.startsWith("carpettisaddition.")) return "TIS Addition";
        if (pkg.startsWith("club.mcams.")) return "AMS Addition";
        if (pkg.startsWith("chronos.")) return "Chronos";
        if (pkg.contains("carpetorgaddition")) return "Carpet Org";
        if (pkg.startsWith("carpet.carpetsettings")) return "Carpet";

        if (rule.categories() != null) {
            for (String cat : rule.categories()) {
                if (cat == null) continue;
                String low = cat.toLowerCase(Locale.ROOT);
                if (low.equals("aeternum")) return "Aeternum";
                if (low.equals("extra")) return "Carpet Extra";
                if (low.equals("tis")) return "TIS Addition";
                if (low.equals("ams")) return "AMS Addition";
                if (low.equals("chronos")) return "Chronos";
                if (low.equals("org") || low.equals("carpet_org")) return "Carpet Org";
            }
        }
        if (managerId != null && !managerId.isBlank() && !"carpet".equalsIgnoreCase(managerId)) {
            return Character.toUpperCase(managerId.charAt(0)) + managerId.substring(1);
        }
        return "Carpet";
    }

    public static final class RuleSnapshot {
        public String managerId;
        public String sourceMod;
        public String name;
        public String displayName;
        public String description;
        public List<String> categories = new ArrayList<>();
        public String value;
        public String defaultValue;
        public boolean isDefault;
        public boolean isBoolean;
        public List<String> suggestions = new ArrayList<>();
        public boolean strict;
    }
}
