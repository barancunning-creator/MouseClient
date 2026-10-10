package com.mouseclient.gui;

import com.mouseclient.render.pipeline.CircleRenderer;
import com.mouseclient.render.pipeline.CircleSpec;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2f;

public final class CircleRenderState implements SimpleGuiElementRenderState {
   private final CircleSpec spec;
   private final Matrix3x2f transform;
   private final ScreenRect scissorArea;
   private final ScreenRect bounds;

   @Override
   public ScreenRect bounds() {
      return this.bounds;
   }

   @Override
   public ScreenRect scissorArea() {
      return this.scissorArea;
   }

   private static float extent(CircleSpec spec) {
      return spec.radius() + Math.max(spec.smoothness(), 0.0F);
   }

   private void vertex(VertexConsumer vertices, float x, float y, int encodedU, int encodedV, int paramsIndex) {
      vertices.vertex(this.transform, x, y).color(encodedU, encodedV, 255, 255).lineWidth(paramsIndex + 1);
   }

   public CircleRenderState(Matrix3x2f transform, CircleSpec spec, ScreenRect scissorArea) {
      this.spec = spec;
      this.transform = new Matrix3x2f(transform);
      this.scissorArea = scissorArea;
      float radiusExtent = extent(spec);
      int left = (int)Math.floor(spec.x() - radiusExtent);
      int top = (int)Math.floor(spec.y() - radiusExtent);
      int diameter = (int)Math.ceil(radiusExtent * 2.0F);
      ScreenRect bounds = new ScreenRect(left, top, diameter, diameter).transformEachVertex(this.transform);
      this.bounds = scissorArea == null ? bounds : scissorArea.intersection(bounds);
   }

   @Override
   public TextureSetup textureSetup() {
      return TextureSetup.empty();
   }

   @Override
   public void setupVertices(VertexConsumer vertices) {
      int paramsIndex = CircleRenderer.getInstance().allocateParams(this.spec);
      if (paramsIndex >= 0) {
         float radiusExtent = extent(this.spec);
         float left = this.spec.x() - radiusExtent;
         float top = this.spec.y() - radiusExtent;
         float right = this.spec.x() + radiusExtent;
         float bottom = this.spec.y() + radiusExtent;
         this.vertex(vertices, left, top, 0, 0, paramsIndex);
         this.vertex(vertices, left, bottom, 0, 255, paramsIndex);
         this.vertex(vertices, right, bottom, 255, 255, paramsIndex);
         this.vertex(vertices, right, top, 255, 0, paramsIndex);
      }
   }

   @Override
   public RenderPipeline pipeline() {
      return CircleRenderer.renderPipeline;
   }
}
