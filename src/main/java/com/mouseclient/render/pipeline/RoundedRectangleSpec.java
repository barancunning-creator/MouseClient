package com.mouseclient.render.pipeline;

import net.minecraft.client.gui.DrawContext;

public record RoundedRectangleSpec(
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
   float scissorX,
   float scissorY,
   float scissorWidth,
   float scissorHeight,
   float scissorRadiusTopLeft,
   float scissorRadiusTopRight,
   float scissorRadiusBottomRight,
   float scissorRadiusBottomLeft,
   int paletteMode,
   float paletteTint,
   float paletteAlpha
) {
   public static final float DEFAULT_SMOOTHNESS = 0.5F;
   public static final int DEFAULT_COLOR = -1;
   public static final int PALETTE_NONE = 0;
   public static final int PALETTE_VERTICAL = 1;
   public static final int PALETTE_HORIZONTAL = 2;

   public RoundedRectangleSpec withColor(int color) {
      return new RoundedRectangleSpec(
         this.x,
         this.y,
         this.width,
         this.height,
         this.radiusTopLeft,
         this.radiusTopRight,
         this.radiusBottomRight,
         this.radiusBottomLeft,
         color,
         color,
         color,
         color,
         this.smoothness,
         this.scissorX,
         this.scissorY,
         this.scissorWidth,
         this.scissorHeight,
         this.scissorRadiusTopLeft,
         this.scissorRadiusTopRight,
         this.scissorRadiusBottomRight,
         this.scissorRadiusBottomLeft,
         this.paletteMode,
         this.paletteTint,
         this.paletteAlpha
      );
   }

   public boolean visible() {
      if (this.width <= 0.0F || this.height <= 0.0F) {
         return false;
      } else {
         return this.paletteMode != 0
            ? this.paletteAlpha > 0.0F
            : (this.colorTopLeft | this.colorTopRight | this.colorBottomRight | this.colorBottomLeft) >>> 24 != 0;
      }
   }

   public RoundedRectangleSpec(float x, float y, float width, float height, float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft, int color) {
      this(x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft, color, color, color, color, 0.5F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0, 1.0F, 1.0F);
   }

   public void render(DrawContext context) {
      RoundedRectangleRenderer.getInstance().render(context, this);
   }

   public RoundedRectangleSpec(float x, float y, float width, float height, float radius, int color) {
      this(x, y, width, height, radius, radius, radius, radius, color, color, color, color, 0.5F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0, 1.0F, 1.0F);
   }

   public RoundedRectangleSpec withSmoothness(float smoothness) {
      return new RoundedRectangleSpec(
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
         smoothness,
         this.scissorX,
         this.scissorY,
         this.scissorWidth,
         this.scissorHeight,
         this.scissorRadiusTopLeft,
         this.scissorRadiusTopRight,
         this.scissorRadiusBottomRight,
         this.scissorRadiusBottomLeft,
         this.paletteMode,
         this.paletteTint,
         this.paletteAlpha
      );
   }

   public RoundedRectangleSpec(
      float x, float y, float width, float height, float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft, int colorTopLeft, int colorTopRight, int colorBottomRight, int colorBottomLeft, float smoothness
   ) {
      this(x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft, colorTopLeft, colorTopRight, colorBottomRight, colorBottomLeft, smoothness, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0, 1.0F, 1.0F);
   }

   public RoundedRectangleSpec withPaletteGradient(int paletteMode, float paletteTint, float paletteAlpha) {
      return new RoundedRectangleSpec(
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
         this.scissorX,
         this.scissorY,
         this.scissorWidth,
         this.scissorHeight,
         this.scissorRadiusTopLeft,
         this.scissorRadiusTopRight,
         this.scissorRadiusBottomRight,
         this.scissorRadiusBottomLeft,
         paletteMode,
         paletteTint,
         paletteAlpha
      );
   }
}
