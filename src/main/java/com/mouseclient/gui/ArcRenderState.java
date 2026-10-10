package com.mouseclient.gui;

import com.mouseclient.render.pipeline.ArcRenderer;
import com.mouseclient.render.pipeline.ArcSpec;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2f;

public final class ArcRenderState implements SimpleGuiElementRenderState {
   private final ArcSpec spec;
   private final Matrix3x2f transform;
   private final ScreenRect scissorArea;
   private final ScreenRect bounds;

   public ArcRenderState(Matrix3x2f transform, ArcSpec spec, ScreenRect scissorArea) {
      this.spec = spec;
      this.transform = new Matrix3x2f(transform);
      this.scissorArea = scissorArea;
      float spanX = spec.spanX();
      float spanY = spec.spanY();
      float centerX = spec.centerX();
      float liftedCenterY = spec.centerY() - spec.lift() * (0.5F);
      int left = (int)Math.floor(centerX - spanX);
      int top = (int)Math.floor(liftedCenterY - spanY);
      int width = (int)Math.ceil(spanX * 2.0F);
      int height = (int)Math.ceil(spanY * 2.0F);
      ScreenRect bounds = new ScreenRect(left, top, width, height).transformEachVertex(this.transform);
      this.bounds = scissorArea == null ? bounds : scissorArea.intersection(bounds);
   }

   @Override
   public TextureSetup textureSetup() {
      return TextureSetup.empty();
   }

   @Override
   public ScreenRect bounds() {
      return this.bounds;
   }

   @Override
   public void setupVertices(VertexConsumer vertices) {
      int paramsIndex = ArcRenderer.getInstance().allocateParams(this.spec);
      if (paramsIndex >= 0) {
         float spanX = this.spec.spanX();
         float spanY = this.spec.spanY();
         float centerX = this.spec.centerX();
         float liftedCenterY = this.spec.centerY() - this.spec.lift() * (0.5F);
         float left = centerX - spanX;
         float top = liftedCenterY - spanY;
         float right = centerX + spanX;
         float bottom = liftedCenterY + spanY;
         this.vertex(vertices, left, top, 0, 0, paramsIndex);
         this.vertex(vertices, left, bottom, 0, 255, paramsIndex);
         this.vertex(vertices, right, bottom, 255, 255, paramsIndex);
         this.vertex(vertices, right, top, 255, 0, paramsIndex);
      }
   }

   @Override
   public RenderPipeline pipeline() {
      return ArcRenderer.renderPipeline;
   }

   private void vertex(VertexConsumer vertices, float x, float y, int encodedU, int encodedV, int paramsIndex) {
      vertices.vertex(this.transform, x, y).color(encodedU, encodedV, 255, 255).lineWidth(paramsIndex + 1);
   }

   @Override
   public ScreenRect scissorArea() {
      return this.scissorArea;
   }
}
