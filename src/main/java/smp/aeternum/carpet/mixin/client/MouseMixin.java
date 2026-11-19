package smp.aeternum.carpet.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import smp.aeternum.carpet.client.AeternumDockOverlay;

@Mixin(Mouse.class)
public abstract class MouseMixin {
    @Shadow @Final private MinecraftClient client;

    @Inject(method = "updateMouse", at = @At("HEAD"), cancellable = true)
    private void aeternum$freezeCameraWhenDockOpen(double timeDelta, CallbackInfo ci) {
        if (AeternumDockOverlay.isVisible()) {
            ci.cancel();
        }
    }

    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void aeternum$handleDockMouseClick(long window, int button, int action, int mods, CallbackInfo ci) {
        if (!AeternumDockOverlay.isVisible()) return;
        if (window == this.client.getWindow().getHandle() && button == GLFW.GLFW_MOUSE_BUTTON_LEFT && action == GLFW.GLFW_PRESS) {
            Mouse self = (Mouse) (Object) this;
            double mx = self.getScaledX(this.client.getWindow());
            double my = self.getScaledY(this.client.getWindow());
            AeternumDockOverlay.handleClick(mx, my);
        }
        ci.cancel();
    }
}
