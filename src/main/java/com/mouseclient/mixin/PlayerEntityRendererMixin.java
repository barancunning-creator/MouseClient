package com.mouseclient.mixin;

import com.mouseclient.modules.impl.render.Tags;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.entity.PlayerLikeEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {
   @Inject(method = "hasLabel(Lnet/minecraft/entity/PlayerLikeEntity;D)Z", at = @At("HEAD"), cancellable = true)
   private void hideVanillaNameTag(PlayerLikeEntity entity, double squaredDistanceToCamera, CallbackInfoReturnable<Boolean> callback) {
      if (Tags.getInstance().isEnabled() && entity instanceof AbstractClientPlayerEntity) {
         callback.setReturnValue(false);
      }
   }
}
