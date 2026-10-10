package com.mouseclient.util.entity;

import com.mouseclient.internal.ClientMath;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class LegitRotationController implements MinecraftAccess {
   private float yawVelocity;
   private float pitchVelocity;
   private float yawRemainder;
   private float pitchRemainder;
   private long lastUpdateTime;

   public void reset() {
      this.yawVelocity = this.pitchVelocity = 0.0F;
      this.yawRemainder = this.pitchRemainder = 0.0F;
      this.lastUpdateTime = 0L;
   }

   public void update(LivingEntity entity) {
      if (entity != null && minecraftClient.player != null && minecraftClient.world != null) {
         float elapsedTicks = Math.max(1.0F, Math.min((float)(System.currentTimeMillis() - this.lastUpdateTime), (100.0F))) / (50.0F);
         this.lastUpdateTime = System.currentTimeMillis();
         Vec3d position = minecraftClient.player.getEyePos();
         Box contractedBounds = entity.getBoundingBox().contract(entity.getWidth() * (0.25D), entity.getHeight() * (0.2D), entity.getWidth() * (0.25D));
         Vec3d projectedLookPoint = position.add(
            CombatTargeting.direction(minecraftClient.player.getYaw(), minecraftClient.player.getPitch()).multiply(position.distanceTo(contractedBounds.getCenter()))
         );
         float[] targetAngles = CombatTargeting.anglesBetween(
            new Vec3d(
               MathHelper.clamp(projectedLookPoint.x, contractedBounds.minX, contractedBounds.maxX), MathHelper.clamp(projectedLookPoint.y, contractedBounds.minY, contractedBounds.maxY), MathHelper.clamp(projectedLookPoint.z, contractedBounds.minZ, contractedBounds.maxZ)
            ),
            position
         );
         float yawError = MathHelper.wrapDegrees(targetAngles[0] - minecraftClient.player.getYaw());
         float pitchError = targetAngles[1] - minecraftClient.player.getPitch();
         float angleFactor = ClientMath.clampUnit(((100.0F) - (float)Math.hypot(yawError, pitchError)) / Math.max(1.0F, (25.0F)));
         if (!(angleFactor <= 0.0F)) {
            float smoothingStrength = (0.15F);
            float desiredYawVelocity = MathHelper.clamp(yawError * smoothingStrength, (-15.0F), (15.0F)) * angleFactor;
            float desiredPitchVelocity = MathHelper.clamp(pitchError * smoothingStrength, (-5.0F), (5.0F)) * angleFactor;
            this.yawVelocity = this.yawVelocity + (desiredYawVelocity - this.yawVelocity) * ClientMath.clampUnit((0.4F) * elapsedTicks);
            this.pitchVelocity = this.pitchVelocity + (desiredPitchVelocity - this.pitchVelocity) * ClientMath.clampUnit((0.4F) * elapsedTicks);
            this.yawRemainder = this.yawRemainder + MathHelper.clamp(this.yawVelocity * elapsedTicks, -Math.abs(yawError), Math.abs(yawError));
            this.pitchRemainder = this.pitchRemainder + MathHelper.clamp(this.pitchVelocity * elapsedTicks, -Math.abs(pitchError), Math.abs(pitchError));
            float quantizedYaw = MouseSensitivity.quantize(this.yawRemainder);
            float quantizedPitch = MouseSensitivity.quantize(this.pitchRemainder);
            this.yawRemainder -= quantizedYaw;
            this.pitchRemainder -= quantizedPitch;
            minecraftClient.player.setYaw(minecraftClient.player.getYaw() + quantizedYaw);
            minecraftClient.player.setPitch(MathHelper.clamp(minecraftClient.player.getPitch() + quantizedPitch, (-90.0F), (90.0F)));
            minecraftClient.player.lastYaw += quantizedYaw;
            minecraftClient.player.lastPitch = MathHelper.clamp(minecraftClient.player.lastPitch + quantizedPitch, (-90.0F), (90.0F));
         }
      }
   }
}
