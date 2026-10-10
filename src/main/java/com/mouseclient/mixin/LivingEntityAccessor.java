package com.mouseclient.mixin;

import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
   public @Accessor("jumpingCooldown")
   void setNoJumpDelay(int noJumpDelay);

   public @Accessor("jumpingCooldown")
   int getNoJumpDelay();

   public @Accessor("jumping")
   boolean getJumping();

   public @Invoker("getJumpVelocity")
   float invokeGetJumpPower();
}
