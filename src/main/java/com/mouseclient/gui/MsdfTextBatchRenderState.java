package com.mouseclient.gui;

import com.mouseclient.render.pipeline.MsdfTextRenderer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2f;

public final class MsdfTextBatchRenderState implements SimpleGuiElementRenderState {
   private final Matrix3x2f transform;
   private final TextureSetup textureSetup;
   private final ScreenRect scissorArea;
   private final boolean shimmer;
   private final float pxRange;
   private final List<MsdfTextBatchRenderState.MsdfGlyphQuad> glyphs = new ArrayList<>(128);
   private float minX;
   private float minY;
   private float maxX;
   private float maxY;

   @Override
   public TextureSetup textureSetup() {
      return this.textureSetup;
   }

   @Override
   public RenderPipeline pipeline() {
      return this.shimmer ? MsdfTextRenderer.SHIMMER_PIPELINE : MsdfTextRenderer.TEXT_PIPELINE;
   }

   @Override
   public ScreenRect scissorArea() {
      return this.scissorArea;
   }

   private void include(float x, float y) {
      this.minX = Math.min(this.minX, x);
      this.minY = Math.min(this.minY, y);
      this.maxX = Math.max(this.maxX, x);
      this.maxY = Math.max(this.maxY, y);
   }

   @Override
   public void setupVertices(VertexConsumer vertices) {
      if (this.shimmer) {
         for (MsdfTextBatchRenderState.MsdfGlyphQuad shimmerQuad : this.glyphs) {
            this.shimmerVertex(vertices, shimmerQuad.topLeftX(), shimmerQuad.topLeftY(), shimmerQuad.uMin(), shimmerQuad.vMin(), shimmerQuad.colorTopLeft(), shimmerQuad.shimmerLeft());
            this.shimmerVertex(vertices, shimmerQuad.bottomLeftX(), shimmerQuad.bottomLeftY(), shimmerQuad.uMin(), shimmerQuad.vMax(), shimmerQuad.colorBottomLeft(), shimmerQuad.shimmerLeft());
            this.shimmerVertex(vertices, shimmerQuad.bottomRightX(), shimmerQuad.bottomRightY(), shimmerQuad.uMax(), shimmerQuad.vMax(), shimmerQuad.colorBottomRight(), shimmerQuad.shimmerRight());
            this.shimmerVertex(vertices, shimmerQuad.topRightX(), shimmerQuad.topRightY(), shimmerQuad.uMax(), shimmerQuad.vMin(), shimmerQuad.colorTopRight(), shimmerQuad.shimmerRight());
         }
      } else {
         for (MsdfTextBatchRenderState.MsdfGlyphQuad plainQuad : this.glyphs) {
            this.vertex(vertices, plainQuad.topLeftX(), plainQuad.topLeftY(), plainQuad.uMin(), plainQuad.vMin(), plainQuad.colorTopLeft());
            this.vertex(vertices, plainQuad.bottomLeftX(), plainQuad.bottomLeftY(), plainQuad.uMin(), plainQuad.vMax(), plainQuad.colorBottomLeft());
            this.vertex(vertices, plainQuad.bottomRightX(), plainQuad.bottomRightY(), plainQuad.uMax(), plainQuad.vMax(), plainQuad.colorBottomRight());
            this.vertex(vertices, plainQuad.topRightX(), plainQuad.topRightY(), plainQuad.uMax(), plainQuad.vMin(), plainQuad.colorTopRight());
         }
      }
   }

