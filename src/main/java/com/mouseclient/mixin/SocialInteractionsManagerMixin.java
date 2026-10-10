package com.mouseclient.mixin;

import java.util.UUID;
import net.minecraft.client.network.SocialInteractionsManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SocialInteractionsManager.class)
public abstract class SocialInteractionsManagerMixin {
   @Inject(method = "isPlayerBlocked(Ljava/util/UUID;)Z", at = @At("HEAD"), cancellable = true)
   private void skipBlockingBlocklistFetch(UUID uuid, CallbackInfoReturnable<Boolean> callback) {
      callback.setReturnValue(false);
   }
}
