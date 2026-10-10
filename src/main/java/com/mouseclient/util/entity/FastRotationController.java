package com.mouseclient.util.entity;

import com.mouseclient.internal.ClientMath;
import com.mouseclient.modules.impl.combat.AttackAura;
import com.mouseclient.network.RotationLimits;
import com.mouseclient.network.RotationController;
import com.mouseclient.render.avatar.Easings;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class FastRotationController implements MinecraftAccess {
   private float attackImpulse;
   private float yawVelocity;
   private float pitchVelocity;
   private float maxSpeed;
   private float easingAngle;
   private float acceleration;
   private float yawPitchCoupling;
   private long lastRandomizeTime;
   private long randomizeInterval;
   private long startTime;

   public void update(LivingEntity entity, boolean attackReady, float attackRange) {
      if (entity != null && minecraftClient.player != null && minecraftClient.world != null) {
         AttackAura attackAura = AttackAura.getInstance();
         long startTime = System.currentTimeMillis();
         if (this.startTime == 0L) {
            this.startTime = startTime;
         }

         if (startTime - this.lastRandomizeTime > this.randomizeInterval) {
            this.lastRandomizeTime = startTime;
            this.randomizeInterval = (long)ClientMath.randomFloat((25.0F), (50.0F));
            this.maxSpeed = ClientMath.randomFloat((248.0F), (360.0F));
            this.easingAngle = ClientMath.randomFloat((34.0F), (54.0F));
            this.acceleration = ClientMath.randomFloat((8.0F), (16.0F));
            this.yawPitchCoupling = ClientMath.randomFloat((0.04F), (0.12F)) * (ThreadLocalRandom.current().nextBoolean() ? 1.0F : (-1.0F));
         }

         Vec3d position = minecraftClient.player.getCameraPosVec(minecraftClient.getRenderTickCounter().getTickProgress(false));
         Vec3d aimDirection = entity.getEntityPos().add(0.0, Math.max((0.1D), entity.getHeight() - (0.1D)), 0.0).subtract(position).normalize();
         float targetYaw = (float)Math.toDegrees(Math.atan2(-aimDirection.x, aimDirection.z));
         float targetPitch = (float)MathHelper.clamp(-Math.toDegrees(Math.atan2(aimDirection.y, Math.hypot(aimDirection.x, aimDirection.z))), (-90.0D), (90.0D));
         boolean impulseTriggered = false;
         if (attackReady && CombatTargeting.distanceToBounds(entity) < attackRange) {
            this.attackImpulse = attackAura.randomFloat((1.9F), (2.3F));
         }

         if (this.attackImpulse > attackAura.randomFloat((0.43F), (0.75F))) {
            impulseTriggered = true;
            this.attackImpulse = attackAura.randomFloat((0.13F), (0.25F));
         }

         float yawError = MathHelper.wrapDegrees(targetYaw - minecraftClient.player.getYaw());
         float pitchError = targetPitch - minecraftClient.player.getPitch();
         float angularDistance = (float)Math.hypot(yawError, pitchError);
         float effectiveSpeed = this.maxSpeed
            * (float)Easings.LINEAR.apply(ClientMath.clampUnit(angularDistance / this.easingAngle))
            * MathHelper.lerp(MathHelper.clamp((float)(startTime - this.startTime) / (160.0F), 0.0F, 1.0F), (0.78F), 1.0F)
            * (impulseTriggered ? (1.25F) : 1.0F)
            * (3.6F);
         float effectiveAcceleration = this.acceleration * (impulseTriggered ? (4.0F) : 1.0F) * (3.0F);
         this.yawVelocity = this.yawVelocity
            + MathHelper.clamp(
               effectiveSpeed * (angularDistance < (0.001F) ? 0.0F : yawError / angularDistance) * (1.25F) / (CombatTargeting.overlapsPlayer(entity) ? 2.0F : 1.0F) - this.yawVelocity,
               -effectiveAcceleration * (1.2F),
               effectiveAcceleration * (1.2F)
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
         RotationController.getInstance()
            .requestRotation(
               new RotationAngles(targetYaw, targetPitch),
               new RotationLimits(
                  Math.min(Math.abs(this.yawVelocity), CombatTargeting.yawStepWithRandomDivisor(new RotationAngles(targetYaw, targetPitch), (0.5F), (0.9F))) * (3.9F),
                  Math.min(Math.abs(this.pitchVelocity), CombatTargeting.pitchStepWithRandomDivisor(new RotationAngles(targetYaw, targetPitch), (0.9F), (1.6F))) * (3.9F),
                  (25.0F),
                  (25.0F)
               ),
               RotationController.ReturnMode.NONE
            );
      }
   }

   public void reset() {
      this.attackImpulse = 0.0F;
      this.yawVelocity = this.pitchVelocity = 0.0F;
      this.lastRandomizeTime = this.randomizeInterval = 0L;
      this.startTime = 0L;
   }
}
