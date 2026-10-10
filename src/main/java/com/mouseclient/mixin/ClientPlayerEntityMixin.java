package com.mouseclient.mixin;

import com.mouseclient.access.GroundTickTracker;

import com.mouseclient.gui.CloseScreenEvent;
import com.mouseclient.modules.impl.combat.BlockSlot;
import com.mouseclient.modules.impl.combat.NoEntityTrace;
import com.mouseclient.modules.impl.misc.ChestStealer;
import com.mouseclient.modules.impl.misc.EventBus;
import com.mouseclient.modules.impl.movement.Spider;
import com.mouseclient.modules.impl.movement.ItemUseSlowdownEvent;
import com.mouseclient.modules.impl.player.NoPush;
import com.mouseclient.network.PlayerTickEvent;
import com.mouseclient.network.CombatAttackHelper;
import com.mouseclient.network.RotationController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.dreamix.fabricloader.VMBridge;

@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin implements GroundTickTracker {
   @Unique
   private int ticksOnGround;

   @Inject(method = "tick()V", at = @At("HEAD"))
   private void tick(CallbackInfo callback) {
      CombatAttackHelper.tick();
      if (EventBus.getInstance().hasListeners(PlayerTickEvent.class)) {
         EventBus.getInstance().post(new PlayerTickEvent());
      }
   }

   @Inject(method = "sendMovementPackets()V", at = @At("HEAD"))
   private void countGroundTicks(CallbackInfo callback) {
      if (((ClientPlayerEntity)(Object)this).isOnGround()) {
         this.ticksOnGround++;
      } else {
         this.ticksOnGround = 0;
      }
   }

   @Inject(method = "sendMovementPackets()V", at = @At("HEAD"))
   private void spiderPreMotion(CallbackInfo callback) {
      Spider.getInstance().applyQueuedJump();
   }

   @Override
   public int ticksOnGround() {
      return this.ticksOnGround;
   }

   @Inject(method = "closeHandledScreen()V", at = @At("HEAD"), cancellable = true)
   private void closeContainer(CallbackInfo callback) {
      if (MinecraftClient.getInstance().currentScreen == null) {
         callback.cancel();
      } else if (EventBus.getInstance().hasListeners(CloseScreenEvent.class)) {
         CloseScreenEvent event = new CloseScreenEvent(MinecraftClient.getInstance().currentScreen);
         EventBus.getInstance().post(event);
         if (event.isCancelled()) {
            callback.cancel();
         }
      }
   }

   @Inject(method = "pushOutOfBlocks(DD)V", at = @At("HEAD"), cancellable = true)
   private void removeBlockPush(double x, double z, CallbackInfo callback) {
      if (NoPush.getInstance().blocksSource(("Блоки"))) {
         callback.cancel();
      }
   }

   @Inject(method = "getActiveItemSpeedMultiplier()F", at = @At("HEAD"), cancellable = true)
   private void liftItemUseSlow(CallbackInfoReturnable<Float> callback) {
      if (EventBus.getInstance().hasListeners(ItemUseSlowdownEvent.class)) {
         ItemUseSlowdownEvent event = new ItemUseSlowdownEvent();
         EventBus.getInstance().post(event);
         if (event.isCancelled()) {
            callback.setReturnValue(1.0F);
         }
      }
   }

   @Inject(method = "getCrosshairTarget(FLnet/minecraft/entity/Entity;)Lnet/minecraft/util/hit/HitResult;", at = @At("HEAD"), cancellable = true)
   private void traceThroughEntities(float tickProgress, Entity entity, CallbackInfoReturnable<HitResult> callback) {
      if (NoEntityTrace.getInstance().isEnabled() || ChestStealer.getInstance().isEnabled() && ChestStealer.getInstance().disableHitboxes.getValue()) {
         callback.setReturnValue(entity.raycast(((ClientPlayerEntity)(Object)this).getBlockInteractionRange(), tickProgress, false));
      }
   }

   @Inject(method = "dropSelectedItem(Z)Z", at = @At("HEAD"), cancellable = true)
   private void blockSelectedSlotDrop(boolean entireStack, CallbackInfoReturnable<Boolean> callback) {
      if (BlockSlot.getInstance().protectsHotbarSlot(((ClientPlayerEntity)(Object)this).getInventory().getSelectedSlot())) {
         callback.setReturnValue(false);
      }
   }

   @Redirect(method = "tickMovementInput()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getPitch()F"))
   private float steerHandBobPitch(ClientPlayerEntity player) {
      return RotationController.getInstance().isActive() ? RotationController.getInstance().cameraPitch() : player.getPitch();
   }

   @Redirect(method = "tickMovementInput()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getYaw()F"))
   private float steerHandBobYaw(ClientPlayerEntity player) {
      return RotationController.getInstance().isActive() ? RotationController.getInstance().cameraYaw() : player.getYaw();
   }

   static {
      VMBridge.identifyClass(ClientPlayerEntityMixin.class, "ZdXUcnyB");
   }
}
