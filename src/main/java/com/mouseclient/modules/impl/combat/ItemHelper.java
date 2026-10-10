package com.mouseclient.modules.impl.combat;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.gui.RenderSlotsEvent;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.modules.impl.player.ServerHelper;
import com.mouseclient.modules.impl.player.BindSetting;
import com.mouseclient.modules.impl.render.BooleanSetting;
import com.mouseclient.modules.impl.render.SettingSeparator;
import com.mouseclient.network.PlayerTickEvent;
import com.mouseclient.network.InventoryActions;
import com.mouseclient.network.InventorySwapQueue;
import com.mouseclient.render.pipeline.SliderSetting;
import lombok.Generated;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.screen.slot.Slot;

@ModuleInfo(name = "ItemHelper", description = "Использует предмет, пока зажата его клавиша.", category = ModuleCategory.COMBAT)
public final class ItemHelper extends Module {
   private static ItemHelper INSTANCE;
   public BooleanSetting releaseAfterUse;
   public SliderSetting healthHighlight;
   public BooleanSetting healingPotion;
   public BooleanSetting enchantedGoldenApple;
   public BooleanSetting goldenApple;
   public BooleanSetting chorusFruit;
   public final EventListener<PlayerTickEvent> playerTickListener = event -> {
      if (minecraftClient.player != null && minecraftClient.interactionManager != null) {
         for (ItemHelper.ItemUseAction action : ItemHelper.ItemUseAction.values()) {
            boolean keyDown = minecraftClient.currentScreen == null && action.binding.getBinding().isPressed(minecraftClient.getWindow().getHandle());
            if (!keyDown) {
               if (action.using) {
                  minecraftClient.options.useKey.setPressed(false);
                  action.using = false;
               }

               this.restoreSelection(action);
            } else {
               action.using = true;
               int itemSlot = action == ItemHelper.ItemUseAction.HEAL_POTION
                  ? InventoryActions.findSlot(this::isHealingPotion)
                  : InventoryActions.findItem(action.item);
               if (itemSlot != -1 && action.restoreHotbarSlot < 0) {
                  if (itemSlot <= 8) {
                     action.restoreHotbarSlot = InventoryActions.selectedHotbarSlot();
                     minecraftClient.player.getInventory().setSelectedSlot(itemSlot);
                  } else if (minecraftClient.player.currentScreenHandler == minecraftClient.player.playerScreenHandler && !InventorySwapQueue.getInstance().hasPendingSwaps()) {
                     action.restoreHotbarSlot = InventoryActions.selectedHotbarSlot();
                     action.inventorySlot = itemSlot;
                     InventorySwapQueue.getInstance().queueSwap(itemSlot, action.restoreHotbarSlot, 1);
                  }
               }

               if (minecraftClient.player.getMainHandStack().getItem() == action.item) {
                  if (action == ItemHelper.ItemUseAction.CROSSBOW
                     && minecraftClient.player.isUsingItem()
                     && (
                        CrossbowItem.isCharged(minecraftClient.player.getMainHandStack())
                           || minecraftClient.player.getItemUseTime()
                              > CrossbowItem.getPullTime(minecraftClient.player.getMainHandStack(), minecraftClient.player) + 3
                     )) {
                     if (minecraftClient.player.isUsingItem()) {
                        minecraftClient.interactionManager.stopUsingItem(minecraftClient.player);
                     }

                     minecraftClient.options.useKey.setPressed(false);
                  } else {
                     minecraftClient.options.useKey.setPressed(true);
                     if (this.releaseAfterUse.getValue() && action != ItemHelper.ItemUseAction.CROSSBOW && minecraftClient.player.getItemUseTime() >= 33) {
                        minecraftClient.interactionManager.stopUsingItem(minecraftClient.player);
                        minecraftClient.options.useKey.setPressed(false);
                        action.using = false;
                     }
                  }
               }
            }
         }
      }
   };
   public final EventListener<RenderSlotsEvent> renderSlotsListener = event -> {
      if (this.shouldHighlightHealingItems()) {
         for (Slot slot : event.getScreen().getScreenHandler().slots) {
            if (slot.hasStack()) {
               int color = this.highlightColor(slot.getStack());
               if (color != -1) {
                  event.getDrawContext().fill(slot.x, slot.y, slot.x + 16, slot.y + 16, (-2147483648) | color);
               }
            }
         }
      }
   };

   private static final String HIGHLIGHT_SECTION_LABEL = "Подсвечивание";

   private static final String ENCHANTED_APPLE_LABEL = "Чарка";

   public int highlightColor(ItemStack stack) {
      if (stack.isEmpty()) {
         return -1;
      } else if (this.enchantedGoldenApple.getValue() && stack.getItem() == Items.ENCHANTED_GOLDEN_APPLE) {
         return (58879);
      } else if (this.healingPotion.getValue() && this.isHealingPotion(stack)) {
         return stack.get(DataComponentTypes.POTION_CONTENTS).getColor() & (16777215);
      } else if (this.goldenApple.getValue() && stack.getItem() == Items.GOLDEN_APPLE) {
         return (14725168);
      } else {
         return this.chorusFruit.getValue() && stack.getItem() == Items.CHORUS_FRUIT ? (10049023) : -1;
      }
   }

