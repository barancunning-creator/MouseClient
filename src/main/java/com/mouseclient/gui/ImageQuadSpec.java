package com.mouseclient.gui;

public record ImageQuadSpec(
   float x,
   float y,
   float width,
   float height,
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
   float rotationOriginY
) {
   public float vMin() {
      return this.vMin;
   }

   public float vMax() {
      return this.vMax;
   }

   public float uMax() {
      return this.uMax;
   }

   public ImageQuadSpec(float x, float y, float width, float height, float radius, int colorTopLeft, int colorTopRight, int colorBottomRight, int colorBottomLeft) {
      this(x, y, width, height, radius, radius, radius, radius, 0.0F, colorTopLeft, colorTopRight, colorBottomRight, colorBottomLeft, 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, 0.0F, 0.0F);
   }

   public float uMin() {
      return this.uMin;
   }
}
