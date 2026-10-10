package com.mouseclient.render.pipeline;

import net.minecraft.client.gui.DrawContext;

public record SoftGlowSpec(
   float x,
   float y,
   float width,
   float height,
   float radiusTopLeft,
   float radiusTopRight,
   float radiusBottomRight,
   float radiusBottomLeft,
   int color,
   float softness,
   float intensity,
   float mode,
   float dirX,
   float dirY,
   float dirStrength,
   float falloff
) {
   public static final float MODE_FILL = 0.0F;
   public static final float MODE_RING = 1.0F;
   public static final float DEFAULT_FALLOFF = 2.0F;
   private static final float PAD_SIGMAS = 4.0F;
   private static final float MIN_PAD = 2.0F;

   public float pad() {
      return Math.max(this.softness * 4.0F, 2.0F);
   }

   public SoftGlowSpec asFill() {
      return new SoftGlowSpec(
         this.x,
         this.y,
         this.width,
         this.height,
         this.radiusTopLeft,
         this.radiusTopRight,
         this.radiusBottomRight,
         this.radiusBottomLeft,
         this.color,
         this.softness,
         this.intensity,
         0.0F,
         this.dirX,
         this.dirY,
         this.dirStrength,
         this.falloff
      );
   }

   public boolean visible() {
      return this.width >= 0.0F && this.height >= 0.0F && this.softness > 0.0F && this.intensity > 0.0F && this.color >>> 24 != 0;
   }

   public float extentX() {
      return this.width / 2.0F + this.pad();
   }

   public SoftGlowSpec(float x, float y, float width, float height, float radius, int color, float softness) {
      this(x, y, width, height, radius, radius, radius, radius, color, softness, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 2.0F);
   }

   public SoftGlowSpec withIntensity(float intensity) {
      return new SoftGlowSpec(
         this.x,
         this.y,
         this.width,
         this.height,
         this.radiusTopLeft,
         this.radiusTopRight,
         this.radiusBottomRight,
         this.radiusBottomLeft,
         this.color,
         this.softness,
         intensity,
         this.mode,
         this.dirX,
         this.dirY,
         this.dirStrength,
         this.falloff
      );
   }

   public SoftGlowSpec asRing() {
      return new SoftGlowSpec(
         this.x,
         this.y,
         this.width,
         this.height,
         this.radiusTopLeft,
         this.radiusTopRight,
         this.radiusBottomRight,
         this.radiusBottomLeft,
         this.color,
         this.softness,
         this.intensity,
         1.0F,
         this.dirX,
         this.dirY,
         this.dirStrength,
         this.falloff
      );
   }

   public SoftGlowSpec withFalloff(float falloff) {
      return new SoftGlowSpec(
         this.x,
         this.y,
         this.width,
         this.height,
         this.radiusTopLeft,
         this.radiusTopRight,
         this.radiusBottomRight,
         this.radiusBottomLeft,
         this.color,
         this.softness,
         this.intensity,
         this.mode,
         this.dirX,
         this.dirY,
         this.dirStrength,
         falloff
      );
   }

   public void render(DrawContext context) {
      SoftGlowRenderer.getInstance().render(context, this);
   }

   public SoftGlowSpec withRadii(float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft) {
      return new SoftGlowSpec(
         this.x,
         this.y,
         this.width,
         this.height,
         radiusTopLeft,
         radiusTopRight,
         radiusBottomRight,
         radiusBottomLeft,
         this.color,
         this.softness,
         this.intensity,
         this.mode,
         this.dirX,
         this.dirY,
         this.dirStrength,
         this.falloff
      );
   }

   public SoftGlowSpec withSoftness(float softness) {
      return new SoftGlowSpec(
         this.x,
         this.y,
         this.width,
         this.height,
         this.radiusTopLeft,
         this.radiusTopRight,
         this.radiusBottomRight,
         this.radiusBottomLeft,
         this.color,
         softness,
         this.intensity,
         this.mode,
         this.dirX,
         this.dirY,
         this.dirStrength,
         this.falloff
      );
   }

   public float centerX() {
      return this.x + this.width / 2.0F;
   }

   public float extentY() {
      return this.height / 2.0F + this.pad();
   }

   public float centerY() {
      return this.y + this.height / 2.0F;
   }

   public SoftGlowSpec withDirection(float dirX, float dirY, float dirStrength) {
      return new SoftGlowSpec(
         this.x,
         this.y,
         this.width,
         this.height,
         this.radiusTopLeft,
         this.radiusTopRight,
         this.radiusBottomRight,
         this.radiusBottomLeft,
         this.color,
         this.softness,
         this.intensity,
         this.mode,
         dirX,
         dirY,
         dirStrength,
         this.falloff
      );
   }
}
