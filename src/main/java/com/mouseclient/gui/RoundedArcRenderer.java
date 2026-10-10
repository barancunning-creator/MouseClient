package com.mouseclient.gui;

import com.mouseclient.mixin.DrawContextAccessor;
import com.mouseclient.render.pipeline.GuiShapePipelines;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;

public final class RoundedArcRenderer {

   public static void drawAnnularArc(
      DrawContext context,
      TextureSetup textureSetup,
      Matrix3x2f transform,
      float centerX,
      float centerY,
      float innerRadius,
      float outerRadius,
      float startDegrees,
      float sweepDegrees,
      float gap,
      float cornerRadius,
      int color,
      float alpha,
      float tintMix
   ) {
      if (context != null && !(alpha <= (5.0E-4F))) {
         java.util.List<org.joml.Vector2f> points = buildAnnularArc(innerRadius, outerRadius, sweepDegrees, gap, cornerRadius);
         if (points != null) {
            float rotationRadians = (float)Math.toRadians(startDegrees + sweepDegrees / 2.0F);
            float cos = (float)Math.cos(rotationRadians);
            float sin = (float)Math.sin(rotationRadians);
            float anchorX = 0.0F;
            float anchorY = 0.0F;

            for (Vector2f point : points) {
               point.set(centerX + point.x * cos - point.y * sin, centerY + point.x * sin + point.y * cos);
               anchorX += point.x / points.size();
               anchorY += point.y / points.size();
            }

            ScreenRect bounds = ScissorStack.current();
            ScreenRect clippedBounds = GuiShapeRenderer.transformedClippedBounds(
               transform, bounds, centerX - outerRadius - 2.0F, centerY - outerRadius - 2.0F, outerRadius * 2.0F + (4.0F), outerRadius * 2.0F + (4.0F)
            );
            if (clippedBounds != null) {
               boolean blur = textureSetup != null;
               ((DrawContextAccessor)(Object)context)
                  .getGuiRenderState()
                  .addSimpleElement(
                     new RoundedArcRenderer.ArcPolygonState(
                        blur ? textureSetup : TextureSetup.empty(),
                        new Matrix3x2f(transform),
                        points,
                        anchorX,
                        anchorY,
                        GuiShapeRenderer.multiplyAlpha(color, alpha),
                        tintMix,
                        blur,
                        bounds,
                        clippedBounds
                     )
                  );
            }
         }
      }
   }

   private static List<Vector2f> buildSector(float centerX, float centerY, float radius, float startDegrees, float sweepDegrees, float gap, float cornerRadius) {
      if (!(radius <= 0.0F) && !(Math.abs(sweepDegrees) < (0.01F))) {
         if (sweepDegrees < 0.0F) {
            startDegrees += sweepDegrees;
            sweepDegrees = -sweepDegrees;
         }

         java.util.List<org.joml.Vector2f> points = buildCenteredSector(radius, sweepDegrees, gap, cornerRadius);
         if (points == null) {
            return null;
         }

         float rotationRadians = (float)Math.toRadians(startDegrees + sweepDegrees / 2.0F);
         float cos = (float)Math.cos(rotationRadians);
         float sin = (float)Math.sin(rotationRadians);

         for (Vector2f point : points) {
            point.set(centerX + point.x * cos - point.y * sin, centerY + point.x * sin + point.y * cos);
         }

         return points;
      } else {
         return null;
      }
   }

