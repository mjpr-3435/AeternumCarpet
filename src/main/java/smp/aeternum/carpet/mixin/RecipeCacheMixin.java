package smp.aeternum.carpet.mixin;

import net.minecraft.recipe.RecipeCache;
import net.minecraft.recipe.ServerRecipeManager;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import smp.aeternum.carpet.AeternumCarpetSettings;

import java.lang.ref.WeakReference;

@Mixin(RecipeCache.class)
public abstract class RecipeCacheMixin {
    @Shadow
    private WeakReference<ServerRecipeManager> recipeManagerRef;

    @Unique
    private boolean aeternum$lastCraftableCalcite = AeternumCarpetSettings.craftableCalcite;

    @Inject(method = "validateRecipeManager", at = @At("HEAD"))
    private void aeternum$invalidateOnRuleToggle(ServerWorld world, CallbackInfo ci) {
        if (this.aeternum$lastCraftableCalcite != AeternumCarpetSettings.craftableCalcite) {
            this.aeternum$lastCraftableCalcite = AeternumCarpetSettings.craftableCalcite;
            this.recipeManagerRef = new WeakReference<>(null);
        }
    }
}
