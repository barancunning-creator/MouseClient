package com.mouseclient.render.pipeline;

public final class BlurSpecBuilder {
   private float x;
   private float y;
   private float width;
   private float height;
   private float radiusTopLeft;
   private float radiusTopRight;
   private float radiusBottomRight;
   private float radiusBottomLeft;
   private float smoothness = 1.0F;
   private float blurRadius;
   private int color;

   public BlurSpecBuilder withSmoothness(float smoothness) {
      this.smoothness = smoothness;
      return this;
   }

   public BlurSpecBuilder withRoundedBounds(float x, float y, float width, float height, float radius) {
      return this.withBounds(x, y, width, height).withRadius(radius);
   }

   public BlurSpecBuilder withBlurRadius(float blurRadius) {
      this.blurRadius = blurRadius;
      return this;
   }

   public BlurSpecBuilder withColor(int color) {
      this.color = color;
      return this;
   }

   public BlurSpec build() {
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
         this.color
      );
   }

   public BlurSpecBuilder withRadius(float radius) {
      this.radiusTopLeft = radius;
      this.radiusTopRight = radius;
      this.radiusBottomRight = radius;
      this.radiusBottomLeft = radius;
      return this;
   }

   public BlurSpecBuilder withRadii(float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft) {
      this.radiusTopLeft = radiusTopLeft;
      this.radiusTopRight = radiusTopRight;
      this.radiusBottomRight = radiusBottomRight;
      this.radiusBottomLeft = radiusBottomLeft;
      return this;
   }

   public BlurSpecBuilder withBounds(float x, float y, float width, float height) {
      this.x = x;
      this.y = y;
      this.width = width;
      this.height = height;
      return this;
   }

   public BlurSpecBuilder() {
      this.blurRadius = (16.0F);
      this.color = -1;
   }
}
