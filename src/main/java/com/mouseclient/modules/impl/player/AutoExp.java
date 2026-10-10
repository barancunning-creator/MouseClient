package com.mouseclient.modules.impl.player;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.modules.impl.render.BooleanSetting;
import com.mouseclient.network.PlayerTickEvent;
import com.mouseclient.network.InventoryActions;
import com.mouseclient.network.ItemUseQueue;
import com.mouseclient.network.RotationLimits;
import com.mouseclient.network.RotationController;
import com.mouseclient.network.InventorySwapQueue;
import com.mouseclient.util.entity.RotationAngles;
import com.mouseclient.util.time.ToastNotifications;
import net.minecraft.component.EnchantmentEffectComponentTypes;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;

@ModuleInfo(
   name = "AutoExp",
   description = "Пока зажат бинд смотрит в пол, кидает пузырьки опыта и чинит броню мендингом.",
   category = ModuleCategory.PLAYER
)
public final class AutoExp extends Module {
   private static AutoExp INSTANCE;
   private static final EquipmentSlot[] ARMOR_SLOTS = new EquipmentSlot[]{
      EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
   };
   public BindSetting throwExperience;
   public BooleanSetting fastRepair;
   private boolean repairing;
   private int previousHotbarSlot = -1;
   private int experienceInventorySlot = -1;
   private int experienceHotbarSlot = -1;
   private boolean missingExperienceNotified;
   private EquipmentSlot repairSlot;
   private int initialRemainingDurability;
   private final boolean[] armorNeedsRestore = new boolean[4];
   private final Item[] originalArmorItems = new Item[4];
   private final EventListener<PlayerTickEvent> playerTickListener = event -> {
      if (minecraftClient.player != null && minecraftClient.interactionManager != null) {
         boolean repairKeyPressed = this.throwExperience.isBound() && this.throwExperience.getBinding().isPressed(minecraftClient.getWindow().getHandle());
         if (!repairKeyPressed) {
            if (this.repairing) {
               this.restoreExperienceSlots();
               this.restoreArmor();
            } else if (!InventorySwapQueue.getInstance().hasPendingSwaps()
               && !ItemUseQueue.getInstance().hasPendingUses()
               && minecraftClient.player.currentScreenHandler == minecraftClient.player.playerScreenHandler) {
               if (this.experienceHotbarSlot != -1) {
                  this.restoreSwappedExperience(this.experienceInventorySlot);
               }

               if (this.armorNeedsRestore[0] || this.armorNeedsRestore[1] || this.armorNeedsRestore[2] || this.armorNeedsRestore[3]) {
                  this.restoreArmor();
               }
            }

            this.repairing = false;
         } else {
            if (!this.repairing) {
               for (int var3 = 0; var3 < 4; var3++) {
                  if (this.originalArmorItems[var3] == null) {
                     this.originalArmorItems[var3] = minecraftClient.player.getEquippedStack(ARMOR_SLOTS[var3]).getItem();
                  }
               }
            }

            this.repairing = true;
            RotationController.getInstance().requestRotation(new RotationAngles(RotationAngles.cameraYaw(), (90.0F)), new RotationLimits((360.0F), (360.0F), (44.0F), (44.0F)));
            if (this.fastRepair.getValue()) {
               this.selectRepairArmor();
            }

            if (minecraftClient.player.getPitch() > (85.0F)) {
               this.throwExperienceBottle();
            }
         }
      }
   };

