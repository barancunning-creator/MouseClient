package com.mouseclient.mixin;

import com.mouseclient.events.impl.SharedHitBoxEvent;
import com.mouseclient.modules.impl.combat.AimAssist;
import com.mouseclient.modules.impl.combat.AttackAura;
import com.mouseclient.modules.impl.misc.EventBus;
import com.mouseclient.modules.impl.movement.PlayerMovementEvent;
import com.mouseclient.modules.impl.movement.MoveVectorEvent;
import com.mouseclient.modules.impl.player.NoPush;
import com.mouseclient.modules.impl.render.CameraFeature;
import com.mouseclient.modules.impl.render.FreeCamera;
import com.mouseclient.modules.impl.render.SeeInvisible;
import com.mouseclient.network.RotationController;
import com.mouseclient.util.entity.MinecraftAccess;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.dreamix.fabricloader.VMBridge;

@Mixin(Entity.class)
public abstract class EntityMixin implements MinecraftAccess {
   @Unique
   private static SharedHitBoxEvent sharedHitBoxEvent;
   @Unique
   private static boolean sharedHitBoxEventBusy;

   @Inject(method = "changeLookDirection(DD)V", at = @At("HEAD"), cancellable = true)
   private void steerFreeCamera(double yawDelta, double pitchDelta, CallbackInfo callback) {
      if ((Object)this == minecraftClient.player && FreeCamera.getInstance().isEnabled()) {
         FreeCamera.getInstance().rotateCamera(yawDelta, pitchDelta);
         callback.cancel();
      } else if ((Object)this == minecraftClient.player && CameraFeature.getInstance().handleLookInput(yawDelta, pitchDelta)) {
         callback.cancel();
      } else {
         if ((Object)this == minecraftClient.player && RotationController.getInstance().applyMouseInput(yawDelta, pitchDelta)) {
            callback.cancel();
         }
      }
   }

   @Inject(method = "changeLookDirection(DD)V", at = @At("RETURN"))
   private void steerAimAssist(double mouseYawDelta, double mousePitchDelta, CallbackInfo callback) {
      if ((Object)this == minecraftClient.player) {
         AimAssist.getInstance().applyMouseAim(mouseYawDelta, mousePitchDelta);
      }

      if ((Object)this == minecraftClient.player) {
         AttackAura.getInstance().updateLegitRotation();
      }
   }

   @ModifyVariable(method = "move(Lnet/minecraft/entity/MovementType;Lnet/minecraft/util/math/Vec3d;)V", at = @At("HEAD"), argsOnly = true)
   private Vec3d applyMoveEvent(Vec3d position) {
      if ((Object)this != minecraftClient.player) {
         return position;
      }

      if (EventBus.getInstance().hasListeners(MoveVectorEvent.class)) {
         MoveVectorEvent event = new MoveVectorEvent(position);
         EventBus.getInstance().post(event);
         position = event.getMovement();
      }

      if (!EventBus.getInstance().hasListeners(PlayerMovementEvent.class)) {
         return position;
      }

      PlayerMovementEvent movementEvent = new PlayerMovementEvent(position.x, position.y, position.z, minecraftClient.player.isOnGround());
      EventBus.getInstance().post(movementEvent);
      minecraftClient.player.setOnGround(movementEvent.isOnGround());
      if (movementEvent.isCancelled()) {
         minecraftClient.player.setVelocity(movementEvent.getX(), movementEvent.getY(), movementEvent.getZ());
      }

      return new Vec3d(movementEvent.getX(), movementEvent.getY(), movementEvent.getZ());
   }

   @Inject(method = "pushAwayFrom(Lnet/minecraft/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
   private void removeEntityPush(Entity entity, CallbackInfo callback) {
      if (NoPush.getInstance().blocksSource(("Сущности"))) {
         if ((Object)this == minecraftClient.player || entity == minecraftClient.player) {
            callback.cancel();
         }
      }
   }

   @Inject(method = "adjustMovementForPiston(Lnet/minecraft/util/math/Vec3d;)Lnet/minecraft/util/math/Vec3d;", at = @At("HEAD"), cancellable = true)
   private void removePistonPush(Vec3d position, CallbackInfoReturnable<Vec3d> callback) {
      if ((Object)this == minecraftClient.player && NoPush.getInstance().blocksSource(("Блоки"))) {
         callback.setReturnValue(Vec3d.ZERO);
      }
   }

   @Redirect(method = "updateMovementInFluid(Lnet/minecraft/registry/tag/TagKey;D)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;isPushedByFluids()Z"))
   private boolean removeFluidPush(Entity entity, TagKey<Fluid> tag, double speed) {
      return entity != minecraftClient.player || (tag == FluidTags.WATER ? !NoPush.getInstance().blocksSource(("Вода")) : !NoPush.getInstance().blocksSource(("Лава")))
         ? entity.isPushedByFluids()
         : false;
   }

   @Inject(method = "isInvisibleTo(Lnet/minecraft/entity/player/PlayerEntity;)Z", at = @At("HEAD"), cancellable = true)
   private void showInvisibleEntities(PlayerEntity player, CallbackInfoReturnable<Boolean> callback) {
      if (player == minecraftClient.player && (Object)this instanceof AbstractClientPlayerEntity && SeeInvisible.getInstance().isEnabled()) {
         callback.setReturnValue(false);
      }
   }

   @Inject(method = "getBoundingBox()Lnet/minecraft/util/math/Box;", at = @At("RETURN"), cancellable = true)
   private void applyEntityHitBox(CallbackInfoReturnable<Box> callback) {
      if ((Object)this instanceof LivingEntity
         && minecraftClient.player != null
         && (Object)this != minecraftClient.player
         && EventBus.getInstance().hasListeners(SharedHitBoxEvent.class)) {
         boolean reuseEvent = !sharedHitBoxEventBusy && minecraftClient.isOnThread();
         if (reuseEvent && sharedHitBoxEvent == null) {
            sharedHitBoxEvent = new SharedHitBoxEvent(null, 0.0F);
         }

         SharedHitBoxEvent event = reuseEvent ? sharedHitBoxEvent : new SharedHitBoxEvent((Entity)(Object)this, 0.0F);
         float expansion;
         if (reuseEvent) {
            sharedHitBoxEventBusy = true;

            try {
               event.setEntity((Entity)(Object)this);
               event.setExpansion(0.0F);
               EventBus.getInstance().post(event);
               expansion = event.getExpansion();
            } finally {
               event.setEntity(null);
               sharedHitBoxEventBusy = false;
            }
         } else {
            EventBus.getInstance().post(event);
            expansion = event.getExpansion();
         }

         if (expansion != 0.0F) {
            Box originalBounds = (Box)callback.getReturnValue();
            callback.setReturnValue(new Box(originalBounds.minX - expansion, originalBounds.minY, originalBounds.minZ - expansion, originalBounds.maxX + expansion, originalBounds.maxY, originalBounds.maxZ + expansion));
         }
      }
   }

   static {
      VMBridge.identifyClass(EntityMixin.class, "qY92U9te");
   }
}
