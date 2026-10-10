package com.mouseclient.mixin;

import com.mouseclient.events.impl.PotionSplashEvent;
import com.mouseclient.modules.impl.misc.EventBus;
import com.mouseclient.util.time.SpecialPotion;
import com.mouseclient.util.time.PotionEffectsHud;
import com.mouseclient.util.time.PlayerSpecialPotionTracker;
import net.minecraft.client.world.WorldEventHandler;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldEventHandler.class)
public class WorldEventHandlerMixin {
   @Inject(method = {"processWorldEvent", "processGlobalEvent"}, at = @At("HEAD"))
   private void firePotionSplash(int eventId, BlockPos position, int potionColor, CallbackInfo callback) {
      if (eventId == 2002 || eventId == 2007) {
         SpecialPotion specialPotion = SpecialPotion.fromNearbyPotion(position);
         if (specialPotion == null) {
            specialPotion = SpecialPotion.fromColor(potionColor);
         }

         if (specialPotion != null) {
            PotionEffectsHud.recordNearbySplash(specialPotion, position);
            PlayerSpecialPotionTracker.recordSplash(specialPotion, position);
         }

         if (EventBus.getInstance().hasListeners(PotionSplashEvent.class)) {
            EventBus.getInstance().post(new PotionSplashEvent(potionColor, position));
         }
      }
   }
}
