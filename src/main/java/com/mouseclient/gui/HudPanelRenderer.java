package com.mouseclient.gui;

import com.mouseclient.hud.HudIcon;
import com.mouseclient.network.HudStyle;
import com.mouseclient.util.time.GuiVisuals;
import com.mouseclient.util.time.GuiTextRenderer;
import com.mouseclient.util.time.GuiFont;

public final class HudPanelRenderer {
   public static final float HEADER_HEIGHT = 13.0F;
   public static final float ROW_GAP = 2.0F;
   public static final float ROW_HEIGHT = 11.0F;
   public static final float ICON_CHIP_WIDTH = 13.0F;
   public static final float CORNER_RADIUS = 5.0F;
   private static final CornerRadii PANEL_RADII = com.mouseclient.gui.CornerRadii.uniform((5.0F));
   private static final CornerRadii DIVIDER_RADII = com.mouseclient.gui.CornerRadii.uniform((0.25F));

   public static float combinedRowWidth(float iconWidth, float textWidth) {
      float contentWidth = iconWidth > 0.0F && textWidth > 0.0F ? iconWidth + (4.0F) + textWidth : iconWidth + textWidth;
      return contentWidth > 0.0F ? 2.0F + contentWidth + (4.0F) : 0.0F;
   }

   public static void drawDivider(float x, float centerY, float alpha, float height) {
      GuiShapeRenderer.fill(x, centerY - height / 2.0F, (0.5F), height, accentColor(1.0F), alpha * (0.35F), DIVIDER_RADII);
   }

   private HudPanelRenderer() {
   }

   public static int accentColor(float alpha) {
      return GuiVisuals.multiplyAlpha(HudStyle.accentColor, alpha);
   }

   public static void drawGlassPanel(float x, float y, float width, float height, float alpha, float radius, GuiShapeRenderer.BlurQuality quality) {
      drawStackCard(x, y, width, height, alpha);
   }

   private static void drawTintedPanel(float x, float y, float width, float height, float alpha, CornerRadii radii) {
      if (alpha <= 0.001F || width <= 1.0F || height <= 1.0F) {
         return;
      }
      Render2D.fillRoundedRectangle(x, y, width, height, radii.getTopLeft(), GuiVisuals.multiplyAlpha(0xF012141A, alpha));
      Render2D.drawOutline(x, y, width, height, radii.getTopLeft(), 0.6F, GuiVisuals.multiplyAlpha(0x40FFFFFF, alpha));
   }

   public static float openingOffset(float progress) {
      float squaredProgress = progress * progress;
      float easedProgress = squaredProgress >= 1.0F ? 1.0F : 1.0F - (float)Math.pow((2.0D), (-10.0D) * squaredProgress);
      return (7.0F) * (1.0F - easedProgress);
   }

   public static int secondaryTextColor(float alpha) {
      return GuiVisuals.multiplyAlpha(GuiVisuals.lerpColor(HudStyle.accentColor, -1, (0.78F)), alpha);
   }

   public static float rowTextY(float y) {
      return y + (5.5F) - (3.5F);
   }

   public static void drawGlassPanel(float x, float y, float width, float height, float alpha, float radius) {
      drawGlassPanel(x, y, width, height, alpha, radius, GuiShapeRenderer.BlurQuality.MIDDLE);
   }

   public static int panelTint() {
      return GuiVisuals.lerpColor(HudStyle.accentColor, (-16777216), (0.93F));
   }

   public static void drawPanel(float x, float y, float width, float height, float alpha, float radius) {
      drawTintedPanel(x, y, width, height, alpha, com.mouseclient.gui.CornerRadii.uniform(radius));
   }

   public static int primaryTextColor(float alpha) {
      return GuiVisuals.multiplyAlpha(GuiVisuals.lerpColor(HudStyle.accentColor, -1, (0.95F)), alpha);
   }

   public static void drawPanel(float x, float y, float width, float height, float alpha) {
      drawTintedPanel(x, y, width, height, alpha, PANEL_RADII);
   }

   public static float textChipWidth(float textWidth) {
      return textWidth > 0.0F ? (4.0F) + textWidth + (4.0F) : 0.0F;
   }

   public static float headerWidth(String title) {
      return (18.0F) + GuiTextRenderer.getWidth(GuiFont.SEMI_BOLD, title, (6.0F)) + (6.0F);
   }

   public static void drawHeader(float x, float y, float width, float alpha, HudIcon icon, String title) {
      drawStackCard(x, y, width, 13.0F, alpha);
      icon.draw(x + (5.5F), y + (icon.glyph != null ? 2.0F : (2.5F)), (8.0F), accentColor(alpha));
      GuiTextRenderer.draw(GuiFont.SEMI_BOLD, title, x + (17.0F), y + (6.5F) - (3.65F), (6.0F), primaryTextColor(alpha));
   }

   public static void drawStackCard(float x, float y, float width, float height, float alpha) {
      if (alpha <= 0.001F || width <= 1.0F || height <= 1.0F) {
         return;
      }
      Render2D.fillRoundedRectangle(x, y, width, height, 5.5F, GuiVisuals.multiplyAlpha(0xF012141A, alpha));
      Render2D.drawOutline(x, y, width, height, 5.5F, 0.6F, GuiVisuals.multiplyAlpha(0x40FFFFFF, alpha));
   }

   public static void drawStackTitle(float x, float y, float width, float alpha, HudIcon icon, String title) {
      if (alpha <= 0.001F || width <= 1.0F) {
         return;
      }
      GuiTextRenderer.draw(GuiFont.SEMI_BOLD, title, x + 6.0F, y + 4.0F, 6.5F, GuiVisuals.multiplyAlpha(-1, alpha));
      icon.draw(x + width - 13.0F, y + 3.2F, 7.0F, GuiVisuals.multiplyAlpha(0xFFD7DCE4, alpha));
   }
}
