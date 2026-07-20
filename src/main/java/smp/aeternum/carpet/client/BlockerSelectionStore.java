package smp.aeternum.carpet.client;

import smp.aeternum.carpet.blockers.BlockerEntry;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Per-client, per-scan selection. It intentionally survives closing the GUI. */
final class BlockerSelectionStore {
    private static final Map<String, Set<String>> SELECTED = new LinkedHashMap<>();

    private BlockerSelectionStore() {}

    static boolean isSelected(String scanId, BlockerEntry entry) { return SELECTED.getOrDefault(scanId, Set.of()).contains(key(entry)); }
    static void toggle(String scanId, BlockerEntry entry) {
        Set<String> selected = SELECTED.computeIfAbsent(scanId, ignored -> new LinkedHashSet<>());
        if (!selected.add(key(entry))) selected.remove(key(entry));
        if (selected.isEmpty()) SELECTED.remove(scanId);
    }
    static void clear(String scanId) { SELECTED.remove(scanId); }
    static void selectAll(String scanId, Collection<BlockerEntry> entries) {
        Set<String> selected = SELECTED.computeIfAbsent(scanId, ignored -> new LinkedHashSet<>());
        for (BlockerEntry entry : entries) selected.add(key(entry));
    }
    static Set<String> keys(String scanId) { return Set.copyOf(SELECTED.getOrDefault(scanId, Set.of())); }
    static String key(BlockerEntry entry) { return entry.x() + ":" + entry.y() + ":" + entry.z() + ":" + entry.blockId(); }
}
