package com.mouseclient.gui;

public record RasterGlyphQuad(
   float topLeftX,
   float topLeftY,
   float bottomLeftX,
   float bottomLeftY,
   float bottomRightX,
   float bottomRightY,
   float topRightX,
   float topRightY,
   float uMin,
   float vMin,
   float uMax,
   float vMax,
   int colorTopLeft,
   int colorTopRight,
   int colorBottomRight,
   int colorBottomLeft
) {
   public float uMin() {
      return this.uMin;
   }

   public float vMax() {
      return this.vMax;
   }

   public float bottomLeftX() {
      return this.bottomLeftX;
   }

   public float topLeftY() {
      return this.topLeftY;
   }

   public float bottomLeftY() {
      return this.bottomLeftY;
   }

   public float topRightY() {
      return this.topRightY;
   }

   public float topRightX() {
      return this.topRightX;
   }

   public float vMin() {
      return this.vMin;
   }

   public float bottomRightX() {
      return this.bottomRightX;
   }

   public float topLeftX() {
      return this.topLeftX;
   }

   public float bottomRightY() {
      return this.bottomRightY;
   }

   public float uMax() {
      return this.uMax;
   }
}
