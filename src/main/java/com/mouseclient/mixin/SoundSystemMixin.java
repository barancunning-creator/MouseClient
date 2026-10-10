package com.mouseclient.mixin;

import com.mouseclient.modules.impl.misc.BassBoost;
import com.mouseclient.modules.impl.render.NoRender;
import java.util.Map;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.client.sound.Channel.SourceManager;
import net.minecraft.client.sound.SoundSystem.PlayResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundSystem.class)
public abstract class SoundSystemMixin {
   @Shadow
   @Final
   private Map<SoundInstance, SourceManager> sources;

   @Shadow
   public abstract void stop(SoundInstance sound);

   @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)Lnet/minecraft/client/sound/SoundSystem$PlayResult;", at = @At("HEAD"), cancellable = true)
   private void muteSounds(SoundInstance sound, CallbackInfoReturnable<PlayResult> callback) {
      if (NoRender.getInstance().shouldMuteSound(sound)) {
         callback.setReturnValue(PlayResult.NOT_STARTED);
      }
   }

   @Inject(method = "tick(Z)V", at = @At("HEAD"))
   private void stopMutedPlayingSounds(boolean paused, CallbackInfo callback) {
      NoRender noRender = NoRender.getInstance();
      if (noRender.isEnabled()) {
         for (SoundInstance playingSound : this.sources.keySet()) {
            if (noRender.shouldMuteSound(playingSound)) {
               this.stop(playingSound);
            }
         }
      }
   }

   @Inject(method = "stop()V", at = @At("HEAD"))
   private void forgetBassChain(CallbackInfo callback) {
      BassBoost.resetSoundContext();
   }
}
