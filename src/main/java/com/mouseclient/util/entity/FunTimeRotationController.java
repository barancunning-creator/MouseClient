package com.mouseclient.util.entity;

import com.mouseclient.internal.ClientMath;
import com.mouseclient.modules.impl.combat.AttackAura;
import com.mouseclient.modules.impl.combat.MaceHelper;
import com.mouseclient.network.RotationLimits;
import com.mouseclient.network.RotationController;
import lombok.Generated;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class FunTimeRotationController implements MinecraftAccess {
   private boolean boosted;
   private float yawVelocity;
   private float pitchVelocity;
   private float heldYaw;
   private float heldPitch;
   private long lastAttackReadyTime;
   private long holdDurationMillis;

   private float cooldownFactor() {
      float cooldownProgress = minecraftClient.player.getAttackCooldownProgress(1.0F);
      double attackSpeed = minecraftClient.player.getAttributeValue(EntityAttributes.ATTACK_SPEED);
      if (attackSpeed > (1.6D)) {
         cooldownProgress *= (float)((1.6D) / attackSpeed);
      }

      float easedCooldown;
      if (attackSpeed < (2.0D)) {
         easedCooldown = (2.70158F) * cooldownProgress * cooldownProgress * cooldownProgress - (1.70158F) * cooldownProgress * cooldownProgress;
      } else if (cooldownProgress < (0.5F)) {
         easedCooldown = (8.0F) * cooldownProgress * cooldownProgress * cooldownProgress * cooldownProgress;
      } else {
         easedCooldown = 1.0F - (8.0F) * (cooldownProgress - 1.0F) * (cooldownProgress - 1.0F) * (cooldownProgress - 1.0F) * (cooldownProgress - 1.0F);
      }

      return (1.0F + (29.0F) * easedCooldown) / (50.0F);
   }

   public void update(LivingEntity entity, boolean attackReady, float attackRange) {
      if (entity != null && minecraftClient.player != null && minecraftClient.world != null) {
         Vec3d position = minecraftClient.player.getEyePos();
         Vec3d targetMotion = new Vec3d(entity.getX() - entity.lastRenderX, entity.getY() - entity.lastRenderY, entity.getZ() - entity.lastRenderZ);
         Box predictedBounds = entity.getBoundingBox().offset(targetMotion.lengthSquared() > (0.36D) ? targetMotion.normalize().multiply((0.6D)) : targetMotion).contract((0.08D));
         Vec3d nearestAimPoint = new Vec3d(
            MathHelper.clamp(position.x, predictedBounds.minX, predictedBounds.maxX),
            MathHelper.clamp(position.y, predictedBounds.minY + (0.15D), Math.max(predictedBounds.minY + (0.15D), predictedBounds.minY + (predictedBounds.maxY - predictedBounds.minY) * (0.7D))),
            MathHelper.clamp(position.z, predictedBounds.minZ, predictedBounds.maxZ)
         );
         Vec3d horizontalCenterOffset = new Vec3d((predictedBounds.minX + predictedBounds.maxX) * (0.5D) - nearestAimPoint.x, 0.0, (predictedBounds.minZ + predictedBounds.maxZ) * (0.5D) - nearestAimPoint.z);
         Vec3d rangeAdjustedAimPoint = nearestAimPoint.add(
            horizontalCenterOffset.multiply(MathHelper.clamp((attackRange - (0.15D) - position.distanceTo(nearestAimPoint)) / Math.max((0.05D), horizontalCenterOffset.length()), 0.0, (0.8D)))
         );
         Box contractedBounds = entity.getBoundingBox().contract((0.1D));
         Vec3d aimOffset = new Vec3d(
               MathHelper.clamp(rangeAdjustedAimPoint.x, contractedBounds.minX, contractedBounds.maxX),
               MathHelper.clamp(rangeAdjustedAimPoint.y, contractedBounds.minY, contractedBounds.maxY),
               MathHelper.clamp(rangeAdjustedAimPoint.z, contractedBounds.minZ, contractedBounds.maxZ)
            )
            .subtract(position);
         RotationAngles aimRotation = new RotationAngles(
            (float)MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(aimOffset.z, aimOffset.x)) - (90.0D)),
            (float)MathHelper.clamp(-Math.toDegrees(Math.atan2(aimOffset.y, Math.hypot(aimOffset.x, aimOffset.z))), (-90.0D), (90.0D))
         );
         float currentYaw = minecraftClient.player.getYaw();
         float currentPitch = minecraftClient.player.getPitch();
         boolean nearlyVertical = aimOffset.x * aimOffset.x + aimOffset.z * aimOffset.z < (0.0925000011920929D);
         if (attackReady || AttackAura.getInstance().isAirborneWithMace() || MaceHelper.getInstance().isWindBoostActive() && !minecraftClient.player.isOnGround()) {
            boolean var31 = false;
         } else {
            boolean var10000 = true;
         }

         long lastAttackReadyTime = System.currentTimeMillis();
         float heldYaw = aimRotation.getYaw();
         float heldPitch = aimRotation.getPitch();
         if (attackReady) {
            this.lastAttackReadyTime = lastAttackReadyTime;
            this.holdDurationMillis = (long)ClientMath.randomFloat((200.0F), (250.0F));
            this.heldYaw = heldYaw;
            this.heldPitch = heldPitch;
         } else if (lastAttackReadyTime - this.lastAttackReadyTime < this.holdDurationMillis) {
            heldYaw = this.heldYaw;
            heldPitch = this.heldPitch;
         }

         float cooldownRemaining = 1.0F - minecraftClient.player.getAttackCooldownProgress(1.0F);
         float noiseStrength = attackReady ? 0.0F : cooldownRemaining * cooldownRemaining;
         float alternatingDirection = AttackAura.getInstance().successfulAttacks % 2 == 0 ? 1.0F : (-1.0F);
         if (!attackReady) {
            heldYaw += (float)Math.ceil(
               noiseStrength
                  * (
                     AttackAura.getInstance().randomFloat((5.0F), (15.0F)) * MathHelper.cos(minecraftClient.player.age * (0.5F))
                        + AttackAura.getInstance().randomFloat((310.0F), (530.0F)) * alternatingDirection
                  )
            );
            heldPitch += (float)Math.ceil(
               noiseStrength
                  * (
                     AttackAura.getInstance().randomFloat((5.0F), (10.0F)) * MathHelper.sin(minecraftClient.player.age * (0.5F))
                        + AttackAura.getInstance().randomFloat((310.0F), (530.0F)) * alternatingDirection
                  )
            );
         }

         float yawError = MathHelper.wrapDegrees(heldYaw - currentYaw);
         float pitchError = heldPitch - currentPitch;
         float angularDistance = (float)Math.hypot(yawError, pitchError);
         float cooldownBlend = MathHelper.clamp(MathHelper.sin((float)(this.cooldownFactor() * (3.141592653589793D))), (0.15F), (0.9F));
         float effectiveSpeed = angularDistance * cooldownBlend * (this.boosted ? (1.25F) : 1.0F);
         float effectiveAcceleration = (6.0F) + (26.0F) * cooldownBlend;
         this.yawVelocity = this.yawVelocity + MathHelper.clamp(effectiveSpeed * (angularDistance < (0.001F) ? 0.0F : yawError / angularDistance) - this.yawVelocity, -effectiveAcceleration, effectiveAcceleration);
         this.pitchVelocity = this.pitchVelocity
            + MathHelper.clamp(effectiveSpeed * (angularDistance < (0.001F) ? 0.0F : pitchError / angularDistance) - this.pitchVelocity, -effectiveAcceleration * (0.72F), effectiveAcceleration * (0.72F));
         this.yawVelocity = MathHelper.clamp(this.yawVelocity, -Math.abs(yawError), Math.abs(yawError));
         this.pitchVelocity = MathHelper.clamp(this.pitchVelocity, -Math.abs(pitchError), Math.abs(pitchError));
         RotationAngles requestedRotation = new RotationAngles(
            currentYaw + this.yawVelocity + ClientMath.randomFloat((-0.08F), (0.08F)),
            MathHelper.clamp(currentPitch + this.pitchVelocity + ClientMath.randomFloat((-0.04F), (0.04F)), (-90.0F), (90.0F))
         );
         RotationController.getInstance()
            .requestRotation(
               requestedRotation,
               new RotationLimits(Math.abs(MathHelper.wrapDegrees(requestedRotation.getYaw() - currentYaw)), Math.abs(requestedRotation.getPitch() - currentPitch), (23.0F), (23.0F)),
               RotationController.ReturnMode.FAST
            );
      }
   }

   @Generated
   public void setBoosted(boolean boosted) {
      this.boosted = boosted;
   }

   public void reset() {
      this.boosted = false;
      this.yawVelocity = this.pitchVelocity = 0.0F;
      this.heldYaw = this.heldPitch = 0.0F;
      this.lastAttackReadyTime = this.holdDurationMillis = 0L;
   }
}
