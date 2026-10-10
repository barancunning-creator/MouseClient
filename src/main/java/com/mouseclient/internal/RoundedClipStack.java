package com.mouseclient.internal;

import java.util.ArrayDeque;

public final class RoundedClipStack {
   private static float x;
   private static float y;
   private static float width;
   private static float height;
   private static float topLeftRadius;
   private static float topRightRadius;
   private static float bottomRightRadius;
   private static float bottomLeftRadius;
   private static boolean active;
   private static final ArrayDeque<float[]> savedClips = new ArrayDeque<>();

   public static float getTopRightRadius() {
      return topRightRadius;
   }

   public static float getHeight() {
      return height;
   }

   public static float getTopLeftRadius() {
      return topLeftRadius;
   }

   public static float getY() {
      return y;
   }

   public static void clear() {
      savedClips.clear();
      active = false;
   }

   public static float getBottomRightRadius() {
      return bottomRightRadius;
   }

   public static float getWidth() {
      return width;
   }

   public static float getBottomLeftRadius() {
      return bottomLeftRadius;
   }

   public static float getCoverage(float pointX, float pointY) {
      if (!active) {
         return 1.0F;
      }

      float centerX = x + width * (0.5F);
      float centerY = y + height * (0.5F);
      boolean leftHalf = pointX < centerX;
      boolean topHalf = pointY < centerY;
      float cornerRadius = topHalf ? (leftHalf ? topLeftRadius : topRightRadius) : (leftHalf ? bottomLeftRadius : bottomRightRadius);
      float edgeX = Math.abs(pointX - centerX) - (width * (0.5F) - cornerRadius);
      float edgeY = Math.abs(pointY - centerY) - (height * (0.5F) - cornerRadius);
      float outsideX = Math.max(edgeX, 0.0F);
      float outsideY = Math.max(edgeY, 0.0F);
      float signedDistance = Math.min(Math.max(edgeX, edgeY), 0.0F) + (float)Math.sqrt(outsideX * outsideX + outsideY * outsideY) - cornerRadius;
      return Math.max(0.0F, Math.min(1.0F, -signedDistance));
   }

   private RoundedClipStack() {
   }

   public static float getX() {
      return x;
   }

   public static int clipColor(int color, float pointX, float pointY) {
      float coverage = getCoverage(pointX, pointY);
      if (coverage >= (0.999F)) {
         return color;
      }

      if (coverage <= (0.001F)) {
         return color & (16777215);
      }

      int clippedAlpha = Math.round((color >>> 24 & 0xFF) * coverage);
      return clippedAlpha << 24 | color & (16777215);
   }

   public static void push(float clipX, float clipY, float clipWidth, float clipHeight, float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft) {
      if (active) {
         savedClips.push(new float[]{x, y, width, height, topLeftRadius, topRightRadius, bottomRightRadius, bottomLeftRadius});
      }

      x = clipX;
      y = clipY;
      width = clipWidth;
      height = clipHeight;
      topLeftRadius = radiusTopLeft;
      topRightRadius = radiusTopRight;
      bottomRightRadius = radiusBottomRight;
      bottomLeftRadius = radiusBottomLeft;
      active = true;
   }

   public static boolean isActive() {
      return active;
   }

   public static void pop() {
      float[] savedClip = savedClips.poll();
      if (savedClip != null) {
         x = savedClip[0];
         y = savedClip[1];
         width = savedClip[2];
         height = savedClip[3];
         topLeftRadius = savedClip[4];
         topRightRadius = savedClip[5];
         bottomRightRadius = savedClip[6];
         bottomLeftRadius = savedClip[7];
      } else {
         active = false;
      }
   }
}
