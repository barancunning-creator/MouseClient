package com.mouseclient.mixin;

import com.mouseclient.render.pipeline.CustomWorld;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ru.dreamix.fabricloader.VMBridge;

@Mixin(World.class)
public abstract class WorldWeatherMixin {

   @ModifyReturnValue(method = "getRainGradient(F)F", at = @At("RETURN"))
   private float forceRainLevel(float gradient) {
      if (!((Object)this instanceof ClientWorld)) {
         return gradient;
      } else {
         CustomWorld customWorld = CustomWorld.getInstance();
         if (!customWorld.isEnabled() || customWorld.weather.isSelected(("Выкл"))) {
            return gradient;
         } else {
            return customWorld.weather.isSelected(("Ясно")) ? 0.0F : 1.0F;
         }
      }
   }

   @ModifyReturnValue(method = "getThunderGradient(F)F", at = @At("RETURN"))
   private float forceThunderLevel(float gradient) {
      if (!((Object)this instanceof ClientWorld)) {
         return gradient;
      } else {
         CustomWorld customWorld = CustomWorld.getInstance();
         if (!customWorld.isEnabled() || customWorld.weather.isSelected(("Выкл"))) {
            return gradient;
         } else {
            return customWorld.weather.isSelected(("Гроза")) ? 1.0F : 0.0F;
         }
      }
   }

   static {
      VMBridge.identifyClass(WorldWeatherMixin.class, "9l5yDGd0");
   }
}