   public static AutoExp getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new AutoExp();
      }

      return INSTANCE;
   }

   private void restoreArmorSlot(int armorIndex, Item item) {
      if (minecraftClient.player != null && minecraftClient.interactionManager != null && !this.repairing) {
         if (minecraftClient.player.currentScreenHandler == minecraftClient.player.playerScreenHandler) {
            if (minecraftClient.player.getEquippedStack(ARMOR_SLOTS[armorIndex]).getItem() != item) {
               if (item == Items.AIR) {
                  int emptySlot = InventoryActions.findEmptySlot(9, 35);
                  if (emptySlot == -1) {
                     return;
                  }

                  InventorySwapQueue.getInstance().queueScreenSlotSwap(emptySlot, 5 + armorIndex, 1);
               } else {
                  int itemSlot = InventoryActions.findSlot(stack -> stack.getItem() == item, 0, 35);
                  if (itemSlot != -1) {
                     InventorySwapQueue.getInstance().queueScreenSlotSwap(itemSlot, 5 + armorIndex, 1);
                  }
               }
            }

            this.armorNeedsRestore[armorIndex] = false;
            this.originalArmorItems[armorIndex] = null;
         }
      }
   }

   private void restoreArmor() {
      this.repairSlot = null;

      for (int var1 = 0; var1 < 4; var1++) {
         if (!this.armorNeedsRestore[var1]) {
            this.originalArmorItems[var1] = null;
         } else if (this.originalArmorItems[var1] == null) {
            this.armorNeedsRestore[var1] = false;
         } else {
            this.restoreArmorSlot(var1, this.originalArmorItems[var1]);
         }
      }
   }

   private void restoreExperienceSlots() {
      this.missingExperienceNotified = false;
      if (this.previousHotbarSlot != -1) {
         InventoryActions.selectHotbarSlot(this.previousHotbarSlot, false);
         this.previousHotbarSlot = -1;
      }

      if (this.experienceHotbarSlot != -1) {
         this.restoreSwappedExperience(this.experienceInventorySlot);
      } else {
         this.experienceInventorySlot = -1;
      }
   }

   private void throwExperienceBottle() {
      if (!minecraftClient.player.isUsingItem()) {
         if (this.experienceInventorySlot != -1) {
            if (this.experienceHotbarSlot == -1) {
               if (!InventorySwapQueue.getInstance().hasPendingSwaps() && !ItemUseQueue.getInstance().hasPendingUses()) {
                  this.experienceInventorySlot = -1;
               }
            } else if (InventoryActions.selectedHotbarSlot() != this.experienceHotbarSlot) {
               InventoryActions.selectHotbarSlot(this.experienceHotbarSlot, false);
            } else if (minecraftClient.player.getMainHandStack().getItem() != Items.EXPERIENCE_BOTTLE) {
               if (!InventorySwapQueue.getInstance().hasPendingSwaps() && !ItemUseQueue.getInstance().hasPendingUses()) {
                  this.restoreExperienceSlots();
               }
            } else if (!minecraftClient.player.getItemCooldownManager().isCoolingDown(minecraftClient.player.getMainHandStack())) {
               InventoryActions.interactItem(Hand.MAIN_HAND);
            }
         } else if (this.previousHotbarSlot == -1) {
            if (InventoryActions.findHotbarItem(Items.EXPERIENCE_BOTTLE) == -1) {
               if (InventoryActions.findMainInventoryItem(Items.EXPERIENCE_BOTTLE) == -1) {
                  if (!InventoryActions.extractBundleItem(Items.EXPERIENCE_BOTTLE) && !this.missingExperienceNotified) {
                     this.missingExperienceNotified = true;
                     ToastNotifications.showStyledToast(
                        this.category.iconGlyph(),
                        Items.EXPERIENCE_BOTTLE.getDefaultStack().getName().getString(),
                        Text.literal(("нет в инвентаре")).setStyle(Style.EMPTY.withColor(Formatting.RED)),
                        true
                     );
                  }
               } else {
                  this.experienceInventorySlot = InventoryActions.findMainInventoryItem(Items.EXPERIENCE_BOTTLE);
                  this.experienceHotbarSlot = InventoryActions.selectedHotbarSlot();
                  InventorySwapQueue.getInstance().queueSwap(this.experienceInventorySlot, this.experienceHotbarSlot, 1);
               }
            } else {
               this.previousHotbarSlot = InventoryActions.selectedHotbarSlot();
               InventoryActions.selectHotbarSlot(InventoryActions.findHotbarItem(Items.EXPERIENCE_BOTTLE), false);
            }
         } else if (minecraftClient.player.getMainHandStack().getItem() != Items.EXPERIENCE_BOTTLE) {
            InventoryActions.selectHotbarSlot(this.previousHotbarSlot, false);
            this.previousHotbarSlot = -1;
         } else if (!minecraftClient.player.getItemCooldownManager().isCoolingDown(minecraftClient.player.getMainHandStack())) {
            InventoryActions.interactItem(Hand.MAIN_HAND);
         }
      }
   }

   private AutoExp() {
      this.throwExperience = this.addSetting(new BindSetting(("Кидать опыт"), ("Пока зажат смотрим в пол и кидаем пузырьки опыта из мешка, инвентаря или хотбара, отпустил всё вернулось.")));
      this.fastRepair = this.addSetting(new BooleanSetting(("Быстрая починка"), ("Оставляет надетым только самый поломанный элемент брони и меняет его по мере починки."), false));
   }

   private void restoreSwappedExperience(int inventorySlot) {
      if (this.experienceHotbarSlot == -1) {
         this.experienceInventorySlot = -1;
      } else {
         InventorySwapQueue.getInstance().queueSwap(inventorySlot, this.experienceHotbarSlot, 1);
         this.experienceHotbarSlot = -1;
         this.experienceInventorySlot = -1;
      }
   }

   private void removeOtherArmor() {
      int emptySlots = 0;

      for (int var2 = 9; var2 <= 35; var2++) {
         if (minecraftClient.player.getInventory().getStack(var2).isEmpty()) {
            emptySlots++;
         }
      }

      boolean[] consideredArmor = new boolean[4];

      while (emptySlots > 0) {
         int bestArmorIndex = -1;
         float bestScore = (-1.0F);

         for (int armorIndex = 0; armorIndex < 4; armorIndex++) {
            if (!consideredArmor[armorIndex] && ARMOR_SLOTS[armorIndex] != this.repairSlot) {
               ItemStack stack = minecraftClient.player.getEquippedStack(ARMOR_SLOTS[armorIndex]);
               if (!stack.isEmpty()) {
                  float score = (this.canRepairWithExperience(stack) ? (10.0F) : 0.0F)
                     + (float)(stack.getMaxDamage() - stack.getDamage()) / Math.max(1, stack.getMaxDamage());
                  if (score > bestScore) {
                     bestScore = score;
                     bestArmorIndex = armorIndex;
                  }
               }
            }
         }

         if (bestArmorIndex == -1) {
            return;
         }

         consideredArmor[bestArmorIndex] = true;
         emptySlots--;
         this.armorNeedsRestore[bestArmorIndex] = true;
         this.unequipArmorSlot(bestArmorIndex);
      }
   }

   private void selectRepairArmor() {
      if (!InventorySwapQueue.getInstance().hasPendingSwaps() && !ItemUseQueue.getInstance().hasPendingUses()) {
         if (this.repairSlot != null) {
            ItemStack stack = minecraftClient.player.getEquippedStack(this.repairSlot);
            if (!this.canRepairWithExperience(stack)
               || stack.getMaxDamage() - stack.getDamage() >= Math.max(this.initialRemainingDurability * (1.3F), this.initialRemainingDurability + stack.getMaxDamage() * (0.1F))) {
               this.repairSlot = null;
            }
         }

         if (this.repairSlot == null) {
            int selectedArmorIndex = -1;
            int sourceInventorySlot = -1;
            float lowestDurabilityRatio = (3.4028235E38F);

            for (int armorIndex = 0; armorIndex < 4; armorIndex++) {
               ItemStack candidateStack = minecraftClient.player.getEquippedStack(ARMOR_SLOTS[armorIndex]);
               int candidateInventorySlot = -1;
               if (!this.canRepairWithExperience(candidateStack)) {
                  Item item = this.originalArmorItems[armorIndex];
                  if (!this.armorNeedsRestore[armorIndex] || item == null || item == Items.AIR) {
                     continue;
                  }

                  candidateInventorySlot = InventoryActions.findSlot(storedStack -> storedStack.getItem() == item && this.canRepairWithExperience(storedStack), 0, 35);
                  if (candidateInventorySlot == -1) {
                     continue;
                  }

                  candidateStack = minecraftClient.player.getInventory().getStack(candidateInventorySlot);
               }

               float durabilityRatio = (float)(candidateStack.getMaxDamage() - candidateStack.getDamage()) / Math.max(1, candidateStack.getMaxDamage());
               if (durabilityRatio < lowestDurabilityRatio) {
                  lowestDurabilityRatio = durabilityRatio;
                  selectedArmorIndex = armorIndex;
                  sourceInventorySlot = candidateInventorySlot;
               }
            }

            if (selectedArmorIndex == -1) {
               return;
            }

            this.repairSlot = ARMOR_SLOTS[selectedArmorIndex];
            if (sourceInventorySlot != -1) {
               this.initialRemainingDurability = minecraftClient.player.getInventory().getStack(sourceInventorySlot).getMaxDamage()
                  - minecraftClient.player.getInventory().getStack(sourceInventorySlot).getDamage();
               this.armorNeedsRestore[selectedArmorIndex] = true;
               InventorySwapQueue.getInstance().queueScreenSlotSwap(sourceInventorySlot, 5 + selectedArmorIndex, 1);
               return;
            }

            this.initialRemainingDurability = minecraftClient.player.getEquippedStack(this.repairSlot).getMaxDamage()
               - minecraftClient.player.getEquippedStack(this.repairSlot).getDamage();
         }

         this.removeOtherArmor();
      }
   }

   @Override
   protected void onDisable() {
      if (this.repairing) {
         this.restoreExperienceSlots();
         this.restoreArmor();
      }

      this.repairing = false;
   }

   private boolean canRepairWithExperience(ItemStack stack) {
      return !stack.isEmpty() && stack.isDamaged() && EnchantmentHelper.hasAnyEnchantmentsWith(stack, EnchantmentEffectComponentTypes.REPAIR_WITH_XP);
   }

   private void unequipArmorSlot(int armorIndex) {
      if (minecraftClient.player != null
         && minecraftClient.interactionManager != null
         && minecraftClient.player.currentScreenHandler == minecraftClient.player.playerScreenHandler) {
         int emptySlot = InventoryActions.findEmptySlot(9, 35);
         if (emptySlot != -1 && !minecraftClient.player.getEquippedStack(ARMOR_SLOTS[armorIndex]).isEmpty()) {
            InventorySwapQueue.getInstance().queueScreenSlotSwap(emptySlot, 5 + armorIndex, 1);
         }
      }
   }
}
