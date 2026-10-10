package com.mouseclient.mixin;

import com.mouseclient.modules.impl.render.Tags;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class MinecraftEntityOutlineMixin {
   @Inject(method = "hasOutline(Lnet/minecraft/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
   private void forcePlayersGlowMask(Entity entity, CallbackInfoReturnable<Boolean> callback) {
      if (Tags.getInstance().isOutlineShaderEnabled()) {
         callback.setReturnValue(entity instanceof AbstractClientPlayerEntity player && Tags.getInstance().shouldDisplayPlayer(player));
      }
   }
}
