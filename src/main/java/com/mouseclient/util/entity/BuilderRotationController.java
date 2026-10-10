package com.mouseclient.util.entity;

import com.mouseclient.network.RotationController;
import com.mouseclient.network.RotationLimits;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class BuilderRotationController implements MinecraftAccess {
   private Vec3d aimPoint;
   private float yawSpeed;
   private float pitchSpeed;
   private float overshootYaw;
   private float overshootPitch;
   private int returnTicks;

   public void reset() {
      this.aimPoint = null;
      this.yawSpeed = this.pitchSpeed = 0.0F;
      this.overshootYaw = this.overshootPitch = 0.0F;
      this.returnTicks = 0;
      RotationController.getInstance().reset();
   }

   public void update(LivingEntity entity, boolean attackReady, float attackRange) {
      RotationBuilder.LearnedRotation profile = RotationBuilder.getInstance().resolve(AttackAuraMode.selected());
      if (entity == null || minecraftClient.player == null || profile == null) {
         return;
      }

      Vec3d eye = minecraftClient.player.getEyePos();
      Box box = entity.getBoundingBox();
      Vec3d motion = new Vec3d(entity.getX() - entity.lastX, 0.0, entity.getZ() - entity.lastZ);
      Vec3d right = this.horizontalRight(eye, box.getCenter());
      float sideSpeed = (float)motion.dotProduct(right);
      float side = MathHelper.clamp(profile.aimSide + profile.sideFollow * sideSpeed, -0.82F, 0.82F);
      float height = attackReady ? Math.min(0.86F, profile.aimHeight + 0.06F) : profile.aimHeight;
      Vec3d desired = new Vec3d(
         box.getCenter().x + right.x * side * box.getLengthX() * 0.42D,
         box.minY + box.getLengthY() * MathHelper.clamp(height, 0.28F, 0.88F),
         box.getCenter().z + right.z * side * box.getLengthX() * 0.42D
      );
      this.aimPoint = this.aimPoint == null ? desired : this.aimPoint.lerp(desired, 0.34D);
      float[] angles = CombatTargeting.anglesBetween(this.aimPoint, eye);
      float idealYaw = angles[0];
      float idealPitch = MathHelper.clamp(angles[1], -90.0F, 90.0F);
      float yawError = MathHelper.wrapDegrees(idealYaw - minecraftClient.player.getYaw());
      float pitchError = idealPitch - minecraftClient.player.getPitch();
      double rayRange = Math.max(attackRange + 0.8F, eye.distanceTo(box.getCenter()) + 1.2D);
      boolean onBox = CombatTargeting.rayHitsEntityBounds(entity, rayRange, minecraftClient.player.getYaw(), minecraftClient.player.getPitch());
      float[] matched = this.nearest(profile, yawError, pitchError, (float)CombatTargeting.distanceToBounds(entity), sideSpeed, onBox, minecraftClient.player.getAttackCooldownProgress(0.0F));
      int bin = RotationBuilder.bin(yawError);
      float fallbackYaw = Math.copySign(Math.max(MouseSensitivity.rotationStep(), profile.yawMean[bin]), yawError == 0.0F ? this.yawSpeed : yawError);
      float fallbackPitch = Math.copySign(Math.max(MouseSensitivity.rotationStep() * profile.pitchLag, profile.pitchMean[bin]), pitchError == 0.0F ? this.pitchSpeed : pitchError);
      float learnedYaw = matched == null ? fallbackYaw : matched[0];
      float learnedPitch = matched == null ? fallbackPitch : matched[1];
      if (Math.abs(yawError) > 12.0F && learnedYaw * yawError < 0.0F) {
         learnedYaw = fallbackYaw;
      }

      if (Math.abs(learnedYaw) < Math.abs(fallbackYaw) * 0.4F && Math.abs(yawError) > 6.0F) {
         learnedYaw = MathHelper.lerp(0.6F, learnedYaw, fallbackYaw);
         learnedPitch = MathHelper.lerp(0.6F, learnedPitch, fallbackPitch);
      }

      if (!onBox && this.returnTicks == 0 && Math.abs(this.overshootYaw) < 1.0F && Math.abs(yawError) > 14.0F && Math.abs(this.yawSpeed) > Math.max(4.0F, profile.yawMean[bin]) * 0.55F && profile.overshootRate > 0.08F) {
         this.overshootYaw = Math.copySign(profile.overshoot, yawError == 0.0F ? this.yawSpeed : yawError);
         this.overshootPitch = Math.copySign(profile.overshoot * profile.pitchLag * 0.45F, pitchError == 0.0F ? this.pitchSpeed : pitchError);
      }

      if (onBox && this.returnTicks == 0 && Math.abs(this.overshootYaw) > 1.5F) {
         this.returnTicks = 10;
      }

      if (this.returnTicks > 0) {
         this.returnTicks--;
         this.overshootYaw *= this.returnTicks > 5 ? 0.9F : 0.62F;
         this.overshootPitch *= 0.7F;
         learnedYaw *= 0.55F;
         learnedPitch *= 0.5F;
      } else if (onBox && Math.abs(yawError) < 3.5F && profile.holdRate > 0.45F && Math.abs(learnedYaw) < 1.4F) {
         this.yawSpeed *= 0.45F;
         this.pitchSpeed *= 0.45F;
         this.request(minecraftClient.player.getYaw(), minecraftClient.player.getPitch(), MouseSensitivity.rotationStep(), MouseSensitivity.rotationStep());
         return;
      }

      float aimYaw = idealYaw + this.overshootYaw;
      float aimPitch = MathHelper.clamp(idealPitch + this.overshootPitch, -90.0F, 90.0F);
      float aimYawError = MathHelper.wrapDegrees(aimYaw - minecraftClient.player.getYaw());
      float aimPitchError = aimPitch - minecraftClient.player.getPitch();
      boolean settling = onBox && this.returnTicks == 0;
      float yawPace = this.pace(aimYawError, learnedYaw, settling);
      float pitchPace = this.pace(aimPitchError, learnedPitch, settling);
      float yawBlend = Math.abs(aimYawError) > 7.0F ? Math.max(profile.windup, 0.5F) : Math.max(profile.windup, 0.3F);
      float pitchBlend = Math.abs(aimPitchError) > 4.0F ? Math.max(profile.windup, 0.38F) : Math.max(profile.windup, 0.24F);
      this.yawSpeed += (Math.copySign(yawPace, aimYawError == 0.0F ? this.yawSpeed : aimYawError) - this.yawSpeed) * yawBlend;
      this.pitchSpeed += (Math.copySign(pitchPace, aimPitchError == 0.0F ? this.pitchSpeed : aimPitchError) - this.pitchSpeed) * pitchBlend;
      float yawFloor = settling ? MouseSensitivity.rotationStep() : Math.max(MouseSensitivity.rotationStep(), yawPace * 0.42F);
      float pitchFloor = settling ? MouseSensitivity.rotationStep() * 0.7F : Math.max(MouseSensitivity.rotationStep() * 0.7F, pitchPace * 0.42F);
      float yawStep = Math.min(Math.abs(aimYawError), Math.max(yawFloor, Math.abs(this.yawSpeed)));
      float pitchStep = Math.min(Math.abs(aimPitchError), Math.max(pitchFloor, Math.abs(this.pitchSpeed)));
      this.request(aimYaw, aimPitch, yawStep, pitchStep);
   }

   private float pace(float error, float learnedStep, boolean settling) {
      float distance = Math.abs(error);
      float learned = Math.abs(learnedStep);
      if (settling || distance < 1.6F) {
         return Math.max(learned, distance < 0.35F ? 0.0F : MouseSensitivity.rotationStep());
      }

      float ticks = distance > 28.0F ? 5.5F : 4.0F;
      return Math.max(learned, MathHelper.clamp(distance / ticks, 3.4F, 20.0F));
   }

   private float[] nearest(RotationBuilder.LearnedRotation profile, float yawError, float pitchError, float distance, float sideSpeed, boolean onBox, float cooldown) {
      if (profile.prototypes == null || profile.prototypes.length == 0) {
         return null;
      }

      int keep = Math.min(5, profile.prototypes.length);
      int[] indexes = new int[keep];
      float[] distances = new float[keep];
      for (int index = 0; index < keep; index++) {
         distances[index] = Float.MAX_VALUE;
         indexes[index] = -1;
      }

      for (int index = 0; index < profile.prototypes.length; index++) {
         float[] prototype = profile.prototypes[index];
         if (prototype.length < 8) {
            continue;
         }

         float score = this.square(prototype[0] / 28.0F - yawError / 28.0F)
            + this.square(prototype[1] / 16.0F - pitchError / 16.0F)
            + this.square(prototype[2] / 3.5F - distance / 3.5F)
            + this.square(prototype[3] / 0.18F - sideSpeed / 0.18F)
            + this.square((prototype[4] - (onBox ? 1.0F : 0.0F)) * 0.85F)
            + this.square((prototype[5] - cooldown) * 0.35F);
         int slot = -1;
         for (int rank = 0; rank < keep; rank++) {
            if (score < distances[rank]) {
               slot = rank;
               break;
            }
         }

         if (slot != -1) {
            for (int rank = keep - 1; rank > slot; rank--) {
               distances[rank] = distances[rank - 1];
               indexes[rank] = indexes[rank - 1];
            }

            distances[slot] = score;
            indexes[slot] = index;
         }
      }

      float yaw = 0.0F;
      float pitch = 0.0F;
      float weightSum = 0.0F;
      for (int rank = 0; rank < keep; rank++) {
         if (indexes[rank] == -1) {
            continue;
         }

         float weight = 1.0F / (distances[rank] + 0.04F);
         yaw += profile.prototypes[indexes[rank]][6] * weight;
         pitch += profile.prototypes[indexes[rank]][7] * weight;
         weightSum += weight;
      }

      return weightSum == 0.0F ? null : new float[]{yaw / weightSum, pitch / weightSum};
   }

   private float square(float value) {
      return value * value;
   }

   private Vec3d horizontalRight(Vec3d eye, Vec3d center) {
      Vec3d forward = new Vec3d(center.x - eye.x, 0.0, center.z - eye.z);
      if (forward.lengthSquared() < 1.0E-6D) {
         forward = new Vec3d(0.0, 0.0, 1.0);
      }

      forward = forward.normalize();
      return new Vec3d(-forward.z, 0.0, forward.x);
   }

   private void request(float yaw, float pitch, float yawStep, float pitchStep) {
      RotationController.getInstance()
         .requestRotation(
            new RotationAngles(yaw, MathHelper.clamp(pitch, -90.0F, 90.0F)),
            new RotationLimits(Math.max(MouseSensitivity.rotationStep(), yawStep), Math.max(MouseSensitivity.rotationStep() * 0.65F, pitchStep), 14.0F, 9.0F),
            RotationController.ReturnMode.SMOOTH
         );
   }
}
