package com.mouseclient.render;

import com.mouseclient.util.entity.MinecraftAccess;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.gl.Framebuffer;

public final class DepthSnapshot implements MinecraftAccess {
   
   private static GpuTexture depthTexture;
   private static GpuTextureView snapshotView;
   private static boolean capturedThisFrame;
   private static long lastCaptureMillis;

   public static GpuTextureView depthView() {
      return capturedThisFrame && snapshotView != null && !snapshotView.isClosed() ? snapshotView : minecraftClient.getFramebuffer().getDepthAttachmentView();
   }

   public static void captureIfNeeded() {
      if (!capturedThisFrame) {
         Framebuffer framebuffer = minecraftClient.getFramebuffer();
         if (RenderSystem.getDevice() != null && framebuffer != null && framebuffer.getDepthAttachment() != null && framebuffer.textureWidth > 0 && framebuffer.textureHeight > 0) {
            GpuTexture texture = framebuffer.getDepthAttachment();
            if (depthTexture != null
               && (
                  depthTexture.isClosed()
                     || depthTexture.getWidth(0) != framebuffer.textureWidth
                     || depthTexture.getHeight(0) != framebuffer.textureHeight
                     || depthTexture.getFormat() != texture.getFormat()
               )) {
               release();
            }

            if (depthTexture == null) {
               depthTexture = RenderSystem.getDevice().createTexture(() -> ("mouseclient_depth_snapshot"), 15, texture.getFormat(), framebuffer.textureWidth, framebuffer.textureHeight, 1, 1);
               snapshotView = RenderSystem.getDevice().createTextureView(depthTexture);
            }

            RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(texture, depthTexture, 0, 0, 0, 0, 0, framebuffer.textureWidth, framebuffer.textureHeight);
            capturedThisFrame = true;
            lastCaptureMillis = System.currentTimeMillis();
         }
      }
   }

   private DepthSnapshot() {
   }

   public static void captureAndClearDepth() {
      Framebuffer framebuffer = minecraftClient.getFramebuffer();
      if (RenderSystem.getDevice() != null && framebuffer != null && framebuffer.getDepthAttachment() != null) {
         captureIfNeeded();
         RenderSystem.getDevice().createCommandEncoder().clearDepthTexture(framebuffer.getDepthAttachment(), 1.0);
      }
   }

   public static void release() {
      if (snapshotView != null) {
         snapshotView.close();
         snapshotView = null;
      }

      if (depthTexture != null) {
         depthTexture.close();
         depthTexture = null;
      }

      capturedThisFrame = false;
   }

   public static void beginFrame() {
      capturedThisFrame = false;
      if (depthTexture != null && System.currentTimeMillis() - lastCaptureMillis > (5000L)) {
         release();
      }
   }
}
