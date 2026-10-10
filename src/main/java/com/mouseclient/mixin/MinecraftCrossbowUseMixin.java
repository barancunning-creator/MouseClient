package com.mouseclient.mixin;

import com.mouseclient.util.entity.ProjectileHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.CrossbowItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class MinecraftCrossbowUseMixin {
   @Inject(method = "doItemUse()V", at = @At("HEAD"), cancellable = true)
   private void deferCrossbowShotUntilAimed(CallbackInfo callback) {
      if (MinecraftClient.getInstance().player != null
         && MinecraftClient.getInstance().player.getMainHandStack().getItem() instanceof CrossbowItem
         && CrossbowItem.isCharged(MinecraftClient.getInstance().player.getMainHandStack())) {
         if (ProjectileHelper.getInstance().deferCrossbowUse()) {
            callback.cancel();
         }
      }
   }
}
