package com.mouseclient.util.entity;

import com.mouseclient.internal.ClientMath;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class SmoothedAimVector implements MinecraftAccess {
   private static Vec3d smoothedEyePosition = Vec3d.ZERO;
   private static Vec3d smoothedTargetPosition = Vec3d.ZERO;
   private static float smoothingX;
   private static float smoothingZ;
   private static long lastVerticalUpdate;

   public static RotationAngles anglesForDirection(Vec3d direction) {
      double horizontalLength = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
      float yaw = (float)Math.toDegrees(Math.atan2(direction.z, direction.x)) - (90.0F);
      float pitch = (float)(-Math.toDegrees(Math.atan2(direction.y, horizontalLength)));
      return new RotationAngles(MathHelper.wrapDegrees(yaw), MathHelper.clamp(pitch, (-90.0F), (90.0F)));
   }

   private SmoothedAimVector() {
   }

   private static Vec3d nearestTargetPoint(LivingEntity entity, Vec3d eyePosition) {
      Vec3d position = entity.getBoundingBox()
         .raycast(
            eyePosition,
            eyePosition.add(
               new RotationAngles(minecraftClient.player.getYaw(), minecraftClient.player.getPitch())
                  .direction()
                  .multiply(minecraftClient.player.distanceTo(entity) + entity.getWidth() + 1.0F)
            )
         )
         .orElse(
            new Vec3d(
               MathHelper.clamp(eyePosition.x, entity.getBoundingBox().minX, entity.getBoundingBox().maxX),
               MathHelper.clamp(eyePosition.y, entity.getBoundingBox().minY, entity.getBoundingBox().maxY),
               MathHelper.clamp(eyePosition.z, entity.getBoundingBox().minZ, entity.getBoundingBox().maxZ)
            )
         );
      float bestAngularError = angleDifference(eyePosition, position);

      for (float heightFraction : new float[]{(0.35F), (0.55F), (0.75F), (0.9F)}) {
         Vec3d candidatePosition = new Vec3d(
            MathHelper.clamp(eyePosition.x, entity.getBoundingBox().minX, entity.getBoundingBox().maxX),
            entity.getBoundingBox().minY + (entity.getBoundingBox().maxY - entity.getBoundingBox().minY) * heightFraction,
            MathHelper.clamp(eyePosition.z, entity.getBoundingBox().minZ, entity.getBoundingBox().maxZ)
         );
         float candidateAngle = angleDifference(eyePosition, candidatePosition);
         if (candidateAngle < bestAngularError) {
            bestAngularError = candidateAngle;
            position = candidatePosition;
         }
      }

      return position;
   }

   public static Vec3d update(LivingEntity entity, boolean fastRefresh, boolean useNearestPoint) {
      smoothingX = MathHelper.lerp(ClientMath.randomFloatMixture((0.65F), (0.85F)), smoothingX, ClientMath.randomFloatMixture((0.65F), (0.85F)));
      smoothingZ = MathHelper.lerp(ClientMath.randomFloatMixture((0.65F), (0.85F)), smoothingZ, ClientMath.randomFloatMixture((0.65F), (0.85F)));
      if (smoothedTargetPosition == Vec3d.ZERO) {
         smoothedTargetPosition = entity.getLerpedPos(1.0F);
      }

      if (smoothedEyePosition == Vec3d.ZERO) {
         smoothedEyePosition = minecraftClient.player.getCameraPosVec(1.0F);
      }

      if (!useNearestPoint
         && System.currentTimeMillis() - lastVerticalUpdate
            > (fastRefresh ? ClientMath.randomLong((25L), (67L)) : ClientMath.randomLong((87L), (185L)))) {
         lastVerticalUpdate = System.currentTimeMillis();
         smoothedTargetPosition = new Vec3d(
            smoothedTargetPosition.x,
            MathHelper.lerp(
               ClientMath.randomFloatMixture((0.29F), Math.abs(entity.getY() - minecraftClient.player.getY()) > 1.0 ? (0.45F) : (0.69F)),
               smoothedTargetPosition.y,
               entity.getY() + entity.getHeight() / 2.0F
            ),
            smoothedTargetPosition.z
         );
      }

      if (System.currentTimeMillis() - lastVerticalUpdate
         > (fastRefresh ? ClientMath.randomLong((25L), (67L)) : ClientMath.randomLong((55L), (115L)))) {
         lastVerticalUpdate = System.currentTimeMillis();
         smoothedEyePosition = new Vec3d(
            smoothedEyePosition.x, MathHelper.lerp(ClientMath.randomFloatMixture((0.15F), (0.35F)), smoothedEyePosition.y, minecraftClient.player.getCameraPosVec(1.0F).y), smoothedEyePosition.z
         );
      }

      smoothedEyePosition = new Vec3d(
         MathHelper.lerp(smoothingX, smoothedEyePosition.x, minecraftClient.player.getCameraPosVec(1.0F).x),
         smoothedEyePosition.y,
         MathHelper.lerp(smoothingZ, smoothedEyePosition.z, minecraftClient.player.getCameraPosVec(1.0F).z)
      );
      if (useNearestPoint) {
         Vec3d position = nearestTargetPoint(entity, smoothedEyePosition);
         smoothedTargetPosition = new Vec3d(
            MathHelper.lerp(smoothingX, smoothedTargetPosition.x, position.x),
            MathHelper.lerp(fastRefresh ? (0.44999998807907104D) : (0.6499999761581421D), smoothedTargetPosition.y, position.y),
            MathHelper.lerp(smoothingZ, smoothedTargetPosition.z, position.z)
         );
         return smoothedTargetPosition.subtract(smoothedEyePosition);
      } else {
         smoothedTargetPosition = new Vec3d(MathHelper.lerp(smoothingX, smoothedTargetPosition.x, entity.getX()), smoothedTargetPosition.y, MathHelper.lerp(smoothingZ, smoothedTargetPosition.z, entity.getZ()));
         return smoothedTargetPosition.subtract(smoothedEyePosition);
      }
   }

   public static void reset() {
      smoothedEyePosition = Vec3d.ZERO;
      smoothedTargetPosition = Vec3d.ZERO;
      smoothingX = ClientMath.randomFloatMixture((0.38F), (0.55F));
      smoothingZ = ClientMath.randomFloatMixture((0.38F), (0.55F));
      lastVerticalUpdate = 0L;
   }

   private static float angleDifference(Vec3d eyePosition, Vec3d targetPosition) {
      return (float)Math.hypot(
         MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(targetPosition.subtract(eyePosition).z, targetPosition.subtract(eyePosition).x)) - (90.0D) - minecraftClient.player.getYaw()),
         -Math.toDegrees(Math.atan2(targetPosition.subtract(eyePosition).y, Math.hypot(targetPosition.subtract(eyePosition).x, targetPosition.subtract(eyePosition).z))) - minecraftClient.player.getPitch()
      );
   }

   public static Vec3d directionTo(LivingEntity entity, boolean fastRefresh) {
      return update(entity, fastRefresh, false);
   }
}
