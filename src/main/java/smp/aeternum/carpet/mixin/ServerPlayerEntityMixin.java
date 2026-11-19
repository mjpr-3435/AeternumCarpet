package smp.aeternum.carpet.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.EndGatewayBlock;
import net.minecraft.block.EndPortalBlock;
import net.minecraft.block.NetherPortalBlock;
import net.minecraft.entity.EntityCollisionHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import smp.aeternum.carpet.AeternumCarpetSettings;
import smp.aeternum.carpet.util.PlayerSitManager;

@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityMixin {
    @Inject(method = "playerTick", at = @At("TAIL"))
    private void aeternum$allowSpectatorPortalTravel(CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        PlayerSitManager.onSneakChange(player, player.isSneaking());
        PlayerSitManager.onPlayerTick(player);
        if (!AeternumCarpetSettings.spectatorPortalTravel || !player.isSpectator()) {
            return;
        }

        World world = player.getWorld();
        BlockPos[] candidates = { BlockPos.ofFloored(player.getEyePos()), player.getBlockPos() };
        for (BlockPos pos : candidates) {
            BlockState blockState = world.getBlockState(pos);
            if (blockState.getBlock() instanceof NetherPortalBlock || blockState.getBlock() instanceof EndPortalBlock || blockState.getBlock() instanceof EndGatewayBlock) {
                blockState.onEntityCollision(world, pos, player, EntityCollisionHandler.DUMMY);
                break;
            }
        }
    }
}
