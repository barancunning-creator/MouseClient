package com.mouseclient.mixin;

import com.mouseclient.render.VisualRotationState;

import com.mouseclient.modules.impl.movement.Spider;
import com.mouseclient.modules.impl.render.SwordAnimations;
import com.mouseclient.util.entity.MinecraftAccess;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin implements MinecraftAccess {
   @Inject(method = "getHandSwingDuration()I", at = @At("HEAD"), cancellable = true)
   private void customHandSwingDuration(CallbackInfoReturnable<Integer> callback) {
      if (SwordAnimations.getInstance().shouldOverrideSwingDuration() && (Object)this == minecraftClient.player) {
         callback.setReturnValue(SwordAnimations.getInstance().swingDurationTicks());
      }
   }

   @Inject(method = "tick()V", at = @At("HEAD"))
   private void shiftVisualRotationHistory(CallbackInfo callback) {
      if ((Object)this == minecraftClient.player) {
         VisualRotationState.previousPitch = VisualRotationState.pitch;
         VisualRotationState.previousHeadYaw = VisualRotationState.headYaw;
      }
   }

   @Inject(method = "jump()V", at = @At("HEAD"), cancellable = true)
   private void holdSpiderJump(CallbackInfo callback) {
      if ((Object)this == minecraftClient.player && Spider.getInstance().shouldSuppressJump()) {
         callback.cancel();
      }
   }

   @Inject(method = "tick()V", at = @At("TAIL"))
   private void captureVisualRotation(CallbackInfo callback) {
      if ((Object)this == minecraftClient.player) {
         VisualRotationState.pitch = ((LivingEntity)(Object)this).getPitch();
         VisualRotationState.headYaw = ((LivingEntity)(Object)this).headYaw;
      }
   }
}
