package com.mouseclient.util.entity;

import com.mouseclient.network.CombatAttackHelper;
import com.mouseclient.network.RotationLimits;
import com.mouseclient.network.RotationController;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class AimRotationController implements MinecraftAccess, TargetRotationController {
   private final YawAimSmoother yawSmoother = new YawAimSmoother();
   private final PitchAimSmoother pitchSmoother = new PitchAimSmoother();
   private final RotationStepAccumulator yawQuantizer = new RotationStepAccumulator();
   private final RotationStepAccumulator pitchQuantizer = new RotationStepAccumulator();
   private int lastTargetTick;
   private boolean correctionRequired;

   @Override
   public void reset() {
      this.yawSmoother.reset();
      this.pitchSmoother.reset();
      this.yawQuantizer.reset();
      this.pitchQuantizer.reset();
   }

   @Override
   public void updateTarget(LivingEntity entity, boolean attackReady, float attackRange) {
      if (entity != null && minecraftClient.player != null) {
         this.lastTargetTick = minecraftClient.player.age;
         this.correctionRequired = attackReady || CombatAttackHelper.isCooldownReady((-150L));
         if (attackReady && CombatTargeting.distanceToBounds(entity) < attackRange) {
            this.yawSmoother.markAttack();
         }

         RotationController.getInstance()
            .requestRotation(
               new RotationAngles(minecraftClient.player.getYaw(), minecraftClient.player.getPitch()),
               new RotationLimits(0.0F, 0.0F, (60.0F), (24.0F)),
               RotationController.ReturnMode.NONE
            );
      }
   }

   @Override
   public void updateFrame(LivingEntity entity, float tickDelta) {
      if (entity != null && minecraftClient.player != null && RotationController.getInstance().isActive() && minecraftClient.player.age - this.lastTargetTick <= 1) {
         float currentYaw = minecraftClient.player.getYaw();
         float currentPitch = minecraftClient.player.getPitch();
         Vec3d position = minecraftClient.player.getCameraPosVec(tickDelta);
         boolean needsRaycastCorrection = this.correctionRequired
            && !entity.getBoundingBox()
               .offset(entity.getLerpedPos(tickDelta).subtract(entity.getEntityPos()))
               .expand(entity.getTargetingMargin())
               .raycast(position, position.add(CombatTargeting.direction(currentYaw, currentPitch).multiply((8.0D))))
               .isPresent();
         minecraftClient.player.setYaw(minecraftClient.player.headYaw = currentYaw + this.yawQuantizer.quantize(this.yawSmoother.update(entity, tickDelta, currentYaw, currentPitch, needsRaycastCorrection)));
         minecraftClient.player.setPitch(MathHelper.clamp(currentPitch + this.pitchQuantizer.quantize(this.pitchSmoother.update(entity, tickDelta, currentPitch, needsRaycastCorrection)), (-90.0F), (90.0F)));
      }
   }
}
