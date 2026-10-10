package com.mouseclient.gui;

import com.mouseclient.render.pipeline.AtlasTextRenderer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2f;

public final class AtlasQuadBatchRenderState implements SimpleGuiElementRenderState {
   private final Matrix3x2f transform;
   private final TextureSetup textureSetup;
   private final ScreenRect scissorArea;
   private final List<AtlasQuadBatchRenderState.AtlasQuad> quads = new ArrayList<>(64);
   private float minX;
   private float minY;
   private float maxX;
   private float maxY;

   @Override
   public void setupVertices(VertexConsumer vertices) {
      for (AtlasQuadBatchRenderState.AtlasQuad quad : this.quads) {
         this.vertex(vertices, quad.left(), quad.top(), quad.uMin(), quad.vMin(), quad.colorTopLeft());
         this.vertex(vertices, quad.left(), quad.bottom(), quad.uMin(), quad.vMax(), quad.colorBottomLeft());
         this.vertex(vertices, quad.right(), quad.bottom(), quad.uMax(), quad.vMax(), quad.colorBottomRight());
         this.vertex(vertices, quad.right(), quad.top(), quad.uMax(), quad.vMin(), quad.colorTopRight());
      }
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
      ScreenRect bounds = new ScreenRect(left, top, width, height).transformEachVertex(this.transform);
      return this.scissorArea == null ? bounds : this.scissorArea.intersection(bounds);
   }

   @Override
   public ScreenRect scissorArea() {
      return this.scissorArea;
   }

   @Override
   public RenderPipeline pipeline() {
      return AtlasTextRenderer.TEXT_PIPELINE;
   }

   private void vertex(VertexConsumer vertices, float x, float y, float u, float v, int color) {
      vertices.vertex(this.transform, x, y).texture(u, v).color(color);
   }

   public AtlasQuadBatchRenderState(Matrix3x2f transform, TextureSetup textureSetup, ScreenRect scissorArea) {
      this.minX = (3.4028235E38F);
      this.minY = (3.4028235E38F);
      this.maxX = (-3.4028235E38F);
      this.maxY = (-3.4028235E38F);
      this.transform = new Matrix3x2f(transform);
      this.textureSetup = textureSetup;
      this.scissorArea = scissorArea;
   }

   public void add(float left, float top, float right, float bottom, float uMin, float vMin, float uMax, float vMax, int colorTopLeft, int colorTopRight, int colorBottomRight, int colorBottomLeft) {
      this.quads.add(new AtlasQuadBatchRenderState.AtlasQuad(left, top, right, bottom, uMin, vMin, uMax, vMax, colorTopLeft, colorTopRight, colorBottomRight, colorBottomLeft));
      this.minX = Math.min(this.minX, left);
      this.minY = Math.min(this.minY, top);
      this.maxX = Math.max(this.maxX, right);
      this.maxY = Math.max(this.maxY, bottom);
   }

   @Override
   public TextureSetup textureSetup() {
      return this.textureSetup;
   }

   private record AtlasQuad(
      float left,
      float top,
      float right,
      float bottom,
      float uMin,
      float vMin,
      float uMax,
      float vMax,
      int colorTopLeft,
      int colorTopRight,
      int colorBottomRight,
      int colorBottomLeft
   ) {
      public float left() {
         return this.left;
      }

      public float uMin() {
         return this.uMin;
      }

      public float bottom() {
         return this.bottom;
      }

      public float uMax() {
         return this.uMax;
      }

      public float vMax() {
         return this.vMax;
      }

      public float top() {
         return this.top;
      }

      public float vMin() {
         return this.vMin;
      }

      public float right() {
         return this.right;
      }
   }
}
