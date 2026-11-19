package smp.aeternum.carpet.util;

import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import smp.aeternum.carpet.AeternumCarpet;
import smp.aeternum.carpet.AeternumCarpetSettings;

/** Captures update suppression throwables during neighbor updates so the server does not crash. */
public final class UpdateSuppressionException extends RuntimeException {
    private final String dimension;
    private final BlockPos pos;

    public UpdateSuppressionException(World world, BlockPos pos, Throwable cause) {
        super(cause);
        this.dimension = world != null ? world.getRegistryKey().getValue().toString() : "unknown";
        this.pos = pos != null ? pos.toImmutable() : BlockPos.ORIGIN;
    }

    public static boolean isSuppression(Throwable t) {
        return t instanceof UpdateSuppressionException
            || t instanceof StackOverflowError
            || t instanceof OutOfMemoryError
            || t instanceof ClassCastException
            || t instanceof IllegalStateException
            || t instanceof IllegalArgumentException;
    }

    public static void handle(MinecraftServer server, UpdateSuppressionException e) {
        if ("silence".equals(AeternumCarpetSettings.updateSuppressionCrashFix)) {
            return;
        }
        String msg = String.format(
            "Update suppression caught at [%d, %d, %d] in %s (%s)",
            e.pos.getX(), e.pos.getY(), e.pos.getZ(), e.dimension,
            e.getCause() != null ? e.getCause().getClass().getSimpleName() : "Throwable"
        );
        AeternumCarpet.LOGGER.warn(msg);
        if (server != null) {
            server.getPlayerManager().broadcast(Text.literal("[Aeternum] " + msg).formatted(Formatting.YELLOW), false);
        }
    }
}
