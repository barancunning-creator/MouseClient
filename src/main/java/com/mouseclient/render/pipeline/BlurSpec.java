package com.mouseclient.render.pipeline;

import net.minecraft.client.gui.DrawContext;

public record BlurSpec(
   float x,
   float y,
   float width,
   float height,
   float radiusTopLeft,
   float radiusTopRight,
   float radiusBottomRight,
   float radiusBottomLeft,
   float smoothness,
   float blurRadius,
   int colorTopLeft,
   int colorTopRight,
   int colorBottomRight,
   int colorBottomLeft
) {
   private static final int DEFAULT_COLOR = -1;

   public BlurSpec(float x, float y, float width, float height, float radius, float smoothness, float blurRadius) {
      this(x, y, width, height, radius, radius, radius, radius, smoothness, blurRadius, -1);
   }

   public BlurSpec withColor(int color) {
      return this.withColors(color, color, color, color);
   }

   public void render(DrawContext context) {
      GuiBlurRenderer.getInstance().renderInContext(context, this);
   }

   public BlurSpec withColors(int colorTopLeft, int colorTopRight, int colorBottomRight, int colorBottomLeft) {
      return new BlurSpec(
         this.x,
         this.y,
         this.width,
         this.height,
         this.radiusTopLeft,
         this.radiusTopRight,
         this.radiusBottomRight,
         this.radiusBottomLeft,
         this.smoothness,
         this.blurRadius,
         colorTopLeft,
         colorTopRight,
         colorBottomRight,
         colorBottomLeft
      );
   }

   public boolean visible() {
      int combinedColor = this.colorTopLeft | this.colorTopRight | this.colorBottomRight | this.colorBottomLeft;
      return this.width > 0.0F && this.height > 0.0F && combinedColor >>> 24 != 0 && this.blurRadius > 0.0F;
   }

   public BlurSpec(float x, float y, float width, float height, float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft, float smoothness, float blurRadius, int color) {
      this(x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft, smoothness, blurRadius, color, color, color, color);
   }

   public int color() {
      return this.colorTopLeft;
   }
}
