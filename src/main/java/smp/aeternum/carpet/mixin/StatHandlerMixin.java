package smp.aeternum.carpet.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stat;
import net.minecraft.stat.StatHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import smp.aeternum.carpet.stats.ScoreboardConfigStore;

@Mixin(StatHandler.class)
public abstract class StatHandlerMixin {
    @Inject(method = "increaseStat", at = @At("TAIL"))
    private void aeternum$onIncreaseStat(PlayerEntity player, Stat<?> stat, int amount, CallbackInfo ci) {
        if (amount > 0 && player instanceof ServerPlayerEntity serverPlayer) {
            ScoreboardConfigStore.onStatIncrease(serverPlayer, stat, amount);
        }
    }
}
