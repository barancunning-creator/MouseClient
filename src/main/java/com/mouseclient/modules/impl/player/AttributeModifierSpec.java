package com.mouseclient.modules.impl.player;

import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier.Operation;
import net.minecraft.registry.entry.RegistryEntry;

public record AttributeModifierSpec(RegistryEntry<EntityAttribute> attribute, Operation operation, double amount) {
   public static AttributeModifierSpec of(RegistryEntry<EntityAttribute> attribute, double amount, Operation operation) {
      return new AttributeModifierSpec(attribute, operation, amount);
   }
}