   private static List<Vector2f> buildAnnularArc(float innerRadius, float outerRadius, float sweepDegrees, float gap, float cornerRadius) {
      float halfSweepRadians = (float)Math.toRadians(Math.abs(sweepDegrees)) / 2.0F;
      float effectiveCornerRadius = Math.max((0.5F), Math.min(cornerRadius, (outerRadius - innerRadius) * (0.45F)));
      float edgeInset = Math.max(0.0F, gap) / 2.0F + effectiveCornerRadius;
      float outerCenterRadius = outerRadius - effectiveCornerRadius;
      float innerCenterRadius = innerRadius + effectiveCornerRadius;
      if (!(innerCenterRadius <= edgeInset) && !(outerCenterRadius <= innerCenterRadius)) {
         float outerHalfAngle = halfSweepRadians - (float)Math.asin(edgeInset / outerCenterRadius);
         float innerHalfAngle = halfSweepRadians - (float)Math.asin(edgeInset / innerCenterRadius);
         if (!(outerHalfAngle <= 0.0F) && !(innerHalfAngle <= 0.0F)) {
            ArrayList points = new ArrayList();
            int outerSegmentCount = Math.max(2, Math.round((float)Math.toDegrees(outerHalfAngle * 2.0F) / (3.0F)));

            for (int outerSegment = 0; outerSegment <= outerSegmentCount; outerSegment++) {
               float outerAngle = -outerHalfAngle + outerHalfAngle * 2.0F * outerSegment / outerSegmentCount;
               points.add(new Vector2f(outerCenterRadius * (float)Math.cos(outerAngle), outerCenterRadius * (float)Math.sin(outerAngle)));
            }

            int innerSegmentCount = Math.max(2, Math.round((float)Math.toDegrees(innerHalfAngle * 2.0F) / (3.0F)));

            for (int innerSegment = 0; innerSegment <= innerSegmentCount; innerSegment++) {
               float innerAngle = innerHalfAngle - innerHalfAngle * 2.0F * innerSegment / innerSegmentCount;
               points.add(new Vector2f(innerCenterRadius * (float)Math.cos(innerAngle), innerCenterRadius * (float)Math.sin(innerAngle)));
            }

            return roundPolygon(points, effectiveCornerRadius);
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private static float outwardNormalAngle(Vector2f start, Vector2f end, float centerX, float centerY) {
      float edgeX = end.x - start.x;
      float edgeY = end.y - start.y;
      float normalX = edgeY;
      float normalY = -edgeX;
      float midpointDeltaX = (start.x + end.x) / 2.0F - centerX;
      float midpointDeltaY = (start.y + end.y) / 2.0F - centerY;
      if (normalX * midpointDeltaX + normalY * midpointDeltaY < 0.0F) {
         normalX = -normalX;
         normalY = -normalY;
      }

      return (float)Math.atan2(normalY, normalX);
   }

   public static void drawSector(
      DrawContext context,
      TextureSetup textureSetup,
      Matrix3x2f transform,
      float centerX,
      float centerY,
      float radius,
      float startDegrees,
      float sweepDegrees,
      float gap,
      float cornerRadius,
      int color,
      float alpha,
      float tintMix
   ) {
      if (context != null && !(alpha <= (5.0E-4F))) {
         java.util.List<org.joml.Vector2f> points = buildSector(centerX, centerY, radius, startDegrees, sweepDegrees, gap, cornerRadius);
         if (points != null) {
            ScreenRect bounds = ScissorStack.current();
            ScreenRect clippedBounds = GuiShapeRenderer.transformedClippedBounds(
               transform, bounds, centerX - radius - 2.0F, centerY - radius - 2.0F, radius * 2.0F + (4.0F), radius * 2.0F + (4.0F)
            );
            if (clippedBounds != null) {
               float anchorX = 0.0F;
               float anchorY = 0.0F;

               for (Vector2f point : points) {
                  anchorX += point.x / points.size();
                  anchorY += point.y / points.size();
               }

               boolean blur = textureSetup != null;
               ((DrawContextAccessor)(Object)context)
                  .getGuiRenderState()
                  .addSimpleElement(
                     new RoundedArcRenderer.ArcPolygonState(
                        blur ? textureSetup : TextureSetup.empty(),
                        new Matrix3x2f(transform),
                        points,
                        anchorX,
                        anchorY,
                        GuiShapeRenderer.multiplyAlpha(color, alpha),
                        tintMix,
                        blur,
                        bounds,
                        clippedBounds
                     )
                  );
            }
         }
      }
   }

   private static List<Vector2f> buildCenteredSector(float radius, float sweepDegrees, float gap, float cornerRadius) {
      float halfSweepRadians = (float)Math.toRadians(sweepDegrees) / 2.0F;
      float halfSweepSin = (float)Math.sin(halfSweepRadians);
      if (halfSweepSin < (1.0E-4F)) {
         return null;
      }

      float halfGap = Math.max(0.0F, gap) / 2.0F;
      float effectiveCornerRadius = Math.max((0.5F), Math.min(cornerRadius, (radius - halfGap) * (0.45F)));
      float edgeInset = halfGap + effectiveCornerRadius;
      float arcCenterRadius = radius - effectiveCornerRadius;
      float apexX = edgeInset / halfSweepSin;
      if (!(arcCenterRadius <= 0.0F) && !(apexX >= arcCenterRadius)) {
         float halfSweepCos = (float)Math.cos(halfSweepRadians);
         float edgeDirectionY = (float)Math.sin(halfSweepRadians);
         float apexProjection = apexX * halfSweepCos;
         float edgeDistance = -apexProjection + (float)Math.sqrt(Math.max(0.0F, apexProjection * apexProjection - apexX * apexX + arcCenterRadius * arcCenterRadius));
         float arcHalfAngle = (float)Math.atan2(edgeDirectionY * edgeDistance, apexX + halfSweepCos * edgeDistance);
         if (arcHalfAngle <= 0.0F) {
            return null;
         }

         ArrayList points = new ArrayList();
         points.add(new Vector2f(apexX, 0.0F));
         int segmentCount = Math.max(2, Math.round((float)Math.toDegrees(arcHalfAngle * 2.0F) / (4.0F)));

         for (int segment = 0; segment <= segmentCount; segment++) {
            float angle = -arcHalfAngle + arcHalfAngle * 2.0F * segment / segmentCount;
            points.add(new Vector2f(arcCenterRadius * (float)Math.cos(angle), arcCenterRadius * (float)Math.sin(angle)));
         }

         return roundPolygon(points, effectiveCornerRadius);
      } else {
         return null;
      }
   }

   private static List<Vector2f> roundPolygon(List<Vector2f> points, float radius) {
      int pointCount = points.size();
      float centerX = 0.0F;
      float centerY = 0.0F;

      for (Vector2f point : points) {
         centerX += point.x / pointCount;
         centerY += point.y / pointCount;
      }

      ArrayList roundedPoints = new ArrayList();

      for (int pointIndex = 0; pointIndex < pointCount; pointIndex++) {
         Vector2f previous = (Vector2f)points.get((pointIndex - 1 + pointCount) % pointCount);
         Vector2f current = (Vector2f)points.get(pointIndex);
         Vector2f next = (Vector2f)points.get((pointIndex + 1) % pointCount);
         float incomingNormalAngle = outwardNormalAngle(previous, current, centerX, centerY);
         float outgoingNormalAngle = outwardNormalAngle(current, next, centerX, centerY);
         float cornerSweep = outgoingNormalAngle - incomingNormalAngle;

         while (cornerSweep <= (-3.1415927F)) {
            cornerSweep += (6.2831855F);
         }

         while (cornerSweep > (3.1415927F)) {
            cornerSweep -= (6.2831855F);
         }

         int segmentCount = Math.max(1, Math.round(Math.abs(cornerSweep) / (0.2F)));

         for (int segment = 0; segment <= segmentCount; segment++) {
            float angle = incomingNormalAngle + cornerSweep * segment / segmentCount;
            roundedPoints.add(new Vector2f(current.x + (float)Math.cos(angle) * radius, current.y + (float)Math.sin(angle) * radius));
         }
      }

      return roundedPoints;
   }

   private RoundedArcRenderer() {
   }

   public record ArcPolygonState(
      TextureSetup textureSetup,
      Matrix3x2f pose,
      List<Vector2f> shape,
      float anchorX,
      float anchorY,
      int color,
      float tintMix,
      boolean blur,
      ScreenRect scissorArea,
      ScreenRect bounds
   ) implements SimpleGuiElementRenderState {
      @Override
      public RenderPipeline pipeline() {
         return this.blur ? GuiShapePipelines.ARC_BLUR : GuiShapePipelines.ARC;
      }

      @Override
      public void setupVertices(VertexConsumer vertices) {
         int pointCount = this.shape.size();

         for (int pointIndex = 0; pointIndex < pointCount; pointIndex++) {
            Vector2f current = this.shape.get(pointIndex);
            Vector2f next = this.shape.get((pointIndex + 1) % pointCount);
            this.emit(vertices, this.anchorX, this.anchorY, 0.0F);
            this.emit(vertices, this.anchorX, this.anchorY, 0.0F);
            this.emit(vertices, next.x, next.y, 1.0F);
            this.emit(vertices, current.x, current.y, 1.0F);
         }
      }

      private void emit(VertexConsumer vertices, float x, float y, float edgeWeight) {
         vertices.vertex(this.pose, x, y).color(this.color).texture(edgeWeight, 0.0F).overlay(0, 0).light(0, 0).normal(0.0F, 0.0F, 0.0F).lineWidth(this.tintMix);
      }
   }
}
