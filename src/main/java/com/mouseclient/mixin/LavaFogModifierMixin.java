package com.mouseclient.mixin;

import com.mouseclient.modules.impl.render.NoRender;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.fog.FogData;
import net.minecraft.client.render.fog.LavaFogModifier;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.dreamix.fabricloader.VMBridge;

@Mixin(LavaFogModifier.class)
public abstract class LavaFogModifierMixin {

   @Inject(method = "applyStartEndModifier(Lnet/minecraft/client/render/fog/FogData;Lnet/minecraft/client/render/Camera;Lnet/minecraft/client/world/ClientWorld;FLnet/minecraft/client/render/RenderTickCounter;)V", at = @At("TAIL"))
   private void removeLavaFog(FogData fogData, Camera camera, ClientWorld world, float unusedDistance, RenderTickCounter tickCounter, CallbackInfo callback) {
      if (NoRender.getInstance().isOverlayDisabled(("Оверлей лавы"))) {
         fogData.environmentalStart = (-8.0F);
         fogData.environmentalEnd = (64.0F);
         fogData.renderDistanceStart = (-8.0F);
         fogData.renderDistanceEnd = (64.0F);
      }
   }

   static {
      VMBridge.identifyClass(LavaFogModifierMixin.class, "ID5njDBO");
   }
}
