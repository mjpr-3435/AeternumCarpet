package smp.aeternum.carpet.client;

import java.util.List;
import java.util.HashSet;
import java.util.Set;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import smp.aeternum.carpet.AeternumCarpet;
import smp.aeternum.carpet.blockers.BlockerEntry;
import smp.aeternum.carpet.blockers.BlockerScan;

/** Selected target. World-line rendering is intentionally client-only. */
public final class BlockerGuide {
    /*
     * A normal debug box has a depth test, so terrain hides it. These pipelines deliberately
     * have no depth buffer: the selection behaves like a Litematica ghost overlay, not like
     * a recoloured world block.
     */
    private static final RenderLayer THROUGH_WALLS_FILL = RenderLayer.of(
        "aeternum_blockers_fill_through_walls", RenderLayer.DEFAULT_BUFFER_SIZE, false, true,
        RenderPipelines.register(RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(AeternumCarpet.id("blockers_fill_through_walls"))
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLE_STRIP)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .build()),
        RenderLayer.MultiPhaseParameters.builder().build(false));
    private static final RenderLayer THROUGH_WALLS_OUTLINE = RenderLayer.of(
        "aeternum_blockers_outline_through_walls", RenderLayer.DEFAULT_BUFFER_SIZE, false, true,
        RenderPipelines.register(RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
            .withLocation(AeternumCarpet.id("blockers_outline_through_walls"))
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .build()),
        RenderLayer.MultiPhaseParameters.builder().build(false));
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
            VertexConsumer fill = context.consumers().getBuffer(THROUGH_WALLS_FILL);
            VertexConsumer outline = context.consumers().getBuffer(THROUGH_WALLS_OUTLINE);
            for (BlockerEntry entry : targets) {
                double x = entry.x() - cameraX;
                double y = entry.y() - cameraY;
                double z = entry.z() - cameraZ;
                // Abstract translucent prism plus a crisp outline; neither samples nor replaces the block texture.
                VertexRendering.drawFilledBox(matrices, fill,
                    x + 0.003, y + 0.003, z + 0.003,
                    x + 0.997, y + 0.997, z + 0.997,
                    0.55f, 0.20f, 0.92f, 0.22f);
                VertexRendering.drawBox(matrices, outline,
                    x - 0.002, y - 0.002, z - 0.002,
                    x + 1.002, y + 1.002, z + 1.002,
                    0.78f, 0.34f, 1.0f, 0.95f);
            }
        });
    }
}
