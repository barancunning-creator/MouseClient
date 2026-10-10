package com.mouseclient.internal;

public final class ArgbColor {
   public static final int WHITE = -1;
   public static final int BLACK = -16777216;
   public static final int RED = -65536;
   public static final int GREEN = -16711936;
   public static final int BLUE = -16776961;

   private static int green(int color) {
      return color >>> 8 & 0xFF;
   }

   public static int lerp(int start, int end, float progress) {
      float clampedProgress = Float.isFinite(progress) ? Math.clamp(progress, 0.0F, 1.0F) : 0.0F;
      int interpolatedAlpha = Math.round(alpha(ensureAlpha(start)) + (alpha(ensureAlpha(end)) - alpha(ensureAlpha(start))) * clampedProgress);
      int interpolatedRed = Math.round(red(start) + (red(end) - red(start)) * clampedProgress);
      int interpolatedGreen = Math.round(green(start) + (green(end) - green(start)) * clampedProgress);
      int interpolatedBlue = Math.round(blue(start) + (blue(end) - blue(start)) * clampedProgress);
      return argb(interpolatedRed, interpolatedGreen, interpolatedBlue, interpolatedAlpha);
   }

   private static int red(int color) {
      return color >>> 16 & 0xFF;
   }

   public static int alpha(int color) {
      return color >>> 24 & 0xFF;
   }

   public static int argb(int red, int green, int blue, int alpha) {
      return (alpha & 0xFF) << 24 | (red & 0xFF) << 16 | (green & 0xFF) << 8 | blue & 0xFF;
   }

   private ArgbColor() {
   }

   public static int multiplyAlpha(int color, float opacity) {
      if (!Float.isFinite(opacity)) {
         opacity = 1.0F;
      }

      return withAlpha(color, Math.clamp(Math.round(alpha(ensureAlpha(color)) * opacity), 0, 255));
   }

   public static int withAlpha(int color, int alpha) {
      return color & (16777215) | (alpha & 0xFF) << 24;
   }

   private static int blue(int color) {
      return color & 0xFF;
   }

   private static int ensureAlpha(int color) {
      return (color & (-16777216)) == 0 && (color & (16777215)) != 0 ? color | (-16777216) : color;
   }

   public static int rgb(int red, int green, int blue) {
      return argb(red, green, blue, 255);
   }
}
