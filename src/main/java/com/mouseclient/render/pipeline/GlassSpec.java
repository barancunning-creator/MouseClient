package com.mouseclient.render.pipeline;

import net.minecraft.client.gui.DrawContext;

public record GlassSpec(
   float x,
   float y,
   float width,
   float height,
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
   int splitIndex
) {
   private static final float DEFAULT_BLUR_RADIUS = 30.0F;

   private static float radiusValue(float[] radii, int index) {
      return radii != null && index >= 0 && index < radii.length ? radii[index] : 0.0F;
   }

   public GlassSpec withBlurRadius(float blurRadius) {
      return new GlassSpec(
         this.x,
         this.y,
         this.width,
         this.height,
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
         this.colorOffset
      );
   }

   public GlassSpec withRainbow(float spread, float saturation) {
      int encodedSpread = Math.max(0, Math.min(255, Math.round(spread * 255.0F)));
      int encodedSaturation = Math.max(0, Math.min(255, Math.round(saturation * 255.0F)));
      int rainbowParameters = 0xFF000000 | encodedSpread << 16 | encodedSaturation << 8;
      return new GlassSpec(
         this.x,
         this.y,
         this.width,
         this.height,
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
         this.colorOffset
      );
   }

   public GlassSpec withSplitIndex(int splitIndex) {
      return new GlassSpec(
         this.x,
         this.y,
         this.width,
         this.height,
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
         splitIndex
      );
   }

   public GlassSpec(
      float x,
      float y,
      float width,
      float height,
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
      float z
   ) {
      this(x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft, color, globalAlpha, fresnelPower, fresnelColor, baseAlpha, fresnelInvert, fresnelMix, distortStrength, squirt, z, 30.0F, color, 0.0F);
   }

   public void render(DrawContext context) {
      GlassRenderer.getInstance().render(context, this);
   }

   public GlassSpec(
      float x,
      float y,
      float width,
      float height,
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
      float colorOffset
   ) {
      this(x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft, color, globalAlpha, fresnelPower, fresnelColor, baseAlpha, fresnelInvert, fresnelMix, distortStrength, squirt, z, blurRadius, secondColor, colorOffset, 0);
   }

   public GlassSpec withSecondColor(int secondColor, float colorOffset) {
      return new GlassSpec(
         this.x,
         this.y,
         this.width,
         this.height,
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
         colorOffset
      );
   }

   public GlassSpec(
      float x,
      float y,
      float width,
      float height,
      float[] radii,
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
         radiusValue(radii, 0),
         radiusValue(radii, 1),
         radiusValue(radii, 2),
         radiusValue(radii, 3),
         color,
         globalAlpha,
         fresnelPower,
         fresnelColor,
         baseAlpha,
         fresnelInvert,
         fresnelMix,
         distortStrength,
         squirt,
         z
      );
   }

   public boolean visible() {
      return this.width > 0.0F
         && this.height > 0.0F
         && this.globalAlpha > 0.0F
         && (this.baseAlpha > 0.0F || this.fresnelColor >>> 24 != 0 || this.color >>> 24 != 0);
   }
}
