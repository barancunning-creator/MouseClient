package com.mouseclient.render.pipeline;

import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.entry.RegistryEntry;

public record EffectIconSpec(RegistryEntry<StatusEffect> effect, float x, float y, float size, int color) {
   public static final int DEFAULT_COLOR = -1;

   public EffectIconSpec(StatusEffectInstance effect, float x, float y, float size, int color) {
      this(effect == null ? null : effect.getEffectType(), x, y, size, color);
   }

   public EffectIconSpec(RegistryEntry<StatusEffect> effect, float x, float y, float size) {
      this(effect, x, y, size, -1);
   }

   public EffectIconSpec(StatusEffectInstance effect, float x, float y, float size) {
      this(effect == null ? null : effect.getEffectType(), x, y, size, -1);
   }

   public boolean visible() {
      return this.effect != null && this.effect.hasKeyAndValue() && this.size > 0.0F && (this.color >>> 24 & 0xFF) > 0;
   }
}
