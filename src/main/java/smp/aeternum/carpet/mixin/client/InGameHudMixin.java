package smp.aeternum.carpet.mixin.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import smp.aeternum.carpet.client.MaterialsHudOverlay;

@Mixin(InGameHud.class)
public class InGameHudMixin {
	@Inject(method = "render", at = @At("TAIL"))
	private void aeternum$renderMaterialsHud(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
		MaterialsHudOverlay.render(context);
	}
}