   public void addGlyph(
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
      int colorBottomLeft,
      float shimmerLeft,
      float shimmerRight,
      float rotationDegrees,
      float rotationOriginX,
      float rotationOriginY
   ) {
      float topLeftX = left;
      float topLeftY = top;
      float bottomLeftX = left;
      float bottomLeftY = bottom;
      float bottomRightX = right;
      float bottomRightY = bottom;
      float topRightX = right;
      float topRightY = top;
      if (Math.abs(rotationDegrees) >= (0.001F)) {
         float rotationRadians = (float)Math.toRadians(rotationDegrees);
         float sin = (float)Math.sin(rotationRadians);
         float cos = (float)Math.cos(rotationRadians);
         float[] topLeft = this.rotate(topLeftX, topLeftY, rotationOriginX, rotationOriginY, sin, cos);
         float[] bottomLeft = this.rotate(bottomLeftX, bottomLeftY, rotationOriginX, rotationOriginY, sin, cos);
         float[] bottomRight = this.rotate(bottomRightX, bottomRightY, rotationOriginX, rotationOriginY, sin, cos);
         float[] topRight = this.rotate(topRightX, topRightY, rotationOriginX, rotationOriginY, sin, cos);
         topLeftX = topLeft[0];
         topLeftY = topLeft[1];
         bottomLeftX = bottomLeft[0];
         bottomLeftY = bottomLeft[1];
         bottomRightX = bottomRight[0];
         bottomRightY = bottomRight[1];
         topRightX = topRight[0];
         topRightY = topRight[1];
      }

      this.glyphs
         .add(
            new MsdfTextBatchRenderState.MsdfGlyphQuad(
               topLeftX, topLeftY, bottomLeftX, bottomLeftY, bottomRightX, bottomRightY, topRightX, topRightY, uMin, vMin, uMax, vMax, colorTopLeft, colorTopRight, colorBottomRight, colorBottomLeft, shimmerLeft, shimmerRight
            )
         );
      this.include(topLeftX, topLeftY);
      this.include(bottomLeftX, bottomLeftY);
      this.include(bottomRightX, bottomRightY);
      this.include(topRightX, topRightY);
   }

   private void vertex(VertexConsumer vertices, float x, float y, float u, float v, int color) {
      vertices.vertex(this.transform, x, y).texture(u, v).color(color).lineWidth(this.pxRange);
   }

   private float[] rotate(float x, float y, float originX, float originY, float sin, float cos) {
      float deltaX = x - originX;
      float deltaY = y - originY;
      return new float[]{originX + deltaX * cos - deltaY * sin, originY + deltaX * sin + deltaY * cos};
   }

   @Override
   public ScreenRect bounds() {
      if (this.glyphs.isEmpty()) {
         return new ScreenRect(0, 0, 1, 1);
      }

      int left = (int)Math.floor(this.minX);
      int top = (int)Math.floor(this.minY);
      int width = Math.max(1, (int)Math.ceil(this.maxX - this.minX));
      int height = Math.max(1, (int)Math.ceil(this.maxY - this.minY));
      ScreenRect bounds = new ScreenRect(left, top, width, height).transformEachVertex(this.transform);
      return this.scissorArea == null ? bounds : this.scissorArea.intersection(bounds);
   }

   private void shimmerVertex(VertexConsumer vertices, float x, float y, float u, float v, int color, float shimmerPosition) {
      vertices.vertex(this.transform, x, y).texture(u, v).color(color).lineWidth(shimmerPosition);
   }

   public MsdfTextBatchRenderState(Matrix3x2f transform, TextureSetup textureSetup, ScreenRect scissorArea, boolean shimmer, float pxRange) {
      this.minX = (3.4028235E38F);
      this.minY = (3.4028235E38F);
      this.maxX = (-3.4028235E38F);
      this.maxY = (-3.4028235E38F);
      this.transform = new Matrix3x2f(transform);
      this.textureSetup = textureSetup;
      this.scissorArea = scissorArea;
      this.shimmer = shimmer;
      this.pxRange = pxRange;
   }

   private record MsdfGlyphQuad(
      float topLeftX,
      float topLeftY,
      float bottomLeftX,
      float bottomLeftY,
      float bottomRightX,
      float bottomRightY,
      float topRightX,
      float topRightY,
      float uMin,
      float vMin,
      float uMax,
      float vMax,
      int colorTopLeft,
      int colorTopRight,
      int colorBottomRight,
      int colorBottomLeft,
      float shimmerLeft,
      float shimmerRight
   ) {
      public float uMax() {
         return this.uMax;
      }

      public float vMax() {
         return this.vMax;
      }

      public float topLeftX() {
         return this.topLeftX;
      }

      public float vMin() {
         return this.vMin;
      }

      public float bottomRightX() {
         return this.bottomRightX;
      }

      public float bottomRightY() {
         return this.bottomRightY;
      }

      public float shimmerLeft() {
         return this.shimmerLeft;
      }

      public float topRightY() {
         return this.topRightY;
      }

      public float shimmerRight() {
         return this.shimmerRight;
      }

      public float bottomLeftX() {
         return this.bottomLeftX;
      }

      public float topRightX() {
         return this.topRightX;
      }

      public float uMin() {
         return this.uMin;
      }

      public float topLeftY() {
         return this.topLeftY;
      }

      public float bottomLeftY() {
         return this.bottomLeftY;
      }
   }
}
