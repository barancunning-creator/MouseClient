package com.mouseclient.gui;

import com.mouseclient.render.pipeline.BracketRenderer;
import com.mouseclient.render.pipeline.BracketSpec;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2f;

public final class BracketRenderState implements SimpleGuiElementRenderState {
   private final BracketSpec spec;
   private final Matrix3x2f transform;
   private final ScreenRect scissorArea;
   private final ScreenRect bounds;

   public BracketRenderState(Matrix3x2f transform, BracketSpec spec, ScreenRect scissorArea) {
      this.spec = spec;
      this.transform = new Matrix3x2f(transform);
      this.scissorArea = scissorArea;
      float padding = pad(spec);
      int left = (int)Math.floor(spec.x() - padding);
      int top = (int)Math.floor(spec.y() - padding);
      int width = (int)Math.ceil(spec.x() + spec.size() + padding) - left;
      int height = (int)Math.ceil(spec.y() + spec.size() + padding) - top;
      ScreenRect bounds = new ScreenRect(left, top, width, height).transformEachVertex(this.transform);
      this.bounds = scissorArea == null ? bounds : scissorArea.intersection(bounds);
   }

   @Override
   public void setupVertices(VertexConsumer vertices) {
      int paramsIndex = BracketRenderer.getInstance().allocateParams(this.spec);
      if (paramsIndex >= 0) {
         float padding = pad(this.spec);
         float left = this.spec.x() - padding;
         float top = this.spec.y() - padding;
         float right = this.spec.x() + this.spec.size() + padding;
         float bottom = this.spec.y() + this.spec.size() + padding;
         int quadrant = this.spec.quadrant() & 3;
         boolean reverseU = quadrant == 0 || quadrant == 3;
         boolean reverseV = quadrant == 0 || quadrant == 1;
         int encodedU0 = reverseU ? 255 : 0;
         int encodedU1 = 255 - encodedU0;
         int encodedV0 = reverseV ? 255 : 0;
         int encodedV1 = 255 - encodedV0;
         this.vertex(vertices, left, top, encodedU0, encodedV0, paramsIndex);
         this.vertex(vertices, left, bottom, encodedU0, encodedV1, paramsIndex);
         this.vertex(vertices, right, bottom, encodedU1, encodedV1, paramsIndex);
         this.vertex(vertices, right, top, encodedU1, encodedV0, paramsIndex);
      }
   }

   private void vertex(VertexConsumer vertices, float x, float y, int encodedU, int encodedV, int paramsIndex) {
      vertices.vertex(this.transform, x, y).color(encodedU, encodedV, 255, 255).lineWidth(paramsIndex + 1);
   }

   private static float pad(BracketSpec spec) {
      return Math.max(spec.smoothness(), (0.05F));
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
   public RenderPipeline pipeline() {
      return BracketRenderer.renderPipeline;
   }

   @Override
   public ScreenRect scissorArea() {
      return this.scissorArea;
   }
}
