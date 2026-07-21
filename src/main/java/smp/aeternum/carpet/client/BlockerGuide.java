package smp.aeternum.carpet.client;

import java.util.List;
import java.util.HashSet;
import java.util.Set;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.debug.DebugRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import smp.aeternum.carpet.blockers.BlockerEntry;
import smp.aeternum.carpet.blockers.BlockerScan;

/** Selected target. World-line rendering is intentionally client-only. */
public final class BlockerGuide {
    private static List<BlockerEntry> targets = List.of();
    private static final Gson GSON = new Gson();
    private static final Type SCANS = new TypeToken<List<BlockerScan>>() {}.getType();

    private BlockerGuide() {
    }

    public static void select(List<BlockerEntry> entries) { targets = List.copyOf(entries); }
    public static void clear() { targets = List.of(); }
    public static List<BlockerEntry> targets() { return targets; }

    /** Server snapshots are authoritative: a broken/resolved entry immediately loses its local guide. */
    public static void reconcile(String json) {
        List<BlockerScan> scans = GSON.fromJson(json, SCANS);
        if (scans == null || targets.isEmpty()) return;
        Set<String> existing = new HashSet<>();
        for (BlockerScan scan : scans) for (BlockerEntry entry : scan.entries) {
            existing.add(entry.x() + ":" + entry.y() + ":" + entry.z() + ":" + entry.blockId());
        }
        targets = targets.stream().filter(entry -> existing.contains(entry.x() + ":" + entry.y() + ":" + entry.z() + ":" + entry.blockId())).toList();
    }

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (targets.isEmpty() || context.matrixStack() == null || context.consumers() == null) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;
            MatrixStack matrices = context.matrixStack();
            double cameraX = context.camera().getPos().x;
            double cameraY = context.camera().getPos().y;
            double cameraZ = context.camera().getPos().z;
            for (BlockerEntry entry : targets) {
                // A pure selection outline, like a Litematica selection: no particles, no texture replacement.
                double x = entry.x() - cameraX;
                double y = entry.y() - cameraY;
                double z = entry.z() - cameraZ;
                DebugRenderer.drawBox(matrices, context.consumers(),
                    x - 0.002, y - 0.002, z - 0.002,
                    x + 1.002, y + 1.002, z + 1.002,
                    0.72f, 0.32f, 1.0f, 0.22f);
            }
        });
    }
}
