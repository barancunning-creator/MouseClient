package com.mouseclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net/minecraft/client/realms/util/PeriodicRunnerFactory$PeriodicRunner")
public abstract class RealmsPeriodicRunnerMixin {
   @Inject(method = "run(J)V", at = @At("HEAD"), cancellable = true)
   private void stopRealmsUpdate(long currentTime, CallbackInfo callback) {
      callback.cancel();
   }
}
