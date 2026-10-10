package com.mouseclient.gui;

import com.mouseclient.render.pipeline.ShimmerRenderer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2f;

public final class ShimmerRenderState implements SimpleGuiElementRenderState {
   private final Matrix3x2f transform;
   private final ShimmerSpec shimmer;
   private final ScreenRect scissorArea;

   public ShimmerSpec shimmer() {
      return this.shimmer;
   }

   private void vertex(VertexConsumer vertices, float x, float y, float u, float v, int color, int paramsIndex) {
      vertices.vertex(this.transform, x, y).texture(u, v).color(color).lineWidth(paramsIndex + 1);
   }

   @Override
   public void setupVertices(VertexConsumer vertices) {
      int paramsIndex = ShimmerRenderer.getInstance().allocateParams(this);
      if (paramsIndex >= 0) {
         float left = this.shimmer.x();
         float top = this.shimmer.y();
         float right = this.shimmer.x() + this.shimmer.width();
         float bottom = this.shimmer.y() + this.shimmer.height();
         int color = this.shimmer.color();
         this.vertex(vertices, left, top, 0.0F, 0.0F, color, paramsIndex);
         this.vertex(vertices, left, bottom, 0.0F, 1.0F, color, paramsIndex);
         this.vertex(vertices, right, bottom, 1.0F, 1.0F, color, paramsIndex);
         this.vertex(vertices, right, top, 1.0F, 0.0F, color, paramsIndex);
      }
   }

   @Override
   public RenderPipeline pipeline() {
      return ShimmerRenderer.renderPipeline;
   }

   public ShimmerRenderState(Matrix3x2f transform, ShimmerSpec shimmer, ScreenRect scissorArea) {
      this.transform = new Matrix3x2f(transform);
      this.shimmer = shimmer;
      this.scissorArea = scissorArea;
   }

   @Override
   public ScreenRect bounds() {
      int left = (int)Math.floor(this.shimmer.x());
      int top = (int)Math.floor(this.shimmer.y());
      int width = Math.max(1, (int)Math.ceil(this.shimmer.width()));
      int height = Math.max(1, (int)Math.ceil(this.shimmer.height()));
      ScreenRect bounds = new ScreenRect(left, top, width, height).transformEachVertex(this.transform);
      return this.scissorArea == null ? bounds : this.scissorArea.intersection(bounds);
   }

   @Override
   public TextureSetup textureSetup() {
      return TextureSetup.empty();
   }

   @Override
   public ScreenRect scissorArea() {
      return this.scissorArea;
   }
}
