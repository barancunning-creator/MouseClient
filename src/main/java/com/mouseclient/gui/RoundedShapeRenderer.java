package com.mouseclient.gui;

import com.mouseclient.mixin.DrawContextAccessor;
import com.mouseclient.render.pipeline.GuiShapePipelines;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2f;

public final class RoundedShapeRenderer {
   public static final int FILL = 0;
   public static final int SHADOW = 1;
   public static final int HSV_GRADIENT = 2;
   public static final int HOLLOW_SHADOW = 3;
   public static final int OUTLINE = 4;
   public static final int OUTLINE_EDGE = 5;

   public static void submit(
      DrawContext context,
      Matrix3x2f transform,
      float x,
      float y,
      float width,
      float height,
      CornerRadii radii,
      float spread,
      float thickness,
      float alpha,
      int colorTopLeft,
      int colorBottomLeft,
      int colorBottomRight,
      int colorTopRight,
      int mode
   ) {
      if (context != null && !(width <= 0.0F) && !(height <= 0.0F) && !(alpha <= (5.0E-4F))) {
         float shapeParameter = mode != 4 && mode != 5 ? spread : thickness;
         float pixelScale = MinecraftClient.getInstance().getWindow().getScaleFactor() * transform.m00();
         float outlinePadding = 0.0F;
         if (mode == 5) {
            outlinePadding = thickness * (0.5F) + 1.0F / Math.max(pixelScale, (1.0E-4F));
            x -= outlinePadding;
            y -= outlinePadding;
            width += outlinePadding * 2.0F;
            height += outlinePadding * 2.0F;
         }

         ScreenRect bounds = ScissorStack.current();
         ScreenRect clippedBounds = GuiShapeRenderer.transformedClippedBounds(transform, bounds, x, y, width, height);
         if (clippedBounds != null) {
            int alphaTopLeft = GuiShapeRenderer.multiplyAlpha(colorTopLeft, alpha);
            int alphaBottomLeft = GuiShapeRenderer.multiplyAlpha(colorBottomLeft, alpha);
            int alphaBottomRight = GuiShapeRenderer.multiplyAlpha(colorBottomRight, alpha);
            int alphaTopRight = GuiShapeRenderer.multiplyAlpha(colorTopRight, alpha);
            short packedBottomRightRadius = GuiShapeRenderer.packRadius((radii.getBottomRight() + (radii.getBottomRight() > 0.0F ? outlinePadding : 0.0F)) * pixelScale, radii.isSquircle());
            short packedTopRightRadius = GuiShapeRenderer.packRadius((radii.getTopRight() + (radii.getTopRight() > 0.0F ? outlinePadding : 0.0F)) * pixelScale, radii.isSquircle());
            short packedBottomLeftRadius = GuiShapeRenderer.packRadius((radii.getBottomLeft() + (radii.getBottomLeft() > 0.0F ? outlinePadding : 0.0F)) * pixelScale, radii.isSquircle());
            short packedTopLeftRadius = GuiShapeRenderer.packRadius((radii.getTopLeft() + (radii.getTopLeft() > 0.0F ? outlinePadding : 0.0F)) * pixelScale, radii.isSquircle());
            ((DrawContextAccessor)(Object)context)
               .getGuiRenderState()
               .addSimpleElement(
                  new RoundedShapeRenderer.ShapeQuadState(
                     new Matrix3x2f(transform),
                     x,
                     y,
                     x + width,
                     y + height,
                     alphaTopLeft,
                     alphaBottomLeft,
                     alphaBottomRight,
                     alphaTopRight,
                     packedBottomRightRadius,
                     packedTopRightRadius,
                     packedBottomLeftRadius,
                     packedTopLeftRadius,
                     shapeParameter * pixelScale,
                     encodeTypeSignal(mode),
                     bounds,
                     clippedBounds
                  )
               );
         }
      }
   }

   private RoundedShapeRenderer() {
   }

   private static float encodeTypeSignal(int mode) {
      return switch (mode) {
         case 1 -> 0.0F;
         case 2 -> (-0.5F);
         case 3 -> (0.25F);
         case 4 -> 1.0F;
         case 5 -> 2.0F;
         default -> (-1.0F);
      };
   }

   public record ShapeQuadState(
      Matrix3x2f pose,
      float left,
      float top,
      float right,
      float bottom,
      int colorTopLeft,
      int colorBottomLeft,
      int colorBottomRight,
      int colorTopRight,
      short rBR,
      short rTR,
      short rBL,
      short rTL,
      float param,
      float typeSignal,
      ScreenRect scissorArea,
      ScreenRect bounds
   ) implements SimpleGuiElementRenderState {
      public float getRight() {
         return this.right;
      }

      public float getTop() {
         return this.top;
      }

      public float getLeft() {
         return this.left;
      }

      @Override
      public RenderPipeline pipeline() {
         return GuiShapePipelines.ROUNDED;
      }

      private void emit(VertexConsumer vertices, float x, float y, float u, float v, int color) {
         vertices.vertex(this.pose, x, y)
            .color(color)
            .texture(u, v)
            .overlay(this.rBR, this.rTR)
            .light(this.rBL, this.rTL)
            .normal(this.typeSignal, 0.0F, 0.0F)
            .lineWidth(this.param);
      }

      public float getBottom() {
         return this.bottom;
      }

      @Override
      public void setupVertices(VertexConsumer vertices) {
         this.emit(vertices, this.left, this.top, 0.0F, 0.0F, this.colorTopLeft);
         this.emit(vertices, this.left, this.bottom, 0.0F, 1.0F, this.colorBottomLeft);
         this.emit(vertices, this.right, this.bottom, 1.0F, 1.0F, this.colorBottomRight);
         this.emit(vertices, this.right, this.top, 1.0F, 0.0F, this.colorTopRight);
      }

      @Override
      public TextureSetup textureSetup() {
         return TextureSetup.empty();
      }
   }
}
