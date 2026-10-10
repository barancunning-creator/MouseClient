package com.mouseclient.util.entity;

import com.mouseclient.internal.ClientMath;
import com.mouseclient.modules.impl.combat.AttackAura;
import com.mouseclient.modules.impl.combat.MaceHelper;
import com.mouseclient.network.RotationLimits;
import com.mouseclient.network.RotationController;
import com.mouseclient.render.avatar.Easings;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class SpookyRotationController implements MinecraftAccess {
   private float attackImpulse;
   private float heldYaw;
   private float heldPitch;
   private long impulseTime;
   private long holdDurationMillis;
   private float yawVelocity;
   private float pitchVelocity;
   private float maxSpeed;
   private float easingAngle;
   private float acceleration;
   private float yawPitchCoupling;
   private float deadzone;
   private long lastRandomizeTime;
   private long randomizeInterval;
   private long startTime;
   private long alignedSince;
   private float alignedFactor;
   private float targetHeightFraction;
   private float targetOffsetX;
   private float targetOffsetZ;

   public void update(LivingEntity entity, boolean attackReady, float attackRange) {
      if (entity != null && minecraftClient.player != null && minecraftClient.world != null) {
         AttackAura attackAura = AttackAura.getInstance();
         long startTime = System.currentTimeMillis();
         boolean airborneMaceMode = attackAura.isAirborneWithMace() || MaceHelper.getInstance().isWindBoostActive() && !minecraftClient.player.isOnGround();
         if (this.startTime == 0L) {
            this.startTime = startTime;
         }

         if (this.targetHeightFraction == 0.0F) {
            this.targetHeightFraction = ClientMath.randomFloat((0.24F), (0.72F));
         }

         if (startTime - this.lastRandomizeTime > this.randomizeInterval) {
            this.lastRandomizeTime = startTime;
            this.randomizeInterval = (long)ClientMath.randomFloat((25.0F), (50.0F));
            this.maxSpeed = ClientMath.randomFloat((142.0F), (200.0F));
            this.easingAngle = ClientMath.randomFloat((34.0F), (54.0F));
            this.acceleration = ClientMath.randomFloat((7.0F), (14.0F));
            this.yawPitchCoupling = ClientMath.randomFloat((0.04F), (0.12F)) * (ThreadLocalRandom.current().nextBoolean() ? 1.0F : (-1.0F));
            this.deadzone = ClientMath.randomFloat((0.4F), (0.9F));
         }

         Vec3d position = minecraftClient.player.getCameraPosVec(minecraftClient.getRenderTickCounter().getTickProgress(false));
         boolean partialBlockOcclusion = AttackAura.getInstance().attackThroughBlocks.isSelected(("Неполноценные"))
            && !CombatTargeting.hasLineOfSight(position, entity.getBoundingBox().getCenter())
            && CombatTargeting.hasLineOfSightIgnoringPartialBlocks(position, entity.getBoundingBox().getCenter());
         Vec3d aimDirection = this.targetPoint(
               entity, airborneMaceMode ? 1.0 : this.targetHeightFraction, !airborneMaceMode && !partialBlockOcclusion ? this.targetOffsetX : 0.0, 0.0, !airborneMaceMode && !partialBlockOcclusion ? this.targetOffsetZ : 0.0
            )
            .subtract(position)
            .normalize();
         float heldYaw = (float)Math.toDegrees(Math.atan2(-aimDirection.x, aimDirection.z));
         float heldPitch = (float)MathHelper.clamp(-Math.toDegrees(Math.atan2(aimDirection.y, Math.hypot(aimDirection.x, aimDirection.z))), (-90.0D), (90.0D));
         float currentYaw = minecraftClient.player.getYaw();
         float currentPitch = minecraftClient.player.getPitch();
         boolean impulseTriggered = false;
         if (attackReady && CombatTargeting.distanceToBounds(entity) < attackRange) {
            this.attackImpulse = attackAura.randomFloat((1.9F), (2.3F));
            this.impulseTime = startTime;
            this.holdDurationMillis = (long)ThreadLocalRandom.current().nextDouble((200.0D), (250.0D));
            this.targetHeightFraction = ClientMath.randomFloat((0.54F), (0.72F));
            this.targetOffsetX = ClientMath.randomFloat((-0.12F), (0.12F));
            this.targetOffsetZ = ClientMath.randomFloat((-0.12F), (0.12F));
         }

         if (this.attackImpulse > attackAura.randomFloat((0.43F), (0.75F))) {
            impulseTriggered = true;
            this.attackImpulse = attackAura.randomFloat((0.13F), (0.25F));
         }

         if (impulseTriggered) {
            this.heldYaw = heldYaw;
            this.heldPitch = heldPitch;
         } else if (startTime - this.impulseTime < this.holdDurationMillis) {
            heldYaw = this.heldYaw;
            heldPitch = this.heldPitch;
         }

         if (CombatTargeting.rayIntersectsBox(minecraftClient.player.getEyePos(), new RotationAngles(currentYaw, currentPitch).direction(), entity.getBoundingBox(), attackRange + 1.0)) {
            if (this.alignedSince == 0L) {
               this.alignedSince = startTime;
            }
         } else {
            this.alignedSince = 0L;
         }

         float alignedFactor = MathHelper.clamp(((float)(this.alignedSince == 0L ? 0L : startTime - this.alignedSince) - (250.0F)) / (300.0F), 0.0F, 1.0F);
         if (alignedFactor >= this.alignedFactor) {
            this.alignedFactor = alignedFactor;
         } else {
            this.alignedFactor = this.alignedFactor + (alignedFactor - this.alignedFactor) * (4.0F);
         }

         float yawError = MathHelper.wrapDegrees(heldYaw - currentYaw);
         float pitchError = heldPitch - currentPitch;
         float angularDistance = (float)Math.hypot(yawError, pitchError);
         float effectiveSpeed = this.maxSpeed
            * (float)Easings.LINEAR.apply(ClientMath.clampUnit(angularDistance / this.easingAngle))
            * MathHelper.lerp(MathHelper.clamp((float)(startTime - this.startTime) / (220.0F), 0.0F, 1.0F), (0.78F), 1.0F)
            * (1.0F - ClientMath.clampUnit(this.alignedFactor) * (airborneMaceMode ? (0.35F) : 1.0F))
            * (impulseTriggered ? (1.25F) : 1.0F)
            * (airborneMaceMode ? (3.2F) : 1.0F)
            * (minecraftClient.player.handSwingTicks > attackAura.randomFloat((0.2F), (0.4F)) && !airborneMaceMode ? (0.5F) : 1.0F);
         float effectiveAcceleration = this.acceleration * (impulseTriggered ? (4.0F) : 1.0F) * (airborneMaceMode ? (2.6F) : 1.0F);
         this.yawVelocity = this.yawVelocity
            + MathHelper.clamp(
               effectiveSpeed * (angularDistance < (0.001F) ? 0.0F : yawError / angularDistance) * (1.25F) / (CombatTargeting.overlapsPlayer(entity) ? 2.0F : 1.0F) - this.yawVelocity,
               -effectiveAcceleration * (airborneMaceMode ? (0.6F) : (1.2F)),
               effectiveAcceleration * (airborneMaceMode ? (0.6F) : (1.2F))
            );
         this.pitchVelocity = this.pitchVelocity
            + MathHelper.clamp(
               effectiveSpeed * (angularDistance < (0.001F) ? 0.0F : pitchError / angularDistance) + this.yawVelocity * this.yawPitchCoupling - this.pitchVelocity,
               -effectiveAcceleration * (0.68F),
               effectiveAcceleration * (0.68F)
            );
         this.yawVelocity = MathHelper.clamp(this.yawVelocity, -Math.abs(yawError), Math.abs(yawError));
         this.pitchVelocity = MathHelper.clamp(
            this.pitchVelocity, -Math.abs(pitchError) - Math.abs(this.yawVelocity * this.yawPitchCoupling), Math.abs(pitchError) + Math.abs(this.yawVelocity * this.yawPitchCoupling)
         );
         if (angularDistance < this.deadzone) {
            this.yawVelocity = this.pitchVelocity = 0.0F;
         }

         RotationController.getInstance()
            .requestRotation(
               new RotationAngles(heldYaw, heldPitch),
               new RotationLimits(
                  Math.min(
                        Math.abs(this.yawVelocity), CombatTargeting.yawStepWithRandomDivisor(new RotationAngles(heldYaw, heldPitch), airborneMaceMode ? (0.6F) : 1.0F, airborneMaceMode ? 1.0F : (1.5F))
                     )
                     * (airborneMaceMode ? (3.4F) : 1.0F),
                  Math.min(
                        Math.abs(this.pitchVelocity),
                        CombatTargeting.pitchStepWithRandomDivisor(new RotationAngles(heldYaw, heldPitch), airborneMaceMode ? 1.0F : (2.5F), airborneMaceMode ? (1.8F) : (4.0F))
                     )
                     * (airborneMaceMode ? (3.4F) : 1.0F),
                  (25.0F),
                  (25.0F)
               ),
               RotationController.ReturnMode.NONE
            );
      }
   }

   public void reset() {
      this.attackImpulse = 0.0F;
      this.heldYaw = this.heldPitch = 0.0F;
      this.impulseTime = this.holdDurationMillis = 0L;
      this.yawVelocity = this.pitchVelocity = 0.0F;
      this.lastRandomizeTime = this.randomizeInterval = 0L;
      this.startTime = 0L;
      this.alignedSince = 0L;
      this.alignedFactor = 0.0F;
      this.targetHeightFraction = 0.0F;
      this.targetOffsetX = this.targetOffsetZ = 0.0F;
   }

   private Vec3d targetPoint(LivingEntity entity, double heightFraction, double offsetX, double offsetY, double offsetZ) {
      return entity.getEntityPos()
         .add(offsetX, MathHelper.clamp(entity.getHeight() * heightFraction + offsetY, (0.1D), Math.max((0.1D), entity.getHeight() - (0.1D))), offsetZ);
   }
}
