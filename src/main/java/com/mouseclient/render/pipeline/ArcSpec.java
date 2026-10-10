package com.mouseclient.render.pipeline;

public record ArcSpec(float centerX, float centerY, float width, float lift, float thickness, float feather, int color) {
   public static final float DEFAULT_FEATHER = 0.85F;

   public float padX() {
      return this.thickness + this.feather;
   }

   public float spanX() {
      return this.halfWidth() + this.padX();
   }

   public float padY() {
      return this.thickness + this.feather;
   }

   public float halfWidth() {
      return this.width * 0.5F;
   }

   public boolean visible() {
      return this.width > 0.5F && this.color >>> 24 != 0;
   }

   public float spanY() {
      return this.lift + this.padY();
   }
}
