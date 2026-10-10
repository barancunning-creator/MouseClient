package com.mouseclient.mixin;

import com.mouseclient.modules.impl.combat.HitBoxes;
import net.minecraft.client.render.debug.EntityHitboxDebugRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityHitboxDebugRenderer.class)
public abstract class EntityHitboxDebugRendererMixin {
   @Inject(method = "render(DDDLnet/minecraft/world/debug/DebugDataStore;Lnet/minecraft/client/render/Frustum;F)V", at = @At("HEAD"))
   private void markHitboxDebugPassBegin(CallbackInfo callback) {
      HitBoxes.getInstance().debugRenderPass = true;
   }

   @Inject(method = "render(DDDLnet/minecraft/world/debug/DebugDataStore;Lnet/minecraft/client/render/Frustum;F)V", at = @At("RETURN"))
   private void markHitboxDebugPassEnd(CallbackInfo callback) {
      HitBoxes.getInstance().debugRenderPass = false;
   }
}
