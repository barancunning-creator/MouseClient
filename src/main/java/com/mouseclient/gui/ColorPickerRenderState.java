package com.mouseclient.gui;

import com.mouseclient.render.pipeline.ColorPickerRenderer;
import com.mouseclient.render.pipeline.ColorPickerSpec;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2f;

public final class ColorPickerRenderState implements SimpleGuiElementRenderState {
   private final ColorPickerSpec spec;
   private final Matrix3x2f transform;
   private final ScreenRect scissorArea;
   private final ScreenRect bounds;

   @Override
   public void setupVertices(VertexConsumer vertices) {
      int paramsIndex = ColorPickerRenderer.getInstance().allocateParams(this.spec);
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
   public TextureSetup textureSetup() {
      return TextureSetup.empty();
   }

   @Override
   public ScreenRect bounds() {
      return this.bounds;
   }

   private void vertex(VertexConsumer vertices, float x, float y, int encodedU, int encodedV, int paramsIndex) {
      vertices.vertex(this.transform, x, y).color(encodedU, encodedV, 255, 255).lineWidth(paramsIndex + 1);
   }

   @Override
   public ScreenRect scissorArea() {
      return this.scissorArea;
   }

   @Override
   public RenderPipeline pipeline() {
      return ColorPickerRenderer.renderPipeline;
   }

   public ColorPickerRenderState(Matrix3x2f transform, ColorPickerSpec spec, ScreenRect scissorArea) {
      this.spec = spec;
      this.transform = new Matrix3x2f(transform);
      this.scissorArea = scissorArea;
      int left = (int)Math.floor(spec.x());
      int top = (int)Math.floor(spec.y());
      int width = (int)Math.ceil(spec.width());
      int height = (int)Math.ceil(spec.height());
      ScreenRect bounds = new ScreenRect(left, top, width, height).transformEachVertex(this.transform);
      this.bounds = scissorArea == null ? bounds : scissorArea.intersection(bounds);
   }
}
