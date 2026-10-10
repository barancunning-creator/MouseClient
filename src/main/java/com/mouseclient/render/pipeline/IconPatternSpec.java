package com.mouseclient.render.pipeline;

import net.minecraft.client.gui.DrawContext;

public record IconPatternSpec(
   float x,
   float y,
   float width,
   float height,
   float radiusTopLeft,
   float radiusTopRight,
   float radiusBottomRight,
   float radiusBottomLeft,
   int colorTopLeft,
   int colorTopRight,
   int colorBottomRight,
   int colorBottomLeft,
   float smoothness,
   String fontName,
   String icon,
   float iconSize,
   int iconColor,
   float spacingX,
   float spacingY,
   float paddingX,
   float paddingY,
   float jitterX,
   float jitterY,
   float rotationMaxDegrees,
   long seed
) {
   public static final float DEFAULT_SMOOTHNESS = 0.0F;
   public static final float DEFAULT_ICON_SIZE = 8.0F;
   public static final float DEFAULT_SPACING = 14.0F;
   public static final float DEFAULT_PADDING = 2.0F;

   public IconPatternSpec(
      float x,
      float y,
      float width,
      float height,
      float radius,
      int color,
      String fontName,
      String icon,
      float iconSize,
      int iconColor,
      float spacingX,
      float spacingY,
      float rotationMaxDegrees
   ) {
      this(
         x, y, width, height, radius, radius, radius, radius, color, color, color, color, 0.0F, fontName, icon, iconSize, iconColor, spacingX, spacingY, 2.0F, 2.0F, 0.0F, 0.0F, rotationMaxDegrees, 0L
      );
   }

   public IconPatternSpec withBackground(int background) {
      return new IconPatternSpec(
         this.x,
         this.y,
         this.width,
         this.height,
         this.radiusTopLeft,
         this.radiusTopRight,
         this.radiusBottomRight,
         this.radiusBottomLeft,
         background,
         background,
         background,
         background,
         this.smoothness,
         this.fontName,
         this.icon,
         this.iconSize,
         this.iconColor,
         this.spacingX,
         this.spacingY,
         this.paddingX,
         this.paddingY,
         this.jitterX,
         this.jitterY,
         this.rotationMaxDegrees,
         this.seed
      );
   }

   public boolean visible() {
      return this.backgroundVisible() || this.iconsVisible();
   }

   public boolean backgroundVisible() {
      return this.width > 0.0F
         && this.height > 0.0F
         && ((this.colorTopLeft | this.colorTopRight | this.colorBottomRight | this.colorBottomLeft) >>> 24 & 0xFF) > 0;
   }

   public void render(DrawContext context) {
      RectHalfIconRenderer.getInstance().render(context, this);
   }

   public IconPatternSpec withPadding(float paddingX, float paddingY) {
      return new IconPatternSpec(
         this.x,
         this.y,
         this.width,
         this.height,
         this.radiusTopLeft,
         this.radiusTopRight,
         this.radiusBottomRight,
         this.radiusBottomLeft,
         this.colorTopLeft,
         this.colorTopRight,
         this.colorBottomRight,
         this.colorBottomLeft,
         this.smoothness,
         this.fontName,
         this.icon,
         this.iconSize,
         this.iconColor,
         this.spacingX,
         this.spacingY,
         paddingX,
         paddingY,
         this.jitterX,
         this.jitterY,
         this.rotationMaxDegrees,
         this.seed
      );
   }

   public IconPatternSpec withIcon(int iconColor, float iconSize, float spacingX, float spacingY) {
      return new IconPatternSpec(
         this.x,
         this.y,
         this.width,
         this.height,
         this.radiusTopLeft,
         this.radiusTopRight,
         this.radiusBottomRight,
         this.radiusBottomLeft,
         this.colorTopLeft,
         this.colorTopRight,
         this.colorBottomRight,
         this.colorBottomLeft,
         this.smoothness,
         this.fontName,
         this.icon,
         iconSize,
         iconColor,
         spacingX,
         spacingY,
         this.paddingX,
         this.paddingY,
         this.jitterX,
         this.jitterY,
         this.rotationMaxDegrees,
         this.seed
      );
   }

   public boolean iconsVisible() {
      return this.width > 0.0F
         && this.height > 0.0F
         && this.fontName != null
         && this.icon != null
         && !this.icon.isEmpty()
         && this.iconSize > 0.0F
         && this.iconColor >>> 24 != 0;
   }

   public IconPatternSpec withRandomization(float rotationMaxDegrees, float jitterX, float jitterY, long seed) {
      return new IconPatternSpec(
         this.x,
         this.y,
         this.width,
         this.height,
         this.radiusTopLeft,
         this.radiusTopRight,
         this.radiusBottomRight,
         this.radiusBottomLeft,
         this.colorTopLeft,
         this.colorTopRight,
         this.colorBottomRight,
         this.colorBottomLeft,
         this.smoothness,
         this.fontName,
         this.icon,
         this.iconSize,
         this.iconColor,
         this.spacingX,
         this.spacingY,
         this.paddingX,
         this.paddingY,
         jitterX,
         jitterY,
         rotationMaxDegrees,
         seed
      );
   }
}
