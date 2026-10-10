package com.mouseclient.render.pipeline;

import net.minecraft.client.gui.DrawContext;

public record Glow(
   float x,
   float y,
   float width,
   float height,
   float radiusTopLeft,
   float radiusTopRight,
   float radiusBottomRight,
   float radiusBottomLeft,
   int color,
   float intensity,
   float blurRadius,
   float alpha,
   int secondColor,
   float colorOffset,
   float[] spans,
   int spanCount,
   float leftAligned,
   float bottomAnchored,
   int splitIndex
) {
   private static final float DEFAULT_BLUR_RADIUS = 16.0F;
   private static final float DEFAULT_INTENSITY = 1.0F;
   private static final float MIN_PAD = 6.0F;

   public Glow withSecondColor(int secondColor, float colorOffset) {
      return new Glow(
         this.x,
         this.y,
         this.width,
         this.height,
         this.radiusTopLeft,
         this.radiusTopRight,
         this.radiusBottomRight,
         this.radiusBottomLeft,
         this.color,
         this.intensity,
         this.blurRadius,
         this.alpha,
         secondColor,
         colorOffset,
         this.spans,
         this.spanCount,
         this.leftAligned,
         this.bottomAnchored,
         this.splitIndex
      );
   }

   public Glow(float x, float y, float width, float height, float[] radii, int color, float intensity, float blurRadius, float alpha) {
      this(
         x,
         y,
         width,
         height,
         radiusValue(radii, 0),
         radiusValue(radii, 1),
         radiusValue(radii, 2),
         radiusValue(radii, 3),
         color,
         intensity,
         blurRadius,
         alpha,
         color,
         0.0F,
         null,
         0,
         1.0F,
         0.0F,
         0
      );
   }

   public float effectivePad() {
      return Math.max(this.blurRadius, 6.0F);
   }

   public void render(DrawContext context) {
      GuiGlowRenderer.getInstance().renderInContext(context, this);
   }

   public Glow withBoxesMode() {
      return new Glow(
         this.x,
         this.y,
         this.width,
         this.height,
         this.radiusTopLeft,
         this.radiusTopRight,
         this.radiusBottomRight,
         this.radiusBottomLeft,
         this.color,
         this.intensity,
         this.blurRadius,
         this.alpha,
         this.secondColor,
         this.colorOffset,
         this.spans,
         this.spanCount,
         -1.0F,
         this.bottomAnchored,
         this.splitIndex
      );
   }

   public boolean visible() {
      return this.width > 0.0F && this.height > 0.0F && this.alpha > 0.0F && this.intensity > 0.0F && this.color >>> 24 != 0;
   }

   private static float radiusValue(float[] radii, int index) {
      return radii != null && index >= 0 && index < radii.length ? radii[index] : 0.0F;
   }

   public Glow withAlignment(boolean leftAligned, boolean bottomAnchored) {
      return new Glow(
         this.x,
         this.y,
         this.width,
         this.height,
         this.radiusTopLeft,
         this.radiusTopRight,
         this.radiusBottomRight,
         this.radiusBottomLeft,
         this.color,
         this.intensity,
         this.blurRadius,
         this.alpha,
         this.secondColor,
         this.colorOffset,
         this.spans,
         this.spanCount,
         leftAligned ? 1.0F : 0.0F,
         bottomAnchored ? 1.0F : 0.0F,
         this.splitIndex
      );
   }

   public Glow(float x, float y, float width, float height, float radius, int color, float alpha) {
      this(x, y, width, height, radius, radius, radius, radius, color, 1.0F, 16.0F, alpha, color, 0.0F, null, 0, 1.0F, 0.0F, 0);
   }

   public Glow withSplitIndex(int splitIndex) {
      return new Glow(
         this.x,
         this.y,
         this.width,
         this.height,
         this.radiusTopLeft,
         this.radiusTopRight,
         this.radiusBottomRight,
         this.radiusBottomLeft,
         this.color,
         this.intensity,
         this.blurRadius,
         this.alpha,
         this.secondColor,
         this.colorOffset,
         this.spans,
         this.spanCount,
         this.leftAligned,
         this.bottomAnchored,
         splitIndex
      );
   }

   public Glow withSpans(float[] spans, int spanCount) {
      return new Glow(
         this.x,
         this.y,
         this.width,
         this.height,
         this.radiusTopLeft,
         this.radiusTopRight,
         this.radiusBottomRight,
         this.radiusBottomLeft,
         this.color,
         this.intensity,
         this.blurRadius,
         this.alpha,
         this.secondColor,
         this.colorOffset,
         spans,
         spanCount,
         this.leftAligned,
         this.bottomAnchored,
         this.splitIndex
      );
   }
}
