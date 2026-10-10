package com.mouseclient.gui;

import com.mouseclient.render.pipeline.GlassOutlineRenderer;
import com.mouseclient.render.pipeline.BlurTextureRegion;
import com.mouseclient.render.pipeline.GlassOutlineSpec;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2f;

public final class GlassOutlineRenderState implements SimpleGuiElementRenderState {
   private final GlassOutlineSpec spec;
   private final Matrix3x2f transform;
   private final ScreenRect scissorArea;
   private final ScreenRect bounds;
   private final BlurTextureRegion blurRegion;

   private void vertex(VertexConsumer vertices, float x, float y, int encodedU, int encodedV, int paramsIndex) {
      vertices.vertex(this.transform, x, y).color(encodedU, encodedV, 255, 255).lineWidth(paramsIndex + 1);
   }

   @Override
   public TextureSetup textureSetup() {
      return this.blurRegion.textureSetup;
   }

   @Override
   public void setupVertices(VertexConsumer vertices) {
      int paramsIndex = GlassOutlineRenderer.getInstance().allocateParams(this.spec, this.blurRegion);
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
   public ScreenRect scissorArea() {
      return this.scissorArea;
   }

   public GlassOutlineRenderState(Matrix3x2f transform, GlassOutlineSpec spec, ScreenRect scissorArea, BlurTextureRegion blurRegion) {
      this.spec = spec;
      this.transform = new Matrix3x2f(transform);
      this.scissorArea = scissorArea;
      this.blurRegion = blurRegion;
      ScreenRect bounds = new ScreenRect(Math.round(spec.x()), Math.round(spec.y()), Math.round(spec.width()), Math.round(spec.height()))
         .transformEachVertex(this.transform);
      this.bounds = scissorArea == null ? bounds : scissorArea.intersection(bounds);
   }

   @Override
   public RenderPipeline pipeline() {
      return GlassOutlineRenderer.renderPipeline;
   }

   @Override
   public ScreenRect bounds() {
      return this.bounds;
   }
}
