package com.mouseclient.render.pipeline;

public record MsdfTextSpec(
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
   float fadeLeftX,
   float fadeRightX,
   float fadeWidth,
   float fadeLeftStrength,
   float fadeRightStrength
) {
   private static int alpha(int color) {
      return color >>> 24 & 0xFF;
   }

   private static float clamp01(float value) {
      return Math.max(0.0F, Math.min(1.0F, value));
   }

   public boolean hasHorizontalFade() {
      return (this.fadeLeftStrength > 0.001F || this.fadeRightStrength > 0.001F) && this.fadeWidth > 0.0F && this.fadeRightX > this.fadeLeftX;
   }

   public MsdfTextSpec(
      String fontName, String text, float x, float y, float size, int colorTopLeft, int colorTopRight, int colorBottomRight, int colorBottomLeft, float rotationDegrees, float rotationOriginX, float rotationOriginY
   ) {
      this(fontName, text, x, y, size, colorTopLeft, colorTopRight, colorBottomRight, colorBottomLeft, rotationDegrees, rotationOriginX, rotationOriginY, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
   }

   public MsdfTextSpec(String fontName, String text, float x, float y, float size, int color, float rotationDegrees, float rotationOriginX, float rotationOriginY) {
      this(fontName, text, x, y, size, color, color, color, color, rotationDegrees, rotationOriginX, rotationOriginY, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
   }

   public MsdfTextSpec(String fontName, String text, float x, float y, float size, int colorTopLeft, int colorTopRight, int colorBottomRight, int colorBottomLeft) {
      this(fontName, text, x, y, size, colorTopLeft, colorTopRight, colorBottomRight, colorBottomLeft, 0.0F, x, y, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
   }

   public MsdfTextSpec withHorizontalFade(float fadeLeftX, float fadeRightX, float fadeWidth, float fadeLeftStrength, float fadeRightStrength) {
      return new MsdfTextSpec(
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
         fadeLeftX,
         fadeRightX,
         fadeWidth,
         clamp01(fadeLeftStrength),
         clamp01(fadeRightStrength)
      );
   }

   public MsdfTextSpec(String fontName, String text, float x, float y, float size, int color) {
      this(fontName, text, x, y, size, color, color, color, color);
   }

   public boolean visible() {
      return this.text != null && !this.text.isEmpty() && !(this.size <= 0.0F)
         ? alpha(this.colorTopLeft) > 0 || alpha(this.colorTopRight) > 0 || alpha(this.colorBottomRight) > 0 || alpha(this.colorBottomLeft) > 0
         : false;
   }
}
