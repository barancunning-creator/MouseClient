package com.mouseclient.gui;

import com.mouseclient.render.pipeline.GuiBlurRenderer;
import com.mouseclient.render.pipeline.BlurTextureRegion;
import com.mouseclient.render.pipeline.BlurSpec;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2f;

public final class BlurRenderState implements SimpleGuiElementRenderState {
   private final BlurSpec spec;
   private final Matrix3x2f transform;
   private final ScreenRect scissorArea;
   private final ScreenRect bounds;
   private final BlurTextureRegion blurRegion;

   @Override
   public TextureSetup textureSetup() {
      return this.blurRegion.textureSetup;
   }

   private void vertex(VertexConsumer vertices, float x, float y, int color, int paramsIndex) {
      vertices.vertex(this.transform, x, y).color(color).lineWidth(paramsIndex + 1);
   }

   @Override
   public ScreenRect scissorArea() {
      return this.scissorArea;
   }

   @Override
   public void setupVertices(VertexConsumer vertices) {
      int paramsIndex = GuiBlurRenderer.getInstance().allocateParams(this.spec, this.blurRegion);
      if (paramsIndex >= 0) {
         float minX = this.spec.x();
         float minY = this.spec.y();
         float maxX = this.spec.x() + this.spec.width();
         float maxY = this.spec.y() + this.spec.height();
         this.vertex(vertices, minX, minY, this.spec.colorTopLeft(), paramsIndex);
         this.vertex(vertices, minX, maxY, this.spec.colorBottomLeft(), paramsIndex);
         this.vertex(vertices, maxX, maxY, this.spec.colorBottomRight(), paramsIndex);
         this.vertex(vertices, maxX, minY, this.spec.colorTopRight(), paramsIndex);
      }
   }

   @Override
   public RenderPipeline pipeline() {
      return GuiBlurRenderer.COMPOSITE_PIPELINE;
   }

   public BlurRenderState(Matrix3x2f transform, BlurSpec spec, ScreenRect scissorArea, BlurTextureRegion blurRegion) {
      this.spec = spec;
      this.transform = new Matrix3x2f(transform);
      this.scissorArea = scissorArea;
      this.blurRegion = blurRegion;
      ScreenRect bounds = new ScreenRect(Math.round(spec.x()), Math.round(spec.y()), Math.round(spec.width()), Math.round(spec.height()))
         .transformEachVertex(this.transform);
      this.bounds = scissorArea == null ? bounds : scissorArea.intersection(bounds);
   }

   @Override
   public ScreenRect bounds() {
      return this.bounds;
   }
}
