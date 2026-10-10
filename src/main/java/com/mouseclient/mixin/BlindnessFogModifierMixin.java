package com.mouseclient.mixin;

import com.mouseclient.modules.impl.render.NoRender;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.fog.BlindnessEffectFogModifier;
import net.minecraft.client.render.fog.FogData;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.dreamix.fabricloader.VMBridge;

@Mixin(BlindnessEffectFogModifier.class)
public abstract class BlindnessFogModifierMixin {

   @Inject(method = "applyStartEndModifier(Lnet/minecraft/client/render/fog/FogData;Lnet/minecraft/client/render/Camera;Lnet/minecraft/client/world/ClientWorld;FLnet/minecraft/client/render/RenderTickCounter;)V", at = @At("TAIL"))
   private void removeBlindnessFog(FogData fogData, Camera camera, ClientWorld world, float environmentalStart, RenderTickCounter tickCounter, CallbackInfo callback) {
      if (NoRender.getInstance().isOverlayDisabled(("Туман слепоты"))) {
         fogData.environmentalStart = environmentalStart;
         fogData.environmentalEnd = environmentalStart;
         fogData.skyEnd = 1.0F;
         fogData.cloudEnd = 1.0F;
      }
   }

   static {
      VMBridge.identifyClass(BlindnessFogModifierMixin.class, "AqV0KMJ4");
   }
}
