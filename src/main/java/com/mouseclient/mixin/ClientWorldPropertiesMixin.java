package com.mouseclient.mixin;

import com.mouseclient.render.pipeline.CustomWorld;
import com.mouseclient.modules.impl.fun.FunModules;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ru.dreamix.fabricloader.VMBridge;

@Mixin(targets = "net/minecraft/client/world/ClientWorld$Properties")
public abstract class ClientWorldPropertiesMixin {

   @ModifyReturnValue(method = "getTimeOfDay()J", at = @At("RETURN"))
   private long forceWorldTime(long timeOfDay) {
      if (FunModules.DiscoSky.visualTime >= 0L) {
         return FunModules.DiscoSky.visualTime;
      }
      CustomWorld customWorld = CustomWorld.getInstance();
      return customWorld.isEnabled() && customWorld.fixedTime.getValue() ? timeOfDay - timeOfDay % (24000L) + (long)customWorld.timeOfDay.getValue() : timeOfDay;
   }

   static {
      VMBridge.identifyClass(ClientWorldPropertiesMixin.class, "tZhzwdn1");
   }
}
