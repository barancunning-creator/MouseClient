package com.mouseclient.render.pipeline;

import com.mouseclient.gui.OutlineColorRange;
import java.util.List;
import net.minecraft.client.gui.DrawContext;

public record Outline360Spec(
   float x,
   float y,
   float width,
   float height,
   float radiusTopLeft,
   float radiusTopRight,
   float radiusBottomRight,
   float radiusBottomLeft,
   float thickness,
   int defaultColor,
   float smoothness,
   float blendDegrees,
   float angleOffsetDegrees,
   List<OutlineColorRange> ranges
) {
   public static final float DEFAULT_SMOOTHNESS = 0.5F;
   public static final float DEFAULT_BLEND_DEGREES = 14.0F;
   public static final int DEFAULT_COLOR = -13619152;

   public boolean visible() {
      return this.width > 0.0F && this.height > 0.0F && this.thickness > 0.0F && this.defaultColor >>> 24 != 0;
   }

   public Outline360Spec withBlendDegrees(float blendDegrees) {
      return new Outline360Spec(
         this.x,
         this.y,
         this.width,
         this.height,
         this.radiusTopLeft,
         this.radiusTopRight,
         this.radiusBottomRight,
         this.radiusBottomLeft,
         this.thickness,
         this.defaultColor,
         this.smoothness,
         blendDegrees,
         this.angleOffsetDegrees,
         this.ranges
      );
   }

   public Outline360Spec withAngleOffset(float angleOffset) {
      return new Outline360Spec(
         this.x,
         this.y,
         this.width,
         this.height,
         this.radiusTopLeft,
         this.radiusTopRight,
         this.radiusBottomRight,
         this.radiusBottomLeft,
         this.thickness,
         this.defaultColor,
         this.smoothness,
         this.blendDegrees,
         angleOffset,
         this.ranges
      );
   }

   public void render(DrawContext context) {
      Outline360Renderer.getInstance().render(context, this);
   }

   public Outline360Spec(
      float x, float y, float width, float height, float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft, float thickness, int defaultColor, List<OutlineColorRange> ranges
   ) {
      this(x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft, thickness, defaultColor, 0.5F, 14.0F, 0.0F, ranges);
   }

   public Outline360Spec withSmoothness(float smoothness) {
      return new Outline360Spec(
         this.x,
         this.y,
         this.width,
         this.height,
         this.radiusTopLeft,
         this.radiusTopRight,
         this.radiusBottomRight,
         this.radiusBottomLeft,
         this.thickness,
         this.defaultColor,
         smoothness,
         this.blendDegrees,
         this.angleOffsetDegrees,
         this.ranges
      );
   }

   public Outline360Spec(
      float x,
      float y,
      float width,
      float height,
      float radiusTopLeft,
      float radiusTopRight,
      float radiusBottomRight,
      float radiusBottomLeft,
      float thickness,
      int defaultColor,
      float smoothness,
      float blendDegrees,
      float angleOffsetDegrees,
      List<OutlineColorRange> ranges
   ) {
      ranges = ranges == null ? List.of() : List.copyOf(ranges);
      this.x = x;
      this.y = y;
      this.width = width;
      this.height = height;
      this.radiusTopLeft = radiusTopLeft;
      this.radiusTopRight = radiusTopRight;
      this.radiusBottomRight = radiusBottomRight;
      this.radiusBottomLeft = radiusBottomLeft;
      this.thickness = thickness;
      this.defaultColor = defaultColor;
      this.smoothness = smoothness;
      this.blendDegrees = blendDegrees;
      this.angleOffsetDegrees = angleOffsetDegrees;
      this.ranges = ranges;
   }

   public Outline360Spec(float x, float y, float width, float height, float radius, float thickness, int defaultColor, List<OutlineColorRange> ranges) {
      this(x, y, width, height, radius, radius, radius, radius, thickness, defaultColor, 0.5F, 14.0F, 0.0F, ranges);
   }
}
