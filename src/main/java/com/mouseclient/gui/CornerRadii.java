package com.mouseclient.gui;

public final class CornerRadii {
   private static final CornerRadii ZERO = new CornerRadii(0.0F, 0.0F, 0.0F, 0.0F, false);
   private final float bottomRight;
   private final float topRight;
   private final float bottomLeft;
   private final float topLeft;
   private final boolean squircle;

   public float getTopRight() {
      return this.topRight;
   }

   public static CornerRadii of(float bottomRight, float topRight, float bottomLeft, float topLeft, boolean squircle) {
      return new CornerRadii(bottomRight, topRight, bottomLeft, topLeft, squircle);
   }

   public boolean isSquircle() {
      return this.squircle;
   }

   public CornerRadii expanded(float amount) {
      return of(this.bottomRight + amount, this.topRight + amount, this.bottomLeft + amount, this.topLeft + amount, this.squircle);
   }

   public float getTopLeft() {
      return this.topLeft;
   }

   public float maxRadius() {
      return Math.max(Math.max(this.bottomRight, this.topRight), Math.max(this.bottomLeft, this.topLeft));
   }

   public static CornerRadii zero() {
      return ZERO;
   }

   public static CornerRadii uniform(float radius) {
      return of(radius, radius, radius, radius, false);
   }

   public float getBottomLeft() {
      return this.bottomLeft;
   }

   public static CornerRadii of(float bottomRight, float topRight, float bottomLeft, float topLeft) {
      return of(bottomRight, topRight, bottomLeft, topLeft, false);
   }

   private CornerRadii(float bottomRight, float topRight, float bottomLeft, float topLeft, boolean squircle) {
      this.bottomRight = bottomRight;
      this.topRight = topRight;
      this.bottomLeft = bottomLeft;
      this.topLeft = topLeft;
      this.squircle = squircle;
   }

   public float getBottomRight() {
      return this.bottomRight;
   }

   public static CornerRadii squircle(float radius) {
      return of(radius, radius, radius, radius, true);
   }
}
