package com.mouseclient.modules.impl.player;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.modules.impl.misc.FilteredTextSetting;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.modules.impl.render.DecimalSetting;
import com.mouseclient.network.PlayerTickEvent;
import com.mouseclient.network.InventoryActions;
import com.mouseclient.network.ItemUseQueue;
import com.mouseclient.network.InventorySwapQueue;
import com.mouseclient.util.time.PotionWheel;
import java.util.ArrayList;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.lwjgl.glfw.GLFW;

@ModuleInfo(name = "PotionThrower", description = "Быстрое метание бафов через колесо.", category = ModuleCategory.PLAYER)
public final class PotionThrower extends Module {
   private static PotionThrower INSTANCE;
   public BindSetting openWheelBind;
   public DecimalSetting potionCount;
   public FilteredTextSetting wheelSlots;
   private final ArrayList<String> slotNames = new ArrayList<>();
   private final PotionWheel potionWheel = new PotionWheel();
   private boolean wasWheelHeld;
   private final EventListener<PlayerTickEvent> playerTickListener = event -> {
      if (minecraftClient.player != null) {
         this.updateWheelInput();
      }
   };

   public boolean isWheelHeld() {
      return this.openWheelBind.isBound() && this.isWheelKeyDown();
   }

   public void saveSlotNames() {
      String serializedSlots = String.join((";"), this.slotNames);
      java.util.ArrayList<java.lang.String> slotSnapshot = new ArrayList<>(this.slotNames);
      this.wheelSlots.setValue(serializedSlots);
      this.slotNames.clear();
      this.slotNames.addAll(slotSnapshot);
   }

   public void throwPotion(ServerItemDefinition definition) {
      if (definition != null
         && minecraftClient.player != null
         && minecraftClient.interactionManager != null
         && !ItemUseQueue.getInstance().hasPendingUses()
         && !InventorySwapQueue.getInstance().hasPendingSwaps()) {
         if (InventoryActions.findSlot(stack -> ServerItemDefinition.CHECKER.test(stack, definition)) != -1) {
            ServerHelper.getInstance().preparePotionPitch(definition.name());
            ItemUseQueue.getInstance()
               .use(
                  minecraftClient.player.getInventory().getStack(InventoryActions.findSlot(candidateStack -> ServerItemDefinition.CHECKER.test(candidateStack, definition))).copy(),
                  true
               );
         } else if (InventoryActions.findSlot(candidateStack -> this.findPotionInBundle(candidateStack, definition) != -1) != -1 && InventoryActions.findEmptySlot(0, 35) != -1) {
            ServerHelper.getInstance().preparePotionPitch(definition.name());
            ItemUseQueue.getInstance()
               .use(
                  minecraftClient.player
                     .getInventory()
                     .getStack(InventoryActions.findSlot(candidateStack -> this.findPotionInBundle(candidateStack, definition) != -1))
                     .get(DataComponentTypes.BUNDLE_CONTENTS)
                     .get(
                        this.findPotionInBundle(
                           minecraftClient.player.getInventory().getStack(InventoryActions.findSlot(candidateStack -> this.findPotionInBundle(candidateStack, definition) != -1)), definition
                        )
                     )
                     .copy(),
                  true
               );
         }
      }
   }

   public ServerItemDefinition potionForSlot(int slotIndex) {
      if (slotIndex >= 0 && slotIndex < this.slotNames().size() && !this.slotNames().get(slotIndex).isEmpty()) {
         for (ServerItemDefinition potion : ServerItemCatalog.items()) {
            if (potion.name().equals(this.slotNames().get(slotIndex))) {
               return potion;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   public ArrayList<String> slotNames() {
      if (this.slotNames.isEmpty()) {
         for (String slotName : this.wheelSlots.getValue().split((";"), -1)) {
            this.slotNames.add(slotName);
         }
      }

      int slotCount = Math.max(2, Math.min(7, this.potionCount.intValue()));

      while (this.slotNames.size() < slotCount) {
         this.slotNames.add("");
      }

      while (this.slotNames.size() > slotCount) {
         this.slotNames.remove(this.slotNames.size() - 1);
      }

      return this.slotNames;
   }

   private int findPotionInBundle(ItemStack stack, ServerItemDefinition definition) {
      if (stack.get(DataComponentTypes.BUNDLE_CONTENTS) == null) {
         return -1;
      }

      for (int bundleIndex = 0; bundleIndex < stack.get(DataComponentTypes.BUNDLE_CONTENTS).size(); bundleIndex++) {
         if (ServerItemDefinition.CHECKER.test(stack.get(DataComponentTypes.BUNDLE_CONTENTS).get(bundleIndex), definition)) {
            return bundleIndex;
         }
      }

      return -1;
   }

   public ArrayList<ServerItemDefinition> availablePotions() {
      ArrayList potions = new ArrayList();
      ServerItemCatalog.ensureInitialized();

      for (ServerItemDefinition definition : ServerItemCatalog.items()) {
         if (definition.item() == Items.SPLASH_POTION) {
            potions.add(definition);
         }
      }

      return potions;
   }

   private boolean isWheelKeyDown() {
      return this.openWheelBind.getBinding().getType() == InputBinding.BindingType.MOUSE
         ? GLFW.glfwGetMouseButton(minecraftClient.getWindow().getHandle(), this.openWheelBind.getBinding().getCode()) == 1
         : GLFW.glfwGetKey(minecraftClient.getWindow().getHandle(), this.openWheelBind.getBinding().getCode()) == 1;
   }

   public static PotionThrower getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new PotionThrower();
      }

      return INSTANCE;
   }

   private PotionThrower() {
      this.openWheelBind = this.addSetting(new BindSetting(("Открыть колесо"), ("Удерживай клавишу, наводи на зелье и отпускай, чтобы кинуть."), InputBinding.keyboard(86)));
      this.potionCount = this.addSetting(new DecimalSetting(("Количество зелий"), ("Сколько секторов в колесе."), (3.0D), (2.0D), (7.0D), 1.0));
      this.wheelSlots = this.addSetting(new FilteredTextSetting(("Слоты колеса"), "", "", 512));
      this.wheelSlots.setVisibility(() -> false);
      this.wheelSlots.onChange(this.slotNames::clear);
   }

   private void updateWheelInput() {
      if (this.openWheelBind.isBound()) {
         boolean held = this.isWheelKeyDown() && (minecraftClient.currentScreen == null || minecraftClient.currentScreen == this.potionWheel);
         if (held && !this.wasWheelHeld && (minecraftClient.currentScreen == null || this.potionWheel.closing())) {
            minecraftClient.setScreen(this.potionWheel);
         }

         if (!held && this.wasWheelHeld && minecraftClient.currentScreen == this.potionWheel && !this.potionWheel.pickerOpen() && !this.potionWheel.closing()) {
            if (this.potionWheel.draggedSlot() != -1) {
               this.potionWheel.releaseHeldSlot(this.potionWheel.hoveredSlot());
            } else if (this.potionWheel.hoveredSlot() != -1) {
               this.throwPotion(this.potionForSlot(this.potionWheel.hoveredSlot()));
            }

            this.potionWheel.startClosing();
         }

         this.wasWheelHeld = held;
      }
   }
}
