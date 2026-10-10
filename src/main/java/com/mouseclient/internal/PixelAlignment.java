package com.mouseclient.internal;

public final class PixelAlignment {
   public static final int RESERVED_VALUE_512 = 512;
   public static final int RESERVED_VALUE_2 = 2;
   public static final int RESERVED_VALUE_1024 = 1024;

   public static int getPixelScale(float unusedScale) {
      return Math.max(1, Math.round(GuiScale.getBaseScale()));
   }

   public static float snapToScale(float coordinate, float scale) {
      return scale > (1.0E-4F) ? Math.round(coordinate * scale) / scale : snap(coordinate);
   }

   public static int padTextureSize(int size) {
      return Math.max(4, size + 2);
   }

   public static float snap(float coordinate) {
      float scale = GuiScale.getBaseScale();
      return Math.round(coordinate * scale) / scale;
   }

   private PixelAlignment() {
   }
}
