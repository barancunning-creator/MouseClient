package com.mouseclient.modules.impl.misc;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.modules.impl.render.DecimalSetting;
import com.mouseclient.network.PlayerTickEvent;
import com.mouseclient.network.InventoryActions;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.ActionResult.Success;
import net.minecraft.util.ActionResult.SwingSource;

@ModuleInfo(name = "AutoEat", description = "Сам ест из хотбара, когда голод падает ниже порога.", category = ModuleCategory.MISC)
public final class AutoEat extends Module {
   private static AutoEat INSTANCE;
   public DecimalSetting hungerThreshold;
   private int previousHotbarSlot = -1;
   private boolean eating;
   private final EventListener<PlayerTickEvent> playerTickListener = event -> {
      if (minecraftClient.player != null
         && minecraftClient.world != null
         && minecraftClient.interactionManager != null
         && !minecraftClient.player.isCreative()
         && !minecraftClient.player.isSpectator()) {
         boolean hungry = minecraftClient.player.getHungerManager().getFoodLevel() <= this.hungerThreshold.intValue();
         if (!this.eating) {
            if (!hungry) {
               return;
            }

            int initialFoodSlot = this.findBestFoodSlot();
            if (initialFoodSlot == -1) {
               return;
            }

            this.previousHotbarSlot = InventoryActions.selectedHotbarSlot();
            if (initialFoodSlot != this.previousHotbarSlot) {
               InventoryActions.selectHotbarSlot(initialFoodSlot, false);
            }

            this.eating = true;
         }

         if (!minecraftClient.player.getInventory().getStack(InventoryActions.selectedHotbarSlot()).contains(DataComponentTypes.FOOD)) {
            int replacementFoodSlot = this.findBestFoodSlot();
            if (replacementFoodSlot == -1) {
               this.stopEating();
               return;
            }

            InventoryActions.selectHotbarSlot(replacementFoodSlot, false);
         }

         if (!minecraftClient.player.isUsingItem()
            && minecraftClient.interactionManager.interactItem(minecraftClient.player, Hand.MAIN_HAND) instanceof Success useResult
            && useResult.swingSource() == SwingSource.CLIENT) {
            minecraftClient.player.swingHand(Hand.MAIN_HAND);
         }

         if (!hungry && !minecraftClient.player.isUsingItem()) {
            this.stopEating();
         }
      } else {
         this.stopEating();
      }
   };

   private int findBestFoodSlot() {
      int bestSlot = -1;
      int bestNutrition = 0;

      for (int var5 = 0; var5 < 9; var5++) {
         ItemStack stack = minecraftClient.player.getInventory().getStack(var5);
         FoodComponent food = stack.get(DataComponentTypes.FOOD);
         if (food != null
            && !stack.isOf(Items.ROTTEN_FLESH)
            && !stack.isOf(Items.SPIDER_EYE)
            && !stack.isOf(Items.POISONOUS_POTATO)
            && !stack.isOf(Items.PUFFERFISH)
            && !stack.isOf(Items.CHORUS_FRUIT)
            && food.nutrition() > bestNutrition) {
            bestNutrition = food.nutrition();
            bestSlot = var5;
         }
      }

      return bestSlot;
   }

   @Override
   protected void onDisable() {
      this.stopEating();
   }

   private void stopEating() {
      if (this.eating) {
         if (minecraftClient.player != null && minecraftClient.interactionManager != null && minecraftClient.player.isUsingItem()) {
            minecraftClient.interactionManager.stopUsingItem(minecraftClient.player);
         }

         int restoreSlot = this.previousHotbarSlot;
         if (restoreSlot != -1 && restoreSlot != InventoryActions.selectedHotbarSlot()) {
            InventoryActions.selectHotbarSlot(restoreSlot, false);
         }
      }

      this.eating = false;
      this.previousHotbarSlot = -1;
   }

   private AutoEat() {
      this.hungerThreshold = this.addSetting(new DecimalSetting(("Голод"), ("Есть, когда уровень голода не выше этого."), (16.0D), 1.0, (19.0D), 1.0));
   }

   public static AutoEat getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new AutoEat();
      }

      return INSTANCE;
   }
}
