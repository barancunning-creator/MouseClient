package com.mouseclient.gui;

import com.mouseclient.render.pipeline.RectHalfIconRenderer;
import com.mouseclient.render.pipeline.IconPatternSpec;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2f;

public final class RectHalfIconBatchRenderState implements SimpleGuiElementRenderState {
   private final Matrix3x2f transform;
   private final TextureSetup textureSetup;
   private final List<RectHalfIconBatchRenderState.IconGroup> groups = new ArrayList<>(8);
   private float minX;
   private float minY;
   private float maxX;
   private float maxY;

   @Override
   public TextureSetup textureSetup() {
      return this.textureSetup;
   }

   public boolean empty() {
      return this.groups.isEmpty();
   }

   @Override
   public ScreenRect scissorArea() {
      return null;
   }

   @Override
   public RenderPipeline pipeline() {
      return RectHalfIconRenderer.renderPipeline;
   }

   public void add(IconPatternSpec spec, List<RectHalfIconBatchRenderState.IconQuad> quads) {
      if (!quads.isEmpty()) {
         this.groups.add(new RectHalfIconBatchRenderState.IconGroup(spec, quads));
         this.minX = Math.min(this.minX, spec.x());
         this.minY = Math.min(this.minY, spec.y());
         this.maxX = Math.max(this.maxX, spec.x() + spec.width());
         this.maxY = Math.max(this.maxY, spec.y() + spec.height());
      }
   }

   @Override
   public void setupVertices(VertexConsumer vertices) {
      for (RectHalfIconBatchRenderState.IconGroup iconGroup : this.groups) {
         int paramsIndex = RectHalfIconRenderer.getInstance().allocateParams(iconGroup.rectangle());
         if (paramsIndex >= 0) {
            for (RectHalfIconBatchRenderState.IconQuad iconQuad : iconGroup.quads()) {
               this.vertex(vertices, iconQuad.x1(), iconQuad.y1(), iconQuad.u1(), iconQuad.v1(), iconQuad.color(), paramsIndex);
               this.vertex(vertices, iconQuad.x2(), iconQuad.y2(), iconQuad.u2(), iconQuad.v2(), iconQuad.color(), paramsIndex);
               this.vertex(vertices, iconQuad.x3(), iconQuad.y3(), iconQuad.u3(), iconQuad.v3(), iconQuad.color(), paramsIndex);
               this.vertex(vertices, iconQuad.x4(), iconQuad.y4(), iconQuad.u4(), iconQuad.v4(), iconQuad.color(), paramsIndex);
            }
         }
      }
   }

   @Override
   public ScreenRect bounds() {
      return this.groups.isEmpty()
         ? new ScreenRect(0, 0, 1, 1)
         : new ScreenRect(
               (int)Math.floor(this.minX),
               (int)Math.floor(this.minY),
               Math.max(1, (int)Math.ceil(this.maxX - this.minX)),
               Math.max(1, (int)Math.ceil(this.maxY - this.minY))
            )
            .transformEachVertex(this.transform);
   }

   private void vertex(VertexConsumer vertices, float x, float y, float u, float v, int color, int paramsIndex) {
      vertices.vertex(this.transform, x, y).texture(u, v).color(color).lineWidth(paramsIndex + 1);
   }

   public RectHalfIconBatchRenderState(Matrix3x2f transform, TextureSetup textureSetup) {
      this.minX = (3.4028235E38F);
      this.minY = (3.4028235E38F);
      this.maxX = (-3.4028235E38F);
      this.maxY = (-3.4028235E38F);
      this.transform = new Matrix3x2f(transform);
      this.textureSetup = textureSetup;
   }

   public record IconQuad(
      float x1,
      float y1,
      float u1,
      float v1,
      float x2,
      float y2,
      float u2,
      float v2,
      float x3,
      float y3,
      float u3,
      float v3,
      float x4,
      float y4,
      float u4,
      float v4,
      int color
   ) {
      public float u3() {
         return this.u3;
      }

      public float y4() {
         return this.y4;
      }

      public float v4() {
         return this.v4;
      }

      public float y2() {
         return this.y2;
      }

      public float u1() {
         return this.u1;
      }

      public float x1() {
         return this.x1;
      }

      public float y1() {
         return this.y1;
      }

      public float x4() {
         return this.x4;
      }

      public float u2() {
         return this.u2;
      }

      public float v1() {
         return this.v1;
      }

      public float v2() {
         return this.v2;
      }

      public float v3() {
         return this.v3;
      }

      public float x2() {
         return this.x2;
      }

      public float y3() {
         return this.y3;
      }

      public float u4() {
         return this.u4;
      }

      public float x3() {
         return this.x3;
      }
   }

   private record IconGroup(IconPatternSpec rectangle, List<RectHalfIconBatchRenderState.IconQuad> quads) {
   }
}
