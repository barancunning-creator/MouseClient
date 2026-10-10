package com.mouseclient.mixin;

import com.mouseclient.render.VisualRotationState;

import com.mouseclient.access.ChamsRenderState;

import com.mouseclient.modules.impl.render.Chams;
import com.mouseclient.modules.impl.render.SeeInvisible;
import com.mouseclient.util.entity.MinecraftAccess;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.dreamix.fabricloader.VMBridge;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin implements MinecraftAccess {

   @Inject(method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
   private void markChams(LivingEntity entity, LivingEntityRenderState state, float tickProgress, CallbackInfo callback) {
      ((ChamsRenderState)state).mouseclient$setChams(Chams.getInstance().shouldRenderThroughWalls(entity));
   }

   @Inject(
      method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
      at = @At("HEAD")
   )
   private void pullChamsToCamera(LivingEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState cameraState, CallbackInfo callback) {
      if (((ChamsRenderState)state).mouseclient$chams()) {
         Matrix4f positionMatrix = matrices.peek().getPositionMatrix();
         float distanceToCamera = (float)Math.sqrt(positionMatrix.m30() * positionMatrix.m30() + positionMatrix.m31() * positionMatrix.m31() + positionMatrix.m32() * positionMatrix.m32());
         if (distanceToCamera > (3.0F)) {
            positionMatrix.scaleLocal((1.5F) / distanceToCamera);
         }
      }
   }

   @ModifyExpressionValue(
      method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/MathHelper;lerpAngleDegrees(FFF)F", ordinal = 0)
   )
   private float smoothHeadYaw(float yaw, LivingEntity entity, LivingEntityRenderState state, float tickProgress) {
      return entity == minecraftClient.player ? MathHelper.lerpAngleDegrees(tickProgress, VisualRotationState.previousHeadYaw, VisualRotationState.headYaw) : yaw;
   }

   @ModifyExpressionValue(
      method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getLerpedPitch(F)F")
   )
   private float smoothHeadPitch(float pitch, LivingEntity entity, LivingEntityRenderState state, float tickProgress) {
      return entity == minecraftClient.player ? MathHelper.lerp(tickProgress, VisualRotationState.previousPitch, VisualRotationState.pitch) : pitch;
   }

   @ModifyConstant(
      method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
      constant = @Constant(intValue = 654311423)
   )
   private int seeInvisibleOpacityTint(int color) {
      return !SeeInvisible.getInstance().isEnabled()
         ? color
         : Math.max(1, (int)(SeeInvisible.getInstance().opacity.getValue() * (255.0F))) << 24 | (16777215);
   }

   static {
      VMBridge.identifyClass(LivingEntityRendererMixin.class, "qXEUklpw");
   }
}
