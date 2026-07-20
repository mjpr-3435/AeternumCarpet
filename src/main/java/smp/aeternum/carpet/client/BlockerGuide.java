package smp.aeternum.carpet.client;

import java.util.List;
import java.util.HashSet;
import java.util.Set;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
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
            VertexConsumer vertices = context.consumers().getBuffer(RenderLayer.getLines());
            double cameraX = context.camera().getPos().x;
            double cameraY = context.camera().getPos().y;
            double cameraZ = context.camera().getPos().z;
            for (BlockerEntry entry : targets) {
                Vec3d end = Vec3d.ofCenter(entry.pos());
                float dx = (float) (end.x - cameraX), dy = (float) (end.y - cameraY), dz = (float) (end.z - cameraZ);
                float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (distance < 0.001f) continue;
                float limit = Math.min(distance, 96.0f);
                float scale = limit / distance;
                // Do not start inside the near clipping plane; keep the route segment inside render distance.
                float start = Math.min(1.0f, limit * 0.25f) / distance;
                line(matrices, vertices, dx * start, dy * start, dz * start, dx * scale, dy * scale, dz * scale);
            }
        });
    }

    private static void line(MatrixStack matrices, VertexConsumer vertices, float x1, float y1, float z1, float x2, float y2, float z2) {
        MatrixStack.Entry entry = matrices.peek();
        float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 0.0001f) return;
        dx /= length; dy /= length; dz /= length;
        vertices.vertex(entry.getPositionMatrix(), x1, y1, z1).color(184, 82, 255, 255).normal(entry, dx, dy, dz);
        vertices.vertex(entry.getPositionMatrix(), x2, y2, z2).color(184, 82, 255, 255).normal(entry, dx, dy, dz);
    }
}
