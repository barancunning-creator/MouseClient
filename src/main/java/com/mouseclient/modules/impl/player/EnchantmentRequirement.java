package com.mouseclient.modules.impl.player;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.registry.RegistryKey;

public record EnchantmentRequirement(RegistryKey<Enchantment> enchantment, int level) {
   public static EnchantmentRequirement of(RegistryKey<Enchantment> enchantment, int level) {
      return new EnchantmentRequirement(enchantment, level);
   }
}
