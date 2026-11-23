package smp.aeternum.carpet.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import net.minecraft.command.CommandSource;
import net.minecraft.registry.Registries;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stat;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import smp.aeternum.carpet.AeternumCarpetSettings;
import smp.aeternum.carpet.network.StatsNetworking;
import smp.aeternum.carpet.stats.ScoreboardConfigStore;
import smp.aeternum.carpet.stats.ScoreboardConfigStore.ObjectiveConfig;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class StatsCommand {
    private static final List<String> STAT_CATEGORIES = List.of(
        "mined", "used", "crafted", "broken", "picked_up", "dropped", "killed", "killed_by", "custom"
    );

    private StatsCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        LiteralArgumentBuilder<ServerCommandSource> scoreboardNode = literal("scoreboard")
            .then(literal("hide").executes(context -> {
                MinecraftServer server = context.getSource().getServer();
                server.getScoreboard().setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, null);
                context.getSource().sendFeedback(() -> Text.literal("Scoreboard del sidebar ocultada."), false);
                return 1;
            }));

        for (String cat : STAT_CATEGORIES) {
            scoreboardNode.then(literal(cat)
                .then(argument("stat", StringArgumentType.word())
                    .suggests((context, builder) -> suggestStats(cat, builder))
                    .executes(context -> executeQuickScoreboard(
                        context.getSource(),
                        cat,
                        StringArgumentType.getString(context, "stat")
                    ))));
        }

        dispatcher.register(literal("stats")
            .requires(source -> AeternumCarpetSettings.commandStats)
            .executes(context -> {
                if (!context.getSource().hasPermissionLevel(2)) {
                    context.getSource().sendError(Text.literal("Solo administradores pueden abrir el panel /stats. Usa /stats scoreboard <categoría> <stat>."));
                    return 0;
                }
                if (context.getSource().getEntity() instanceof ServerPlayerEntity player) {
                    StatsNetworking.sendSnapshot(player, true);
                } else {
                    context.getSource().sendFeedback(() -> Text.literal("Este comando abre la interfaz solo para jugadores."), false);
                }
                return 1;
            })
            .then(scoreboardNode));
    }

    private static CompletableFuture<Suggestions> suggestStats(String cat, SuggestionsBuilder builder) {
        return switch (cat) {
            case "mined" -> CommandSource.suggestMatching(Registries.BLOCK.getIds().stream().map(Identifier::getPath), builder);
            case "used", "crafted", "broken", "picked_up", "dropped" ->
                CommandSource.suggestMatching(Registries.ITEM.getIds().stream().map(Identifier::getPath), builder);
            case "killed", "killed_by" ->
                CommandSource.suggestMatching(Registries.ENTITY_TYPE.getIds().stream().map(Identifier::getPath), builder);
            case "custom" ->
                CommandSource.suggestMatching(Registries.CUSTOM_STAT.stream().map(Identifier::getPath), builder);
            default -> builder.buildFuture();
        };
    }

    private static int executeQuickScoreboard(ServerCommandSource source, String cat, String rawStat) {
        String statPath = rawStat == null ? "" : rawStat.trim().toLowerCase(Locale.ROOT);
        if (statPath.startsWith("minecraft:")) {
            statPath = statPath.substring("minecraft:".length());
        }
        if (statPath.isEmpty() || statPath.contains(" ")) {
            source.sendError(Text.literal("Stat inválida."));
            return 0;
        }
        String criterionRaw = "minecraft." + cat + ":minecraft." + statPath;
        ScoreboardCriterion criterion = ScoreboardCriterion.getOrCreateStatCriterion(criterionRaw).orElse(null);
        if (criterion == null) {
            source.sendError(Text.literal("Stat inválida: " + criterionRaw));
            return 0;
        }

        MinecraftServer server = source.getServer();
        Scoreboard sb = server.getScoreboard();
        String objName = "ae-" + cat + "." + statPath;
        ScoreboardObjective existing = null;
        for (ScoreboardObjective obj : sb.getObjectives()) {
            if (obj.getName().equals(objName)) {
                existing = obj;
                break;
            }
        }

        String displayTitle = formatStatTitle(cat, statPath);
        boolean createdNew = false;
        ScoreboardObjective target = existing;
        if (target == null) {
            target = sb.addObjective(
                objName,
                criterion,
                Text.literal(displayTitle),
                criterion.getDefaultRenderType(),
                false,
                null
            );
            ObjectiveConfig cfg = new ObjectiveConfig();
            cfg.totalEnabled = false;
            cfg.totalHumans = true;
            cfg.totalBots = false;
            cfg.totalUnknown = false;
            cfg.displayNameMarkup = displayTitle;
            ScoreboardConfigStore.put(objName, cfg);
            if (criterion instanceof Stat<?> stat) {
                StatsNetworking.populateObjectiveFromStats(server, sb, target, stat, criterionRaw, true);
            }
            createdNew = true;
        }

        sb.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, target);
        String msg = (createdNew ? "Creada y mostrando en sidebar: " : "Mostrando en sidebar: ")
            + displayTitle + " (" + objName + ")";
        source.sendFeedback(() -> Text.literal(msg), false);
        return 1;
    }

    private static String formatStatTitle(String cat, String statPath) {
        String catLabel = capitalizeWords(cat.replace('_', ' '));
        String statLabel = capitalizeWords(statPath.replace('_', ' '));
        return catLabel + ": " + statLabel;
    }

    private static String capitalizeWords(String raw) {
        String[] parts = raw.split("\\s+");
        StringBuilder out = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return out.toString();
    }
}
