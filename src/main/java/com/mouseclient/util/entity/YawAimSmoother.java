package com.mouseclient.util.entity;

import com.mouseclient.internal.ClientMath;
import com.mouseclient.render.avatar.Easings;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class YawAimSmoother implements MinecraftAccess {
   private float yawStep;
   private float deadzone;
   private float alignedTicks;
   private boolean settled;
   private long slowUntil;

   public void markAttack() {
      this.slowUntil = System.currentTimeMillis() + (long)ClientMath.randomFloat((200.0F), (250.0F));
   }

   public static float cooldownFactor() {
      float cooldownProgress = minecraftClient.player.getAttackCooldownProgress(1.0F);
      double attackSpeed = minecraftClient.player.getAttributeValue(EntityAttributes.ATTACK_SPEED);
      if (attackSpeed > (1.6D)) {
         cooldownProgress *= (float)((1.6D) / attackSpeed);
      }

      return MathHelper.clamp(MathHelper.sin((float)(Easings.BACK_OUT.apply(cooldownProgress) * (3.141592653589793D))), (0.12F), 1.0F);
   }

   public float update(LivingEntity entity, float tickDelta, float yaw, float pitch, boolean forceCorrection) {
      if (entity != null && minecraftClient.player != null) {
         Vec3d position = new Vec3d(
            entity.getX() - entity.lastRenderX - minecraftClient.player.getX() + minecraftClient.player.lastRenderX,
            0.0,
            entity.getZ() - entity.lastRenderZ - minecraftClient.player.getZ() + minecraftClient.player.lastRenderZ
         );
         Vec3d cameraPosition = minecraftClient.player.getCameraPosVec(tickDelta);
         Box predictedBounds = entity.getBoundingBox().offset(entity.getLerpedPos(tickDelta).subtract(entity.getEntityPos()).add(position.multiply((4.0D))));
         predictedBounds = predictedBounds.contract(predictedBounds.getLengthX() * (0.25D), 0.0, predictedBounds.getLengthZ() * (0.25D));
         Vec3d projectedLookPoint = cameraPosition.add(CombatTargeting.direction(yaw, pitch).multiply(cameraPosition.distanceTo(predictedBounds.getCenter())));
         double deltaX = MathHelper.clamp(projectedLookPoint.x, predictedBounds.minX, predictedBounds.maxX) - cameraPosition.x;
         double deltaZ = MathHelper.clamp(projectedLookPoint.z, predictedBounds.minZ, predictedBounds.maxZ) - cameraPosition.z;
         float yawErrorWithJitter = MathHelper.wrapDegrees((float)Math.toDegrees(Math.atan2(deltaZ, deltaX)) - (90.0F) - yaw)
            + MathHelper.sin(minecraftClient.player.age * (0.21F)) * (0.35F);
         this.alignedTicks = Math.abs(yawErrorWithJitter) < (1.5F) ? Math.min(this.alignedTicks + 1.0F, (240.0F)) : 0.0F;
         if (Math.abs(yawErrorWithJitter) < this.deadzone) {
            this.settled = true;
            this.yawStep = 0.0F;
            return 0.0F;
         }

         if (this.settled) {
            this.settled = false;
            this.deadzone = ClientMath.randomFloat((0.4F), (0.9F));
         }

         float speedFactor = forceCorrection
            ? 1.0F
            : (1.0F - MathHelper.clamp((this.alignedTicks - (60.0F)) / (72.0F), 0.0F, 1.0F))
               * (minecraftClient.player.handSwingTicks > ClientMath.randomFloat((0.2F), (0.4F)) ? (0.5F) : 1.0F)
               * (CombatTargeting.overlapsPlayer(entity) ? (0.5F) : 1.0F)
               * (System.currentTimeMillis() < this.slowUntil ? (0.35F) : 1.0F);
         float maximumStep = forceCorrection ? (4.5F) : (3.0F);
         float acceleration = forceCorrection ? (0.35F) : (0.22F);
         this.yawStep = this.yawStep
            + MathHelper.clamp(
               MathHelper.clamp(yawErrorWithJitter * (forceCorrection ? (0.2F) : (0.14F) * cooldownFactor()) * speedFactor, -maximumStep, maximumStep) - this.yawStep, -acceleration, acceleration
            );
         return this.yawStep;
      } else {
         return 0.0F;
      }
   }

   public void reset() {
      this.yawStep = this.alignedTicks = 0.0F;
      this.settled = false;
      this.slowUntil = 0L;
   }

   public YawAimSmoother() {
      this.deadzone = (0.6F);
   }
}
