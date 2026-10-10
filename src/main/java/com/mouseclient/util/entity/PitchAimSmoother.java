package com.mouseclient.util.entity;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class PitchAimSmoother implements MinecraftAccess {
   private float pitchStep;

   public void reset() {
      this.pitchStep = 0.0F;
   }

   public float update(LivingEntity entity, float tickDelta, float pitch, boolean forceCorrection) {
      if (entity != null && minecraftClient.player != null) {
         Vec3d position = minecraftClient.player.getCameraPosVec(tickDelta);
         Box interpolatedBounds = entity.getBoundingBox().offset(entity.getLerpedPos(tickDelta).subtract(entity.getEntityPos()));
         double horizontalDistance = Math.max((0.6D), Math.hypot(interpolatedBounds.getCenter().x - position.x, interpolatedBounds.getCenter().z - position.z));
         float upperPitch = pitchAngle(interpolatedBounds.maxY - interpolatedBounds.getLengthY() * (0.3D) - position.y, horizontalDistance);
         float lowerPitch = pitchAngle(interpolatedBounds.minY + interpolatedBounds.getLengthY() * (0.2D) - position.y, horizontalDistance);
         float pitchCorrection = pitch < upperPitch ? upperPitch - pitch : (pitch > lowerPitch ? lowerPitch - pitch : 0.0F);
         float pitchJitter = MathHelper.sin(minecraftClient.player.age * (0.37F)) * (0.55F)
            + MathHelper.cos(minecraftClient.player.age * (0.11F)) * (0.35F);
         float maximumStep = forceCorrection ? (1.8F) : (1.1F);
         float maximumAcceleration = forceCorrection ? (0.15F) : (0.09F);
         this.pitchStep = this.pitchStep
            + MathHelper.clamp(
               MathHelper.clamp((pitchCorrection + pitchJitter) * (forceCorrection ? (0.16F) : (0.08F) * YawAimSmoother.cooldownFactor()), -maximumStep, maximumStep) - this.pitchStep, -maximumAcceleration, maximumAcceleration
            );
         return this.pitchStep;
      } else {
         return 0.0F;
      }
   }

   private static float pitchAngle(double verticalDistance, double horizontalDistance) {
      return (float)(-Math.toDegrees(Math.atan2(verticalDistance, horizontalDistance)));
   }
}
