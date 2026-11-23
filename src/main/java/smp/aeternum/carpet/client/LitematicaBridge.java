package smp.aeternum.carpet.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
    private static final Gson GSON = new Gson();
    private static final Type SUBREGION_LIST_TYPE = new TypeToken<List<SubRegionState>>() {}.getType();

    public record SubRegionState(
        String name,
        boolean enabled,
        boolean renderingEnabled,
        int x,
        int y,
        int z,
        String rotation,
        String mirror
    ) {}

    public record ActivePlacement(
        String name,
        Path schematicFile,
        BlockPos origin,
        String rotation,
        String mirror,
        String dimension,
        List<SubRegionState> subregions
    ) {}

    private LitematicaBridge() {
    }

    public static boolean isLitematicaLoaded() {
        return FabricLoader.getInstance().isModLoaded("litematica");
    }

    public static Path schematicsRoot() {
        return FabricLoader.getInstance().getGameDir().resolve("schematics");
    }

    public static String serializeSubregions(List<SubRegionState> list) {
        return GSON.toJson(list != null ? list : List.of());
    }

    public static List<SubRegionState> parseSubregions(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            List<SubRegionState> out = GSON.fromJson(json, SUBREGION_LIST_TYPE);
            return out != null ? out : List.of();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    public static List<ActivePlacement> getActivePlacements() {
        List<ActivePlacement> result = new ArrayList<>();
        if (!isLitematicaLoaded()) return result;

        MinecraftClient client = MinecraftClient.getInstance();
        String fallbackDim = client.world != null ? client.world.getRegistryKey().getValue().toString() : "minecraft:overworld";

        try {
            Class<?> dataManagerCls = Class.forName("fi.dy.masa.litematica.data.DataManager");
            Object placementManager = dataManagerCls.getMethod("getSchematicPlacementManager").invoke(null);
            Collection<?> placements = invokeGetAllPlacements(placementManager);
            if (placements == null) return result;

            for (Object p : placements) {
                if (p == null) continue;
                Path file = extractSchematicPath(p);
                if (file == null || !Files.isRegularFile(file)) continue;

                String name = invokeString(p, "getName");
                if (name == null || name.isBlank()) name = file.getFileName().toString();

                BlockPos origin = invokeTyped(p, "getOrigin", BlockPos.class);
                if (origin == null) origin = BlockPos.ORIGIN;

                BlockRotation rot = invokeTyped(p, "getRotation", BlockRotation.class);
                BlockMirror mirror = invokeTyped(p, "getMirror", BlockMirror.class);
                List<SubRegionState> subregions = readSubregions(p);

                result.add(new ActivePlacement(
                    name,
                    file,
                    origin,
                    rot != null ? rot.name() : "NONE",
                    mirror != null ? mirror.name() : "NONE",
                    fallbackDim,
                    subregions
                ));
            }
        } catch (Throwable t) {
            AeternumCarpet.LOGGER.warn("Could not read active Litematica placements", t);
        }
        return result;
    }

    private static List<SubRegionState> readSubregions(Object placement) {
        List<SubRegionState> out = new ArrayList<>();
        try {
            Object raw = placement.getClass().getMethod("getAllSubRegionsPlacements").invoke(placement);
            if (!(raw instanceof Collection<?> subs)) return out;
            for (Object sub : subs) {
                if (sub == null) continue;
                String sName = invokeString(sub, "getName");
                if (sName == null || sName.isBlank()) continue;
                Boolean enabledObj = invokeTyped(sub, "isEnabled", Boolean.class);
                Boolean renderObj = invokeTyped(sub, "isRenderingEnabled", Boolean.class);
                BlockPos pos = invokeTyped(sub, "getPos", BlockPos.class);
                BlockRotation rot = invokeTyped(sub, "getRotation", BlockRotation.class);
                BlockMirror mir = invokeTyped(sub, "getMirror", BlockMirror.class);
                out.add(new SubRegionState(
                    sName,
                    enabledObj == null || enabledObj,
                    renderObj == null || renderObj,
                    pos != null ? pos.getX() : 0,
                    pos != null ? pos.getY() : 0,
                    pos != null ? pos.getZ() : 0,
                    rot != null ? rot.name() : "NONE",
                    mir != null ? mir.name() : "NONE"
                ));
            }
        } catch (Throwable t) {
            AeternumCarpet.LOGGER.warn("Could not read Litematica subregions", t);
        }
        return out;
    }

    private static Collection<?> invokeGetAllPlacements(Object placementManager) throws Exception {
        for (String name : new String[]{"getAllSchematicsPlacements", "getAllSchematicPlacements"}) {
            try {
                Object res = placementManager.getClass().getMethod(name).invoke(placementManager);
                if (res instanceof Collection<?> col) return col;
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    private static Path extractSchematicPath(Object placement) {
        Path direct = invokeAsPath(placement, "getSchematicFile");
        if (direct != null) return direct;
        try {
            Object schematic = placement.getClass().getMethod("getSchematic").invoke(placement);
            if (schematic != null) {
                return invokeAsPath(schematic, "getFile");
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Path invokeAsPath(Object target, String methodName) {
        try {
            Object res = target.getClass().getMethod(methodName).invoke(target);
            if (res instanceof Path p) return p;
            if (res instanceof File f) return f.toPath();
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String invokeString(Object target, String methodName) {
        try {
            Object res = target.getClass().getMethod(methodName).invoke(target);
            return res != null ? res.toString() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static <T> T invokeTyped(Object target, String methodName, Class<T> type) {
        try {
            Object res = target.getClass().getMethod(methodName).invoke(target);
            if (type.isInstance(res)) return type.cast(res);
        } catch (Throwable ignored) {
        }
        return null;
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

        if (!isLitematicaLoaded()) {
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

        Class<?> dataManagerCls = Class.forName("fi.dy.masa.litematica.data.DataManager");
        Object placementManager = dataManagerCls.getMethod("getSchematicPlacementManager").invoke(null);
        Method addMethod = placementManager.getClass().getMethod("addSchematicPlacement", placementCls, boolean.class);
        addMethod.invoke(placementManager, placement, true);

        if (payload.hasPlacement()) {
            Object msgConsumer = createNoOpProxy("fi.dy.masa.malilib.gui.interfaces.IMessageConsumer");
            BlockRotation rotation = LitematicParser.parseRotation(payload.rotation());
            BlockMirror mirror = LitematicParser.parseMirror(payload.mirror());
            invokeTransform(placementCls, placement, "setRotation", BlockRotation.class, rotation, msgConsumer);
            invokeTransform(placementCls, placement, "setMirror", BlockMirror.class, mirror, msgConsumer);
            List<SubRegionState> subs = parseSubregions(payload.subregionsJson());
            int applied = applySubregions(placementCls, placementManager, placement, subs, msgConsumer);
            AeternumCarpet.LOGGER.info("LitematicaBridge loaded '{}' rot={} mirror={} subregions={} appliedChanges={}",
                payload.projectName(), rotation, mirror, subs.size(), applied);
        }

        try {
            placementManager.getClass().getMethod("markAllPlacementsOfSchematicForRebuild", schematic.getClass())
                .invoke(placementManager, schematic);
        } catch (Throwable ignored) {
        }
        return true;
    }

    private static int applySubregions(
        Class<?> placementCls,
        Object placementManager,
        Object placement,
        List<SubRegionState> subregions,
        Object msgConsumer
    ) {
        if (subregions == null || subregions.isEmpty()) return 0;
        int changes = 0;
        for (SubRegionState sub : subregions) {
            if (sub == null || sub.name() == null || sub.name().isBlank()) continue;
            try {
                Object current = placementCls.getMethod("getRelativeSubRegionPlacement", String.class)
                    .invoke(placement, sub.name());
                if (current == null) {
                    AeternumCarpet.LOGGER.warn("Subregion '{}' not found on SchematicPlacement", sub.name());
                    continue;
                }

                BlockPos curPos = invokeTyped(current, "getPos", BlockPos.class);
                BlockPos targetPos = new BlockPos(sub.x(), sub.y(), sub.z());
                if (curPos == null || !curPos.equals(targetPos)) {
                    setSubRegionRelativePos(placementCls, placementManager, placement, current, sub.name(), targetPos);
                    changes++;
                }

                BlockRotation targetRot = LitematicParser.parseRotation(sub.rotation());
                BlockRotation curRot = invokeTyped(current, "getRotation", BlockRotation.class);
                if (curRot != targetRot) {
                    invokeSubregionMethod(placementCls, placement, "setSubRegionRotation", String.class, sub.name(), BlockRotation.class, targetRot, msgConsumer);
                    changes++;
                }

                BlockMirror targetMirror = LitematicParser.parseMirror(sub.mirror());
                BlockMirror curMirror = invokeTyped(current, "getMirror", BlockMirror.class);
                if (curMirror != targetMirror) {
                    invokeSubregionMethod(placementCls, placement, "setSubRegionMirror", String.class, sub.name(), BlockMirror.class, targetMirror, msgConsumer);
                    changes++;
                }

                Boolean curEnabled = invokeTyped(current, "isEnabled", Boolean.class);
                if (curEnabled != null && curEnabled != sub.enabled()) {
                    invokeSubregionToggle(placementCls, placement, "toggleSubRegionEnabled", sub.name(), msgConsumer);
                    changes++;
                }

                Boolean curRender = invokeTyped(current, "isRenderingEnabled", Boolean.class);
                if (curRender != null && curRender != sub.renderingEnabled()) {
                    placementCls.getMethod("toggleSubRegionRenderingEnabled", String.class).invoke(placement, sub.name());
                    changes++;
                }
            } catch (Throwable t) {
                AeternumCarpet.LOGGER.warn("Could not restore subregion {} on SchematicPlacement", sub.name(), t);
            }
        }
        return changes;
    }

    private static void setSubRegionRelativePos(
        Class<?> placementCls,
        Object placementManager,
        Object placement,
        Object subPlacement,
        String subName,
        BlockPos targetPos
    ) throws Exception {
        Method onPre = placementManager.getClass().getDeclaredMethod("onPrePlacementChange", placementCls);
        onPre.setAccessible(true);
        onPre.invoke(placementManager, placement);
        Method setPos = subPlacement.getClass().getDeclaredMethod("setPos", BlockPos.class);
        setPos.setAccessible(true);
        setPos.invoke(subPlacement, targetPos);
        Method onModified = placementCls.getDeclaredMethod("onModified", String.class, placementManager.getClass());
        onModified.setAccessible(true);
        onModified.invoke(placement, subName, placementManager);
    }

    private static void invokeSubregionMethod(
        Class<?> placementCls,
        Object placement,
        String methodName,
        Class<?> arg1Type,
        Object arg1,
        Class<?> arg2Type,
        Object arg2,
        Object consumer
    ) throws Exception {
        for (Method m : placementCls.getMethods()) {
            if (!m.getName().equals(methodName)) continue;
            Class<?>[] params = m.getParameterTypes();
            if (params.length == 3 && params[0] == arg1Type && params[1] == arg2Type) {
                m.invoke(placement, arg1, arg2, consumer);
                return;
            }
        }
    }

    private static void invokeSubregionToggle(
        Class<?> placementCls,
        Object placement,
        String methodName,
        String name,
        Object msgConsumer
    ) throws Exception {
        for (Method m : placementCls.getMethods()) {
            if (!m.getName().equals(methodName)) continue;
            Class<?>[] params = m.getParameterTypes();
            if (params.length == 2 && params[0] == String.class) {
                m.invoke(placement, name, msgConsumer);
                return;
            }
        }
    }

    private static Object createNoOpProxy(String interfaceName) {
        try {
            Class<?> iface = Class.forName(interfaceName);
            return Proxy.newProxyInstance(
                iface.getClassLoader(),
                new Class<?>[]{iface},
                (proxy, method, args) -> null
            );
        } catch (Throwable ignored) {
            return null;
        }
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
                return m.invoke(null, schematic, origin, "Aeternum: " + name, true, true);
            }
            if (params.length == 4 && params[1] == BlockPos.class && params[2] == String.class
                && params[3] == boolean.class) {
                return m.invoke(null, schematic, origin, "Aeternum: " + name, true);
            }
        }
        return null;
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
            AeternumCarpet.LOGGER.warn("Could not invoke {} on SchematicPlacement", methodName, t);
        }
    }

    private static void notifyPlayer(MinecraftClient client, String text, Formatting color) {
        if (client.player != null) {
            client.player.sendMessage(Text.literal(text).formatted(color), false);
        }
    }
}
