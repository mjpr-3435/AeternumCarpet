package smp.aeternum.carpet.blockers;

import net.minecraft.util.math.BlockPos;

/** A server-authoritative entry. The client is only allowed to choose how to display it. */
public record BlockerEntry(int x, int y, int z, String blockId, String reason) {
    public BlockPos pos() {
        return new BlockPos(x, y, z);
    }
}
