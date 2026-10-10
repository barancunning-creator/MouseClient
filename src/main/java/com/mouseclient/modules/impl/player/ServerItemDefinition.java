package com.mouseclient.modules.impl.player;

import com.mouseclient.util.entity.MinecraftAccess;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.BiPredicate;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item.TooltipContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Formatting;

public record ServerItemDefinition(
   String name,
   ItemStack displayStack,
   Item item,
   String category,
   ArrayList<String> tooltip,
   ArrayList<AttributeModifierSpec> attributes,
   ArrayList<PotionEffectSpec> effects,
   ArrayList<EnchantmentRequirement> enchantments,
   ArrayList<Pair<String, String>> tags
) implements MinecraftAccess {
   public static final BiPredicate<ItemStack, ServerItemDefinition> CHECKER = (stack, definition) -> {
      if (stack.getItem() != definition.item) {
         return false;
      }

      for (EnchantmentRequirement requirement : definition.enchantments) {
         int actualLevel = 0;

         for (Entry enchantmentEntry : stack.getEnchantments().getEnchantmentEntries()) {
            if (((RegistryEntry)enchantmentEntry.getKey()).matchesKey(requirement.enchantment())) {
               actualLevel = enchantmentEntry.getIntValue();
            }
         }

         if (actualLevel != requirement.level()) {
            return false;
         }
      }

      if (!definition.attributes.isEmpty()) {
         AttributeModifiersComponent attributeModifiers = stack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
         if (attributeModifiers == null) {
            return false;
         }

         for (AttributeModifierSpec requiredAttribute : definition.attributes) {
            if (attributeModifiers.modifiers()
               .stream()
               .noneMatch(
                  attributeEntry -> requiredAttribute.attribute().value() == attributeEntry.attribute().value()
                     && requiredAttribute.amount() == attributeEntry.modifier().value()
                     && requiredAttribute.operation() == attributeEntry.modifier().operation()
               )) {
               return false;
            }
         }
      }

      if (!definition.tooltip.isEmpty()) {
         if (minecraftClient.player == null) {
            return false;
         }

         for (String requiredTooltip : definition.tooltip) {
            if (stack.getTooltip(TooltipContext.create(minecraftClient.world), minecraftClient.player, TooltipType.BASIC)
               .stream()
               .noneMatch(text -> requiredTooltip.equalsIgnoreCase(Formatting.strip(text.getString())))) {
               return false;
            }
         }
      }

      if (!definition.effects.isEmpty()) {
         PotionContentsComponent potionContents = stack.get(DataComponentTypes.POTION_CONTENTS);
         if (potionContents == null) {
            return false;
         }

         for (PotionEffectSpec requiredEffect : definition.effects) {
            boolean foundEffect = false;

            for (StatusEffectInstance effectInstance : potionContents.getEffects()) {
               if (effectInstance.getEffectType().value() == requiredEffect.effect().value()
                  && effectInstance.getDuration() >= requiredEffect.durationTicks()
                  && effectInstance.getAmplifier() >= requiredEffect.amplifier()) {
                  foundEffect = true;
               }
            }

            if (!foundEffect) {
               return false;
            }
         }
      }

      if (!definition.tags.isEmpty()) {
         NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
         if (customData == null) {
            return false;
         }

         for (Pair requiredTag : definition.tags) {
            if (!customData.copyNbt().getCompoundOrEmpty("PublicBukkitValues").getString((String)requiredTag.getFirst(), "").equalsIgnoreCase((String)requiredTag.getSecond())) {
               return false;
            }
         }
      }

      return true;
   };

   public ItemStack displayStack() {
      return this.displayStack.isEmpty() ? this.item.getDefaultStack() : this.displayStack;
   }

   public static final class Builder {
      private String name;
      private String category;
      private ItemStack displayStack;
      private Item item;
      private ArrayList<String> tooltip;
      private ArrayList<AttributeModifierSpec> attributes;
      private ArrayList<PotionEffectSpec> effects;
      private ArrayList<EnchantmentRequirement> enchantments;
      private ArrayList<Pair<String, String>> tags;

      public ServerItemDefinition.Builder withTooltip(String... lines) {
         this.tooltip.addAll(Arrays.asList(lines));
         return this;
      }

      public ServerItemDefinition.Builder withAttributes(AttributeModifierSpec... modifiers) {
         this.attributes.addAll(Arrays.asList(modifiers));
         return this;
      }

      @SafeVarargs
      public final ServerItemDefinition.Builder withTags(Pair<String, String>... tags) {
         this.tags.addAll(Arrays.asList(tags));
         return this;
      }

      public ServerItemDefinition.Builder withItem(Item item) {
         this.item = item;
         return this;
      }

      public ServerItemDefinition build() {
         if (this.item == null) {
            throw new IllegalArgumentException(("No item in Builder"));
         } else if (this.name != null && !this.name.isEmpty()) {
            return new ServerItemDefinition(
               this.name, this.displayStack, this.item, this.category, this.tooltip, this.attributes, this.effects, this.enchantments, this.tags
            );
         } else {
            throw new IllegalArgumentException(("No name in Builder"));
         }
      }

      public ServerItemDefinition.Builder withDisplayStack(ItemStack displayStack) {
         this.displayStack = displayStack;
         return this;
      }

      public ServerItemDefinition.Builder withEffects(PotionEffectSpec... effects) {
         this.effects.addAll(Arrays.asList(effects));
         return this;
      }

      public ServerItemDefinition.Builder withEnchantments(EnchantmentRequirement... enchantments) {
         this.enchantments.addAll(Arrays.asList(enchantments));
         return this;
      }

      public ServerItemDefinition.Builder withCategory(String category) {
         this.category = category;
         return this;
      }

      public Builder() {
         this.category = ("Прочее");
         this.displayStack = ItemStack.EMPTY;
         this.tooltip = new ArrayList<>();
         this.attributes = new ArrayList<>();
         this.effects = new ArrayList<>();
         this.enchantments = new ArrayList<>();
         this.tags = new ArrayList<>();
      }

      public ServerItemDefinition.Builder withName(String name) {
         this.name = name;
         return this;
      }
   }
}
