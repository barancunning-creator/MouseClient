package com.mouseclient.internal;

public final class PaletteTransition {
   
   private static long transitionStartedAt = (-4611686018427387904L);

   private PaletteTransition() {
   }

   public static float getProgress() {
      float progress = (float)(System.currentTimeMillis() - transitionStartedAt) / (700.0F);
      return !(progress < 0.0F) && !(progress > 1.0F) ? progress : (-1.0F);
   }

   public static void restart() {
      transitionStartedAt = System.currentTimeMillis();
   }
}
