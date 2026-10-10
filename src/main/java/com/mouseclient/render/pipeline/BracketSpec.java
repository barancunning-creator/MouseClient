package com.mouseclient.render.pipeline;

public record BracketSpec(float x, float y, float size, float radius, float stroke, float smoothness, int quadrant, int color) {
   public static final float DEFAULT_SMOOTHNESS = 0.25F;
   public static final int QUADRANT_0 = 0;
   public static final int QUADRANT_1 = 1;
   public static final int QUADRANT_2 = 2;
   public static final int QUADRANT_3 = 3;

   public BracketSpec(float x, float y, float size, float radius, float stroke, int quadrant, int color) {
      this(x, y, size, radius, stroke, 0.25F, quadrant, color);
   }

   public boolean visible() {
      return this.size > 0.0F && this.stroke > 0.0F && this.color >>> 24 != 0;
   }
}
