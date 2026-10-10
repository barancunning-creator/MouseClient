package com.mouseclient.mixin;

import com.mouseclient.gui.SlotClickEvent;
import com.mouseclient.modules.impl.combat.CrystalAura;
import com.mouseclient.modules.impl.combat.MaceHelper;
import com.mouseclient.modules.impl.misc.EventBus;
import com.mouseclient.modules.impl.movement.StopUsingItemEvent;
import com.mouseclient.modules.impl.player.ServerHelper;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {
   @Inject(method = "clickSlot(IIILnet/minecraft/screen/slot/SlotActionType;Lnet/minecraft/entity/player/PlayerEntity;)V", at = @At("HEAD"), cancellable = true)
   private void clickSlot(int syncId, int slotId, int button, SlotActionType action, PlayerEntity player, CallbackInfo callback) {
      if (EventBus.getInstance().hasListeners(SlotClickEvent.class)) {
         SlotClickEvent event = new SlotClickEvent(syncId, slotId, button, action);
         EventBus.getInstance().post(event);
         if (event.isCancelled()) {
            callback.cancel();
         }
      }
   }

   @Inject(method = "interactBlock(Lnet/minecraft/client/network/ClientPlayerEntity;Lnet/minecraft/util/Hand;Lnet/minecraft/util/hit/BlockHitResult;)Lnet/minecraft/util/ActionResult;", at = @At("HEAD"), cancellable = true)
   private void failAnchorUse(ClientPlayerEntity player, Hand hand, BlockHitResult hitResult, CallbackInfoReturnable<ActionResult> callback) {
      if (MaceHelper.getInstance().isEnabled() || ServerHelper.getInstance().isEnabled()) {
         if ((CrystalAura.getInstance().isEnabled() && CrystalAura.getInstance().anchorAura.getValue())
             || com.mouseclient.modules.impl.combat.AnchorAura.getInstance().isEnabled()) {
            return;
         }

         if (player.getEntityWorld().getBlockState(hitResult.getBlockPos()).isOf(Blocks.RESPAWN_ANCHOR)) {
            callback.setReturnValue(ActionResult.PASS);
         }
      }
   }

   @Inject(method = "stopUsingItem(Lnet/minecraft/entity/player/PlayerEntity;)V", at = @At("HEAD"), cancellable = true)
   private void stopUseItem(PlayerEntity player, CallbackInfo callback) {
      if (EventBus.getInstance().hasListeners(StopUsingItemEvent.class)) {
         StopUsingItemEvent event = new StopUsingItemEvent();
         EventBus.getInstance().post(event);
         if (event.isCancelled()) {
            callback.cancel();
         }
      }
   }
}
