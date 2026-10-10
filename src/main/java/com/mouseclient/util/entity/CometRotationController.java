package com.mouseclient.util.entity;

import com.mouseclient.network.RotationController;
import com.mouseclient.network.RotationLimits;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class CometRotationController implements MinecraftAccess {
   private float yawSpeed;
   private float pitchSpeed;
   private float lead;

   public void reset() {
      this.yawSpeed = this.pitchSpeed = 0.0F;
      this.lead = 0.35F;
      RotationController.getInstance().reset();
   }

   public void update(LivingEntity entity, boolean attackReady, float attackRange) {
      if (entity == null || minecraftClient.player == null) {
         return;
      }

      Vec3d eye = minecraftClient.player.getEyePos();
      Vec3d motion = new Vec3d(entity.getX() - entity.lastX, entity.getY() - entity.lastY, entity.getZ() - entity.lastZ);
      float desiredLead = attackReady ? 0.72F : 0.28F;
      this.lead += (desiredLead - this.lead) * 0.18F;
      Vec3d aim = new Vec3d(entity.getX(), entity.getY() + entity.getHeight() * 0.62, entity.getZ()).add(motion.multiply(this.lead * 8.0));
      if (eye.distanceTo(aim) > attackRange + 1.2) {
         Vec3d toward = aim.subtract(eye);
         if (toward.lengthSquared() > 1.0E-4) {
            aim = eye.add(toward.normalize().multiply(Math.max(0.4, attackRange)));
         }
      }

      float[] angles = CombatTargeting.anglesBetween(aim, eye);
      float yaw = angles[0];
      float pitch = MathHelper.clamp(angles[1], -90.0F, 90.0F);
      boolean onBox = CombatTargeting.rayHitsEntityBounds(entity, Math.max(attackRange + 0.8F, eye.distanceTo(entity.getBoundingBox().getCenter()) + 1.0), minecraftClient.player.getYaw(), minecraftClient.player.getPitch());
      if (onBox && !attackReady) {
         this.yawSpeed *= 0.4F;
         this.pitchSpeed *= 0.4F;
         RotationController.getInstance()
            .requestRotation(
               new RotationAngles(minecraftClient.player.getYaw(), minecraftClient.player.getPitch()),
               new RotationLimits(MouseSensitivity.rotationStep(), MouseSensitivity.rotationStep(), 12.0F, 8.0F),
               RotationController.ReturnMode.SMOOTH
            );
         return;
      }

      float yawError = MathHelper.wrapDegrees(yaw - minecraftClient.player.getYaw());
      float pitchError = pitch - minecraftClient.player.getPitch();
      float pulse = attackReady ? 1.0F : 0.45F + minecraftClient.player.getAttackCooldownProgress(0.0F) * 0.35F;
      float yawCruise = MathHelper.clamp(Math.abs(yawError) * 0.22F, 3.0F, attackReady ? 24.0F : 12.0F) * pulse;
      float pitchCruise = MathHelper.clamp(Math.abs(pitchError) * 0.2F, 1.6F, attackReady ? 12.0F : 6.0F) * pulse;
      float blend = attackReady ? 0.46F : 0.24F;
      this.yawSpeed += (Math.copySign(yawCruise, yawError == 0.0F ? this.yawSpeed : yawError) - this.yawSpeed) * blend;
      this.pitchSpeed += (Math.copySign(pitchCruise, pitchError == 0.0F ? this.pitchSpeed : pitchError) - this.pitchSpeed) * blend;
      float yawStep = Math.min(Math.abs(yawError), Math.max(MouseSensitivity.rotationStep(), Math.abs(this.yawSpeed)));
      float pitchStep = Math.min(Math.abs(pitchError), Math.max(MouseSensitivity.rotationStep() * 0.7F, Math.abs(this.pitchSpeed)));
      RotationController.getInstance()
         .requestRotation(new RotationAngles(yaw, pitch), new RotationLimits(yawStep, pitchStep, 14.0F, 9.0F), RotationController.ReturnMode.SMOOTH);
   }
}
