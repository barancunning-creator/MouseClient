package com.mouseclient.internal;

public final class GuiBlurState {
   private static float radius = (24.0F);
   private static float opacity = 1.0F;
   private static boolean enabled;
   private static boolean requested;

   public static void setEnabled(boolean enabled) {
      enabled = enabled;
   }

   public static boolean isRequested() {
      return requested;
   }

   public static void setRadius(float radius) {
      radius = Math.max(0.0F, radius);
   }

   public static void setRequested(boolean requested) {
      requested = requested;
   }

   public static float getOpacity() {
      return opacity;
   }

   private GuiBlurState() {
   }

   public static boolean isEnabled() {
      return enabled;
   }

   public static float getRadius() {
      return radius;
   }

   public static void setOpacity(float opacity) {
      opacity = Math.max(0.0F, Math.min(1.0F, opacity));
   }
}
