package com.mouseclient.render.avatar;

public record LayoutGlyph(
   FontAtlasPage page,
   float left,
   float top,
   float right,
   float bottom,
   float uMin,
   float vMin,
   float uMax,
   float vMax
) {
   public float bottom() {
      return this.bottom;
   }

   public float vMin() {
      return this.vMin;
   }

   public float uMax() {
      return this.uMax;
   }

   public float uMin() {
      return this.uMin;
   }

   public float vMax() {
      return this.vMax;
   }

   public float top() {
      return this.top;
   }

   public float right() {
      return this.right;
   }

   public float left() {
      return this.left;
   }
}
