package com.mouseclient.mixin;

import com.mouseclient.internal.GuiBlurState;
import com.mouseclient.modules.impl.misc.EventBus;
import com.mouseclient.modules.impl.movement.AspectRatioEvent;
import com.mouseclient.modules.impl.render.AspectRatio;
import com.mouseclient.modules.impl.render.NoRender;
import com.mouseclient.render.DepthSnapshot;
import com.mouseclient.render.pipeline.WorldRainRenderer;
import com.mouseclient.render.pipeline.CustomWorld;
import com.mouseclient.render.pipeline.GuiBlurRenderer;
import com.mouseclient.render.pipeline.MouseClientHandOutlineBlur;
import com.mouseclient.render.pipeline.MouseClientJumpCircleScene;
import com.mouseclient.render.pipeline.RainScreenRenderer;
import com.mouseclient.render.pipeline.MotionBlur;
import com.mouseclient.render.pipeline.WorldRenderEvent;
import com.mouseclient.render.pipeline.SaturationRenderer;
import com.mouseclient.render.pipeline.WetSurfaceRenderer;
import com.mouseclient.util.entity.WorldProjection;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.dreamix.fabricloader.VMBridge;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

   @Inject(method = "getBasicProjectionMatrix(F)Lorg/joml/Matrix4f;", at = @At("TAIL"), cancellable = true)
   public void getProjectionMatrixHook(float fovDegrees, CallbackInfoReturnable<Matrix4f> callback) {
      if (EventBus.getInstance().hasListeners(AspectRatioEvent.class)) {
         AspectRatioEvent event = new AspectRatioEvent();
         EventBus.getInstance().post(event);
         if (event.isCancelled()) {
            Matrix4f projectionMatrix = new Matrix4f();
            projectionMatrix.perspective(fovDegrees * (0.017453292F), event.getAspectRatio(), (0.05F), ((GameRenderer)(Object)this).getFarPlaneDistance());
            callback.setReturnValue(projectionMatrix);
         }
      }
   }

   @ModifyArg(
      method = "renderWorld(Lnet/minecraft/client/render/RenderTickCounter;)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/ProjectionMatrix3;set(IIF)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"),
      index = 0
   )
   private int stretchHandProjection(int originalWidth) {
      return AspectRatio.getInstance().projectionWidth(originalWidth);
   }

   @Inject(method = "renderBlur()V", at = @At("HEAD"), cancellable = true)
   private void customStratumBlur(CallbackInfo callback) {
      if (GuiBlurState.isEnabled()) {
         GuiBlurRenderer.getInstance().renderScreenBlur(GuiBlurState.getRadius(), GuiBlurState.getOpacity());
         GuiBlurRenderer.getInstance().refreshHeavyBlurIfPending();
         GuiBlurRenderer.getInstance().refreshSecondaryGlobalBlur();
         callback.cancel();
      } else {
         if (GuiBlurState.isRequested()) {
            GuiBlurState.setRequested(false);
            GuiBlurRenderer.getInstance().refreshHeavyBlurIfPending();
            GuiBlurRenderer.getInstance().refreshSecondaryGlobalBlur();
            callback.cancel();
         }
      }
   }

   @Inject(method = "tiltViewWhenHurt(Lnet/minecraft/client/util/math/MatrixStack;F)V", at = @At("HEAD"), cancellable = true)
   private void removeHurtShake(MatrixStack matrices, float tickProgress, CallbackInfo callback) {
      if (NoRender.getInstance().isOverlayDisabled(("Тряска экрана")) || NoRender.getInstance().isOverlayDisabled(("Камера при уроне"))) {
         callback.cancel();
      }
   }

   @Inject(method = "renderWorld(Lnet/minecraft/client/render/RenderTickCounter;)V", at = @At("TAIL"))
   private void smearFrameAfterHand(RenderTickCounter tickCounter, CallbackInfo callback) {
      MotionBlur.renderIfEnabled();
   }

   @Inject(method = "renderWorld(Lnet/minecraft/client/render/RenderTickCounter;)V", at = @At("TAIL"))
   private void drawHandOutlineAfterHand(RenderTickCounter tickCounter, CallbackInfo callback) {
      MouseClientHandOutlineBlur.getInstance().compositeOutline();
   }

   @Inject(method = "renderWorld(Lnet/minecraft/client/render/RenderTickCounter;)V", at = @At("TAIL"))
   private void applyWorldSaturation(RenderTickCounter tickCounter, CallbackInfo callback) {
      SaturationRenderer.getInstance().render();
   }

   @Inject(
      method = "renderWorld(Lnet/minecraft/client/render/RenderTickCounter;)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/WorldRenderer;render(Lnet/minecraft/client/util/memory/ObjectAllocator;Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V",
         shift = Shift.BEFORE
      )
   )
   private void captureMatricesBeforeWorld(RenderTickCounter tickCounter, CallbackInfo callback, @Local(ordinal = 1) Matrix4f viewMatrix, @Local(ordinal = 0) Matrix4f projectionMatrix) {
      WorldProjection.captureMatrices(viewMatrix, projectionMatrix);
   }

   @Inject(
      method = "renderWorld(Lnet/minecraft/client/render/RenderTickCounter;)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/WorldRenderer;render(Lnet/minecraft/client/util/memory/ObjectAllocator;Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V",
         shift = Shift.AFTER
      )
   )
   private void hookWorldRender(RenderTickCounter tickCounter, CallbackInfo callback, @Local(ordinal = 1) Matrix4f viewMatrix, @Local(ordinal = 0) Matrix4f projectionMatrix) {
      WorldProjection.captureMatrices(viewMatrix, projectionMatrix);
      DepthSnapshot.beginFrame();
      CustomWorld.getInstance().renderSky(viewMatrix, projectionMatrix);
      CustomWorld.getInstance().renderFog(viewMatrix, projectionMatrix);
      WorldRainRenderer.getInstance().render(viewMatrix, projectionMatrix);
      WetSurfaceRenderer.getInstance().render(viewMatrix, projectionMatrix);
      RainScreenRenderer.getInstance().render(viewMatrix, projectionMatrix);
      MouseClientJumpCircleScene.getInstance().render(viewMatrix, projectionMatrix);
      if (EventBus.getInstance().hasListeners(WorldRenderEvent.class)) {
         MatrixStack matrices = new MatrixStack();
         matrices.multiplyPositionMatrix(viewMatrix);
         EventBus.getInstance().post(new WorldRenderEvent(matrices, tickCounter.getTickProgress(false)));
      }
   }

   @Inject(method = "showFloatingItem(Lnet/minecraft/item/ItemStack;)V", at = @At("HEAD"), cancellable = true)
   private void hideTotemActivation(ItemStack stack, CallbackInfo callback) {
      if (NoRender.getInstance().isOverlayDisabled(("Анимация тотема"))) {
         callback.cancel();
      }
   }

   static {
      VMBridge.identifyClass(GameRendererMixin.class, "1JXtEvNw");
   }
}
