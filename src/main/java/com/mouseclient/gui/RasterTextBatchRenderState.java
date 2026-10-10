package com.mouseclient.gui;

import com.mouseclient.internal.PixelAlignment;
import com.mouseclient.render.avatar.FontAtlasPage;
import com.mouseclient.render.avatar.LayoutGlyph;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2f;

public final class RasterTextBatchRenderState implements SimpleGuiElementRenderState {
   private final Matrix3x2f transform;
   private final FontAtlasPage page;
   private final ScreenRect scissorArea;
   private final RenderPipeline renderPipeline;
   private final List<RasterGlyphQuad> quads = new ArrayList<>(128);
   private float minX;
   private float minY;
   private float maxX;
   private float maxY;

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

   private void vertex(VertexConsumer vertices, float x, float y, float u, float v, int color) {
      vertices.vertex(this.transform, x, y).texture(u, v).color(color);
   }

   private void include(float x, float y) {
      this.minX = Math.min(this.minX, x);
      this.minY = Math.min(this.minY, y);
      this.maxX = Math.max(this.maxX, x);
      this.maxY = Math.max(this.maxY, y);
   }

   @Override
   public ScreenRect scissorArea() {
      return this.scissorArea;
   }

   @Override
   public void setupVertices(VertexConsumer vertices) {
      for (RasterGlyphQuad quad : this.quads) {
         this.vertex(vertices, quad.topLeftX(), quad.topLeftY(), quad.uMin(), quad.vMin(), quad.colorTopLeft());
         this.vertex(vertices, quad.bottomLeftX(), quad.bottomLeftY(), quad.uMin(), quad.vMax(), quad.colorBottomLeft());
         this.vertex(vertices, quad.bottomRightX(), quad.bottomRightY(), quad.uMax(), quad.vMax(), quad.colorBottomRight());
         this.vertex(vertices, quad.topRightX(), quad.topRightY(), quad.uMax(), quad.vMin(), quad.colorTopRight());
      }
   }

   public void add(
      List<LayoutGlyph> glyphs,
      float x,
      float y,
      int colorTopLeft,
      int colorTopRight,
      int colorBottomRight,
      int colorBottomLeft,
      float rotationDegrees,
      float rotationOriginX,
      float rotationOriginY,
      boolean fadeLeft,
      boolean fadeRight,
      float fadeLeftX,
      float fadeRightX,
      float fadeWidth,
      float fadeLeftStrength,
      float fadeRightStrength,
      boolean snapOrigin
   ) {
      boolean unrotated = Math.abs(rotationDegrees) < (0.001F);
      if (unrotated && snapOrigin) {
         x = PixelAlignment.snap(x);
         y = PixelAlignment.snap(y);
      }

      float rotationRadians = (float)Math.toRadians(rotationDegrees);
      float sin = (float)Math.sin(rotationRadians);
      float cos = (float)Math.cos(rotationRadians);

      for (LayoutGlyph glyph : glyphs) {
         float left = glyph.left() + x;
         float top = glyph.top() + y;
         float right = glyph.right() + x;
         float bottom = glyph.bottom() + y;
         float[] topLeft = this.rotate(left, top, rotationOriginX, rotationOriginY, sin, cos);
         float[] bottomLeft = this.rotate(left, bottom, rotationOriginX, rotationOriginY, sin, cos);
         float[] bottomRight = this.rotate(right, bottom, rotationOriginX, rotationOriginY, sin, cos);
         float[] topRight = this.rotate(right, top, rotationOriginX, rotationOriginY, sin, cos);
         this.quads
            .add(
               new RasterGlyphQuad(
                  topLeft[0],
                  topLeft[1],
                  bottomLeft[0],
                  bottomLeft[1],
                  bottomRight[0],
                  bottomRight[1],
                  topRight[0],
                  topRight[1],
                  glyph.uMin(),
                  glyph.vMin(),
                  glyph.uMax(),
                  glyph.vMax(),
                  this.fadeColor(colorTopLeft, topLeft[0], fadeLeft, fadeRight, fadeLeftX, fadeRightX, fadeWidth, fadeLeftStrength, fadeRightStrength),
                  this.fadeColor(colorTopRight, topRight[0], fadeLeft, fadeRight, fadeLeftX, fadeRightX, fadeWidth, fadeLeftStrength, fadeRightStrength),
                  this.fadeColor(colorBottomRight, bottomRight[0], fadeLeft, fadeRight, fadeLeftX, fadeRightX, fadeWidth, fadeLeftStrength, fadeRightStrength),
                  this.fadeColor(colorBottomLeft, bottomLeft[0], fadeLeft, fadeRight, fadeLeftX, fadeRightX, fadeWidth, fadeLeftStrength, fadeRightStrength)
               )
            );
         this.include(topLeft[0], topLeft[1]);
         this.include(bottomLeft[0], bottomLeft[1]);
         this.include(bottomRight[0], bottomRight[1]);
         this.include(topRight[0], topRight[1]);
      }
   }

   private float smoothstep(float progress) {
      return progress * progress * ((3.0F) - 2.0F * progress);
   }

   @Override
   public RenderPipeline pipeline() {
      return this.renderPipeline;
   }

   public RasterTextBatchRenderState(Matrix3x2f transform, FontAtlasPage page, ScreenRect scissorArea, RenderPipeline renderPipeline) {
      this.minX = (3.4028235E38F);
      this.minY = (3.4028235E38F);
      this.maxX = (-3.4028235E38F);
      this.maxY = (-3.4028235E38F);
      this.transform = new Matrix3x2f(transform);
      this.page = page;
      this.scissorArea = scissorArea;
      this.renderPipeline = renderPipeline;
   }

   private float clamp(float value, float minimum, float maximum) {
      return Math.max(minimum, Math.min(maximum, value));
   }

   private int fadeColor(int color, float x, boolean fadeLeft, boolean fadeRight, float fadeLeftX, float fadeRightX, float fadeWidth, float fadeLeftStrength, float fadeRightStrength) {
      if ((fadeLeft || fadeRight) && !(fadeWidth <= 0.0F) && !(fadeRightX <= fadeLeftX) && (!(fadeLeftStrength <= (0.001F)) || !(fadeRightStrength <= (0.001F)))) {
         float alphaFactor = 1.0F;
         if (fadeLeft) {
            float leftProgress = this.smoothstep(this.clamp((x - fadeLeftX) / fadeWidth, 0.0F, 1.0F));
            alphaFactor = Math.min(alphaFactor, 1.0F - this.clamp(fadeLeftStrength, 0.0F, 1.0F) * (1.0F - leftProgress));
         }

         if (fadeRight) {
            float rightProgress = this.smoothstep(this.clamp((fadeRightX - x) / fadeWidth, 0.0F, 1.0F));
            alphaFactor = Math.min(alphaFactor, 1.0F - this.clamp(fadeRightStrength, 0.0F, 1.0F) * (1.0F - rightProgress));
         }

         int originalAlpha = color >>> 24 & 0xFF;
         return Math.round(originalAlpha * alphaFactor) << 24 | color & (16777215);
      } else {
         return color;
      }
   }

   @Override
   public TextureSetup textureSetup() {
      return this.page.getTextureSetup();
   }

   private float[] rotate(float x, float y, float originX, float originY, float sin, float cos) {
      float deltaX = x - originX;
      float deltaY = y - originY;
      return new float[]{originX + deltaX * cos - deltaY * sin, originY + deltaX * sin + deltaY * cos};
   }
}
