package smp.aeternum.carpet.command;

import com.google.gson.Gson;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.command.CommandSource;
import net.minecraft.registry.Registries;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stat;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import smp.aeternum.carpet.AeternumCarpet;
import smp.aeternum.carpet.AeternumCarpetSettings;
import smp.aeternum.carpet.network.StatsNetworking;
import smp.aeternum.carpet.stats.PlayerRegistryStore;
import smp.aeternum.carpet.stats.PlayerRegistryStore.Entry;
import smp.aeternum.carpet.stats.PlayerRegistryStore.EntryType;
import smp.aeternum.carpet.stats.ScoreboardConfigStore;
import smp.aeternum.carpet.stats.ScoreboardConfigStore.ObjectiveConfig;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class StatsCommand {
    private static final Gson GSON = new Gson();
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

        LiteralArgumentBuilder<ServerCommandSource> apiNode = literal("api")
            .requires(source -> source.hasPermissionLevel(2))
            .then(literal("list")
                .executes(ctx -> executeApiList(ctx.getSource(), false))
                .then(argument("include_ae", BoolArgumentType.bool())
                    .executes(ctx -> executeApiList(ctx.getSource(), BoolArgumentType.getBool(ctx, "include_ae")))))
            .then(literal("get")
                .then(argument("objective", StringArgumentType.word())
                    .executes(ctx -> executeApiGet(ctx.getSource(), StringArgumentType.getString(ctx, "objective"), 1, 15))
                    .then(argument("page", IntegerArgumentType.integer(1))
                        .executes(ctx -> executeApiGet(
                            ctx.getSource(),
                            StringArgumentType.getString(ctx, "objective"),
                            IntegerArgumentType.getInteger(ctx, "page"),
                            15))
                        .then(argument("page_size", IntegerArgumentType.integer(1, 25))
                            .executes(ctx -> executeApiGet(
                                ctx.getSource(),
                                StringArgumentType.getString(ctx, "objective"),
                                IntegerArgumentType.getInteger(ctx, "page"),
                                IntegerArgumentType.getInteger(ctx, "page_size")))))));

        LiteralArgumentBuilder<ServerCommandSource> apiStatNode = literal("stat");
        for (String cat : STAT_CATEGORIES) {
            apiStatNode.then(literal(cat)
                .then(argument("stat", StringArgumentType.word())
                    .executes(ctx -> executeApiStat(ctx.getSource(), cat, StringArgumentType.getString(ctx, "stat"), 1, 15))
                    .then(argument("page", IntegerArgumentType.integer(1))
                        .executes(ctx -> executeApiStat(
                            ctx.getSource(),
                            cat,
                            StringArgumentType.getString(ctx, "stat"),
                            IntegerArgumentType.getInteger(ctx, "page"),
                            15))
                        .then(argument("page_size", IntegerArgumentType.integer(1, 25))
                            .executes(ctx -> executeApiStat(
                                ctx.getSource(),
                                cat,
                                StringArgumentType.getString(ctx, "stat"),
                                IntegerArgumentType.getInteger(ctx, "page"),
                                IntegerArgumentType.getInteger(ctx, "page_size")))))));
        }
        apiNode.then(apiStatNode);

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
            .then(scoreboardNode)
            .then(apiNode));
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
        try {
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
                createdNew = true;
            }

            StatsNetworking.syncAeObjective(server, sb, target, criterionRaw);

            sb.setObjectiveSlot(ScoreboardDisplaySlot.SIDEBAR, target);
            String msg = (createdNew ? "Creada y mostrando en sidebar: " : "Mostrando en sidebar: ")
                + displayTitle + " (" + objName + ")";
            source.sendFeedback(() -> Text.literal(msg), false);
            return 1;
        } catch (Throwable t) {
            AeternumCarpet.LOGGER.error("Error in /stats scoreboard {} {}", cat, rawStat, t);
            StackTraceElement[] stack = t.getStackTrace();
            StackTraceElement site = stack.length > 0 ? stack[0] : null;
            for (StackTraceElement el : stack) {
                if (el.getClassName().startsWith("smp.aeternum.carpet.")) {
                    site = el;
                    break;
                }
            }
            String where = site != null ? (site.getClassName() + ":" + site.getLineNumber()) : "unknown";
            source.sendError(Text.literal("Error en /stats scoreboard: " + t.getClass().getSimpleName() + " en " + where));
            return 0;
        }
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

    private static int executeApiList(ServerCommandSource source, boolean includeAe) {
        MinecraftServer server = source.getServer();
        Scoreboard sb = server.getScoreboard();
        List<ApiObjectiveSummary> items = new ArrayList<>();
        for (ScoreboardObjective obj : sb.getObjectives()) {
            boolean isAe = obj.getName().toLowerCase(Locale.ROOT).startsWith("ae-");
            if (!includeAe && isAe) {
                continue;
            }
            ObjectiveConfig cfg = ScoreboardConfigStore.getOrDefault(obj.getName());
            ApiObjectiveSummary s = new ApiObjectiveSummary();
            s.id = obj.getName();
            s.displayName = obj.getDisplayName() != null ? obj.getDisplayName().getString() : obj.getName();
            s.criterion = (cfg.tracker != null && !cfg.tracker.isEmpty())
                ? cfg.tracker
                : (obj.getCriterion() != null ? obj.getCriterion().getName() : "dummy");
            s.totalEnabled = cfg.totalEnabled;
            s.entryCount = sb.getScoreboardEntries(obj).size();
            s.isAe = isAe;
            items.add(s);
        }
        items.sort(Comparator.comparing(a -> a.id, String.CASE_INSENSITIVE_ORDER));
        ApiListResponse resp = new ApiListResponse();
        resp.ok = true;
        resp.scoreboards = items;
        source.sendFeedback(() -> Text.literal(GSON.toJson(resp)), false);
        return 1;
    }

    private static int executeApiGet(ServerCommandSource source, String objectiveName, int page, int pageSize) {
        MinecraftServer server = source.getServer();
        Scoreboard sb = server.getScoreboard();
        ScoreboardObjective target = null;
        for (ScoreboardObjective obj : sb.getObjectives()) {
            if (obj.getName().equals(objectiveName)) {
                target = obj;
                break;
            }
        }
        if (target == null) {
            ApiErrorResponse err = new ApiErrorResponse();
            err.ok = false;
            err.error = "Scoreboard no encontrada: " + objectiveName;
            source.sendFeedback(() -> Text.literal(GSON.toJson(err)), false);
            return 0;
        }
        return sendApiScoreboardPage(source, server, sb, target, page, pageSize);
    }

    private static int executeApiStat(ServerCommandSource source, String cat, String rawStat, int page, int pageSize) {
        String statPath = rawStat == null ? "" : rawStat.trim().toLowerCase(Locale.ROOT);
        if (statPath.startsWith("minecraft:")) {
            statPath = statPath.substring("minecraft:".length());
        }
        if (statPath.isEmpty() || statPath.contains(" ")) {
            ApiErrorResponse err = new ApiErrorResponse();
            err.ok = false;
            err.error = "Stat inválida.";
            source.sendFeedback(() -> Text.literal(GSON.toJson(err)), false);
            return 0;
        }
        String criterionRaw = "minecraft." + cat + ":minecraft." + statPath;
        ScoreboardCriterion criterion = ScoreboardCriterion.getOrCreateStatCriterion(criterionRaw).orElse(null);
        if (criterion == null) {
            ApiErrorResponse err = new ApiErrorResponse();
            err.ok = false;
            err.error = "Stat inválida: " + criterionRaw;
            source.sendFeedback(() -> Text.literal(GSON.toJson(err)), false);
            return 0;
        }

        MinecraftServer server = source.getServer();
        Scoreboard sb = server.getScoreboard();
        String objName = "ae-" + cat + "." + statPath;
        ScoreboardObjective target = null;
        for (ScoreboardObjective obj : sb.getObjectives()) {
            if (obj.getName().equals(objName)) {
                target = obj;
                break;
            }
        }
        if (target == null) {
            String displayTitle = formatStatTitle(cat, statPath);
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
        }
        StatsNetworking.syncAeObjective(server, sb, target, criterionRaw);
        return sendApiScoreboardPage(source, server, sb, target, page, pageSize);
    }

    private static int sendApiScoreboardPage(
        ServerCommandSource source,
        MinecraftServer server,
        Scoreboard sb,
        ScoreboardObjective target,
        int reqPage,
        int reqPageSize
    ) {
        ObjectiveConfig cfg = ScoreboardConfigStore.getOrDefault(target.getName());
        Map<String, EnumSet<EntryType>> typesByName = new HashMap<>();
        for (Entry entry : PlayerRegistryStore.getEntriesSnapshot().values()) {
            if (entry.name != null && !entry.name.isBlank()) {
                typesByName.computeIfAbsent(entry.name.toLowerCase(Locale.ROOT), k -> EnumSet.noneOf(EntryType.class))
                    .add(entry.type != null ? entry.type : EntryType.UNKNOWN);
            }
        }

        List<ApiScoreEntry> filtered = new ArrayList<>();
        long totalSum = 0L;
        for (ScoreboardEntry e : sb.getScoreboardEntries(target)) {
            EnumSet<EntryType> types = typesByName.get(e.owner().toLowerCase(Locale.ROOT));
            boolean include;
            if (types == null || types.isEmpty()) {
                include = cfg.totalUnknown;
            } else {
                boolean hasHuman = types.contains(EntryType.HUMAN);
                boolean hasBot = types.contains(EntryType.BOT);
                if (hasHuman && hasBot) include = cfg.totalHumans || cfg.totalBots;
                else if (hasHuman) include = cfg.totalHumans;
                else if (hasBot) include = cfg.totalBots;
                else include = cfg.totalUnknown;
            }
            if (!include) {
                continue;
            }
            totalSum += e.value();
            ApiScoreEntry row = new ApiScoreEntry();
            row.owner = e.owner();
            row.score = e.value();
            filtered.add(row);
        }
        filtered.sort(Comparator.comparingInt((ApiScoreEntry a) -> a.score).reversed()
            .thenComparing(a -> a.owner, String.CASE_INSENSITIVE_ORDER));

        int pageSize = Math.max(1, Math.min(25, reqPageSize));
        int maxPage = Math.max(1, (filtered.size() + pageSize - 1) / pageSize);
        int page = Math.max(1, Math.min(maxPage, reqPage));
        int fromIdx = (page - 1) * pageSize;
        int toIdx = Math.min(filtered.size(), fromIdx + pageSize);
        List<ApiScoreEntry> pageItems = new ArrayList<>(Math.max(0, toIdx - fromIdx));
        for (int i = fromIdx; i < toIdx; i++) {
            ApiScoreEntry row = filtered.get(i);
            row.rank = i + 1;
            pageItems.add(row);
        }

        ApiScoreboardPageResponse resp = new ApiScoreboardPageResponse();
        resp.ok = true;
        resp.id = target.getName();
        resp.displayName = target.getDisplayName() != null ? target.getDisplayName().getString() : target.getName();
        resp.criterion = (cfg.tracker != null && !cfg.tracker.isEmpty())
            ? cfg.tracker
            : (target.getCriterion() != null ? target.getCriterion().getName() : "dummy");
        resp.totalEnabled = cfg.totalEnabled;
        resp.total = totalSum;
        resp.totalEntries = filtered.size();
        resp.page = page;
        resp.maxPage = maxPage;
        resp.entries = pageItems;
        source.sendFeedback(() -> Text.literal(GSON.toJson(resp)), false);
        return 1;
    }

    private static final class ApiErrorResponse {
        boolean ok;
        String error;
    }

    private static final class ApiObjectiveSummary {
        String id;
        String displayName;
        String criterion;
        boolean totalEnabled;
        int entryCount;
        boolean isAe;
    }

    private static final class ApiListResponse {
        boolean ok;
        List<ApiObjectiveSummary> scoreboards;
    }

    private static final class ApiScoreEntry {
        int rank;
        String owner;
        int score;
    }

    private static final class ApiScoreboardPageResponse {
        boolean ok;
        String id;
        String displayName;
        String criterion;
        boolean totalEnabled;
        long total;
        int totalEntries;
        int page;
        int maxPage;
        List<ApiScoreEntry> entries;
    }
}
