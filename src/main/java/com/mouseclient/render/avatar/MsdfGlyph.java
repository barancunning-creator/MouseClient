package com.mouseclient.render.avatar;

public record MsdfGlyph(
   boolean drawable,
   float advance,
   float planeLeft,
   float planeTop,
   float planeRight,
   float planeBottom,
   float uMin,
   float vMin,
   float uMax,
   float vMax
) {
   public float vMax() {
      return this.vMax;
   }

   public float uMax() {
      return this.uMax;
   }

   public float vMin() {
      return this.vMin;
   }

   public float uMin() {
      return this.uMin;
   }

   public static MsdfGlyph nonDrawable(float advance) {
      return new MsdfGlyph(false, advance, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
   }
}
