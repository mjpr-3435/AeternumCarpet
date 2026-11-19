package smp.aeternum.carpet.mixin;

import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.ServerRecipeManager;
import net.minecraft.recipe.input.RecipeInput;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import smp.aeternum.carpet.AeternumCarpet;
import smp.aeternum.carpet.AeternumCarpetSettings;

import java.util.Optional;

@Mixin(ServerRecipeManager.class)
public abstract class ServerRecipeManagerMixin {
    @Unique
    private static final Identifier CALCITE_RECIPE_ID = AeternumCarpet.id("calcite");

    @Inject(
        method = "getFirstMatch(Lnet/minecraft/recipe/RecipeType;Lnet/minecraft/recipe/input/RecipeInput;Lnet/minecraft/world/World;Lnet/minecraft/recipe/RecipeEntry;)Ljava/util/Optional;",
        at = @At("RETURN"),
        cancellable = true
    )
    private <I extends RecipeInput, T extends Recipe<I>> void aeternum$gateCalciteRecipeWithHint(
        RecipeType<T> type,
        I input,
        World world,
        RecipeEntry<T> recipe,
        CallbackInfoReturnable<Optional<RecipeEntry<T>>> cir
    ) {
        aeternum$filterDisabledCalcite(cir);
    }

    @Inject(
        method = "getFirstMatch(Lnet/minecraft/recipe/RecipeType;Lnet/minecraft/recipe/input/RecipeInput;Lnet/minecraft/world/World;)Ljava/util/Optional;",
        at = @At("RETURN"),
        cancellable = true
    )
    private <I extends RecipeInput, T extends Recipe<I>> void aeternum$gateCalciteRecipe(
        RecipeType<T> type,
        I input,
        World world,
        CallbackInfoReturnable<Optional<RecipeEntry<T>>> cir
    ) {
        aeternum$filterDisabledCalcite(cir);
    }

    @Unique
    private static <I extends RecipeInput, T extends Recipe<I>> void aeternum$filterDisabledCalcite(
        CallbackInfoReturnable<Optional<RecipeEntry<T>>> cir
    ) {
        if (AeternumCarpetSettings.craftableCalcite) {
            return;
        }
        Optional<RecipeEntry<T>> match = cir.getReturnValue();
        if (match.isPresent() && CALCITE_RECIPE_ID.equals(match.get().id().getValue())) {
            cir.setReturnValue(Optional.empty());
        }
    }
}
