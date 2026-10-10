package com.mouseclient.render;

import net.minecraft.client.util.math.MatrixStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class BedrockTransforms {
   private static final Vector3f X_AXIS = new Vector3f(1.0F, 0.0F, 0.0F);
   private static final Vector3f Y_AXIS = new Vector3f(0.0F, 1.0F, 0.0F);
   private static final Vector3f Z_AXIS = new Vector3f(0.0F, 0.0F, 1.0F);

   public static void translateBoneOffset(BedrockBone bone, MatrixStack matrices) {
      matrices.translate(-bone.getOffsetX() / (16.0F), bone.getOffsetY() / (16.0F), bone.getOffsetZ() / (16.0F));
   }

   public static void translateInverseBonePivot(BedrockBone bone, MatrixStack matrices) {
      matrices.translate(-bone.getPivotX() / (16.0F), -bone.getPivotY() / (16.0F), -bone.getPivotZ() / (16.0F));
   }

   public static void rotateCube(BedrockCube cube, MatrixStack matrices) {
      if (cube.rotation.getZ() != 0.0F) {
         matrices.multiply(axisAngleQuaternion(Z_AXIS, cube.rotation.getZ()));
      }

      if (cube.rotation.getY() != 0.0F) {
         matrices.multiply(axisAngleQuaternion(Y_AXIS, cube.rotation.getY()));
      }

      if (cube.rotation.getX() != 0.0F) {
         matrices.multiply(axisAngleQuaternion(X_AXIS, cube.rotation.getX()));
      }
   }

   public static void scaleBone(BedrockBone bone, MatrixStack matrices) {
      matrices.scale(bone.getScaleX(), bone.getScaleY(), bone.getScaleZ());
   }

   public static void translateInverseCubePivot(BedrockCube cube, MatrixStack matrices) {
      matrices.translate(-cube.pivot.getX() / (16.0F), -cube.pivot.getY() / (16.0F), -cube.pivot.getZ() / (16.0F));
   }

   public static void rotateBone(BedrockBone bone, MatrixStack matrices) {
      if (bone.getRotationZ() != 0.0F) {
         matrices.multiply(axisAngleQuaternion(Z_AXIS, bone.getRotationZ()));
      }

      if (bone.getRotationY() != 0.0F) {
         matrices.multiply(axisAngleQuaternion(Y_AXIS, bone.getRotationY()));
      }

      if (bone.getRotationX() != 0.0F) {
         matrices.multiply(axisAngleQuaternion(X_AXIS, bone.getRotationX()));
      }
   }

   public static void translateCubePivot(BedrockCube cube, MatrixStack matrices) {
      matrices.translate(cube.pivot.getX() / (16.0F), cube.pivot.getY() / (16.0F), cube.pivot.getZ() / (16.0F));
   }

   public static void translateBonePivot(BedrockBone bone, MatrixStack matrices) {
      matrices.translate(bone.getPivotX() / (16.0F), bone.getPivotY() / (16.0F), bone.getPivotZ() / (16.0F));
   }

   public static Quaternionf axisAngleQuaternion(Vector3f axis, float radians) {
      float sinHalfAngle = (float)Math.sin(radians / 2.0F);
      float quaternionX = axis.x() * sinHalfAngle;
      float quaternionY = axis.y() * sinHalfAngle;
      float quaternionZ = axis.z() * sinHalfAngle;
      float cosHalfAngle = (float)Math.cos(radians / 2.0F);
      return new Quaternionf(quaternionX, quaternionY, quaternionZ, cosHalfAngle);
   }
}
