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
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix3x2f;

public final class RoundedTextureRenderer {
   public static final int TEXTURE = 2;
   public static final int BLUR = 3;
   public static final int SDF_ICON = 6;

   private RoundedTextureRenderer() {
   }

   private static float encodeTypeSignal(int mode) {
      if (mode == 6) {
         return 1.0F;
      } else {
         return mode == 3 ? 0.0F : (-1.0F);
      }
   }

   public static void submit(
      DrawContext context,
      TextureSetup textureSetup,
      Matrix3x2f transform,
      float x,
      float y,
      float width,
      float height,
      float u0,
      float v0,
      float u1,
      float v1,
      CornerRadii radii,
      float value,
      float smoothStart,
      float smoothEnd,
      float alpha,
      int colorLeft,
      int colorRight,
      int mode,
      boolean includeGuiBlur
   ) {
      if (context != null && textureSetup != null && !(width <= 0.0F) && !(height <= 0.0F) && !(alpha <= (5.0E-4F))) {
         float pixelScale = MinecraftClient.getInstance().getWindow().getScaleFactor() * transform.m00();
         float scaledValue = mode != 6 && mode != 3 ? value * pixelScale : value;
         ScreenRect bounds = ScissorStack.current();
         ScreenRect clippedBounds = GuiShapeRenderer.transformedClippedBounds(transform, bounds, x, y, width, height);
         if (clippedBounds != null) {
            int alphaLeft = GuiShapeRenderer.multiplyAlpha(colorLeft, alpha);
            int alphaRight = GuiShapeRenderer.multiplyAlpha(colorRight, alpha);
            ((DrawContextAccessor)(Object)context)
               .getGuiRenderState()
               .addSimpleElement(
                  new RoundedTextureRenderer.TexturedQuadState(
                     textureSetup,
                     new Matrix3x2f(transform),
                     x,
                     y,
                     x + width,
                     y + height,
                     u0,
                     v0,
                     u1,
                     v1,
                     alphaLeft,
                     alphaRight,
                     GuiShapeRenderer.packRadius(radii.getBottomRight() * pixelScale, radii.isSquircle()),
                     GuiShapeRenderer.packRadius(radii.getTopRight() * pixelScale, radii.isSquircle()),
                     GuiShapeRenderer.packRadius(radii.getBottomLeft() * pixelScale, radii.isSquircle()),
                     GuiShapeRenderer.packRadius(radii.getTopLeft() * pixelScale, radii.isSquircle()),
                     scaledValue,
                     MathHelper.clamp(smoothStart, (-1.0F), 1.0F),
                     MathHelper.clamp(smoothEnd, (-1.0F), 1.0F),
                     encodeTypeSignal(mode),
                     includeGuiBlur && mode == 3,
                     bounds,
                     clippedBounds
                  )
               );
         }
      }
   }

   public record TexturedQuadState(
      TextureSetup textureSetup,
      Matrix3x2f pose,
      float left,
      float top,
      float right,
      float bottom,
      float u0,
      float v0,
      float u1,
      float v1,
      int colorLeft,
      int colorRight,
      short rBR,
      short rTR,
      short rBL,
      short rTL,
      float value,
      float smoothStart,
      float smoothEnd,
      float typeSignal,
      boolean include2DBlur,
      ScreenRect scissorArea,
      ScreenRect bounds
   ) implements SimpleGuiElementRenderState {
      public float getTop() {
         return this.top;
      }

      public float getRight() {
         return this.right;
      }

      public float getU1() {
         return this.u1;
      }

      public float getBottom() {
         return this.bottom;
      }

      @Override
      public void setupVertices(VertexConsumer vertices) {
         this.emit(vertices, this.left, this.top, this.u0, this.v0, this.colorLeft);
         this.emit(vertices, this.left, this.bottom, this.u0, this.v1, this.colorLeft);
         this.emit(vertices, this.right, this.bottom, this.u1, this.v1, this.colorRight);
         this.emit(vertices, this.right, this.top, this.u1, this.v0, this.colorRight);
      }

      private void emit(VertexConsumer vertices, float x, float y, float u, float v, int color) {
         vertices.vertex(this.pose, x, y)
            .color(color)
            .texture(u, v)
            .overlay(this.rBR, this.rTR)
            .light(this.rBL, this.rTL)
            .normal(this.typeSignal, this.smoothStart, this.smoothEnd)
            .lineWidth(this.value);
      }

      public float getLeft() {
         return this.left;
      }

      public float getV0() {
         return this.v0;
      }

      public float getU0() {
         return this.u0;
      }

      @Override
      public RenderPipeline pipeline() {
         return this.include2DBlur ? GuiShapePipelines.TEXTURE_GUI_BLUR : GuiShapePipelines.TEXTURE;
      }

      public float getV1() {
         return this.v1;
      }
   }
}
