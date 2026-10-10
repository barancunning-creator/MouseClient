package com.mouseclient.modules.impl.player;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.entry.RegistryEntry;

public record PotionEffectSpec(RegistryEntry<StatusEffect> effect, int durationTicks, int amplifier) {
   public static PotionEffectSpec of(RegistryEntry<StatusEffect> effect, int durationSeconds, int amplifier) {
      return new PotionEffectSpec(effect, durationSeconds * 20, amplifier);
   }

   public static PotionEffectSpec instant(RegistryEntry<StatusEffect> effect, int amplifier) {
      return new PotionEffectSpec(effect, 1, amplifier);
   }

   public StatusEffectInstance effectInstance() {
      return new StatusEffectInstance(this.effect, this.durationTicks, this.amplifier);
   }
}
