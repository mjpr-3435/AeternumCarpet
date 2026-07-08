package smp.aeternum.carpet.mixin;

import net.minecraft.entity.SpawnGroup;
import net.minecraft.world.SpawnHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(SpawnHelper.Info.class)
public interface SpawnHelperInfoAccessor {
    @Invoker("isBelowCap")
    boolean aeternum$invokeIsBelowCap(SpawnGroup group);
}
