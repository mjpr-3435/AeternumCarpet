package smp.aeternum.carpet.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import smp.aeternum.carpet.AeternumCarpet;
import smp.aeternum.carpet.perimeters.BlockCluster;
import smp.aeternum.carpet.perimeters.Perimeter;

/** Renders 3D bounding boxes through walls for selected perimeter clusters. */
public final class PerimeterGuide {
    private static final RenderLayer THROUGH_WALLS_FILL = RenderLayer.of(
        "aeternum_perimeters_fill_through_walls", RenderLayer.DEFAULT_BUFFER_SIZE, false, true,
        RenderPipelines.register(RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(AeternumCarpet.id("perimeters_fill_through_walls"))
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLE_STRIP)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .build()),
        RenderLayer.MultiPhaseParameters.builder().build(false));
    private static final RenderLayer THROUGH_WALLS_OUTLINE = RenderLayer.of(
        "aeternum_perimeters_outline_through_walls", RenderLayer.DEFAULT_BUFFER_SIZE, false, true,
        RenderPipelines.register(RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
            .withLocation(AeternumCarpet.id("perimeters_outline_through_walls"))
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .build()),
        RenderLayer.MultiPhaseParameters.builder().build(false));

    private static final Map<String, BlockCluster> SELECTED = new LinkedHashMap<>();

    private PerimeterGuide() {}

    public static void selectAll(List<BlockCluster> clusters) {
        SELECTED.clear();
        for (BlockCluster c : clusters) {
            SELECTED.put(c.key(), c);
        }
    }

    public static void toggle(BlockCluster c) {
        String k = c.key();
        if (SELECTED.containsKey(k)) {
            SELECTED.remove(k);
        } else {
            SELECTED.put(k, c);
        }
    }

    public static boolean isSelected(BlockCluster c) {
        return SELECTED.containsKey(c.key());
    }

    public static void clear() {
        SELECTED.clear();
    }

    public static int count() {
        return SELECTED.size();
    }

    public static void reconcile(List<Perimeter> perimeters) {
        if (SELECTED.isEmpty() || perimeters == null) return;
        Set<String> valid = new HashSet<>();
        Map<String, BlockCluster> updated = new LinkedHashMap<>();
        for (Perimeter p : perimeters) {
            if (p.clusters == null) continue;
            for (BlockCluster c : p.clusters) {
                valid.add(c.key());
                if (SELECTED.containsKey(c.key())) {
                    updated.put(c.key(), c);
                }
            }
        }
        SELECTED.keySet().retainAll(valid);
        SELECTED.putAll(updated);
    }

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (SELECTED.isEmpty() || context.matrixStack() == null || context.consumers() == null) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;
            MatrixStack matrices = context.matrixStack();
            double camX = context.camera().getPos().x;
            double camY = context.camera().getPos().y;
            double camZ = context.camera().getPos().z;
            List<BlockCluster> list = new ArrayList<>(SELECTED.values());

            VertexConsumer fill = context.consumers().getBuffer(THROUGH_WALLS_FILL);
            for (BlockCluster c : list) {
                double x1 = c.minX - camX;
                double y1 = c.minY - camY;
                double z1 = c.minZ - camZ;
                double x2 = c.maxX + 1 - camX;
                double y2 = c.maxY + 1 - camY;
                double z2 = c.maxZ + 1 - camZ;

                boolean isLava = "minecraft:lava".equals(c.blockId);
                float r = isLava ? 0.96f : 0.62f;
                float g = isLava ? 0.42f : 0.28f;
                float b = isLava ? 0.14f : 0.96f;

                VertexRendering.drawFilledBox(matrices, fill,
                    x1 + 0.004, y1 + 0.004, z1 + 0.004,
                    x2 - 0.004, y2 - 0.004, z2 - 0.004,
                    r, g, b, 0.20f);
            }

            VertexConsumer outline = context.consumers().getBuffer(THROUGH_WALLS_OUTLINE);
            for (BlockCluster c : list) {
                double x1 = c.minX - camX;
                double y1 = c.minY - camY;
                double z1 = c.minZ - camZ;
                double x2 = c.maxX + 1 - camX;
                double y2 = c.maxY + 1 - camY;
                double z2 = c.maxZ + 1 - camZ;

                boolean isLava = "minecraft:lava".equals(c.blockId);
                float r = isLava ? 0.96f : 0.62f;
                float g = isLava ? 0.42f : 0.28f;
                float b = isLava ? 0.14f : 0.96f;

                VertexRendering.drawBox(matrices, outline,
                    x1 - 0.004, y1 - 0.004, z1 - 0.004,
                    x2 + 0.004, y2 + 0.004, z2 + 0.004,
                    Math.min(1f, r + 0.18f), Math.min(1f, g + 0.18f), Math.min(1f, b + 0.18f), 0.95f);
            }
        });
    }
}
