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

public final class SoftBlurQuadRenderer {

   private SoftBlurQuadRenderer() {
   }

   public static void submit(
      DrawContext context,
      TextureSetup textureSetup,
      Matrix3x2f transform,
      float x,
      float y,
      float width,
      float height,
      CornerRadii radii,
      float spread,
      int tintColor,
      float alpha,
      float tintAmount,
      int shadowColor,
      float shadowStrength,
      boolean includeGuiBlur
   ) {
      if (context != null && textureSetup != null && !(width <= 0.0F) && !(height <= 0.0F) && !(spread <= 0.0F)) {
         float pixelScale = MinecraftClient.getInstance().getWindow().getScaleFactor() * transform.m00();
         ScreenRect bounds = ScissorStack.current();
         ScreenRect clippedBounds = GuiShapeRenderer.transformedClippedBounds(transform, bounds, x, y, width, height);
         if (clippedBounds != null) {
            int tintWithAlpha = MathHelper.clamp(Math.round(MathHelper.clamp(alpha, 0.0F, 1.0F) * (255.0F)), 0, 255) << 24 | tintColor & (16777215);
            ((DrawContextAccessor)(Object)context)
               .getGuiRenderState()
               .addSimpleElement(
                  new SoftBlurQuadRenderer.SoftBlurQuadState(
                     textureSetup,
                     new Matrix3x2f(transform),
                     x,
                     y,
                     x + width,
                     y + height,
                     tintWithAlpha,
                     GuiShapeRenderer.packRadius(radii.maxRadius() * pixelScale, radii.isSquircle()),
                     packUnitFloat(tintAmount),
                     packUnitFloat(shadowStrength),
                     (shadowColor >> 16 & 0xFF) / (255.0F),
                     (shadowColor >> 8 & 0xFF) / (255.0F),
                     (shadowColor & 0xFF) / (255.0F),
                     spread * pixelScale,
                     includeGuiBlur,
                     bounds,
                     clippedBounds
                  )
               );
         }
      }
   }

   private static short packUnitFloat(float value) {
      return (short)MathHelper.clamp(Math.round(MathHelper.clamp(value, 0.0F, 1.0F) * (8192.0F)), 0, 32767);
   }

   public record SoftBlurQuadState(
      TextureSetup textureSetup,
      Matrix3x2f pose,
      float left,
      float top,
      float right,
      float bottom,
      int color,
      short radius,
      short tintAmount,
      short shadowStrength,
      float shadowR,
      float shadowG,
      float shadowB,
      float spread,
      boolean include2D,
      ScreenRect scissorArea,
      ScreenRect bounds
   ) implements SimpleGuiElementRenderState {
      public float getTop() {
         return this.top;
      }

      public float getRight() {
         return this.right;
      }

      private void emit(VertexConsumer vertices, float x, float y, float u, float v) {
         vertices.vertex(this.pose, x, y)
            .color(this.color)
            .texture(u, v)
            .overlay(this.radius, this.tintAmount)
            .light(this.shadowStrength, 0)
            .normal(this.shadowR, this.shadowG, this.shadowB)
            .lineWidth(this.spread);
      }

      @Override
      public void setupVertices(VertexConsumer vertices) {
         this.emit(vertices, this.left, this.top, 0.0F, 0.0F);
         this.emit(vertices, this.left, this.bottom, 0.0F, 1.0F);
         this.emit(vertices, this.right, this.bottom, 1.0F, 1.0F);
         this.emit(vertices, this.right, this.top, 1.0F, 0.0F);
      }

      public float getLeft() {
         return this.left;
      }

      public float getBottom() {
         return this.bottom;
      }

      @Override
      public RenderPipeline pipeline() {
         return this.include2D ? GuiShapePipelines.SOFT_BLUR_GUI_BLUR : GuiShapePipelines.SOFT_BLUR;
      }
   }
}
