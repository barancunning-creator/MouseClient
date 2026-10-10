package com.mouseclient.internal;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;

public final class SceneColorCopy {
   
   private static SimpleFramebuffer colorCopyFramebuffer;
   private static long lastCopyMillis;

   public static GpuTextureView copyColor(Framebuffer source) {
      if (source != null && source.getColorAttachment() != null && source.textureWidth > 0 && source.textureHeight > 0) {
         if (colorCopyFramebuffer == null) {
            colorCopyFramebuffer = new SimpleFramebuffer(("mouseclient_scene_copy"), source.textureWidth, source.textureHeight, false);
         }

         if (colorCopyFramebuffer.textureWidth != source.textureWidth || colorCopyFramebuffer.textureHeight != source.textureHeight) {
            colorCopyFramebuffer.resize(source.textureWidth, source.textureHeight);
         }

         if (colorCopyFramebuffer.getColorAttachment() == null) {
            return null;
         }

         RenderSystem.getDevice()
            .createCommandEncoder()
            .copyTextureToTexture(source.getColorAttachment(), colorCopyFramebuffer.getColorAttachment(), 0, 0, 0, 0, 0, source.textureWidth, source.textureHeight);
         lastCopyMillis = System.currentTimeMillis();
         return colorCopyFramebuffer.getColorAttachmentView();
      } else {
         return null;
      }
   }

   public static void release() {
      if (colorCopyFramebuffer != null) {
         colorCopyFramebuffer.delete();
         colorCopyFramebuffer = null;
      }
   }

   private SceneColorCopy() {
   }

   public static void releaseIfIdle() {
      if (colorCopyFramebuffer != null && System.currentTimeMillis() - lastCopyMillis > (5000L)) {
         release();
      }
   }
}
