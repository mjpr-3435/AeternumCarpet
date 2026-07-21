package smp.aeternum.carpet.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import smp.aeternum.carpet.blockers.BlockerEntry;
import smp.aeternum.carpet.blockers.BlockerScan;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Per-client, per-scan selection. It intentionally survives closing the GUI. */
final class BlockerSelectionStore {
    private static final Gson GSON = new Gson();
    private static final Type SCANS = new TypeToken<java.util.List<BlockerScan>>() {}.getType();
    private static final Map<String, Set<String>> SELECTED = new LinkedHashMap<>();

    private BlockerSelectionStore() {}

    static boolean isSelected(String scanId, BlockerEntry entry) { return SELECTED.getOrDefault(scanId, Set.of()).contains(key(entry)); }
    static void toggle(String scanId, BlockerEntry entry) {
        Set<String> selected = SELECTED.computeIfAbsent(scanId, ignored -> new LinkedHashSet<>());
        if (!selected.add(key(entry))) selected.remove(key(entry));
        if (selected.isEmpty()) SELECTED.remove(scanId);
    }
    static void clear(String scanId) { SELECTED.remove(scanId); }
    static void clearAll() { SELECTED.clear(); }
    /** A server snapshot is authoritative: selections for deleted scans cannot survive it. */
    static void reconcile(String json) {
        java.util.List<BlockerScan> scans = GSON.fromJson(json, SCANS);
        if (scans == null) { SELECTED.clear(); return; }
        Set<String> existing = new java.util.HashSet<>();
        for (BlockerScan scan : scans) existing.add(scan.id);
        SELECTED.keySet().removeIf(id -> !existing.contains(id));
    }
    static void selectAll(String scanId, Collection<BlockerEntry> entries) {
        Set<String> selected = SELECTED.computeIfAbsent(scanId, ignored -> new LinkedHashSet<>());
        for (BlockerEntry entry : entries) selected.add(key(entry));
    }
    static Set<String> keys(String scanId) { return Set.copyOf(SELECTED.getOrDefault(scanId, Set.of())); }
    static String key(BlockerEntry entry) { return entry.x() + ":" + entry.y() + ":" + entry.z() + ":" + entry.blockId(); }
}
