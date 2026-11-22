package smp.aeternum.carpet.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import smp.aeternum.carpet.stats.PlayerRegistryStore;

@Mixin(PlayerManager.class)
public abstract class PlayerManagerMixin {
    @Shadow
    @Final
    private MinecraftServer server;

    @Inject(method = "savePlayerData", at = @At("RETURN"))
    private void aeternum$onSavePlayerData(ServerPlayerEntity player, CallbackInfo ci) {
        PlayerRegistryStore.refreshPlayer(this.server, player);
    }
}
