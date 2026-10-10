package com.mouseclient.gui;

import com.mouseclient.render.pipeline.ImageRenderer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2f;

public final class ImageBatchRenderState implements SimpleGuiElementRenderState {
   private final Matrix3x2f transform;
   private final ImageTexture texture;
   private final ScreenRect scissorArea;
   private final boolean nearestFiltering;
   private final boolean glow;
   private final List<ImageQuadSpec> quads = new ArrayList<>(16);
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

   @Override
   public TextureSetup textureSetup() {
      return this.texture.setup(this.nearestFiltering);
   }

   private void includeBounds(ImageQuadSpec spec) {
      float left = spec.x();
      float top = spec.y();
      float right = spec.x() + spec.width();
      float bottom = spec.y() + spec.height();
      this.include(rotatedX(spec, left, top), rotatedY(spec, left, top));
      this.include(rotatedX(spec, left, bottom), rotatedY(spec, left, bottom));
      this.include(rotatedX(spec, right, bottom), rotatedY(spec, right, bottom));
      this.include(rotatedX(spec, right, top), rotatedY(spec, right, top));
   }

   private void include(float x, float y) {
      this.minX = Math.min(this.minX, x);
      this.minY = Math.min(this.minY, y);
      this.maxX = Math.max(this.maxX, x);
      this.maxY = Math.max(this.maxY, y);
   }

   @Override
   public void setupVertices(VertexConsumer vertices) {
      for (ImageQuadSpec spec : this.quads) {
         int paramsIndex = ImageRenderer.getInstance().allocateParams(spec);
         if (paramsIndex >= 0) {
            float left = spec.x();
            float top = spec.y();
            float right = spec.x() + spec.width();
            float bottom = spec.y() + spec.height();
            this.vertex(vertices, spec, left, top, spec.uMin(), spec.vMin(), 0, 0, paramsIndex);
            this.vertex(vertices, spec, left, bottom, spec.uMin(), spec.vMax(), 0, 255, paramsIndex);
            this.vertex(vertices, spec, right, bottom, spec.uMax(), spec.vMax(), 255, 255, paramsIndex);
            this.vertex(vertices, spec, right, top, spec.uMax(), spec.vMin(), 255, 0, paramsIndex);
         }
      }
   }

   public ImageBatchRenderState(Matrix3x2f transform, ImageTexture value, ScreenRect scissorArea, boolean nearestFiltering, boolean glow) {
      this.minX = (3.4028235E38F);
      this.minY = (3.4028235E38F);
      this.maxX = (-3.4028235E38F);
      this.maxY = (-3.4028235E38F);
      this.transform = new Matrix3x2f(transform);
      this.texture = value;
      this.scissorArea = scissorArea;
      this.nearestFiltering = nearestFiltering;
      this.glow = glow;
   }

   private static float rotatedY(ImageQuadSpec spec, float x, float y) {
      float rotationDegrees = spec.rotationDegrees();
      if (rotationDegrees != 0.0F && Float.isFinite(rotationDegrees)) {
         double rotationRadians = Math.toRadians(rotationDegrees);
         float deltaX = x - spec.rotationOriginX();
         float deltaY = y - spec.rotationOriginY();
         return spec.rotationOriginY() + (float)(deltaX * Math.sin(rotationRadians) + deltaY * Math.cos(rotationRadians));
      } else {
         return y;
      }
   }

   public void add(ImageQuadSpec spec) {
      this.quads.add(spec);
      this.includeBounds(spec);
   }

   @Override
   public RenderPipeline pipeline() {
      return this.glow ? ImageRenderer.GLOW_PIPELINE : ImageRenderer.IMAGE_PIPELINE;
   }

   private static float rotatedX(ImageQuadSpec spec, float x, float y) {
      float rotationDegrees = spec.rotationDegrees();
      if (rotationDegrees != 0.0F && Float.isFinite(rotationDegrees)) {
         double rotationRadians = Math.toRadians(rotationDegrees);
         float deltaX = x - spec.rotationOriginX();
         float deltaY = y - spec.rotationOriginY();
         return spec.rotationOriginX() + (float)(deltaX * Math.cos(rotationRadians) - deltaY * Math.sin(rotationRadians));
      } else {
         return x;
      }
   }

   @Override
   public ScreenRect scissorArea() {
      return this.scissorArea;
   }

   private void vertex(VertexConsumer vertices, ImageQuadSpec spec, float x, float y, float u, float v, int encodedU, int encodedV, int paramsIndex) {
      float transformedX = rotatedX(spec, x, y);
      float transformedY = rotatedY(spec, x, y);
      vertices.vertex(this.transform, transformedX, transformedY).texture(u, v).color(encodedU, encodedV, 255, 255).lineWidth(paramsIndex + 1);
   }
}
