package smp.aeternum.carpet.network;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import smp.aeternum.carpet.AeternumCarpetSettings;
import smp.aeternum.carpet.motd.JoinMotd;
import smp.aeternum.carpet.motd.MotdTaskStore;
import smp.aeternum.carpet.todo.TodoListStore;

public final class ScreenOpenNetworking {
    private static final Gson GSON = new Gson();
    private static final Type TASK_LIST_TYPE = new TypeToken<List<String>>() {}.getType();

    private ScreenOpenNetworking() {}

    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(DockStatePayload.ID, DockStatePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ScreenOpenRequestPayload.ID, ScreenOpenRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MotdTasksSavePayload.ID, MotdTasksSavePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(TodoActionPayload.ID, TodoActionPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ScreenOpenRequestPayload.ID, (payload, context) -> context.server().execute(() -> {
            ServerPlayerEntity player = context.player();
            switch (payload.screen()) {
                case "dock" -> sendDockState(player);
                case "blockers" -> { if (AeternumCarpetSettings.commandBlockers) BlockerNetworking.sendSnapshot(player, true); }
                case "guide" -> { if (AeternumCarpetSettings.commandGuide) GuideNetworking.send(player, true); }
                case "materials", "projects" -> { if (AeternumCarpetSettings.commandProjects) MaterialsNetworking.send(player); }
                case "exploration" -> { if (AeternumCarpetSettings.explorationManager) ExplorationMapNetworking.open(player); }
                case "actions" -> { if (AeternumCarpetSettings.commandActions) CommandPacksNetworking.sendSnapshot(player, true); }
                case "rules" -> { if (player.hasPermissionLevel(2)) CarpetRulesNetworking.sendSnapshot(player, true); }
            }
        }));
        ServerPlayNetworking.registerGlobalReceiver(MotdTasksSavePayload.ID, (payload, context) -> context.server().execute(() -> {
            ServerPlayerEntity player = context.player();
            if (!player.hasPermissionLevel(2)) return;
            try {
                List<String> next = GSON.fromJson(payload.json(), TASK_LIST_TYPE);
                MotdTaskStore.replaceAll(next);
                broadcastDockState(context.server());
            } catch (Exception ignored) {
            }
        }));
        ServerPlayNetworking.registerGlobalReceiver(TodoActionPayload.ID, (payload, context) -> context.server().execute(() -> {
            ServerPlayerEntity player = context.player();
            if (!AeternumCarpetSettings.todoList) return;
            switch (payload.action()) {
                case "add" -> {
                    String note = payload.text() == null ? "" : payload.text().strip();
                    if (!note.isEmpty()) {
                        if (note.length() > 140) note = note.substring(0, 140);
                        String capitalized = Character.toUpperCase(note.charAt(0)) + note.substring(1);
                        TodoListStore.addNote(player.getUuid(), capitalized);
                    }
                    sendDockState(player);
                }
                case "delete" -> {
                    TodoListStore.removeNote(player.getUuid(), payload.index());
                    sendDockState(player);
                }
            }
        }));
    }

    public static void sendDockState(ServerPlayerEntity player) {
        sendDockState(player, false);
    }

    public static void sendDockState(ServerPlayerEntity player, boolean showJoinBanner) {
        if (!ServerPlayNetworking.canSend(player, DockStatePayload.ID)) return;
        DockState state = new DockState();
        state.activeTime = JoinMotd.activeTime();
        state.tasks = MotdTaskStore.all();
        state.todos = AeternumCarpetSettings.todoList ? TodoListStore.getNotes(player.getUuid()) : List.of();
        state.isOp = player.hasPermissionLevel(2);
        state.joinMotd = AeternumCarpetSettings.joinMotd;
        state.quickDock = AeternumCarpetSettings.quickDock;
        state.todoList = AeternumCarpetSettings.todoList;
        state.dimension = AeternumCarpetSettings.commandDimension;
        state.showJoinBanner = showJoinBanner && AeternumCarpetSettings.joinMotd;
        state.projects = AeternumCarpetSettings.commandProjects;
        state.guide = AeternumCarpetSettings.commandGuide;
        state.actions = AeternumCarpetSettings.commandActions;
        state.blockers = AeternumCarpetSettings.commandBlockers;
        state.exploration = AeternumCarpetSettings.explorationManager;
        ServerPlayNetworking.send(player, new DockStatePayload(GSON.toJson(state)));
    }

    public static void broadcastDockState(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            sendDockState(player, false);
        }
    }

    public static final class DockState {
        public String activeTime = "";
        public List<String> tasks = List.of();
        public List<String> todos = List.of();
        public boolean isOp;
        public boolean joinMotd;
        public boolean quickDock;
        public boolean todoList;
        public boolean dimension;
        public boolean showJoinBanner;
        public boolean projects;
        public boolean guide;
        public boolean actions;
        public boolean blockers;
        public boolean exploration;
    }
}
