package com.mouseclient.mixin;

import com.mouseclient.modules.impl.misc.YoutubeModeModule;
import com.mouseclient.modules.impl.render.CameraFeature;
import com.mouseclient.modules.impl.render.NoRender;
import com.mouseclient.util.entity.MinecraftAccess;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.SkinTextures;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.dreamix.fabricloader.VMBridge;

@Mixin(AbstractClientPlayerEntity.class)
public class AbstractClientPlayerMixin implements MinecraftAccess {

   @Inject(method = "getFovMultiplier(ZF)F", at = @At("HEAD"), cancellable = true)
   private void overrideFovModifier(boolean firstPerson, float fovEffectScale, CallbackInfoReturnable<Float> callback) {
      if (CameraFeature.getInstance().zoomFovMultiplier() > 0.0F) {
         callback.setReturnValue(CameraFeature.getInstance().zoomFovMultiplier());
      } else if (NoRender.getInstance().isOverlayDisabled(("Эффекты FOV"))) {
         callback.setReturnValue(1.0F);
      }
   }

   @Inject(method = "getSkin()Lnet/minecraft/entity/player/SkinTextures;", at = @At("HEAD"), cancellable = true)
   private void wearMySkinOnEveryone(CallbackInfoReturnable<SkinTextures> callback) {
      if (YoutubeModeModule.getInstance().isEnabled() && minecraftClient.player != null && (Object)this != minecraftClient.player) {
         callback.setReturnValue(minecraftClient.player.getSkin());
      }
   }

   static {
      VMBridge.identifyClass(AbstractClientPlayerMixin.class, "NiqjLtOh");
   }
}
