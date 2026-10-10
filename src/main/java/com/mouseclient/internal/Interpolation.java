package com.mouseclient.internal;

import lombok.Generated;

public final class Interpolation {

   public static int lerp(int start, int end, double progress) {
      return (int)(start + progress * (end - start));
   }

   public static float lerp(float start, float end, double progress) {
      return (float)(start + progress * (end - start));
   }

   public static double lerp(double start, double end, double progress) {
      return start + progress * (end - start);
   }

   @Generated
   private Interpolation() {
      throw new UnsupportedOperationException(("This is a utility class and cannot be instantiated"));
   }
}
