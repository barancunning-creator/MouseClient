package com.mouseclient.gui;

import com.mouseclient.render.pipeline.GlowAtlasCapture;
import com.mouseclient.render.pipeline.GuiGlowRenderer;
import com.mouseclient.render.pipeline.Glow;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2f;

public final class GlowRenderState implements SimpleGuiElementRenderState {
   private final Glow glow;
   private final Matrix3x2f transform;
   private final ScreenRect scissorArea;
   private final ScreenRect bounds;
   private final GlowAtlasCapture capture;

   @Override
   public RenderPipeline pipeline() {
      return GuiGlowRenderer.COMPOSITE_PIPELINE;
   }

   @Override
   public ScreenRect scissorArea() {
      return this.scissorArea;
   }

   @Override
   public TextureSetup textureSetup() {
      return this.capture.textureSetup;
   }

   public GlowRenderState(Matrix3x2f transform, Glow glow, ScreenRect scissorArea, GlowAtlasCapture capture) {
      this.glow = glow;
      this.transform = new Matrix3x2f(transform);
      this.scissorArea = scissorArea;
      this.capture = capture;
      float padding = glow.effectivePad();
      ScreenRect bounds = new ScreenRect(
            Math.round(glow.x() - padding), Math.round(glow.y() - padding), Math.round(glow.width() + padding * 2.0F), Math.round(glow.height() + padding * 2.0F)
         )
         .transformEachVertex(this.transform);
      this.bounds = scissorArea == null ? bounds : scissorArea.intersection(bounds);
   }

   @Override
   public ScreenRect bounds() {
      return this.bounds;
   }

   private void vertex(VertexConsumer vertices, float x, float y, int paramsIndex) {
      vertices.vertex(this.transform, x, y).color(this.glow.color()).lineWidth(paramsIndex + 1);
   }

   @Override
   public void setupVertices(VertexConsumer vertices) {
      int paramsIndex = GuiGlowRenderer.getInstance().allocateParams(this.glow, this.capture);
      if (paramsIndex >= 0) {
         float padding = this.glow.effectivePad();
         float minX = this.glow.x() - padding;
         float minY = this.glow.y() - padding;
         float maxX = this.glow.x() + this.glow.width() + padding;
         float maxY = this.glow.y() + this.glow.height() + padding;
         this.vertex(vertices, minX, minY, paramsIndex);
         this.vertex(vertices, minX, maxY, paramsIndex);
         this.vertex(vertices, maxX, maxY, paramsIndex);
         this.vertex(vertices, maxX, minY, paramsIndex);
      }
   }
}
