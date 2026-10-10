package com.mouseclient.mixin;

import com.mouseclient.modules.impl.combat.ItemHelper;
import com.mouseclient.modules.impl.player.ServerHelper;
import net.minecraft.client.Mouse;
import net.minecraft.entity.player.PlayerInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Mouse.class)
public class MouseMixin {
   @Redirect(method = "onMouseScroll(JDD)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerInventory;setSelectedSlot(I)V"))
   private void skipBlockedScrollSlots(PlayerInventory inventory, int requestedSlot) {
      inventory.setSelectedSlot(ItemHelper.getInstance().isUsingBoundItem() ? inventory.getSelectedSlot() : ServerHelper.getInstance().scrollSlot(inventory.getSelectedSlot(), requestedSlot));
   }
}
