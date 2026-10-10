package com.mouseclient.mixin;

import com.mouseclient.network.ClientInitializer;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MinecraftClient.class)
public class MinecraftFrameSyncMixin {
   @Unique
   private boolean rendersThisFrame = true;

   @WrapWithCondition(
      method = "render(Z)V",
      at = @At(
         value = "INVOKE",
         target = "Lcom/mojang/blaze3d/systems/CommandEncoder;clearColorAndDepthTextures(Lcom/mojang/blaze3d/textures/GpuTexture;ILcom/mojang/blaze3d/textures/GpuTexture;D)V"
      )
   )
   private boolean skipClearOnSyncedFrame(CommandEncoder encoder, GpuTexture texture, int color, GpuTexture depthAttachment, double depth) {
      this.rendersThisFrame = ClientInitializer.inst().frameRenderGate().shouldRenderFrame();
      return this.rendersThisFrame;
   }

   @WrapWithCondition(method = "render(Z)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/GameRenderer;render(Lnet/minecraft/client/render/RenderTickCounter;Z)V"))
   private boolean skipRenderOnSyncedFrame(GameRenderer renderer, RenderTickCounter tickCounter, boolean tick) {
      return this.rendersThisFrame;
   }
}
