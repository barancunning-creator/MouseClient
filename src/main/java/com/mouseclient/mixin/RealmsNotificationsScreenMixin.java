package com.mouseclient.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.realms.gui.screen.RealmsNotificationsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RealmsNotificationsScreen.class)
public abstract class RealmsNotificationsScreenMixin {
   @Inject(method = "init()V", at = @At("HEAD"), cancellable = true)
   private void stopRealmsInit(CallbackInfo callback) {
      callback.cancel();
   }

   @Inject(method = "onDisplayed()V", at = @At("HEAD"), cancellable = true)
   private void stopRealmsAdded(CallbackInfo callback) {
      callback.cancel();
   }

   @Inject(method = "tick()V", at = @At("HEAD"), cancellable = true)
   private void stopRealmsPolling(CallbackInfo callback) {
      callback.cancel();
   }

   @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At("HEAD"), cancellable = true)
   private void stopRealmsRender(DrawContext context, int mouseX, int mouseY, float tickDelta, CallbackInfo callback) {
      callback.cancel();
   }
}
