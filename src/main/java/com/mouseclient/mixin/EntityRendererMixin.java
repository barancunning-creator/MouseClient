package com.mouseclient.mixin;

import com.mouseclient.modules.impl.render.Tags;
import com.mouseclient.modules.impl.render.WardenEsp;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
   @Inject(method = "hasLabel(Lnet/minecraft/entity/Entity;D)Z", at = @At("HEAD"), cancellable = true)
   private void hideVanillaNameTag(Entity entity, double squaredDistanceToCamera, CallbackInfoReturnable<Boolean> callback) {
      if (Tags.getInstance().isEnabled() && entity instanceof AbstractClientPlayerEntity) {
         callback.setReturnValue(false);
      }
   }

   @Inject(method = "shouldRender(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/render/Frustum;DDD)Z", at = @At("HEAD"), cancellable = true)
   private void hideWardenHologram(Entity entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> callback) {
      if (WardenEsp.getInstance().shouldHideVanillaLabel(entity)) {
         callback.setReturnValue(false);
      }
   }
}
