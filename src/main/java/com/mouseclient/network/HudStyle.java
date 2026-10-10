package com.mouseclient.network;

import com.mouseclient.gui.OutlineColorRange;
import com.mouseclient.gui.Render2D;
import com.mouseclient.util.time.GuiVisuals;
import java.awt.Color;

public final class HudStyle {
   public static int backgroundColor = new Color(15, 14, 17, 184).getRGB();
   public static int whiteAlpha3 = new Color(255, 255, 255, 3).getRGB();
   public static int outlineColor = new Color(255, 255, 255, 5).getRGB();
   public static int dividerColor = new Color(255, 255, 255, 10).getRGB();
   public static int whiteAlpha20 = new Color(255, 255, 255, 20).getRGB();
   public static int textColor = -1;
   public static int whiteAlpha184 = new Color(255, 255, 255, 184).getRGB();
   public static int mutedTextColor = new Color(255, 255, 255, 122).getRGB();
   public static int whiteAlpha61 = new Color(255, 255, 255, 61).getRGB();
   public static int accentColor = (-12092417);
   public static int resizeGripColor = (285212671);

   public static void drawIcon(String glyph, float centerX, float centerY, float size, int color) {
      GuiVisuals.drawCenteredIcon(glyph, centerX, centerY - (0.5F), size, color);
   }

   private HudStyle() {
   }

   public static void drawResizeGrip(float x, float y, float width, float height) {
      Render2D.drawBracket(x + width - (9.0F), y + height - (9.0F), (5.0F), (2.5F), (0.6F), 2, resizeGripColor);
   }

   public static void drawBlurredPanel(float x, float y, float width, float height, float radius) {
      Render2D.drawGuiBlur(x, y, width, height, radius);
      Render2D.drawOutline(x, y, width, height, radius, (0.5F), outlineColor);
   }

   public static void drawDivider(float x, float centerY, float height) {
      Render2D.fillRoundedRectangle(x, centerY - height / 2.0F, (0.5F), height, (0.25F), dividerColor);
   }

   public static float clampUnit(float value) {
      return Math.max(0.0F, Math.min(1.0F, value));
   }

   public static void drawProgressRing(float centerX, float centerY, float diameter, float progress, int color) {
      float left = centerX - diameter / 2.0F;
      float top = centerY - diameter / 2.0F;
      float clampedProgress = clampUnit(progress);
      if (clampedProgress < (0.004F)) {
         Render2D.drawOutlineRanges(left, top, diameter, diameter, diameter / 2.0F, 1.0F, outlineColor);
      } else {
         Render2D.drawOutlineRanges(
            left, top, diameter, diameter, diameter / 2.0F, 1.0F, outlineColor, OutlineColorRange.of((90.0F) - clampedProgress * (360.0F), (90.0F), color)
         );
         if (clampedProgress < (0.996F)) {
            float endpointRadius = diameter / 2.0F - (0.5F);
            double progressRadians = Math.toRadians(clampedProgress * (360.0D));
            Render2D.fillCircle(centerX, centerY - endpointRadius, (0.5F), (0.25F), color);
            Render2D.fillCircle(centerX + endpointRadius * (float)Math.sin(progressRadians), centerY - endpointRadius * (float)Math.cos(progressRadians), (0.5F), (0.25F), color);
         }
      }
   }
}
