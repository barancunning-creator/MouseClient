package com.mouseclient.render.pipeline;

import net.minecraft.client.gui.DrawContext;

public record OutlineSpec(
   float x,
   float y,
   float width,
   float height,
   float radiusTopLeft,
   float radiusTopRight,
   float radiusBottomRight,
   float radiusBottomLeft,
   float thickness,
   int colorTopLeft,
   int colorTopRight,
   int colorBottomRight,
   int colorBottomLeft,
   float smoothness,
   float[] scissor
) {
   public static final float DEFAULT_SMOOTHNESS = 0.5F;
   public static final int DEFAULT_COLOR = -1;

   public void render(DrawContext context) {
      OutlineRenderer.getInstance().render(context, this);
   }

   public OutlineSpec withSmoothness(float smoothness) {
      return new OutlineSpec(
         this.x,
         this.y,
         this.width,
         this.height,
         this.radiusTopLeft,
         this.radiusTopRight,
         this.radiusBottomRight,
         this.radiusBottomLeft,
         this.thickness,
         this.colorTopLeft,
         this.colorTopRight,
         this.colorBottomRight,
         this.colorBottomLeft,
         smoothness,
         this.scissor
      );
   }

   public OutlineSpec(float x, float y, float width, float height, float radius, float thickness, int color) {
      this(x, y, width, height, radius, radius, radius, radius, thickness, color, color, color, color, 0.5F, null);
   }

   public OutlineSpec withColor(int color) {
      return new OutlineSpec(
         this.x,
         this.y,
         this.width,
         this.height,
         this.radiusTopLeft,
         this.radiusTopRight,
         this.radiusBottomRight,
         this.radiusBottomLeft,
         this.thickness,
         color,
         color,
         color,
         color,
         this.smoothness,
         this.scissor
      );
   }

   public OutlineSpec withScissor(float[] scissor) {
      return new OutlineSpec(
         this.x,
         this.y,
         this.width,
         this.height,
         this.radiusTopLeft,
         this.radiusTopRight,
         this.radiusBottomRight,
         this.radiusBottomLeft,
         this.thickness,
         this.colorTopLeft,
         this.colorTopRight,
         this.colorBottomRight,
         this.colorBottomLeft,
         this.smoothness,
         scissor
      );
   }

   public OutlineSpec(float x, float y, float width, float height, float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft, float thickness, int color) {
      this(x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft, thickness, color, color, color, color, 0.5F, null);
   }

   public boolean visible() {
      int combinedColor = this.colorTopLeft | this.colorTopRight | this.colorBottomRight | this.colorBottomLeft;
      return this.width > 0.0F && this.height > 0.0F && this.thickness > 0.0F && combinedColor >>> 24 != 0;
   }
}
