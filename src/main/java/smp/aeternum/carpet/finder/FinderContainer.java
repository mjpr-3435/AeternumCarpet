package smp.aeternum.carpet.finder;

import java.util.LinkedHashMap;
import java.util.Map;

public final class FinderContainer {
    public int x, y, z;
    public String blockId;
    public Map<String, Integer> items = new LinkedHashMap<>();
    public Map<String, Integer> stacks = new LinkedHashMap<>();

    public FinderContainer() { }
    public FinderContainer(int x, int y, int z, String blockId) {
        this.x = x; this.y = y; this.z = z; this.blockId = blockId;
    }
}
