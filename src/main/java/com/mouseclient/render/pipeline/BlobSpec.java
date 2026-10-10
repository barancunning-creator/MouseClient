package com.mouseclient.render.pipeline;

public record BlobSpec(
   float x,
   float y,
   float width,
   float height,
   float centerX,
   float centerY,
   float radiusX,
   float radiusY,
   float falloff,
   float feather,
   float clipTL,
   float clipTR,
   float clipBR,
   float clipBL,
   int color
) {
   public boolean visible() {
      return this.width > 0.0F && this.height > 0.0F && this.radiusX > 0.0F && this.radiusY > 0.0F && this.color >>> 24 != 0;
   }

   public float height() {
      return this.height;
   }

   public float width() {
      return this.width;
   }
}