   private ItemHelper() {
      this.addSetting(new SettingSeparator(("Предметы")));

      for (ItemHelper.ItemUseAction action : ItemHelper.ItemUseAction.values()) {
         action.binding = this.addSetting(new BindSetting(action.label, ("Держи возьмёт предмет в руку и использует.")));
      }

      this.addSetting(new SettingSeparator(HIGHLIGHT_SECTION_LABEL));
      this.healthHighlight = this.addSetting(new SliderSetting(("Подсвечивать при здоровье"), ("Подсвечивает лечащие предметы, когда здоровье не выше этого.")).withRange(0.0F, (20.0F)).withStep(1.0F).setValue((8.0F)));
      this.healingPotion = this.addSetting(new BooleanSetting(("Зелье исцеление"), ("Подсвечивает зелья мгновенного исцеления."), false));
      this.enchantedGoldenApple = this.addSetting(new BooleanSetting(ENCHANTED_APPLE_LABEL, ("Подсвечивает зачарованные яблоки."), false));
      this.goldenApple = this.addSetting(new BooleanSetting(("Гепл"), ("Подсвечивает золотые яблоки."), false));
      this.chorusFruit = this.addSetting(new BooleanSetting(("Хорус"), ("Подсвечивает плоды хоруса."), false));
      this.releaseAfterUse = this.addSetting(new BooleanSetting(("Отжимать при выполнении"), ("Сам отпускает использование, когда предмет доеден."), true));
   }

   public int displayedHotbarSlot() {
      if (this.isEnabled() && minecraftClient.player != null) {
         for (ItemHelper.ItemUseAction action : ItemHelper.ItemUseAction.values()) {
            if (action.using && action.inventorySlot == -1 && action.restoreHotbarSlot != -1 && action.restoreHotbarSlot != InventoryActions.selectedHotbarSlot()) {
               return action.restoreHotbarSlot;
            }
         }

         return -1;
      } else {
         return -1;
      }
   }

   public boolean isEnhancedMace(ItemStack stack) {
      return minecraftClient.world != null && stack.getItem() == Items.MACE
         ? EnchantmentHelper.getLevel(minecraftClient.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.BREACH), stack)
               >= 3
            && EnchantmentHelper.getLevel(
                  minecraftClient.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.DENSITY), stack
               )
               >= 5
         : false;
   }

   public static ItemHelper getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new ItemHelper();
      }

      return INSTANCE;
   }

   public boolean shouldHighlightHealingItems() {
      return minecraftClient.player != null
         && (
            minecraftClient.player.getHealth() <= this.healthHighlight.floatValue()
               || ServerHelper.getInstance().isEnabled() && ServerHelper.getInstance().server.isSelected(("SpookyTime"))
         );
   }

   public void drawHotbarHighlights(DrawContext context) {
      if (this.shouldHighlightHealingItems()) {
         int hotbarX = context.getScaledWindowWidth() / 2 - 91;

         for (int slotIndex = 0; slotIndex < 9; slotIndex++) {
            int color = this.highlightColor(minecraftClient.player.getInventory().getStack(slotIndex));
            if (color != -1) {
               context.fill(hotbarX + 3 + slotIndex * 20, context.getScaledWindowHeight() - 19, hotbarX + 3 + slotIndex * 20 + 16, context.getScaledWindowHeight() - 3, (-2147483648) | color);
            }
         }
      }
   }

   public ItemHelper.ItemUseAction[] itemActions() {
      return ItemHelper.ItemUseAction.values();
   }

   private boolean isHealingPotion(ItemStack stack) {
      if (stack.getItem() == Items.POTION && stack.get(DataComponentTypes.POTION_CONTENTS) != null) {
         for (StatusEffectInstance effect : stack.get(DataComponentTypes.POTION_CONTENTS).getEffects()) {
            if (effect.getEffectType() == StatusEffects.INSTANT_HEALTH) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   public boolean isUsingBoundItem() {
      if (!this.isEnabled()) {
         return false;
      }

      for (ItemHelper.ItemUseAction action : ItemHelper.ItemUseAction.values()) {
         if (action.using) {
            return true;
         }
      }

      return false;
   }

   private void restoreSelection(ItemHelper.ItemUseAction action) {
      if (action.restoreHotbarSlot != -1) {
         if (action.inventorySlot == -1) {
            minecraftClient.player.getInventory().setSelectedSlot(action.restoreHotbarSlot);
            action.restoreHotbarSlot = -1;
         } else if (minecraftClient.player.currentScreenHandler == minecraftClient.player.playerScreenHandler) {
            InventorySwapQueue.getInstance().queueSwap(action.inventorySlot, action.restoreHotbarSlot, 1);
            action.inventorySlot = -1;
            action.restoreHotbarSlot = -1;
         }
      }
   }

   public enum ItemUseAction {
      CROSSBOW(Items.CROSSBOW, "Арбалет"),
      CHORUS_FRUIT(Items.CHORUS_FRUIT, "Плод хоруса"),
      GOLDEN_APPLE(Items.GOLDEN_APPLE, "Золотое яблоко"),
      HEAL_POTION(Items.POTION, "Зелье исцеления"),
      ENCHANTED_GOLDEN_APPLE(Items.ENCHANTED_GOLDEN_APPLE, "Зачар. яблоко");

      final Item item;
      final String label;
      BindSetting binding;
      boolean using;
      boolean unusedFlag;
      int inventorySlot = -1;
      int restoreHotbarSlot = -1;

      ItemUseAction(Item item, String label) {
         this.item = item;
         this.label = label;
      }

      public boolean matchesItem(ItemStack stack) {
         if (stack.isEmpty()) {
            return false;
         } else {
            return this == HEAL_POTION ? ItemHelper.getInstance().isHealingPotion(stack) : stack.getItem() == this.item;
         }
      }

      @Generated
      public Item item() {
         return this.item;
      }

      @Generated
      public BindSetting binding() {
         return this.binding;
      }
   }
}
