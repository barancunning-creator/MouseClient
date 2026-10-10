package com.mouseclient.modules.impl.misc;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.render.pipeline.SliderSetting;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.EXTEfx;

@ModuleInfo(name = "BassBoost", description = "Усиление низких частот всех игровых звуков через эквалайзер OpenAL.", category = ModuleCategory.MISC)
public final class BassBoost extends Module {
   private static BassBoost INSTANCE;
   public SliderSetting bassGain;
   private volatile int auxiliarySlot = -1;
   private int equalizerEffect = -1;
   private volatile boolean initializationFailed;
   private volatile int appliedGainDb;

   public static void resetSoundContext() {
      if (INSTANCE != null) {
         INSTANCE.auxiliarySlot = -1;
         INSTANCE.equalizerEffect = -1;
         INSTANCE.initializationFailed = false;
         INSTANCE.appliedGainDb = 0;
      }
   }

   @Override
   protected void onDisable() {
      if (this.auxiliarySlot != -1) {
         EXTEfx.alAuxiliaryEffectSloti(this.auxiliarySlot, 1, 0);
      }
   }

   public static void attachToSource(int sourceId) {
      if (INSTANCE != null && INSTANCE.isEnabled() && !INSTANCE.initializationFailed) {
         if (INSTANCE.auxiliarySlot == -1) {
            INSTANCE.initializeEqualizer();
         }

         if (INSTANCE.auxiliarySlot != -1) {
            INSTANCE.updateGain();
            AL11.alSource3i(sourceId, (131078), INSTANCE.auxiliarySlot, 0, 0);
         }
      }
   }

   private void initializeEqualizer() {
      try {
         AL10.alGetError();
         this.equalizerEffect = EXTEfx.alGenEffects();
         int createdSlot = EXTEfx.alGenAuxiliaryEffectSlots();
         EXTEfx.alEffecti(this.equalizerEffect, (32769), 12);
         EXTEfx.alEffectf(this.equalizerEffect, 3, (0.126F));
         EXTEfx.alEffectf(this.equalizerEffect, 6, (0.126F));
         EXTEfx.alEffectf(this.equalizerEffect, 9, (0.126F));
         if (AL10.alGetError() != 0) {
            this.initializationFailed = true;
            return;
         }

         this.auxiliarySlot = createdSlot;
      } catch (Throwable initializationError) {
         this.initializationFailed = true;
      }
   }

   @Override
   protected void onEnable() {
      this.appliedGainDb = 0;
   }

   public static BassBoost getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new BassBoost();
      }

      return INSTANCE;
   }

   private BassBoost() {
      this.bassGain = this.addSetting(new SliderSetting(("Басы"), ("Насколько сильнее качают низкие частоты.")).withRange(1.0F, (18.0F)).withStep(1.0F).setValue((8.0F)).withSuffix((" дБ")));
   }

   private void updateGain() {
      if (this.bassGain.intValue() != this.appliedGainDb) {
         this.appliedGainDb = this.bassGain.intValue();
         EXTEfx.alEffectf(this.equalizerEffect, 1, Math.max((0.126F), Math.min((7.943F), (float)Math.pow((10.0D), this.appliedGainDb / (20.0D)) - 1.0F)));
         EXTEfx.alAuxiliaryEffectSloti(this.auxiliarySlot, 1, this.equalizerEffect);
      }
   }
}
