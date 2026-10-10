package com.mouseclient.mixin;

import com.mouseclient.modules.impl.misc.YoutubeModeModule;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public class ItemStackMixin {
   @Inject(method = "hasGlint()Z", at = @At("HEAD"), cancellable = true)
   private void stripFoilOnYtMode(CallbackInfoReturnable<Boolean> callback) {
      if (YoutubeModeModule.getInstance().isEnabled()) {
         callback.setReturnValue(false);
      }
   }
}
