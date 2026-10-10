package com.mouseclient.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.InactivityFpsLimiter;
import net.minecraft.client.option.InactivityFpsLimiter.LimitReason;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InactivityFpsLimiter.class)
public abstract class InactivityFpsLimiterMixin {
   @Inject(method = "getLimitReason()Lnet/minecraft/client/option/InactivityFpsLimiter$LimitReason;", at = @At("HEAD"), cancellable = true)
   private void keepFullFpsUnlessIconified(CallbackInfoReturnable<LimitReason> callback) {
      if (!MinecraftClient.getInstance().getWindow().isMinimized()) {
         callback.setReturnValue(LimitReason.NONE);
      }
   }
}
