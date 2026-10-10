package com.mouseclient.gui;

import com.mouseclient.render.pipeline.GlassRenderer;
import com.mouseclient.render.pipeline.BlurTextureRegion;
import com.mouseclient.render.pipeline.GlassSpec;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2f;

public final class GlassRenderState implements SimpleGuiElementRenderState {
   private final GlassSpec spec;
   private final Matrix3x2f transform;
   private final ScreenRect scissorArea;
   private final ScreenRect bounds;
   private final BlurTextureRegion blurRegion;

   @Override
   public TextureSetup textureSetup() {
      return this.blurRegion.textureSetup;
   }

   @Override
   public ScreenRect bounds() {
      return this.bounds;
   }

   public GlassRenderState(Matrix3x2f transform, GlassSpec spec, ScreenRect scissorArea, BlurTextureRegion blurRegion) {
      this.spec = spec;
      this.transform = new Matrix3x2f(transform);
      this.scissorArea = scissorArea;
      this.blurRegion = blurRegion;
      ScreenRect bounds = new ScreenRect(Math.round(spec.x()), Math.round(spec.y()), Math.round(spec.width()), Math.round(spec.height()))
         .transformEachVertex(this.transform);
      this.bounds = scissorArea == null ? bounds : scissorArea.intersection(bounds);
   }

   private void vertex(VertexConsumer vertices, float x, float y, int paramsIndex) {
      vertices.vertex(this.transform, x, y).color(this.spec.color()).lineWidth(paramsIndex + 1);
   }

   @Override
   public ScreenRect scissorArea() {
      return this.scissorArea;
   }

   @Override
   public RenderPipeline pipeline() {
      return GlassRenderer.renderPipeline;
   }

   @Override
   public void setupVertices(VertexConsumer vertices) {
      int paramsIndex = GlassRenderer.getInstance().allocateParams(this.spec, this.blurRegion);
      if (paramsIndex >= 0) {
         float left = this.spec.x();
         float top = this.spec.y();
         float right = this.spec.x() + this.spec.width();
         float bottom = this.spec.y() + this.spec.height();
         this.vertex(vertices, left, top, paramsIndex);
         this.vertex(vertices, left, bottom, paramsIndex);
         this.vertex(vertices, right, bottom, paramsIndex);
         this.vertex(vertices, right, top, paramsIndex);
      }
   }
}
