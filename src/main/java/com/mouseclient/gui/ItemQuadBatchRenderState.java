package com.mouseclient.gui;

import com.mouseclient.internal.GuiScale;
import com.mouseclient.render.pipeline.ItemRenderer;
import com.mouseclient.render.pipeline.ItemQuad;
import com.mouseclient.render.pipeline.ItemAtlasTexture;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2f;

public final class ItemQuadBatchRenderState implements SimpleGuiElementRenderState {
   
   private final Matrix3x2f transform;
   private final ItemAtlasTexture atlas;
   private float[] vertexData = new float[256];
   private int[] colors = new int[16];
   private float[] glintStrengths = new float[16];
   private ScreenRect scissorArea;
   private int quadCount;
   private float minX;
   private float minY;
   private float maxX;
   private float maxY;

   @Override
   public void setupVertices(VertexConsumer vertices) {
      for (int quadIndex = 0; quadIndex < this.quadCount; quadIndex++) {
         int paramsIndex = ItemRenderer.getInstance().addGlintParam(this.glintStrengths[quadIndex]);
         if (paramsIndex >= 0) {
            int vertexOffset = quadIndex * 16;
            int color = this.colors[quadIndex];
            this.vertex(
               vertices, this.vertexData[vertexOffset], this.vertexData[vertexOffset + 1], this.vertexData[vertexOffset + 2], this.vertexData[vertexOffset + 3], color, paramsIndex
            );
            this.vertex(
               vertices, this.vertexData[vertexOffset + 4], this.vertexData[vertexOffset + 5], this.vertexData[vertexOffset + 6], this.vertexData[vertexOffset + 7], color, paramsIndex
            );
            this.vertex(
               vertices,
               this.vertexData[vertexOffset + 8],
               this.vertexData[vertexOffset + 9],
               this.vertexData[vertexOffset + 10],
               this.vertexData[vertexOffset + 11],
               color,
               paramsIndex
            );
            this.vertex(
               vertices,
               this.vertexData[vertexOffset + 12],
               this.vertexData[vertexOffset + 13],
               this.vertexData[vertexOffset + 14],
               this.vertexData[vertexOffset + 15],
               color,
               paramsIndex
            );
         }
      }
   }

   private void ensureCapacity(int capacity) {
      if (capacity > this.colors.length) {
         int newCapacity = Math.max(capacity, this.colors.length * 2);
         float[] newVertexData = new float[newCapacity * 16];
         int[] newColors = new int[newCapacity];
         float[] newGlintStrengths = new float[newCapacity];
         System.arraycopy(this.vertexData, 0, newVertexData, 0, this.quadCount * 16);
         System.arraycopy(this.colors, 0, newColors, 0, this.quadCount);
         System.arraycopy(this.glintStrengths, 0, newGlintStrengths, 0, this.quadCount);
         this.vertexData = newVertexData;
         this.colors = newColors;
         this.glintStrengths = newGlintStrengths;
      }
   }

   private void vertex(VertexConsumer vertices, float x, float y, float u, float v, int color, int paramsIndex) {
      vertices.vertex(this.transform, x, y).texture(u, v).color(color).lineWidth(paramsIndex + 1);
   }

   public ItemQuadBatchRenderState(Matrix3x2f transform, ItemAtlasTexture atlas, ScreenRect scissorArea) {
      this.minX = (3.4028235E38F);
      this.minY = (3.4028235E38F);
      this.maxX = (-3.4028235E38F);
      this.maxY = (-3.4028235E38F);
      this.transform = new Matrix3x2f(transform);
      this.atlas = atlas;
      this.scissorArea = scissorArea;
   }

   public void add(ItemQuad quad, float x, float y, float scale, int color, float glintStrength) {
      this.ensureCapacity(this.quadCount + 1);
      int vertexOffset = this.quadCount * 16;
      float x1 = x + quad.x1() * scale;
      float y1 = y + quad.y1() * scale;
      float x2 = x + quad.x2() * scale;
      float y2 = y + quad.y2() * scale;
      float x3 = x + quad.x3() * scale;
      float y3 = y + quad.y3() * scale;
      float x4 = x + quad.x4() * scale;
      float y4 = y + quad.y4() * scale;
      this.vertexData[vertexOffset] = x1;
      this.vertexData[vertexOffset + 1] = y1;
      this.vertexData[vertexOffset + 2] = quad.u1();
      this.vertexData[vertexOffset + 3] = quad.v1();
      this.vertexData[vertexOffset + 4] = x2;
      this.vertexData[vertexOffset + 5] = y2;
      this.vertexData[vertexOffset + 6] = quad.u2();
      this.vertexData[vertexOffset + 7] = quad.v2();
      this.vertexData[vertexOffset + 8] = x3;
      this.vertexData[vertexOffset + 9] = y3;
      this.vertexData[vertexOffset + 10] = quad.u3();
      this.vertexData[vertexOffset + 11] = quad.v3();
      this.vertexData[vertexOffset + 12] = x4;
      this.vertexData[vertexOffset + 13] = y4;
      this.vertexData[vertexOffset + 14] = quad.u4();
      this.vertexData[vertexOffset + 15] = quad.v4();
      this.colors[this.quadCount] = color;
      this.glintStrengths[this.quadCount] = glintStrength;
      this.quadCount++;
      this.include(x1, y1);
      this.include(x2, y2);
      this.include(x3, y3);
      this.include(x4, y4);
   }

   @Override
   public ScreenRect bounds() {
      if (this.quadCount == 0) {
         return new ScreenRect(0, 0, 1, 1);
      }

      int left = (int)Math.floor(this.minX);
      int top = (int)Math.floor(this.minY);
      int width = Math.max(1, (int)Math.ceil(this.maxX - this.minX));
      int height = Math.max(1, (int)Math.ceil(this.maxY - this.minY));
      return new ScreenRect(left, top, width, height).transformEachVertex(this.transform);
   }

   @Override
   public ScreenRect scissorArea() {
      return this.scissorArea;
   }

   @Override
   public TextureSetup textureSetup() {
      return this.transform.m00() * GuiScale.getWindowScale() < GuiScale.getBaseScale() ? this.atlas.linearSetup() : this.atlas.setup();
   }

   @Override
   public RenderPipeline pipeline() {
      return ItemRenderer.renderPipeline;
   }

   private void include(float x, float y) {
      this.minX = Math.min(this.minX, x);
      this.minY = Math.min(this.minY, y);
      this.maxX = Math.max(this.maxX, x);
      this.maxY = Math.max(this.maxY, y);
   }
}
