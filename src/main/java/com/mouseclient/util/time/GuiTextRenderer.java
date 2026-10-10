package com.mouseclient.util.time;

import com.mouseclient.gui.Render2D;
import com.mouseclient.internal.StyledTextParser;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public final class GuiTextRenderer {

   public static void drawRotated(GuiFont font, String text, float x, float y, float size, int color, float rotationDegrees, float originX, float originY) {
      Render2D.drawRotatedText(font.getFontKey(), text, x, y, size, color, rotationDegrees, originX, originY);
   }

   private GuiTextRenderer() {
   }

   public static void drawWithShadow(GuiFont font, String text, float x, float y, float size, int color, int shadowColor, float shadowOffset) {
      Render2D.drawText(font.getFontKey(), text, x + shadowOffset, y + shadowOffset, size, shadowColor);
      Render2D.drawText(font.getFontKey(), text, x, y, size, color);
   }

   public static float getWidth(GuiFont font, String text, float size) {
      return Render2D.getTextWidth(font.getFontKey(), text, size);
   }

   public static void draw(GuiFont font, String text, float x, float y, float size, int color) {
      Render2D.drawText(font.getFontKey(), text, x, y, size, color);
   }

   public static void drawWithScaledShadow(GuiFont font, String text, float x, float y, float size, int color) {
      float rawShadowOffset = Math.max((0.5F), size / (16.0F));
      float snappedShadowOffset = Math.round(rawShadowOffset * 2.0F) / 2.0F;
      int shadowArgb = shadowColor(color);
      drawWithShadow(font, text, x, y, size, color, shadowArgb, snappedShadowOffset);
   }

   private static int shadowColor(int color) {
      int originalAlpha = color >>> 24 & 0xFF;
      int shadowAlpha = Math.round(originalAlpha * (0.55F));
      return shadowAlpha << 24;
   }

   public static void drawStyledText(GuiFont font, Text text, float x, float y, float size, int color) {
      StringBuilder textBuilder = new StringBuilder();

      for (StyledTextParser.StyledTextRun run : StyledTextParser.parseText(text, color)) {
         if (run.obfuscated()) {
            Render2D.drawObfuscatedText(
               font.getFontKey(), run.text(), x + getWidth(font, textBuilder.toString(), size), y, size, run.color() & (16777215) | color & (-16777216)
            );
         } else {
            draw(font, run.text(), x + getWidth(font, textBuilder.toString(), size), y, size, run.color() & (16777215) | color & (-16777216));
         }

         textBuilder.append(run.text());
      }
   }

   public static void drawInContext(DrawContext context, GuiFont font, String text, float x, float y, float size, int color) {
      Render2D.begin(context);
      draw(font, text, x, y, size, color);
      Render2D.end();
   }

   public static void drawFaded(
      GuiFont font, String text, float x, float y, float size, int color, float fadeLeftX, float fadeRightX, float fadeWidth, float fadeLeftStrength, float fadeRightStrength
   ) {
      Render2D.drawFadedText(font.getFontKey(), text, x, y, size, color, fadeLeftX, fadeRightX, fadeWidth, fadeLeftStrength, fadeRightStrength);
   }

   public static void drawFaded(
      GuiFont font, String text, float x, float y, float size, int color, float fadeLeftX, float fadeRightX, float fadeWidth, boolean fadeLeft, boolean fadeRight
   ) {
      Render2D.drawFadedText(font.getFontKey(), text, x, y, size, color, fadeLeftX, fadeRightX, fadeWidth, fadeLeft, fadeRight);
   }

   public static void drawGradient(GuiFont font, String text, float x, float y, float size, int colorTopLeft, int colorTopRight, int colorBottomRight, int colorBottomLeft) {
      Render2D.drawGradientText(font.getFontKey(), text, x, y, size, colorTopLeft, colorTopRight, colorBottomRight, colorBottomLeft);
   }

   public static void drawCentered(GuiFont font, String text, float centerX, float y, float size, int color) {
      float textWidth = getWidth(font, text, size);
      draw(font, text, centerX - textWidth * (0.5F), y, size, color);
   }
}
