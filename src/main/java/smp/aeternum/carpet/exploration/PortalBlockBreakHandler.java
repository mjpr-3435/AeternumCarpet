package smp.aeternum.carpet.exploration;

import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import smp.aeternum.carpet.AeternumCarpetSettings;

public final class PortalBlockBreakHandler {
    private PortalBlockBreakHandler() {
    }

    public static void breakEndPortalsAround(ServerWorld world, Vec3d center) {
        if (!AeternumCarpetSettings.breakEndPortalsWithWindCharge) {
            return;
        }

        BlockPos impactPos = BlockPos.ofFloored(center);
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos targetPos = impactPos.add(x, y, z);
                    if (world.getBlockState(targetPos).isOf(Blocks.END_PORTAL)) {
                        world.removeBlock(targetPos, false);
                    }
                }
            }
        }
    }
}
