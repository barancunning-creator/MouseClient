package com.mouseclient.mixin;

import com.mouseclient.modules.impl.render.NoRender;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.dreamix.fabricloader.VMBridge;

@Mixin(targets = "net/minecraft/client/gui/hud/InGameHud$HeartType")
public abstract class HudHeartTypeMixin {

   @Inject(method = "fromPlayerState(Lnet/minecraft/entity/player/PlayerEntity;)Lnet/minecraft/client/gui/hud/InGameHud$HeartType;", at = @At("RETURN"), cancellable = true)
   private static void removeWitherHearts(PlayerEntity player, CallbackInfoReturnable<Object> callback) {
      if (NoRender.getInstance().isOverlayDisabled(("Иссушающие сердца")) && callback.getReturnValue() instanceof Enum heartType && heartType.name().equals(("WITHERED"))) {
         callback.setReturnValue(Enum.valueOf(heartType.getClass(), ("NORMAL")));
      }
   }

   static {
      VMBridge.identifyClass(HudHeartTypeMixin.class, "NPAHNzJf");
   }
}
