package com.mouseclient.gui;

import com.mouseclient.render.pipeline.RippleRenderer;
import com.mouseclient.render.pipeline.RippleSpec;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2f;

public final class RippleRenderState implements SimpleGuiElementRenderState {
   private final Matrix3x2f transform;
   private final RippleSpec spec;
   private final ScreenRect scissorArea;

   @Override
   public TextureSetup textureSetup() {
      return TextureSetup.empty();
   }

   @Override
   public void setupVertices(VertexConsumer vertices) {
      int paramsIndex = RippleRenderer.getInstance().allocateParams(this.spec);
      if (paramsIndex >= 0) {
         this.vertex(vertices, this.spec.x(), this.spec.y(), 0.0F, 0.0F, paramsIndex);
         this.vertex(vertices, this.spec.x(), this.spec.y() + this.spec.height(), 0.0F, 1.0F, paramsIndex);
         this.vertex(vertices, this.spec.x() + this.spec.width(), this.spec.y() + this.spec.height(), 1.0F, 1.0F, paramsIndex);
         this.vertex(vertices, this.spec.x() + this.spec.width(), this.spec.y(), 1.0F, 0.0F, paramsIndex);
      }
   }

   @Override
   public ScreenRect bounds() {
      return new ScreenRect(Math.round(this.spec.x()), Math.round(this.spec.y()), Math.round(this.spec.width()), Math.round(this.spec.height()))
         .transformEachVertex(this.transform);
   }

   public RippleRenderState(Matrix3x2f transform, RippleSpec spec, ScreenRect scissorArea) {
      this.transform = new Matrix3x2f(transform);
      this.spec = spec;
      this.scissorArea = scissorArea;
   }

   private void vertex(VertexConsumer vertices, float x, float y, float u, float v, int paramsIndex) {
      vertices.vertex(this.transform, x, y).texture(u, v).lineWidth(paramsIndex + 1);
   }

   @Override
   public RenderPipeline pipeline() {
      return RippleRenderer.renderPipeline;
   }

   @Override
   public ScreenRect scissorArea() {
      return this.scissorArea;
   }
}
