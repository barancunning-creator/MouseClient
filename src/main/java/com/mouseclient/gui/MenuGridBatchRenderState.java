package com.mouseclient.gui;

import com.mouseclient.render.pipeline.MenuGridRenderer;
import com.mouseclient.render.pipeline.MenuGridSpec;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2f;

public final class MenuGridBatchRenderState implements SimpleGuiElementRenderState {
   private final Matrix3x2f transform;
   private final ScreenRect scissorArea;
   private final List<MenuGridSpec> quads = new ArrayList<>(2);
   private float minX;
   private float minY;
   private float maxX;
   private float maxY;

   @Override
   public void setupVertices(VertexConsumer vertices) {
      for (MenuGridSpec spec : this.quads) {
         int paramsIndex = MenuGridRenderer.getInstance().allocateParams(spec);
         if (paramsIndex >= 0) {
            this.vertex(vertices, spec.x(), spec.y(), 0.0F, 0.0F, spec.color(), paramsIndex);
            this.vertex(vertices, spec.x(), spec.y() + spec.height(), 0.0F, 1.0F, spec.color(), paramsIndex);
            this.vertex(vertices, spec.x() + spec.width(), spec.y() + spec.height(), 1.0F, 1.0F, spec.color(), paramsIndex);
            this.vertex(vertices, spec.x() + spec.width(), spec.y(), 1.0F, 0.0F, spec.color(), paramsIndex);
         }
      }
   }

   public MenuGridBatchRenderState(Matrix3x2f transform, ScreenRect scissorArea) {
      this.minX = (3.4028235E38F);
      this.minY = (3.4028235E38F);
      this.maxX = (-3.4028235E38F);
      this.maxY = (-3.4028235E38F);
      this.transform = new Matrix3x2f(transform);
      this.scissorArea = scissorArea;
   }

   @Override
   public TextureSetup textureSetup() {
      return TextureSetup.empty();
   }

   private void vertex(VertexConsumer vertices, float x, float y, float u, float v, int color, int paramsIndex) {
      vertices.vertex(this.transform, x, y).texture(u, v).color(color).lineWidth(paramsIndex + 1);
   }

   @Override
   public ScreenRect bounds() {
      if (this.scissorArea != null) {
         return this.scissorArea;
      } else {
         return this.quads.isEmpty()
            ? new ScreenRect(0, 0, 1, 1)
            : new ScreenRect(
                  (int)Math.floor(this.minX),
                  (int)Math.floor(this.minY),
                  Math.max(1, (int)Math.ceil(this.maxX - this.minX)),
                  Math.max(1, (int)Math.ceil(this.maxY - this.minY))
               )
               .transformEachVertex(this.transform);
      }
   }

   @Override
   public ScreenRect scissorArea() {
      return this.scissorArea;
   }

   @Override
   public RenderPipeline pipeline() {
      return MenuGridRenderer.renderPipeline;
   }

   public void add(MenuGridSpec spec) {
      this.quads.add(spec);
      this.minX = Math.min(this.minX, spec.x());
      this.minY = Math.min(this.minY, spec.y());
      this.maxX = Math.max(this.maxX, spec.x() + spec.width());
      this.maxY = Math.max(this.maxY, spec.y() + spec.height());
   }
}
