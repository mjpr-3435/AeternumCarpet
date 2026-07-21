package smp.aeternum.carpet.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import smp.aeternum.carpet.mining.MiningRuleHandler;
import smp.aeternum.carpet.pistonaxe.PistonAxeHandler;

@Mixin(ServerPlayerInteractionManager.class)
public class ServerPlayerInteractionManagerMixin {
    @Shadow protected ServerWorld world;
    @Shadow @Final protected ServerPlayerEntity player;
    @Shadow private int tickCounter;

    @Unique private BlockPos aeternum$portalFrameMiningPos;
    @Unique private int aeternum$portalFrameMiningStartTick;
    @Unique private int aeternum$portalFrameMiningStage = -1;
    @Unique private int aeternum$portalFrameBreakerId;

    @Inject(method = "update", at = @At("TAIL"))
    private void aeternum$updatePortalFrameMining(CallbackInfo ci) {
        if (aeternum$portalFrameMiningPos == null) {
            return;
        }

        BlockState state = world.getBlockState(aeternum$portalFrameMiningPos);
        if (!MiningRuleHandler.canBreakEndPortalFrame(player, state, player.getMainHandStack())) {
            aeternum$clearPortalFrameMining();
            return;
        }

        int elapsedTicks = tickCounter - aeternum$portalFrameMiningStartTick + 1;
        int progressStage = MiningRuleHandler.getCustomMiningStage(elapsedTicks);
        if (progressStage != aeternum$portalFrameMiningStage) {
            aeternum$portalFrameMiningStage = progressStage;
            world.setBlockBreakingInfo(aeternum$portalFrameBreakerId, aeternum$portalFrameMiningPos, progressStage);
        }

        if (elapsedTicks < MiningRuleHandler.getEndPortalFrameBreakTicks()) {
            return;
        }

        MiningRuleHandler.tryBreakCustomBlock(player, world, aeternum$portalFrameMiningPos);
        aeternum$clearPortalFrameMining();
    }

    @Inject(method = "processBlockBreakingAction", at = @At("HEAD"), cancellable = true)
    private void aeternum$handlePortalFrameMining(BlockPos pos, PlayerActionC2SPacket.Action action, net.minecraft.util.math.Direction direction, int worldHeight, int sequence, CallbackInfo ci) {
        // The action packet is only the player's intent. The whitelist and removal live entirely on the server.
        if (action == PlayerActionC2SPacket.Action.START_DESTROY_BLOCK && PistonAxeHandler.tryStart((ServerPlayerInteractionManager) (Object) this, player, world, pos)) {
            ci.cancel();
            return;
        }
        BlockState state = world.getBlockState(pos);
        boolean isCustomPortalFrameMining = MiningRuleHandler.canBreakEndPortalFrame(player, state, player.getMainHandStack());
        if (!isCustomPortalFrameMining && aeternum$portalFrameMiningPos == null) {
            return;
        }

        if (action == PlayerActionC2SPacket.Action.START_DESTROY_BLOCK && isCustomPortalFrameMining) {
            aeternum$portalFrameMiningPos = pos.toImmutable();
            aeternum$portalFrameMiningStartTick = tickCounter;
            aeternum$portalFrameMiningStage = 0;
            aeternum$portalFrameBreakerId = player.getId() ^ 0x40000000;
            world.setBlockBreakingInfo(aeternum$portalFrameBreakerId, aeternum$portalFrameMiningPos, 0);
            ci.cancel();
            return;
        }

        if (aeternum$portalFrameMiningPos == null || !aeternum$portalFrameMiningPos.equals(pos)) {
            return;
        }

        if (action == PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK || action == PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK) {
            aeternum$clearPortalFrameMining();
            ci.cancel();
        }
    }

    @Inject(method = "tryBreakBlock", at = @At("HEAD"), cancellable = true)
    private void aeternum$breakCustomBlocks(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (aeternum$portalFrameMiningPos != null && aeternum$portalFrameMiningPos.equals(pos)) {
            cir.setReturnValue(false);
        }
    }

    @Unique
    private void aeternum$clearPortalFrameMining() {
        if (aeternum$portalFrameMiningPos != null) {
            world.setBlockBreakingInfo(aeternum$portalFrameBreakerId, aeternum$portalFrameMiningPos, -1);
        }
        aeternum$portalFrameMiningPos = null;
        aeternum$portalFrameMiningStartTick = 0;
        aeternum$portalFrameMiningStage = -1;
        aeternum$portalFrameBreakerId = 0;
    }
}
