package smp.aeternum.carpet.pistonaxe;

import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import smp.aeternum.carpet.AeternumCarpetSettings;

public final class PistonAxeHandler {
    private PistonAxeHandler() { }
    /** Starts the physical piston task; the target itself is never mutated by this mod. */
    public static boolean tryStart(ServerPlayerInteractionManager manager, ServerPlayerEntity player, ServerWorld world, BlockPos pos) {
        if (!AeternumCarpetSettings.pistonAxe || !PistonAxeStore.enabled(player.getUuid())) return false;
        if (!PistonAxeRunner.canUse(player)) return false;
        BlockState state=world.getBlockState(pos);
        if (state.isAir() || !PistonAxeStore.allows(player.getUuid(), Registries.BLOCK.getId(state.getBlock()).toString())) return false;
        return PistonAxeRunner.start(manager, player, world, pos);
    }
}
