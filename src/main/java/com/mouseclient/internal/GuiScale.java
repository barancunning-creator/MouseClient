package com.mouseclient.internal;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.Window;
import org.joml.Matrix3x2f;

public final class GuiScale {
   
   private static final Matrix3x2f matrix3x2f = new Matrix3x2f();

   public static float getBaseScale() {
      return 2.0F;
   }

   public static Matrix3x2f getScaledMatrices(DrawContext context) {
      Matrix3x2f transform = matrix3x2f.set(context.getMatrices());
      applyScale(transform);
      return transform;
   }

   public static float getFramebufferHeight() {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client == null) {
         return 0.0F;
      }

      Window window = client.getWindow();
      return window == null ? 0.0F : window.getFramebufferHeight();
   }

   public static int getWindowScale() {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client == null) {
         return 1;
      }

      Window window = client.getWindow();
      return window == null ? 1 : Math.max(1, window.getScaleFactor());
   }

   public static int toGuiPixels(float coordinate) {
      return Math.round(toGuiUnits(coordinate));
   }

   private GuiScale() {
   }

   public static float getMatrixScale() {
      return 2.0F / getWindowScale();
   }

   public static float toGuiUnits(float coordinate) {
      return coordinate * getMatrixScale();
   }

   public static void applyScale(Matrix3x2f transform) {
      float matrixScale = getMatrixScale();
      if (matrixScale != 1.0F) {
         transform.scale(matrixScale);
      }
   }
}
