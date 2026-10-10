package com.mouseclient.util.entity;

import net.minecraft.entity.LivingEntity;

public interface TargetRotationController {
   public default boolean shouldSkipAttack(LivingEntity entity, float attackRange) {
      return false;
   }

   public void updateFrame(LivingEntity entity, float tickDelta);

   public void updateTarget(LivingEntity entity, boolean attackReady, float attackRange);

   public void reset();
}
