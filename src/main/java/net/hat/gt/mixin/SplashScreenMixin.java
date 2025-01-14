package net.hat.gt.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.resource.SplashTextResourceSupplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.jab125.thonkutil.util.Util.isModInstalled;

@Environment(EnvType.CLIENT)
@Mixin(SplashTextResourceSupplier.class)
public class SplashScreenMixin {
    @Inject(method = "get", at = @At(value = "RETURN"), cancellable = true)
    private void append(CallbackInfoReturnable<String> cir) {
        if ((int) (Math.random() * 70) == 0 && !isModInstalled("endgoblintraders")) {
            if ((int) (Math.random() * 2) == 0) {
                cir.setReturnValue("Try End Goblin Traders for only $0.99!");
            } else {
                cir.setReturnValue("Try End Goblin Traders on CurseForge!");
            }
        }

    }
}
