package com.mouseclient.gui;

import com.mouseclient.render.pipeline.Outline360Renderer;
import com.mouseclient.render.pipeline.Outline360Spec;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2f;

public final class Outline360RenderState implements SimpleGuiElementRenderState {
   private final Outline360Spec spec;
   private final Matrix3x2f transform;
   private final ScreenRect bounds;

   public Outline360RenderState(Matrix3x2f transform, Outline360Spec spec) {
      this.spec = spec;
      this.transform = new Matrix3x2f(transform);
      this.bounds = new ScreenRect(Math.round(spec.x()), Math.round(spec.y()), Math.round(spec.width()), Math.round(spec.height()))
         .transformEachVertex(this.transform);
   }

   private void vertex(VertexConsumer vertices, float x, float y, int encodedU, int encodedV, int paramsIndex) {
      vertices.vertex(this.transform, x, y).color(encodedU, encodedV, 255, 255).lineWidth(paramsIndex + 1);
   }

   @Override
   public void setupVertices(VertexConsumer vertices) {
      int paramsIndex = Outline360Renderer.getInstance().allocateParams(this.spec);
      if (paramsIndex >= 0) {
         float left = this.spec.x();
         float top = this.spec.y();
         float right = this.spec.x() + this.spec.width();
         float bottom = this.spec.y() + this.spec.height();
         this.vertex(vertices, left, top, 0, 0, paramsIndex);
         this.vertex(vertices, left, bottom, 0, 255, paramsIndex);
         this.vertex(vertices, right, bottom, 255, 255, paramsIndex);
         this.vertex(vertices, right, top, 255, 0, paramsIndex);
      }
   }

   @Override
   public ScreenRect bounds() {
      return this.bounds;
   }

   @Override
   public RenderPipeline pipeline() {
      return Outline360Renderer.renderPipeline;
   }

   @Override
   public TextureSetup textureSetup() {
      return TextureSetup.empty();
   }

   @Override
   public ScreenRect scissorArea() {
      return null;
   }
}
