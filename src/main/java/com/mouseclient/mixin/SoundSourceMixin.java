package com.mouseclient.mixin;

import com.mouseclient.modules.impl.misc.BassBoost;
import net.minecraft.client.sound.Source;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Source.class)
public class SoundSourceMixin {
   @Shadow
   @Final
   private int pointer;

   @Inject(method = "play()V", at = @At("HEAD"))
   private void attachBassBoost(CallbackInfo callback) {
      BassBoost.attachToSource(this.pointer);
   }
}
