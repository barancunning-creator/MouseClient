package com.mouseclient.mixin;

import com.mouseclient.events.impl.AttackEvent;
import com.mouseclient.modules.impl.combat.BackTrack;
import com.mouseclient.modules.impl.misc.EventBus;
import com.mouseclient.modules.impl.movement.AutoSprint;
import com.mouseclient.util.entity.MinecraftAccess;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin implements MinecraftAccess {
   @Redirect(method = "knockbackTarget(Lnet/minecraft/entity/Entity;FLnet/minecraft/util/math/Vec3d;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/Vec3d;multiply(DDD)Lnet/minecraft/util/math/Vec3d;"))
   private Vec3d keepAttackSprintVelocity(Vec3d position, double x, double y, double z) {
      return (Object)this == minecraftClient.player && AutoSprint.getInstance().preservesAttackSprint() ? position.multiply(1.0, y, 1.0) : position.multiply(x, y, z);
   }

   @Inject(method = "hasBlindnessEffect()Z", at = @At("HEAD"), cancellable = true)
   private void sprintThroughBlindness(CallbackInfoReturnable<Boolean> callback) {
      if ((Object)this == minecraftClient.player && AutoSprint.getInstance().ignoresBlindness()) {
         callback.setReturnValue(false);
      }
   }

   @Inject(method = "attack(Lnet/minecraft/entity/Entity;)V", at = @At("TAIL"))
   private void keepSprintAfterAttack(Entity entity, CallbackInfo callback) {
      if ((Object)this == minecraftClient.player && EventBus.getInstance().hasListeners(AttackEvent.class)) {
         EventBus.getInstance().post(new AttackEvent());
      }
   }

   @Inject(method = "attack(Lnet/minecraft/entity/Entity;)V", at = @At("HEAD"))
   private void trackOwnHitForBackTrack(Entity entity, CallbackInfo callback) {
      if ((Object)this == minecraftClient.player) {
         BackTrack.getInstance().trackAttackedEntity(entity);
      }
   }
}
