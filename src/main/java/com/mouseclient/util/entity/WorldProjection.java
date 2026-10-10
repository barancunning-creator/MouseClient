package com.mouseclient.util.entity;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public class WorldProjection implements MinecraftAccess {
   private static final Matrix4f view = new Matrix4f();
   private static final Matrix4f projection = new Matrix4f();
   private static final Matrix4f viewProjection = new Matrix4f();
   private static final FrustumIntersection frustum = new FrustumIntersection();

   public static Vector4f projectBounds(Box bounds, float screenWidth, float screenHeight) {
      if (minecraftClient.gameRenderer == null) {
         return null;
      }

      float minimumX = (3.4028235E38F);
      float minimumY = (3.4028235E38F);
      float maximumX = (-3.4028235E38F);
      float maximumY = (-3.4028235E38F);
      boolean anyVisibleCorner = false;
      Vec3d position = minecraftClient.gameRenderer.getCamera().getCameraPos();
      Vector4f clipPosition = new Vector4f();

      for (int cornerIndex = 0; cornerIndex < 8; cornerIndex++) {
         clipPosition.set(
            (float)((cornerIndex & 1) == 0 ? bounds.minX : bounds.maxX) - (float)position.x,
            (float)((cornerIndex & 2) == 0 ? bounds.minY : bounds.maxY) - (float)position.y,
            (float)((cornerIndex & 4) == 0 ? bounds.minZ : bounds.maxZ) - (float)position.z,
            1.0F
         );
         viewProjection.transform(clipPosition);
         if (!(clipPosition.w <= (0.05F))) {
            anyVisibleCorner = true;
            minimumX = Math.min(minimumX, (clipPosition.x / clipPosition.w * (0.5F) + (0.5F)) * screenWidth);
            maximumX = Math.max(maximumX, (clipPosition.x / clipPosition.w * (0.5F) + (0.5F)) * screenWidth);
            minimumY = Math.min(minimumY, ((0.5F) - clipPosition.y / clipPosition.w * (0.5F)) * screenHeight);
            maximumY = Math.max(maximumY, ((0.5F) - clipPosition.y / clipPosition.w * (0.5F)) * screenHeight);
         }
      }

      return anyVisibleCorner ? new Vector4f(minimumX, minimumY, maximumX - minimumX, maximumY - minimumY) : null;
   }

   public static Matrix4f projectionMatrix() {
      return projection;
   }

   public static boolean isVisible(Box bounds) {
      if (minecraftClient.gameRenderer == null) {
         return true;
      }

      Vec3d position = minecraftClient.gameRenderer.getCamera().getCameraPos();
      return frustum.testAab(
         (float)(bounds.minX - position.x),
         (float)(bounds.minY - position.y),
         (float)(bounds.minZ - position.z),
         (float)(bounds.maxX - position.x),
         (float)(bounds.maxY - position.y),
         (float)(bounds.maxZ - position.z)
      );
   }

   public static Vec3d project(Vec3d position, float screenWidth, float screenHeight) {
      if (minecraftClient.gameRenderer == null) {
         return null;
      }

      Vec3d cameraPosition = minecraftClient.gameRenderer.getCamera().getCameraPos();
      Vector4f clipPosition = viewProjection.transform(new Vector4f((float)(position.x - cameraPosition.x), (float)(position.y - cameraPosition.y), (float)(position.z - cameraPosition.z), 1.0F));
      return clipPosition.w <= (0.05F)
         ? null
         : new Vec3d((clipPosition.x / clipPosition.w * (0.5F) + (0.5F)) * screenWidth, ((0.5F) - clipPosition.y / clipPosition.w * (0.5F)) * screenHeight, clipPosition.w);
   }

   public static void captureMatrices(Matrix4f viewMatrix, Matrix4f projectionMatrix) {
      view.set(viewMatrix);
      projection.set(projectionMatrix);
      viewProjection.set(projectionMatrix).mul(viewMatrix);
      frustum.set(viewProjection);
   }

   public static boolean isSphereVisible(double x, double y, double z, float radius) {
      if (minecraftClient.gameRenderer == null) {
         return true;
      }

      Vec3d position = minecraftClient.gameRenderer.getCamera().getCameraPos();
      return frustum.testSphere((float)(x - position.x), (float)(y - position.y), (float)(z - position.z), radius);
   }

   public static Vec3d projectNearViewport(Vec3d position, float screenWidth, float screenHeight) {
      Vec3d projectedPosition = project(position, screenWidth, screenHeight);
      return projectedPosition != null
            && !(projectedPosition.x < -screenWidth * (0.25F))
            && !(projectedPosition.x > screenWidth * (1.25F))
            && !(projectedPosition.y < -screenHeight * (0.25F))
            && !(projectedPosition.y > screenHeight * (1.25F))
         ? projectedPosition
         : null;
   }

   public static Matrix4f viewMatrix() {
      return view;
   }
}
