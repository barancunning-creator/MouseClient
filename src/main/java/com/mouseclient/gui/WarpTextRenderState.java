package com.mouseclient.gui;

import com.mouseclient.render.pipeline.WarpTextRenderer;
import com.mouseclient.render.pipeline.WarpTextSpec;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2f;

public final class WarpTextRenderState implements SimpleGuiElementRenderState {
   private final Matrix3x2f transform;
   private final TextureSetup textureSetup;
   private final ScreenRect scissorArea;
   private final WarpTextSpec spec;

   private void vertex(VertexConsumer vertices, float x, float y, float u, float v, int paramsIndex) {
      vertices.vertex(this.transform, x, y).texture(u, v).color(this.spec.color()).lineWidth(paramsIndex + 1);
   }

   @Override
   public ScreenRect bounds() {
      return this.scissorArea != null
         ? this.scissorArea
         : new ScreenRect(
               (int)Math.floor(this.spec.x()),
               (int)Math.floor(this.spec.y()),
               Math.max(1, (int)Math.ceil(this.spec.width())),
               Math.max(1, (int)Math.ceil(this.spec.height()))
            )
            .transformEachVertex(this.transform);
   }

   @Override
   public void setupVertices(VertexConsumer vertices) {
      int paramsIndex = WarpTextRenderer.getInstance().allocateParams(this.spec);
      if (paramsIndex >= 0) {
         this.vertex(vertices, this.spec.x(), this.spec.y(), 0.0F, 0.0F, paramsIndex);
         this.vertex(vertices, this.spec.x(), this.spec.y() + this.spec.height(), 0.0F, 1.0F, paramsIndex);
         this.vertex(vertices, this.spec.x() + this.spec.width(), this.spec.y() + this.spec.height(), 1.0F, 1.0F, paramsIndex);
         this.vertex(vertices, this.spec.x() + this.spec.width(), this.spec.y(), 1.0F, 0.0F, paramsIndex);
      }
   }

   @Override
   public RenderPipeline pipeline() {
      return WarpTextRenderer.renderPipeline;
   }

   @Override
   public ScreenRect scissorArea() {
      return this.scissorArea;
   }

   public WarpTextRenderState(Matrix3x2f transform, TextureSetup textureSetup, ScreenRect scissorArea, WarpTextSpec spec) {
      this.transform = new Matrix3x2f(transform);
      this.textureSetup = textureSetup;
      this.scissorArea = scissorArea;
      this.spec = spec;
   }

   @Override
   public TextureSetup textureSetup() {
      return this.textureSetup;
   }
}
