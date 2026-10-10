package com.mouseclient.mixin;

import com.mouseclient.modules.impl.render.BlockOverlay;
import com.mouseclient.modules.impl.render.Tags;
import com.mouseclient.render.pipeline.CustomWorld;
import com.mouseclient.render.pipeline.PlayerOutlineRenderer;
import com.mouseclient.render.pipeline.TargetEsp;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.FrameGraphBuilder;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
   @Shadow
   private Framebuffer entityOutlineFramebuffer;
   private boolean playersMaskFilled;

   @Shadow
   protected abstract boolean canDrawEntityOutlines();

   @Inject(method = "renderSky(Lnet/minecraft/client/render/FrameGraphBuilder;Lnet/minecraft/client/render/Camera;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V", at = @At("HEAD"), cancellable = true)
   private void replaceVanillaSky(FrameGraphBuilder frameGraphBuilder, Camera camera, GpuBufferSlice bufferSlice, CallbackInfo callback) {
      if (CustomWorld.getInstance().isShaderSkyEnabled()) {
         callback.cancel();
      }
   }

   @Inject(method = "renderTargetBlockOutline(Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/util/math/MatrixStack;ZLnet/minecraft/client/render/state/WorldRenderState;)V", at = @At("HEAD"), cancellable = true)
   private void skipVanillaOutlineForBlockOverlay(Immediate immediate, MatrixStack matrices, boolean renderBlockOutline, WorldRenderState renderStates, CallbackInfo callback) {
      if (BlockOverlay.getInstance().isEnabled()) {
         callback.cancel();
      }
   }

   @ModifyExpressionValue(method = "render(Lnet/minecraft/client/util/memory/ObjectAllocator;Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V", at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/state/WorldRenderState;hasOutline:Z"))
   private boolean skipVanillaGlowPostChain(boolean playersMaskFilled) {
      if (Tags.getInstance().isOutlineShaderEnabled()) {
         this.playersMaskFilled = playersMaskFilled;
         return false;
      } else {
         return playersMaskFilled;
      }
   }

   @Inject(method = "drawEntityOutlinesFramebuffer()V", at = @At("HEAD"), cancellable = true)
   private void drawPlayersShaderInsteadOfGlowBlit(CallbackInfo callback) {
      if (!Tags.getInstance().isOutlineShaderEnabled()) {
         PlayerOutlineRenderer.getInstance().close();
      } else {
         callback.cancel();
         PlayerOutlineRenderer.getInstance().renderCosmeticMask(this.entityOutlineFramebuffer);
         if (this.playersMaskFilled && Tags.getInstance().applyShader.getValue()) {
            PlayerOutlineRenderer.getInstance().renderOutline(this.entityOutlineFramebuffer);
         }

         TargetEsp.getInstance().renderDeferredGhosts();
      }
   }
}
