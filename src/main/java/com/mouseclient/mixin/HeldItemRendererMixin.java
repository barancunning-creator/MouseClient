package com.mouseclient.mixin;

import com.mouseclient.events.impl.HeldItemRenderEvent;
import com.mouseclient.modules.impl.combat.ItemHelper;
import com.mouseclient.modules.impl.misc.EventBus;
import com.mouseclient.modules.impl.render.CameraFeature;
import com.mouseclient.modules.impl.render.SwordAnimations;
import com.mouseclient.modules.impl.render.ViewModel;
import com.mouseclient.network.RotationController;
import com.mouseclient.render.pipeline.MouseClientHandOutlineBlur;
import com.mouseclient.util.entity.MinecraftAccess;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.joml.Quaternionfc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.dreamix.fabricloader.VMBridge;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin implements MinecraftAccess {
   @Shadow
   private ItemStack mainHand;
   @Shadow
   private ItemStack offHand;

   @Shadow
   protected abstract void swingArm(float swingProgress, MatrixStack matrices, int armSign, Arm arm);

   @WrapOperation(method = "updateHeldItems()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getMainHandStack()Lnet/minecraft/item/ItemStack;"))
   private ItemStack hideHelperItemInHand(ClientPlayerEntity player, Operation<ItemStack> operation) {
      return ItemHelper.getInstance().displayedHotbarSlot() != -1 ? player.getInventory().getStack(ItemHelper.getInstance().displayedHotbarSlot()) : (ItemStack)operation.call(player);
   }

   @Inject(method = "renderFirstPersonItem(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/util/Hand;FLnet/minecraft/item/ItemStack;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;push()V", ordinal = 0, shift = Shift.AFTER))
   private void hookRenderItem(
      AbstractClientPlayerEntity player,
      float tickProgress,
      float pitch,
      Hand hand,
      float swingProgress,
      ItemStack stack,
      float equipProgress,
      MatrixStack matrices,
      OrderedRenderCommandQueue queue,
      int light,
      CallbackInfo callback
   ) {
      if (EventBus.getInstance().hasListeners(HeldItemRenderEvent.class)) {
         EventBus.getInstance()
            .post(new HeldItemRenderEvent(matrices, stack, hand, hand == Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite(), swingProgress));
      }
   }

   @Redirect(
      method = "renderFirstPersonItem(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/util/Hand;FLnet/minecraft/item/ItemStack;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/item/HeldItemRenderer;swingArm(FLnet/minecraft/client/util/math/MatrixStack;ILnet/minecraft/util/Arm;)V", ordinal = 2)
   )
   private void redirectSwingArm(HeldItemRenderer renderer, float swingProgress, MatrixStack matrices, int armSign, Arm arm, @Local(argsOnly = true, ordinal = 3) float equipProgress) {
      ClientPlayerEntity player = minecraftClient.player;
      if (player instanceof AbstractClientPlayerEntity) {
         if (SwordAnimations.getInstance().shouldAnimateArm(arm)) {
            matrices.translate(-armSign * (0.56F), (0.52F) + equipProgress * (0.6F) - CameraFeature.getInstance().freeLookProgress() * (0.6F), (0.72F));
            SwordAnimations.getInstance().applySwingTransform(matrices, armSign, swingProgress);
         } else {
            this.swingArm(swingProgress, matrices, armSign, arm);
         }

         float handScale = SwordAnimations.getInstance().handScale(arm);
         matrices.scale(handScale, handScale, handScale);
      } else {
         this.swingArm(swingProgress, matrices, armSign, arm);
      }
   }

   @ModifyVariable(method = "renderFirstPersonItem(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/util/Hand;FLnet/minecraft/item/ItemStack;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V", at = @At("HEAD"), argsOnly = true, ordinal = 3)
   private float lowerHandOnFreeLook(float equipProgress) {
      return Math.max(equipProgress, CameraFeature.getInstance().freeLookProgress());
   }

   @WrapOperation(method = "applyEatOrDrinkTransformation(Lnet/minecraft/client/util/math/MatrixStack;FLnet/minecraft/util/Arm;Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/player/PlayerEntity;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;translate(FFF)V", ordinal = 0))
   private void removeUseWobble(MatrixStack matrices, float x, float y, float z, Operation<Void> operation) {
      if (!ViewModel.getInstance().removesUseAnimation()) {
         operation.call(matrices, x, y, z);
      }
   }

   @WrapOperation(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;multiply(Lorg/joml/Quaternionfc;)V"))
   private void freezeHandSway(MatrixStack matrices, Quaternionfc quaternion, Operation<Void> operation) {
      if (!ViewModel.getInstance().freezesHandMotion()) {
         operation.call(matrices, quaternion);
      }
   }

   @Redirect(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getPitch(F)F"))
   private float steerHandPitch(ClientPlayerEntity player, float tickProgress) {
      return RotationController.getInstance().isActive() ? RotationController.getInstance().cameraPitch() : player.getPitch(tickProgress);
   }

   @Redirect(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getYaw(F)F"))
   private float steerHandYaw(ClientPlayerEntity player, float tickProgress) {
      return RotationController.getInstance().isActive() ? RotationController.getInstance().cameraYaw() : player.getYaw(tickProgress);
   }

   @Inject(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V", at = @At("HEAD"))
   private void captureWorldBeforeHand(CallbackInfo callback) {
      MouseClientHandOutlineBlur.getInstance().beginHandCapture();
   }

   @Inject(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V", at = @At("TAIL"))
   private void captureHandForOutline(CallbackInfo callback) {
      MouseClientHandOutlineBlur.getInstance().captureHandDepth();
   }

   static {
      VMBridge.identifyClass(HeldItemRendererMixin.class, "MhxZBnDQ");
   }
}
