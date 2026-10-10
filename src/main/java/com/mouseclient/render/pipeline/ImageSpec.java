package com.mouseclient.render.pipeline;

public record ImageSpec(
   String texture,
   float x,
   float y,
   float size,
   float radiusTL,
   float radiusTR,
   float radiusBR,
   float radiusBL,
   float smoothness,
   int colorTopLeft,
   int colorTopRight,
   int colorBottomRight,
   int colorBottomLeft,
   float uMin,
   float vMin,
   float uMax,
   float vMax,
   float rotationDegrees,
   float rotationOriginX,
   float rotationOriginY,
   float explicitWidth,
   float explicitHeight,
   boolean nearest,
   boolean glow
) {
   public static final int DEFAULT_COLOR = -1;
   public static final float DEFAULT_SMOOTHNESS = 0.0F;

   public boolean visible() {
      return this.texture != null
         && !this.texture.isBlank()
         && (this.size > 0.0F || this.explicitWidth > 0.0F && this.explicitHeight > 0.0F)
         && (
            effectiveAlpha(this.colorTopLeft) > 0
               || effectiveAlpha(this.colorTopRight) > 0
               || effectiveAlpha(this.colorBottomRight) > 0
               || effectiveAlpha(this.colorBottomLeft) > 0
         );
   }

   public float vMin() {
      return this.vMin;
   }

   public ImageSpec withColors(int... colors) {
      if (colors != null && colors.length != 0) {
         if (colors.length == 1) {
            return this.withColors(colors[0], colors[0], colors[0], colors[0]);
         } else if (colors.length == 2) {
            return this.withColors(colors[0], colors[1], colors[1], colors[0]);
         } else {
            return colors.length == 3 ? this.withColors(colors[0], colors[1], colors[2], colors[0]) : this.withColors(colors[0], colors[1], colors[2], colors[3]);
         }
      } else {
         return this;
      }
   }

   public ImageSpec withUv(float uMin, float vMin, float uMax, float vMax) {
      return new ImageSpec(
         this.texture,
         this.x,
         this.y,
         this.size,
         this.radiusTL,
         this.radiusTR,
         this.radiusBR,
         this.radiusBL,
         this.smoothness,
         this.colorTopLeft,
         this.colorTopRight,
         this.colorBottomRight,
         this.colorBottomLeft,
         uMin,
         vMin,
         uMax,
         vMax,
         this.rotationDegrees,
         this.rotationOriginX,
         this.rotationOriginY,
         this.explicitWidth,
         this.explicitHeight,
         this.nearest,
         this.glow
      );
   }

   private ImageSpec withColors(int colorTopLeft, int colorTopRight, int colorBottomRight, int colorBottomLeft) {
      return new ImageSpec(
         this.texture,
         this.x,
         this.y,
         this.size,
         this.radiusTL,
         this.radiusTR,
         this.radiusBR,
         this.radiusBL,
         this.smoothness,
         colorTopLeft,
         colorTopRight,
         colorBottomRight,
         colorBottomLeft,
         this.uMin,
         this.vMin,
         this.uMax,
         this.vMax,
         this.rotationDegrees,
         this.rotationOriginX,
         this.rotationOriginY,
         this.explicitWidth,
         this.explicitHeight,
         this.nearest,
         this.glow
      );
   }

   public float uMax() {
      return this.uMax;
   }

   public float vMax() {
      return this.vMax;
   }

   public float uMin() {
      return this.uMin;
   }

   public ImageSpec(String texture, float x, float y, float size, float radius, int color) {
      this(texture, x, y, size, radius, radius, radius, radius, 0.0F, color, color, color, color, 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, false, false);
   }

   public ImageSpec(
      String texture, float x, float y, float size, float radiusTL, float radiusTR, float radiusBR, float radiusBL, int colorTopLeft, int colorTopRight, int colorBottomRight, int colorBottomLeft
   ) {
      this(texture, x, y, size, radiusTL, radiusTR, radiusBR, radiusBL, 0.0F, colorTopLeft, colorTopRight, colorBottomRight, colorBottomLeft, 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, false, false);
   }

   private static int effectiveAlpha(int color) {
      return color >>> 24 & 0xFF;
   }

   public ImageSpec(String texture, float x, float y, float size, float radius, int colorTopLeft, int colorTopRight, int colorBottomRight, int colorBottomLeft) {
      this(texture, x, y, size, radius, radius, radius, radius, 0.0F, colorTopLeft, colorTopRight, colorBottomRight, colorBottomLeft, 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, false, false);
   }

   public ImageSpec withRotation(float rotationDegrees, float rotationOriginX, float rotationOriginY) {
      return new ImageSpec(
         this.texture,
         this.x,
         this.y,
         this.size,
         this.radiusTL,
         this.radiusTR,
         this.radiusBR,
         this.radiusBL,
         this.smoothness,
         this.colorTopLeft,
         this.colorTopRight,
         this.colorBottomRight,
         this.colorBottomLeft,
         this.uMin,
         this.vMin,
         this.uMax,
         this.vMax,
         rotationDegrees,
         rotationOriginX,
         rotationOriginY,
         this.explicitWidth,
         this.explicitHeight,
         this.nearest,
         this.glow
      );
   }

   public ImageSpec(String texture, float x, float y, float size, float radius) {
      this(texture, x, y, size, radius, radius, radius, radius, 0.0F, -1, -1, -1, -1, 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, false, false);
   }

   public ImageSpec withGlow() {
      return this.glow
         ? this
         : new ImageSpec(
            this.texture,
            this.x,
            this.y,
            this.size,
            this.radiusTL,
            this.radiusTR,
            this.radiusBR,
            this.radiusBL,
            this.smoothness,
            this.colorTopLeft,
            this.colorTopRight,
            this.colorBottomRight,
            this.colorBottomLeft,
            this.uMin,
            this.vMin,
            this.uMax,
            this.vMax,
            this.rotationDegrees,
            this.rotationOriginX,
            this.rotationOriginY,
            this.explicitWidth,
            this.explicitHeight,
            this.nearest,
            true
         );
   }

   public ImageSpec withSmoothness(float smoothness) {
      return new ImageSpec(
         this.texture,
         this.x,
         this.y,
         this.size,
         this.radiusTL,
         this.radiusTR,
         this.radiusBR,
         this.radiusBL,
         smoothness,
         this.colorTopLeft,
         this.colorTopRight,
         this.colorBottomRight,
         this.colorBottomLeft,
         this.uMin,
         this.vMin,
         this.uMax,
         this.vMax,
         this.rotationDegrees,
         this.rotationOriginX,
         this.rotationOriginY,
         this.explicitWidth,
         this.explicitHeight,
         this.nearest,
         this.glow
      );
   }

   public float radius() {
      return this.radiusTL;
   }

   public ImageSpec(String texture, float x, float y, float explicitWidth, float explicitHeight, float radius, int color) {
      this(
         texture,
         x,
         y,
         0.0F,
         radius,
         radius,
         radius,
         radius,
         0.0F,
         color,
         color,
         color,
         color,
         0.0F,
         0.0F,
         1.0F,
         1.0F,
         0.0F,
         0.0F,
         0.0F,
         Math.max(0.0F, explicitWidth),
         Math.max(0.0F, explicitHeight),
         false,
         false
      );
   }

   public ImageSpec withNearest() {
      return this.nearest
         ? this
         : new ImageSpec(
            this.texture,
            this.x,
            this.y,
            this.size,
            this.radiusTL,
            this.radiusTR,
            this.radiusBR,
            this.radiusBL,
            this.smoothness,
            this.colorTopLeft,
            this.colorTopRight,
            this.colorBottomRight,
            this.colorBottomLeft,
            this.uMin,
            this.vMin,
            this.uMax,
            this.vMax,
            this.rotationDegrees,
            this.rotationOriginX,
            this.rotationOriginY,
            this.explicitWidth,
            this.explicitHeight,
            true,
            this.glow
         );
   }
}
