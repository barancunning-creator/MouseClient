package com.mouseclient.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.concurrent.ThreadLocalRandom;
import lombok.Generated;

public final class ClientMath {
   private static float[] smoothedRandomValue;
   private static float[] randomTarget;
   private static long lastRandomUpdate = 0L;
   private static SecureRandom secureRandom = new SecureRandom();

   public static float lerp(float start, float end, float progress) {
      return start + progress * (end - start);
   }

   public static float clampUnit(float value) {
      return clamp(0.0F, 1.0F, value);
   }

   public static int clamp(int min, int max, int value) {
      return Math.max(min, Math.min(max, value));
   }

   public static float randomFloat(float min, float max) {
      validateRange(min, max);
      return min + ThreadLocalRandom.current().nextFloat() * (max - min);
   }

   public static double roundToStep(double value, double step) {
      if (step <= 0.0) {
         throw new IllegalArgumentException(("Step size must be positive"));
      }

      long var4 = Math.round(value / step);
      return var4 * step;
   }

   public static double reverseLerp(double start, double end, float progress) {
      return end + (start - end) * progress;
   }

   public static float roundToStep(float value, float step) {
      if (step <= 0.0F) {
         throw new IllegalArgumentException(("Step size must be positive"));
      }

      int var2 = Math.round(value / step);
      return var2 * step;
   }

   public static float randomFloatFromDouble(float min, float max) {
      validateRange(min, max);
      return min >= max ? min : (float)ThreadLocalRandom.current().nextDouble(min, max);
   }

   public static int lerp(int start, int end, float progress) {
      return start + (int)(progress * (end - start));
   }

   public static float randomTriangularFloat(float min, float max) {
      float var2 = secureRandom.nextFloat();
      float var3 = secureRandom.nextFloat();
      return min + (max - min) * ((var2 + var3) / 2.0F);
   }

   public static double randomDouble(double min, double max) {
      validateRange(min, max);
      return min >= max ? min : ThreadLocalRandom.current().nextDouble(min, max);
   }

   public static double lerp(double start, double end, double progress) {
      return start + progress * (end - start);
   }

   public static float clampDegrees(float value) {
      return clamp(0.0F, (360.0F), value);
   }

   public static float roundStepToThreeDecimals(float value, float step) {
      double var2 = (double)Math.round(value / step) * step;
      BigDecimal var4 = new BigDecimal(var2);
      var4 = var4.setScale(3, RoundingMode.HALF_UP);
      return var4.floatValue();
   }

   public static float toFloat(double value) {
      return (float)value;
   }

   public static double absoluteDifference(double first, double second) {
      return Math.abs(first - second);
   }

   @Generated
   public static SecureRandom secureRandom() {
      return secureRandom;
   }

   public static float randomSecureFloat(float min, float max) {
      return min + (max - min) * secureRandom.nextFloat();
   }

   public static float randomSkewedFloat(float min, float max) {
      double var2 = secureRandom.nextDouble();
      double var4 = secureRandom.nextDouble();
      double var6 = secureRandom.nextGaussian() * (0.019999999552965164D);
      double var8 = Math.pow(var2, 1.0 + secureRandom.nextDouble() * (0.7D));
      double var10 = (var4 * (0.8D) + (0.1D)) * (Math.log1p(var2 * (3.0D)) * (0.5D) + (0.5D));
      return (float)(min + (max - min) * var8 * var10 + var6);
   }

   private static void validateRange(double min, double max) {
      if (min > max) {
         throw new IllegalArgumentException(("Minimum value cannot be greater than maximum value."));
      }
   }

   public static double roundDecimals(double value, int decimals) {
      double var3 = Math.pow((10.0D), decimals);
      return Math.round(value * var3) / var3;
   }

   public static double clampUnit(double value) {
      return clamp(0.0, 1.0, value);
   }

   @Generated
   private ClientMath() {
      throw new UnsupportedOperationException(("This is a utility class and cannot be instantiated"));
   }

   public static long randomLong(long min, long max) {
      return secureRandom.nextLong(min, max);
   }

   public static float randomFloatRange(float min, float max) {
      validateRange(min, max);
      return min + ThreadLocalRandom.current().nextFloat() * (max - min);
   }

   public static double clamp(double min, double max, double value) {
      return Math.max(min, Math.min(max, value));
   }

   public static float randomFloatMixture(float min, float max) {
      switch (secureRandom.nextInt(4)) {
         case 0:
            return randomTriangularFloat(min, max);
         case 1:
            return randomSkewedFloat(min, max);
         case 2:
            return randomMathFloat(min, max);
         default:
            return randomSecureFloat(min, max);
      }
   }

   public static float randomMathFloat(float min, float max) {
      return min + (max - min) * (float)Math.random();
   }

   public static float clamp(float min, float max, float value) {
      return Math.max(min, Math.min(max, value));
   }

   public static float smoothRandomFloat(float min, float max, float smoothing) {
      if (smoothedRandomValue == null) {
         smoothedRandomValue = new float[]{(min + max) / 2.0F};
         randomTarget = new float[]{randomFloat(min, max)};
      }

      if (System.currentTimeMillis() - lastRandomUpdate > (250.0D) + Math.random() * (300.0D)) {
         randomTarget[0] = randomFloat(min, max);
         lastRandomUpdate = System.currentTimeMillis();
      }

      smoothedRandomValue[0] = smoothedRandomValue[0] + (randomTarget[0] - smoothedRandomValue[0]) * smoothing;
      return smoothedRandomValue[0];
   }

   public static float reverseLerp(float start, float end, float progress) {
      return end + (start - end) * progress;
   }

   public static double clampDegrees(double value) {
      return clamp(0.0, (360.0D), value);
   }

   public static double distance(double x1, double y1, double z1, double x2, double y2, double z2) {
      double var12 = x2 - x1;
      double var14 = y2 - y1;
      double var16 = z2 - z1;
      return Math.sqrt(var12 * var12 + var14 * var14 + var16 * var16);
   }
}
