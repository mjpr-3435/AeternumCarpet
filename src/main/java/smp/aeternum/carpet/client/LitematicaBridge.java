package smp.aeternum.carpet.client;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import smp.aeternum.carpet.AeternumCarpet;
import smp.aeternum.carpet.materials.LitematicParser;
import smp.aeternum.carpet.network.ProjectDownloadDataPayload;

public final class LitematicaBridge {
    private LitematicaBridge() {
    }

    public static Path schematicsRoot() {
        return FabricLoader.getInstance().getGameDir().resolve("schematics");
    }

    public static void handleDownload(ProjectDownloadDataPayload payload) {
        MinecraftClient client = MinecraftClient.getInstance();
        Path targetDir = schematicsRoot().resolve("aeternum");
        Path file = targetDir.resolve(payload.projectName() + ".litematic");
        try {
            Files.createDirectories(targetDir);
            Files.write(file, payload.bytes());
        } catch (IOException e) {
            AeternumCarpet.LOGGER.error("Could not save downloaded schematic {}", file, e);
            notifyPlayer(client, "[Proyectos] Error guardando archivo: " + e.getMessage(), Formatting.RED);
            return;
        }

        if (!payload.loadInLitematica()) {
            notifyPlayer(client, "[Proyectos] Descargado en schematics/aeternum/" + payload.projectName() + ".litematic", Formatting.GREEN);
            return;
        }

        if (!FabricLoader.getInstance().isModLoaded("litematica")) {
            notifyPlayer(client, "[Proyectos] Guardado en schematics/aeternum/" + payload.projectName() + ".litematic (Litematica no instalado).", Formatting.YELLOW);
            return;
        }

        try {
            boolean placed = loadIntoLitematica(file, payload);
            if (placed) {
                notifyPlayer(client, "[Syncmatica] Plano '" + payload.projectName() + "' cargado y alineado en Litematica.", Formatting.GREEN);
            } else {
                notifyPlayer(client, "[Syncmatica] Archivo guardado, pero no se pudo crear el placement automático.", Formatting.YELLOW);
            }
        } catch (Throwable t) {
            AeternumCarpet.LOGGER.warn("Litematica auto-placement failed", t);
            notifyPlayer(client, "[Syncmatica] Guardado en schematics/aeternum/, error al conectar con Litematica: " + t.getMessage(), Formatting.YELLOW);
        }
    }

    private static boolean loadIntoLitematica(Path file, ProjectDownloadDataPayload payload) throws Exception {
        Class<?> holderCls = findClass(
            "fi.dy.masa.litematica.world.SchematicHolder",
            "fi.dy.masa.litematica.data.SchematicHolder",
            "fi.dy.masa.litematica.schematic.SchematicHolder"
        );
        Object holder = holderCls.getMethod("getInstance").invoke(null);
        Object schematic = invokeGetOrLoad(holderCls, holder, file);
        if (schematic == null) return false;

        Class<?> placementCls = Class.forName("fi.dy.masa.litematica.schematic.placement.SchematicPlacement");
        BlockPos origin = payload.hasPlacement()
            ? new BlockPos(payload.x(), payload.y(), payload.z())
            : (MinecraftClient.getInstance().player != null ? MinecraftClient.getInstance().player.getBlockPos() : BlockPos.ORIGIN);

        Object placement = createPlacement(placementCls, schematic, origin, payload.projectName());
        if (placement == null) return false;

        if (payload.hasPlacement()) {
            BlockRotation rotation = LitematicParser.parseRotation(payload.rotation());
            BlockMirror mirror = LitematicParser.parseMirror(payload.mirror());
            Object infoConsumer = resolveInfoConsumer();
            invokeTransform(placementCls, placement, "setRotation", BlockRotation.class, rotation, infoConsumer);
            invokeTransform(placementCls, placement, "setMirror", BlockMirror.class, mirror, infoConsumer);
        }

        Class<?> dataManagerCls = Class.forName("fi.dy.masa.litematica.data.DataManager");
        Object placementManager = dataManagerCls.getMethod("getSchematicPlacementManager").invoke(null);
        Method addMethod = placementManager.getClass().getMethod("addSchematicPlacement", placementCls, boolean.class);
        addMethod.invoke(placementManager, placement, true);
        return true;
    }

    private static Class<?> findClass(String... candidates) throws ClassNotFoundException {
        for (String name : candidates) {
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException ignored) {
            }
        }
        throw new ClassNotFoundException(candidates[0]);
    }

    private static Object invokeGetOrLoad(Class<?> holderCls, Object holder, Path file) throws Exception {
        for (Method m : holderCls.getMethods()) {
            if (!m.getName().equals("getOrLoad") || m.getParameterCount() != 1) continue;
            Class<?> param = m.getParameterTypes()[0];
            if (Path.class.isAssignableFrom(param)) {
                return m.invoke(holder, file);
            }
            if (File.class.isAssignableFrom(param)) {
                return m.invoke(holder, file.toFile());
            }
        }
        return null;
    }

    private static Object createPlacement(Class<?> placementCls, Object schematic, BlockPos origin, String name) throws Exception {
        for (Method m : placementCls.getMethods()) {
            if (!m.getName().equals("createFor")) continue;
            Class<?>[] params = m.getParameterTypes();
            if (params.length == 5 && params[1] == BlockPos.class && params[2] == String.class
                && params[3] == boolean.class && params[4] == boolean.class) {
                return m.invoke(null, schematic, origin, "Aeternum: " + name, true, false);
            }
            if (params.length == 4 && params[1] == BlockPos.class && params[2] == String.class
                && params[3] == boolean.class) {
                return m.invoke(null, schematic, origin, "Aeternum: " + name, true);
            }
        }
        return null;
    }

    private static Object resolveInfoConsumer() {
        try {
            Class<?> infoUtils = Class.forName("fi.dy.masa.malilib.util.InfoUtils");
            return infoUtils.getField("INFO_MESSAGE_CONSUMER").get(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void invokeTransform(Class<?> placementCls, Object placement, String methodName, Class<?> argType, Object argValue, Object consumer) {
        try {
            for (Method m : placementCls.getMethods()) {
                if (!m.getName().equals(methodName)) continue;
                Class<?>[] params = m.getParameterTypes();
                if (params.length == 2 && params[0] == argType) {
                    m.invoke(placement, argValue, consumer);
                    return;
                }
                if (params.length == 1 && params[0] == argType) {
                    m.invoke(placement, argValue);
                    return;
                }
            }
        } catch (Throwable t) {
            AeternumCarpet.LOGGER.debug("Could not invoke {} on SchematicPlacement", methodName, t);
        }
    }

    private static void notifyPlayer(MinecraftClient client, String text, Formatting color) {
        if (client.player != null) {
            client.player.sendMessage(Text.literal(text).formatted(color), false);
        }
    }
}
