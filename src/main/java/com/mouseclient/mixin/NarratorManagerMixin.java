package com.mouseclient.mixin;

import com.mojang.text2speech.Narrator;
import net.minecraft.client.option.NarratorMode;
import net.minecraft.client.util.NarratorManager;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NarratorManager.class)
public abstract class NarratorManagerMixin {
   @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/mojang/text2speech/Narrator;getNarrator()Lcom/mojang/text2speech/Narrator;"))
   private Narrator skipNarratorLibrary() {
      return Narrator.EMPTY;
   }

   @Inject(method = "say(Ljava/lang/String;Z)V", at = @At("HEAD"), cancellable = true)
   private void muteMessage(String text, boolean interrupt, CallbackInfo callback) {
      callback.cancel();
   }

   @Inject(method = "narrateText(Lnet/minecraft/text/Text;)V", at = @At("HEAD"), cancellable = true)
   private void muteQueuedMessage(Text text, CallbackInfo callback) {
      callback.cancel();
   }

   @Inject(method = "onModeChange(Lnet/minecraft/client/option/NarratorMode;)V", at = @At("HEAD"), cancellable = true)
   private void ignoreStatusChange(NarratorMode mode, CallbackInfo callback) {
      callback.cancel();
   }

   @Inject(method = "checkNarratorLibrary(Z)V", at = @At("HEAD"), cancellable = true)
   private void ignoreStatusCheck(boolean narratorEnabled, CallbackInfo callback) {
      callback.cancel();
   }

   @Inject(method = "isActive()Z", at = @At("HEAD"), cancellable = true)
   private void neverActive(CallbackInfoReturnable<Boolean> callback) {
      callback.setReturnValue(false);
   }
}
