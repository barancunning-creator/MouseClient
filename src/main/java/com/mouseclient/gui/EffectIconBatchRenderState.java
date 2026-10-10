package com.mouseclient.gui;

import com.mouseclient.render.pipeline.EffectIconRenderer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2f;

public final class EffectIconBatchRenderState implements SimpleGuiElementRenderState {
   private final Matrix3x2f transform;
   private final EffectIconAtlas atlas;
   private final List<EffectIconQuad> quads = new ArrayList<>(16);
   private float minX;
   private float minY;
   private float maxX;
   private float maxY;

   @Override
   public void setupVertices(VertexConsumer vertices) {
      for (EffectIconQuad quad : this.quads) {
         float left = quad.x();
         float top = quad.y();
         float right = quad.x() + quad.size();
         float bottom = quad.y() + quad.size();
         int color = quad.color();
         this.vertex(vertices, left, top, quad.uMin(), quad.vMin(), color);
         this.vertex(vertices, left, bottom, quad.uMin(), quad.vMax(), color);
         this.vertex(vertices, right, bottom, quad.uMax(), quad.vMax(), color);
         this.vertex(vertices, right, top, quad.uMax(), quad.vMin(), color);
      }
   }

   @Override
   public ScreenRect scissorArea() {
      return null;
   }

   private void vertex(VertexConsumer vertices, float x, float y, float u, float v, int color) {
      vertices.vertex(this.transform, x, y).texture(u, v).color(color);
   }

   @Override
   public RenderPipeline pipeline() {
      return EffectIconRenderer.ICON_PIPELINE;
   }

   public void add(EffectIconQuad quad) {
      this.quads.add(quad);
      this.minX = Math.min(this.minX, quad.x());
      this.minY = Math.min(this.minY, quad.y());
      this.maxX = Math.max(this.maxX, quad.x() + quad.size());
      this.maxY = Math.max(this.maxY, quad.y() + quad.size());
   }

   @Override
   public TextureSetup textureSetup() {
      return this.atlas.setup();
   }

   @Override
   public ScreenRect bounds() {
      if (this.quads.isEmpty()) {
         return new ScreenRect(0, 0, 1, 1);
      }

      int left = (int)Math.floor(this.minX);
      int top = (int)Math.floor(this.minY);
      int width = Math.max(1, (int)Math.ceil(this.maxX - this.minX));
      int height = Math.max(1, (int)Math.ceil(this.maxY - this.minY));
      return new ScreenRect(left, top, width, height).transformEachVertex(this.transform);
   }

   public EffectIconBatchRenderState(Matrix3x2f transform, EffectIconAtlas atlas) {
      this.minX = (3.4028235E38F);
      this.minY = (3.4028235E38F);
      this.maxX = (-3.4028235E38F);
      this.maxY = (-3.4028235E38F);
      this.transform = new Matrix3x2f(transform);
      this.atlas = atlas;
   }
}
