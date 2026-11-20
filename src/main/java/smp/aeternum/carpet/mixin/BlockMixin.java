package smp.aeternum.carpet.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import smp.aeternum.carpet.carefulbreak.CarefulBreakStore;

@Mixin(Block.class)
public abstract class BlockMixin {
    @WrapOperation(
        method = "afterBreak",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/block/Block;dropStacks(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/entity/BlockEntity;Lnet/minecraft/entity/Entity;Lnet/minecraft/item/ItemStack;)V"
        )
    )
    private void aeternum$carefulBreakToInventory(
        BlockState state,
        World world,
        BlockPos pos,
        @Nullable BlockEntity blockEntity,
        @Nullable Entity entity,
        ItemStack tool,
        Operation<Void> original
    ) {
        if (world instanceof ServerWorld serverWorld
            && entity instanceof ServerPlayerEntity player
            && CarefulBreakStore.shouldCollect(player)) {
            for (ItemStack drop : Block.getDroppedStacks(state, serverWorld, pos, blockEntity, player, tool)) {
                player.getInventory().insertStack(drop);
                if (!drop.isEmpty()) {
                    Block.dropStack(serverWorld, pos, drop);
                }
            }
            player.playerScreenHandler.sendContentUpdates();
            state.onStacksDropped(serverWorld, pos, tool, true);
            return;
        }
        original.call(state, world, pos, blockEntity, entity, tool);
    }
}
