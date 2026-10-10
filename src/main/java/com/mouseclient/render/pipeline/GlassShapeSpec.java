package com.mouseclient.render.pipeline;

import net.minecraft.client.gui.DrawContext;

public record GlassShapeSpec(
   float x,
   float y,
   float width,
   float height,
   float[] spans,
   int spanCount,
   float innerRadius,
   float radiusTopLeft,
   float radiusTopRight,
   float radiusBottomRight,
   float radiusBottomLeft,
   int color,
   float globalAlpha,
   float fresnelPower,
   int fresnelColor,
   float baseAlpha,
   boolean fresnelInvert,
   float fresnelMix,
   float distortStrength,
   float squirt,
   float z,
   float blurRadius,
   int secondColor,
   float colorOffset,
   float leftAligned,
   float bottomAnchored,
   int splitIndex
) {
   private static final float DEFAULT_BLUR_RADIUS = 30.0F;
   private static final float[] NO_SPANS = new float[0];

   public GlassShapeSpec withSpans(float[] spans, int spanCount, float innerRadius) {
      return new GlassShapeSpec(
         this.x,
         this.y,
         this.width,
         this.height,
         spans,
         spanCount,
         innerRadius,
         this.radiusTopLeft,
         this.radiusTopRight,
         this.radiusBottomRight,
         this.radiusBottomLeft,
         this.color,
         this.globalAlpha,
         this.fresnelPower,
         this.fresnelColor,
         this.baseAlpha,
         this.fresnelInvert,
         this.fresnelMix,
         this.distortStrength,
         this.squirt,
         this.z,
         this.blurRadius,
         this.secondColor,
         this.colorOffset,
         this.leftAligned,
         this.bottomAnchored,
         this.splitIndex
      );
   }

   public GlassShapeSpec withAlignment(boolean leftAligned, boolean bottomAnchored) {
      return new GlassShapeSpec(
         this.x,
         this.y,
         this.width,
         this.height,
         this.spans,
         this.spanCount,
         this.innerRadius,
         this.radiusTopLeft,
         this.radiusTopRight,
         this.radiusBottomRight,
         this.radiusBottomLeft,
         this.color,
         this.globalAlpha,
         this.fresnelPower,
         this.fresnelColor,
         this.baseAlpha,
         this.fresnelInvert,
         this.fresnelMix,
         this.distortStrength,
         this.squirt,
         this.z,
         this.blurRadius,
         this.secondColor,
         this.colorOffset,
         leftAligned ? 1.0F : 0.0F,
         bottomAnchored ? 1.0F : 0.0F,
         this.splitIndex
      );
   }

   public boolean visible() {
      return this.width > 0.0F
         && this.height > 0.0F
         && this.globalAlpha > 0.0F
         && (this.baseAlpha > 0.0F || this.fresnelColor >>> 24 != 0 || this.color >>> 24 != 0);
   }

   public GlassShapeSpec(
      float x,
      float y,
      float width,
      float height,
      float radius,
      int color,
      float globalAlpha,
      float fresnelPower,
      int fresnelColor,
      float baseAlpha,
      boolean fresnelInvert,
      float fresnelMix,
      float distortStrength,
      float squirt,
      float z
   ) {
      this(
         x,
         y,
         width,
         height,
         NO_SPANS,
         0,
         0.0F,
         radius,
         radius,
         radius,
         radius,
         color,
         globalAlpha,
         fresnelPower,
         fresnelColor,
         baseAlpha,
         fresnelInvert,
         fresnelMix,
         distortStrength,
         squirt,
         z,
         30.0F,
         color,
         0.0F,
         1.0F,
         0.0F,
         0
      );
   }

   public GlassShapeSpec withBlurRadius(float blurRadius) {
      return new GlassShapeSpec(
         this.x,
         this.y,
         this.width,
         this.height,
         this.spans,
         this.spanCount,
         this.innerRadius,
         this.radiusTopLeft,
         this.radiusTopRight,
         this.radiusBottomRight,
         this.radiusBottomLeft,
         this.color,
         this.globalAlpha,
         this.fresnelPower,
         this.fresnelColor,
         this.baseAlpha,
         this.fresnelInvert,
         this.fresnelMix,
         this.distortStrength,
         this.squirt,
         this.z,
         blurRadius,
         this.secondColor,
         this.colorOffset,
         this.leftAligned,
         this.bottomAnchored,
         this.splitIndex
      );
   }

   public void render(DrawContext context) {
      GlassShapeRenderer.getInstance().render(context, this);
   }

   public GlassShapeSpec withRainbow(float spread, float saturation) {
      int encodedSpread = Math.max(0, Math.min(255, Math.round(spread * 255.0F)));
      int encodedSaturation = Math.max(0, Math.min(255, Math.round(saturation * 255.0F)));
      int rainbowParameters = 0xFF000000 | encodedSpread << 16 | encodedSaturation << 8;
      return new GlassShapeSpec(
         this.x,
         this.y,
         this.width,
         this.height,
         this.spans,
         this.spanCount,
         this.innerRadius,
         this.radiusTopLeft,
         this.radiusTopRight,
         this.radiusBottomRight,
         this.radiusBottomLeft,
         this.color,
         this.globalAlpha,
         this.fresnelPower,
         this.fresnelColor,
         this.baseAlpha,
         this.fresnelInvert,
         this.fresnelMix,
         this.distortStrength,
         this.squirt,
         1.0F,
         this.blurRadius,
         rainbowParameters,
         this.colorOffset,
         this.leftAligned,
         this.bottomAnchored,
         this.splitIndex
      );
   }

   public GlassShapeSpec withSplitIndex(int splitIndex) {
      return new GlassShapeSpec(
         this.x,
         this.y,
         this.width,
         this.height,
         this.spans,
         this.spanCount,
         this.innerRadius,
         this.radiusTopLeft,
         this.radiusTopRight,
         this.radiusBottomRight,
         this.radiusBottomLeft,
         this.color,
         this.globalAlpha,
         this.fresnelPower,
         this.fresnelColor,
         this.baseAlpha,
         this.fresnelInvert,
         this.fresnelMix,
         this.distortStrength,
         this.squirt,
         this.z,
         this.blurRadius,
         this.secondColor,
         this.colorOffset,
         this.leftAligned,
         this.bottomAnchored,
         splitIndex
      );
   }

   public GlassShapeSpec withSecondColor(int secondColor, float colorOffset) {
      return new GlassShapeSpec(
         this.x,
         this.y,
         this.width,
         this.height,
         this.spans,
         this.spanCount,
         this.innerRadius,
         this.radiusTopLeft,
         this.radiusTopRight,
         this.radiusBottomRight,
         this.radiusBottomLeft,
         this.color,
         this.globalAlpha,
         this.fresnelPower,
         this.fresnelColor,
         this.baseAlpha,
         this.fresnelInvert,
         this.fresnelMix,
         this.distortStrength,
         this.squirt,
         this.z,
         this.blurRadius,
         secondColor,
         colorOffset,
         this.leftAligned,
         this.bottomAnchored,
         this.splitIndex
      );
   }
}
