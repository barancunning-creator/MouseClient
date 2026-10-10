package com.mouseclient.gui;

import com.mouseclient.render.pipeline.LineRenderer;
import com.mouseclient.render.pipeline.LineSpec;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2f;

public final class LineRenderState implements SimpleGuiElementRenderState {
   private final LineSpec spec;
   private final Matrix3x2f transform;
   private final ScreenRect scissorArea;
   private final ScreenRect bounds;

   public LineRenderState(Matrix3x2f transform, LineSpec spec, ScreenRect scissorArea) {
      this.spec = spec;
      this.transform = new Matrix3x2f(transform);
      this.scissorArea = scissorArea;
      float left = Math.min(spec.startX(), spec.endX()) - spec.thickness();
      float top = Math.min(spec.startY(), spec.endY()) - spec.thickness();
      float right = Math.max(spec.startX(), spec.endX()) + spec.thickness();
      float bottom = Math.max(spec.startY(), spec.endY()) + spec.thickness();
      ScreenRect bounds = new ScreenRect(Math.round(left), Math.round(top), Math.round(right - left), Math.round(bottom - top))
         .transformEachVertex(this.transform);
      this.bounds = scissorArea == null ? bounds : scissorArea.intersection(bounds);
   }

   @Override
   public ScreenRect bounds() {
      return this.bounds;
   }

   @Override
   public void setupVertices(VertexConsumer vertices) {
      int paramsIndex = LineRenderer.getInstance().allocateParams(this.spec);
      if (paramsIndex >= 0) {
         float[] lineCorners = this.spec.corners();
         this.vertex(vertices, lineCorners[0], lineCorners[1], 0, 0, paramsIndex);
         this.vertex(vertices, lineCorners[2], lineCorners[3], 0, 255, paramsIndex);
         this.vertex(vertices, lineCorners[4], lineCorners[5], 255, 255, paramsIndex);
         this.vertex(vertices, lineCorners[6], lineCorners[7], 255, 0, paramsIndex);
      }
   }

   @Override
   public ScreenRect scissorArea() {
      return this.scissorArea;
   }

   private void vertex(VertexConsumer vertices, float x, float y, int encodedU, int encodedV, int paramsIndex) {
      vertices.vertex(this.transform, x, y).color(encodedU, encodedV, 255, 255).lineWidth(paramsIndex + 1);
   }

   @Override
   public TextureSetup textureSetup() {
      return TextureSetup.empty();
   }

   @Override
   public RenderPipeline pipeline() {
      return LineRenderer.renderPipeline;
   }
}
