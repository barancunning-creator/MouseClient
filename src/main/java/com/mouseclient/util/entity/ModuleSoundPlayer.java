package com.mouseclient.util.entity;

import com.mouseclient.mixin.ClientCommonNetworkHandlerAccessor;
import com.mouseclient.modules.impl.render.HudSettingsModule;
import java.io.BufferedInputStream;
import java.util.HashMap;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.FloatControl.Type;
import net.minecraft.client.gui.screen.world.LevelLoadingScreen;

public class ModuleSoundPlayer implements MinecraftAccess {
   private static final HashMap<String, Clip[]> clipPools = new HashMap<>();

   public static void play(String soundName) {
      if (minecraftClient.world != null
         && minecraftClient.player != null
         && !(minecraftClient.currentScreen instanceof LevelLoadingScreen)
         && ((ClientCommonNetworkHandlerAccessor)(Object)minecraftClient.player.networkHandler).connection().isOpen()
         && HudSettingsModule.getInstance().moduleSounds.getValue()
         && !(HudSettingsModule.getInstance().soundVolume.getValue() <= 0.0F)) {
         if (!clipPools.containsKey(soundName)) {
            Clip[] clipPool = new Clip[2];
            clipPools.put(soundName, clipPool);
            Thread loaderThread = new Thread(
               () -> {
                  for (int clipIndex = 0; clipIndex < clipPool.length; clipIndex++) {
                     try {
                        Clip audioClip = AudioSystem.getClip();
                        audioClip.open(
                           AudioSystem.getAudioInputStream(
                              new BufferedInputStream(ModuleSoundPlayer.class.getResourceAsStream("/assets/mouseclient/sound/" + soundName + ".wav"))
                           )
                        );
                        clipPool[clipIndex] = audioClip;
                     } catch (Exception error) {
                     }
                  }
               }
            );
            loaderThread.setDaemon(true);
            loaderThread.start();
         } else {
            for (int playbackClipIndex = 0; playbackClipIndex < ((Clip[])clipPools.get(soundName)).length; playbackClipIndex++) {
               if (clipPools.get(soundName)[playbackClipIndex] != null && (!clipPools.get(soundName)[playbackClipIndex].isRunning() || playbackClipIndex >= clipPools.get(soundName).length - 1)) {
                  try {
                     ((FloatControl)clipPools.get(soundName)[playbackClipIndex].getControl(Type.MASTER_GAIN))
                        .setValue(
                           Math.max(
                              (20.0F) * (float)Math.log10(HudSettingsModule.getInstance().soundVolume.getValue() / (100.0F)),
                              ((FloatControl)clipPools.get(soundName)[playbackClipIndex].getControl(Type.MASTER_GAIN)).getMinimum()
                           )
                        );
                     clipPools.get(soundName)[playbackClipIndex].stop();
                     clipPools.get(soundName)[playbackClipIndex].setFramePosition(0);
                     clipPools.get(soundName)[playbackClipIndex].start();
                  } catch (Exception playbackFailure) {
                  }

                  return;
               }
            }
         }
      }
   }
}
