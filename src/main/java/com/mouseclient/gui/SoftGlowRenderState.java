package com.mouseclient.gui;

import com.mouseclient.render.pipeline.SoftGlowRenderer;
import com.mouseclient.render.pipeline.SoftGlowSpec;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2f;

public final class SoftGlowRenderState implements SimpleGuiElementRenderState {
   private final SoftGlowSpec spec;
   private final Matrix3x2f transform;
   private final ScreenRect scissorArea;
   private final ScreenRect bounds;

   @Override
   public ScreenRect scissorArea() {
      return this.scissorArea;
   }

   public SoftGlowRenderState(Matrix3x2f transform, SoftGlowSpec spec, ScreenRect scissorArea) {
      this.spec = spec;
      this.transform = new Matrix3x2f(transform);
      this.scissorArea = scissorArea;
      float centerX = spec.centerX();
      float centerY = spec.centerY();
      float extentX = spec.extentX();
      float extentY = spec.extentY();
      int left = (int)Math.floor(centerX - extentX);
      int top = (int)Math.floor(centerY - extentY);
      int width = (int)Math.ceil(extentX * 2.0F);
      int height = (int)Math.ceil(extentY * 2.0F);
      ScreenRect bounds = new ScreenRect(left, top, width, height).transformEachVertex(this.transform);
      this.bounds = scissorArea == null ? bounds : scissorArea.intersection(bounds);
   }

   @Override
   public ScreenRect bounds() {
      return this.bounds;
   }

   private void vertex(VertexConsumer vertices, float x, float y, int encodedU, int encodedV, int paramsIndex) {
      vertices.vertex(this.transform, x, y).color(encodedU, encodedV, 255, 255).lineWidth(paramsIndex + 1);
   }

   @Override
   public TextureSetup textureSetup() {
      return TextureSetup.empty();
   }

   @Override
   public void setupVertices(VertexConsumer vertices) {
      int paramsIndex = SoftGlowRenderer.getInstance().allocateParams(this.spec);
      if (paramsIndex >= 0) {
         float centerX = this.spec.centerX();
         float centerY = this.spec.centerY();
         float extentX = this.spec.extentX();
         float extentY = this.spec.extentY();
         float left = centerX - extentX;
         float top = centerY - extentY;
         float right = centerX + extentX;
         float bottom = centerY + extentY;
         this.vertex(vertices, left, top, 0, 0, paramsIndex);
         this.vertex(vertices, left, bottom, 0, 255, paramsIndex);
         this.vertex(vertices, right, bottom, 255, 255, paramsIndex);
         this.vertex(vertices, right, top, 255, 0, paramsIndex);
      }
   }

   @Override
   public RenderPipeline pipeline() {
      return SoftGlowRenderer.renderPipeline;
   }
}
