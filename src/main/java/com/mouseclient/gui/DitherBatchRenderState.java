package com.mouseclient.gui;

import com.mouseclient.render.pipeline.DitherRenderer;
import com.mouseclient.render.pipeline.DitherSpec;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2f;

public final class DitherBatchRenderState implements SimpleGuiElementRenderState {
   private final Matrix3x2f transform;
   private final ScreenRect scissorArea;
   private final List<DitherSpec> quads = new ArrayList<>(4);
   private float minX;
   private float minY;
   private float maxX;
   private float maxY;

   public void add(DitherSpec spec) {
      this.quads.add(spec);
      this.minX = Math.min(this.minX, spec.x());
      this.minY = Math.min(this.minY, spec.y());
      this.maxX = Math.max(this.maxX, spec.x() + spec.width());
      this.maxY = Math.max(this.maxY, spec.y() + spec.height());
   }

   @Override
   public TextureSetup textureSetup() {
      return TextureSetup.empty();
   }

   @Override
   public ScreenRect scissorArea() {
      return this.scissorArea;
   }

   @Override
   public void setupVertices(VertexConsumer vertices) {
      for (DitherSpec spec : this.quads) {
         int paramsIndex = DitherRenderer.getInstance().allocateParams(spec);
         if (paramsIndex >= 0) {
            float left = spec.x();
            float top = spec.y();
            float right = spec.x() + spec.width();
            float bottom = spec.y() + spec.height();
            int color = spec.color();
            this.vertex(vertices, left, top, 0.0F, 0.0F, color, paramsIndex);
            this.vertex(vertices, left, bottom, 0.0F, 1.0F, color, paramsIndex);
            this.vertex(vertices, right, bottom, 1.0F, 1.0F, color, paramsIndex);
            this.vertex(vertices, right, top, 1.0F, 0.0F, color, paramsIndex);
         }
      }
   }

   @Override
   public ScreenRect bounds() {
      if (this.scissorArea != null) {
         return this.scissorArea;
      }

      if (this.quads.isEmpty()) {
         return new ScreenRect(0, 0, 1, 1);
      }

      int left = (int)Math.floor(this.minX);
      int top = (int)Math.floor(this.minY);
      int width = Math.max(1, (int)Math.ceil(this.maxX - this.minX));
      int height = Math.max(1, (int)Math.ceil(this.maxY - this.minY));
      return new ScreenRect(left, top, width, height).transformEachVertex(this.transform);
   }

   public DitherBatchRenderState(Matrix3x2f transform, ScreenRect scissorArea) {
      this.minX = (3.4028235E38F);
      this.minY = (3.4028235E38F);
      this.maxX = (-3.4028235E38F);
      this.maxY = (-3.4028235E38F);
      this.transform = new Matrix3x2f(transform);
      this.scissorArea = scissorArea;
   }

   private void vertex(VertexConsumer vertices, float x, float y, float u, float v, int color, int paramsIndex) {
      vertices.vertex(this.transform, x, y).texture(u, v).color(color).lineWidth(paramsIndex + 1);
   }

   @Override
   public RenderPipeline pipeline() {
      return DitherRenderer.renderPipeline;
   }
}
