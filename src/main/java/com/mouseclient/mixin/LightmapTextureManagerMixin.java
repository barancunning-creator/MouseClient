package com.mouseclient.mixin;

import com.mouseclient.modules.impl.render.Brightness;
import com.mouseclient.modules.impl.render.NoRender;
import com.mouseclient.render.pipeline.CustomWorld;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.dreamix.fabricloader.VMBridge;

@Mixin(LightmapTextureManager.class)
public abstract class LightmapTextureManagerMixin {

   @Inject(method = "getDarkness(Lnet/minecraft/entity/LivingEntity;FF)F", at = @At("HEAD"), cancellable = true)
   private void removeDarkness(LivingEntity entity, float factor, float tickProgress, CallbackInfoReturnable<Float> callback) {
      if (NoRender.getInstance().isOverlayDisabled(("Темнота"))) {
         callback.setReturnValue(0.0F);
      }
   }

   @ModifyExpressionValue(method = "update(F)V", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(FF)F"))
   private float boostWorldBrightness(float brightness) {
      CustomWorld customWorld = CustomWorld.getInstance();
      return brightness
         * (
            Brightness.getInstance().isEnabled() && Brightness.getInstance().lighting.isSelected(("Гамма"))
               ? Brightness.getInstance().gammaStrength.getValue()
               : 1.0F
         )
         * (customWorld.isEnabled() ? customWorld.lighting.getValue() : 1.0F);
   }

   static {
      VMBridge.identifyClass(LightmapTextureManagerMixin.class, "4fxSAVJ0");
   }
}
