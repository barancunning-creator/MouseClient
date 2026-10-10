package com.mouseclient.render.avatar;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import java.awt.image.BufferedImage;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureSetup;

public final class FontAtlasPage implements AutoCloseable {
   private static int nextPageId;
   private final int size;
   private final NativeImage pixels;
   private final NativeImageBackedTexture texture;
   private final TextureSetup textureSetup;
   private int cursorX = 1;
   private int cursorY = 1;
   private int rowHeight;
   private int dirtyMinX;
   private int dirtyMinY;
   private int dirtyMaxX;
   private int dirtyMaxY;

   public TextureSetup getTextureSetup() {
      return this.textureSetup;
   }

   public FontAtlasPage.AtlasSlot allocate(int width, int height) {
      int paddedWidth = width + 2;
      int paddedHeight = height + 2;
      if (paddedWidth <= this.size && paddedHeight <= this.size) {
         if (this.cursorX + paddedWidth > this.size) {
            this.cursorX = 1;
            this.cursorY = this.cursorY + this.rowHeight;
            this.rowHeight = 0;
         }

         if (this.cursorY + paddedHeight > this.size) {
            return null;
         }

         int slotX = this.cursorX;
         int slotY = this.cursorY;
         this.cursorX += paddedWidth;
         this.rowHeight = Math.max(this.rowHeight, paddedHeight);
         return new FontAtlasPage.AtlasSlot(
            this,
            slotX,
            slotY,
            (float)slotX / this.size,
            (float)slotY / this.size,
            (float)(slotX + width) / this.size,
            (float)(slotY + height) / this.size
         );
      } else {
         return null;
      }
   }

   @Override
   public void close() {
      this.texture.close();
   }

   public void writeImage(BufferedImage image, int x, int y) {
      int width = image.getWidth();
      int height = image.getHeight();

      for (int row = 0; row < height; row++) {
         for (int column = 0; column < width; column++) {
            this.pixels.setColor(x + column, y + row, argbToAbgr(image.getRGB(column, row)));
         }
      }

      this.dirtyMinX = Math.min(this.dirtyMinX, x);
      this.dirtyMinY = Math.min(this.dirtyMinY, y);
      this.dirtyMaxX = Math.max(this.dirtyMaxX, x + width);
      this.dirtyMaxY = Math.max(this.dirtyMaxY, y + height);
   }

   public FontAtlasPage(int size) {
      this.dirtyMinX = (2147483647);
      this.dirtyMinY = (2147483647);
      this.dirtyMaxX = (-2147483648);
      this.dirtyMaxY = (-2147483648);
      this.size = size;
      this.pixels = new NativeImage(size, size, true);
      this.texture = new NativeImageBackedTexture(() -> "mouseclient_font_atlas_" + nextPageId++, this.pixels);
      this.textureSetup = TextureSetup.of(this.texture.getGlTextureView(), RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
   }

   private static int argbToAbgr(int color) {
      int alpha = color >>> 24 & 0xFF;
      int red = color >>> 16 & 0xFF;
      int green = color >>> 8 & 0xFF;
      int blue = color & 0xFF;
      return alpha << 24 | blue << 16 | green << 8 | red;
   }

   public void uploadDirtyRegion() {
      if (this.dirtyMaxX > this.dirtyMinX && this.dirtyMaxY > this.dirtyMinY) {
         if (this.texture.getGlTexture() != null) {
            int x = Math.max(0, this.dirtyMinX);
            int y = Math.max(0, this.dirtyMinY);
            int width = Math.min(this.size, this.dirtyMaxX) - x;
            int height = Math.min(this.size, this.dirtyMaxY) - y;
            if (width > 0 && height > 0) {
               RenderSystem.getDevice()
                  .createCommandEncoder()
                  .writeToTexture(this.texture.getGlTexture(), this.pixels, 0, 0, x, y, width, height, x, y);
            }

            this.dirtyMinX = (2147483647);
            this.dirtyMinY = (2147483647);
            this.dirtyMaxX = (-2147483648);
            this.dirtyMaxY = (-2147483648);
         }
      }
   }

   public record AtlasSlot(FontAtlasPage page, int x, int y, float uMin, float vMin, float uMax, float vMax) {
      public float vMin() {
         return this.vMin;
      }

      public float vMax() {
         return this.vMax;
      }

      public float uMin() {
         return this.uMin;
      }

      public float uMax() {
         return this.uMax;
      }
   }
}
