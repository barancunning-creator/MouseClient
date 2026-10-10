package com.mouseclient.util.entity;

import com.mouseclient.network.RotationController;
import com.mouseclient.network.RotationLimits;
import com.mouseclient.internal.ClientMath;
import net.minecraft.util.math.Box;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class ShardTestRotationController implements MinecraftAccess {
   private float yawSpeed;
   private float pitchSpeed;
   private float yawRemainder;
   private float pitchRemainder;
   private float lockYaw;
   private float lockPitch;
   private boolean planted;
   private boolean resting;
   private boolean wasReady;
   private float hitHeight = 0.58F;
   private float hitSide;
   private float hitLead;

   public void reset() {
      this.yawSpeed = this.pitchSpeed = 0.0F;
      this.yawRemainder = this.pitchRemainder = 0.0F;
      this.planted = false;
      this.resting = false;
      this.wasReady = false;
      this.rollHit();
      RotationController.getInstance().reset();
   }

   public void update(LivingEntity entity, boolean attackReady, float attackRange) {
      if (entity == null || minecraftClient.player == null) {
         return;
      }

      float cooldown = minecraftClient.player.getAttackCooldownProgress(0.0F);
      if (this.wasReady && !attackReady) {
         this.remember(minecraftClient.player.getYaw(), minecraftClient.player.getPitch());
         this.resting = true;
         this.planted = true;
         this.yawRemainder = this.pitchRemainder = 0.0F;
      }

      this.wasReady = attackReady;
      if (this.resting && cooldown >= 0.72F) {
         this.resting = false;
         this.planted = false;
         this.rollHit();
         this.yawSpeed = this.pitchSpeed = 0.0F;
      }

      if (this.resting) {
         this.hold();
         return;
      }

      Vec3d eye = minecraftClient.player.getEyePos();
      Vec3d motion = new Vec3d(entity.getX() - entity.lastX, 0.0, entity.getZ() - entity.lastZ);
      Vec3d aim = this.hitPoint(entity, eye, motion);
      float[] angles = CombatTargeting.anglesBetween(aim, eye);
      float yaw = angles[0];
      float pitch = MathHelper.clamp(angles[1], -90.0F, 90.0F);
      float yawError = MathHelper.wrapDegrees(yaw - minecraftClient.player.getYaw());
      float pitchError = pitch - minecraftClient.player.getPitch();
      float yawPace = MathHelper.clamp(Math.abs(yawError) * 0.34F, 4.0F, 32.0F);
      float pitchPace = MathHelper.clamp(Math.abs(pitchError) * 0.28F, 2.0F, 16.0F);
      float blend = 0.42F + 0.28F * MathHelper.clamp(Math.abs(this.yawSpeed) / 12.0F, 0.0F, 1.0F);
      this.yawSpeed += (Math.copySign(yawPace, yawError == 0.0F ? this.yawSpeed : yawError) - this.yawSpeed) * blend;
      this.pitchSpeed += (Math.copySign(pitchPace, pitchError == 0.0F ? this.pitchSpeed : pitchError) - this.pitchSpeed) * (blend * 0.8F);
      this.yawRemainder += this.yawSpeed;
      this.pitchRemainder += this.pitchSpeed;
      float yawTick = MouseSensitivity.quantize(MathHelper.clamp(this.yawRemainder, -Math.abs(yawError), Math.abs(yawError)));
      float pitchTick = MouseSensitivity.quantize(MathHelper.clamp(this.pitchRemainder, -Math.abs(pitchError), Math.abs(pitchError)));
      this.yawRemainder -= yawTick;
      this.pitchRemainder -= pitchTick;
      float nextYaw = minecraftClient.player.getYaw() + yawTick;
      float nextPitch = MathHelper.clamp(minecraftClient.player.getPitch() + pitchTick, -90.0F, 90.0F);
      RotationController.getInstance()
         .requestRotation(
            new RotationAngles(nextYaw, nextPitch),
            new RotationLimits(Math.max(Math.abs(yawTick), MouseSensitivity.rotationStep()), Math.max(Math.abs(pitchTick), MouseSensitivity.rotationStep()), 8.0F, 5.0F),
            RotationController.ReturnMode.SMOOTH
         );
   }

   private void remember(float yaw, float pitch) {
      this.lockYaw = yaw;
      this.lockPitch = pitch;
      this.yawSpeed = this.pitchSpeed = 0.0F;
   }

   private void hold() {
      float yawError = Math.abs(MathHelper.wrapDegrees(this.lockYaw - minecraftClient.player.getYaw()));
      float pitchError = Math.abs(this.lockPitch - minecraftClient.player.getPitch());
      float step = Math.max(MouseSensitivity.rotationStep(), Math.max(yawError, pitchError));
      RotationController.getInstance()
         .requestRotation(
            new RotationAngles(this.lockYaw, MathHelper.clamp(this.lockPitch, -90.0F, 90.0F)),
            new RotationLimits(step, step, 8.0F, 5.0F),
            RotationController.ReturnMode.NONE
         );
   }

   private void rollHit() {
      this.hitHeight = ClientMath.randomFloatMixture(0.22F, 0.9F);
      this.hitSide = ClientMath.randomFloatMixture(-0.78F, 0.78F);
      this.hitLead = ClientMath.randomFloatMixture(-0.35F, 0.55F);
   }

   private Vec3d hitPoint(LivingEntity entity, Vec3d eye, Vec3d motion) {
      Box box = entity.getBoundingBox();
      Vec3d center = box.getCenter();
      Vec3d right = this.horizontalRight(eye, center);
      Vec3d forward = new Vec3d(-right.z, 0.0, right.x);
      double side = this.hitSide * box.getLengthX() * 0.38;
      double lead = this.hitLead * box.getLengthZ() * 0.22 + motion.dotProduct(forward) * 1.4;
      return new Vec3d(
         MathHelper.clamp(center.x + right.x * side + forward.x * lead, box.minX + 0.05, box.maxX - 0.05),
         MathHelper.clamp(box.minY + box.getLengthY() * this.hitHeight, box.minY + 0.08, box.maxY - 0.05),
         MathHelper.clamp(center.z + right.z * side + forward.z * lead, box.minZ + 0.05, box.maxZ - 0.05)
      );
   }

   private Vec3d horizontalRight(Vec3d eye, Vec3d center) {
      Vec3d forward = new Vec3d(center.x - eye.x, 0.0, center.z - eye.z);
      if (forward.lengthSquared() < 1.0E-6D) {
         forward = new Vec3d(0.0, 0.0, 1.0);
      }

      forward = forward.normalize();
      return new Vec3d(-forward.z, 0.0, forward.x);
   }
}
