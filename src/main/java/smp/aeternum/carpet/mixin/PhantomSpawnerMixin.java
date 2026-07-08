package smp.aeternum.carpet.mixin;

import net.minecraft.entity.SpawnGroup;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.spawner.PhantomSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import smp.aeternum.carpet.AeternumCarpetSettings;

@Mixin(PhantomSpawner.class)
public class PhantomSpawnerMixin {
    @Inject(method = "spawn", at = @At("HEAD"), cancellable = true)
    private void aeternum$applyPhantomSpawningRule(ServerWorld world, boolean spawnMonsters, boolean spawnAnimals, CallbackInfo ci) {
        String mode = AeternumCarpetSettings.phantomSpawning;
        if ("vanilla".equals(mode)) {
            return;
        }

        if ("disable".equals(mode)) {
            ci.cancel();
            return;
        }

        if ("mobcap".equals(mode) && spawnMonsters) {
            var spawnInfo = world.getChunkManager().getSpawnInfo();
            if (spawnInfo != null && !((SpawnHelperInfoAccessor) spawnInfo).aeternum$invokeIsBelowCap(SpawnGroup.MONSTER)) {
                ci.cancel();
            }
        }
    }
}
