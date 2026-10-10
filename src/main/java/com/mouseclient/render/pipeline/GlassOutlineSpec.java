package com.mouseclient.render.pipeline;

import net.minecraft.client.gui.DrawContext;

public record GlassOutlineSpec(
   float x,
   float y,
   float width,
   float height,
   float radiusTopLeft,
   float radiusTopRight,
   float radiusBottomRight,
   float radiusBottomLeft,
   float thickness,
   int color,
   float globalAlpha,
   float fresnelPower,
   int fresnelColor,
   float baseAlpha,
   boolean fresnelInvert,
   float fresnelMix,
   float distortStrength,
   float squirt,
   float smoothness,
   float z
) {
   public static final float DEFAULT_SMOOTHNESS = 0.5F;

   public boolean visible() {
      return this.width > 0.0F
         && this.height > 0.0F
         && this.thickness > 0.0F
         && this.globalAlpha > 0.0F
         && (this.color >>> 24 != 0 || this.baseAlpha > 0.0F || this.fresnelColor >>> 24 != 0);
   }

   public GlassOutlineSpec(
      float x,
      float y,
      float width,
      float height,
      float[] radii,
      float thickness,
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
         thickness,
         color,
         globalAlpha,
         fresnelPower,
         fresnelColor,
         baseAlpha,
         fresnelInvert,
         fresnelMix,
         distortStrength,
         squirt,
         0.5F,
         z
      );
   }

   private static float radiusValue(float[] radii, int index) {
      return radii != null && index >= 0 && index < radii.length ? radii[index] : 0.0F;
   }

   public void render(DrawContext context) {
      GlassOutlineRenderer.getInstance().render(context, this);
   }
}
