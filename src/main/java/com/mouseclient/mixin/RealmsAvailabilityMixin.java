package com.mouseclient.mixin;

import java.util.concurrent.CompletableFuture;
import net.minecraft.client.realms.RealmsAvailability;
import net.minecraft.client.realms.RealmsAvailability.Info;
import net.minecraft.client.realms.RealmsAvailability.Type;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RealmsAvailability.class)
public abstract class RealmsAvailabilityMixin {
   @Inject(method = "checkInternal()Ljava/util/concurrent/CompletableFuture;", at = @At("HEAD"), cancellable = true)
   private static void forceRealmsUnavailable(CallbackInfoReturnable<CompletableFuture<Info>> callback) {
      callback.setReturnValue(CompletableFuture.completedFuture(new Info(Type.INCOMPATIBLE_CLIENT)));
   }
}
