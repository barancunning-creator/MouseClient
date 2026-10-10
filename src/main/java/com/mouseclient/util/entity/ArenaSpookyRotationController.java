package com.mouseclient.util.entity;

import com.mouseclient.network.CombatAttackHelper;
import com.mouseclient.network.RotationLimits;
import com.mouseclient.network.RotationController;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class ArenaSpookyRotationController implements MinecraftAccess {

   public void update(LivingEntity entity, boolean attackReady, float attackRange) {
      if (entity != null && minecraftClient.player != null && minecraftClient.world != null) {
         float currentYaw = minecraftClient.player.getYaw();
         float currentPitch = minecraftClient.player.getPitch();
         Vec3d position = minecraftClient.player.getEyePos();
         Box targetBounds = entity.getBoundingBox();
         Vec3d relativeMotionLead = new Vec3d(entity.getX() - entity.lastRenderX, 0.0, entity.getZ() - entity.lastRenderZ)
            .subtract(minecraftClient.player.getVelocity().x, 0.0, minecraftClient.player.getVelocity().z)
            .multiply((1.5D));
         Vec3d predictedAimOffset = new Vec3d(targetBounds.getCenter().x, targetBounds.minY + targetBounds.getLengthY() * (0.82D), targetBounds.getCenter().z).add(relativeMotionLead).subtract(position);
         boolean nearlyVertical = predictedAimOffset.x * predictedAimOffset.x + predictedAimOffset.z * predictedAimOffset.z < (0.029999999329447746D);
         double horizontalDistance = Math.max((0.05D), Math.hypot(predictedAimOffset.x, predictedAimOffset.z));
         ThreadLocalRandom random = ThreadLocalRandom.current();
         float distanceFactor = (float)MathHelper.clamp(horizontalDistance / (2.0D), (0.7D), 1.0);
         Vec3d pitchAimOffset = new Vec3d(targetBounds.getCenter().x, targetBounds.minY + targetBounds.getLengthY() * (0.87D), targetBounds.getCenter().z).subtract(position);
         float yawError = nearlyVertical ? 0.0F : MathHelper.wrapDegrees((float)Math.toDegrees(Math.atan2(-predictedAimOffset.x, predictedAimOffset.z)) - currentYaw);
         float pitchError = (float)(-Math.toDegrees(Math.atan2(pitchAimOffset.y, Math.max((0.7D), Math.hypot(pitchAimOffset.x, pitchAimOffset.z))))) - currentPitch;
         float targetAngularWidth = (float)Math.toDegrees(Math.atan2(targetBounds.getLengthX() * (0.5D), horizontalDistance));
         boolean yawCooldownReady = attackReady || CombatAttackHelper.isCooldownReady((-30L));
         boolean pitchCooldownReady = attackReady || CombatAttackHelper.isCooldownReady((-30L));
         float cooldownProgress = minecraftClient.player.getAttackCooldownProgress((0.5F));
         float noiseStrength = yawCooldownReady ? 0.0F : (0.55F) + (0.45F) * cooldownProgress + (0.2F);
         double noiseTime = System.currentTimeMillis() / (60.0D);
         float yawNoise = (
               (float)(Math.cos(noiseTime * (0.7D)) + Math.sin(noiseTime * (1.7D)) * (0.5D)) * (targetAngularWidth * (1.8F) + (7.0F))
                  + random.nextFloat((-4.0F), (4.0F))
            )
            * noiseStrength;
         float pitchNoise = (
               (float)(Math.sin(noiseTime * (0.55D)) + Math.cos(noiseTime * (1.3D)) * (0.4D)) * (6.0F) + random.nextFloat((-2.0F), 2.0F)
            )
            * noiseStrength;
         float yawBlend = MathHelper.clamp(
            (yawCooldownReady ? random.nextFloat((0.85F), 1.0F) : random.nextFloat((0.55F), (0.85F)) * (1.0F - cooldownProgress * (0.35F)))
               * (1.0F + Math.min(Math.abs(yawError), (90.0F)) / (300.0F))
               * distanceFactor,
            (0.1F),
            1.0F
         );
         float maximumYawStep = random.nextFloat((55.0F), (80.0F)) * distanceFactor;
         float yawStep = MathHelper.clamp((yawError + yawNoise) * yawBlend, -maximumYawStep, maximumYawStep);
         if (attackReady && !targetBounds.contains(position) && !targetBounds.raycast(position, position.add(CombatTargeting.direction(currentYaw + yawStep, currentPitch).multiply(attackRange + 1.0))).isPresent()) {
            yawStep = yawError;
         }

         float pitchStep = pitchCooldownReady
            ? pitchError * random.nextFloat((0.8F), (0.95F))
            : pitchNoise * (0.35F) + pitchError * (Math.abs(pitchError) > (18.0F) ? (0.3F) : (0.15F));
         if (!attackReady && CombatAttackHelper.isCooldownReady((-30L))) {
            yawStep *= (0.35F);
            pitchStep *= (0.35F);
         }

         RotationController.getInstance()
            .requestRotation(
               new RotationAngles(currentYaw + yawStep, MathHelper.clamp(currentPitch + pitchStep, (-89.0F), (89.0F))),
               new RotationLimits(nearlyVertical ? (8.0F) : (35.0F), (22.0F), (30.0F), (30.0F)),
               RotationController.ReturnMode.NONE
            );
      }
   }

   public void reset() {
   }
}
