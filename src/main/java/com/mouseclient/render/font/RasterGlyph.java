package com.mouseclient.render.font;

import com.mouseclient.render.avatar.FontAtlasPage;

public record RasterGlyph(
   FontAtlasPage page, float uMin, float vMin, float uMax, float vMax, float xOffset, float yOffset, float width, float height, float advance
) {
   public float vMax() {
      return this.vMax;
   }

   public float uMin() {
      return this.uMin;
   }

   public float vMin() {
      return this.vMin;
   }

   public boolean drawable() {
      return this.page != null && this.width > 0.0F && this.height > 0.0F;
   }

   public float uMax() {
      return this.uMax;
   }

   public static RasterGlyph empty(float advance) {
      return new RasterGlyph(null, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, advance);
   }

   public RasterGlyph withAdvance(float advance) {
      return new RasterGlyph(
         this.page, this.uMin, this.vMin, this.uMax, this.vMax, this.xOffset, this.yOffset, this.width, this.height, advance
      );
   }
}
