package com.mouseclient.render.pipeline;

public record AtlasTextSpec(
   String fontName,
   String text,
   float x,
   float y,
   float size,
   int colorTopLeft,
   int colorTopRight,
   int colorBottomRight,
   int colorBottomLeft,
   float rotationDegrees,
   float rotationOriginX,
   float rotationOriginY,
   boolean fadeLeft,
   boolean fadeRight,
   float fadeLeftX,
   float fadeRightX,
   float fadeWidth,
   float fadeLeftStrength,
   float fadeRightStrength,
   boolean snapOrigin
) {
   public boolean visible() {
      return this.text != null
         && !this.text.isEmpty()
         && this.size > 0.0F
         && ((this.colorTopLeft | this.colorTopRight | this.colorBottomRight | this.colorBottomLeft) >>> 24 & 0xFF) > 0;
   }

   public AtlasTextSpec withHorizontalFade(float fadeLeftX, float fadeRightX, float fadeWidth, float fadeLeftStrength, float fadeRightStrength) {
      return new AtlasTextSpec(
         this.fontName,
         this.text,
         this.x,
         this.y,
         this.size,
         this.colorTopLeft,
         this.colorTopRight,
         this.colorBottomRight,
         this.colorBottomLeft,
         this.rotationDegrees,
         this.rotationOriginX,
         this.rotationOriginY,
         fadeLeftStrength > 0.001F,
         fadeRightStrength > 0.001F,
         fadeLeftX,
         fadeRightX,
         fadeWidth,
         clamp01(fadeLeftStrength),
         clamp01(fadeRightStrength),
         this.snapOrigin
      );
   }

   public AtlasTextSpec withoutPixelSnap() {
      return new AtlasTextSpec(
         this.fontName,
         this.text,
         this.x,
         this.y,
         this.size,
         this.colorTopLeft,
         this.colorTopRight,
         this.colorBottomRight,
         this.colorBottomLeft,
         this.rotationDegrees,
         this.rotationOriginX,
         this.rotationOriginY,
         this.fadeLeft,
         this.fadeRight,
         this.fadeLeftX,
         this.fadeRightX,
         this.fadeWidth,
         this.fadeLeftStrength,
         this.fadeRightStrength,
         false
      );
   }

   public AtlasTextSpec withHorizontalFade(float fadeLeftX, float fadeRightX, float fadeWidth, boolean fadeLeft, boolean fadeRight) {
      return this.withHorizontalFade(fadeLeftX, fadeRightX, fadeWidth, fadeLeft ? 1.0F : 0.0F, fadeRight ? 1.0F : 0.0F);
   }

   public AtlasTextSpec(String fontName, String text, float x, float y, float size, int color) {
      this(fontName, text, x, y, size, color, color, color, color);
   }

   public AtlasTextSpec(String fontName, String text, float x, float y, float size, int colorTopLeft, int colorTopRight, int colorBottomRight, int colorBottomLeft) {
      this(fontName, text, x, y, size, colorTopLeft, colorTopRight, colorBottomRight, colorBottomLeft, 0.0F, x, y, false, false, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, true);
   }

   public boolean hasHorizontalFade() {
      return (this.fadeLeftStrength > 0.001F || this.fadeRightStrength > 0.001F) && this.fadeWidth > 0.0F && this.fadeRightX > this.fadeLeftX;
   }

   private static float clamp01(float value) {
      return Math.max(0.0F, Math.min(1.0F, value));
   }
}
