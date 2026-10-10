package com.mouseclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net/minecraft/client/realms/util/PeriodicRunnerFactory$RunnersManager")
public abstract class RealmsRunnersManagerMixin {
   @Inject(method = "runAll()V", at = @At("HEAD"), cancellable = true)
   private void stopRealmsTick(CallbackInfo callback) {
      callback.cancel();
   }
}
