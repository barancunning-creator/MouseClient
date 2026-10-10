package com.mouseclient.modules.impl.combat;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.gui.ChatMessages;
import com.mouseclient.gui.SlotClickEvent;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.modules.impl.player.InputBinding;
import com.mouseclient.modules.impl.player.ServerItemDefinition;
import com.mouseclient.modules.impl.player.ServerItemCatalog;
import com.mouseclient.modules.impl.player.BindSetting;
import com.mouseclient.modules.impl.render.AutoTotem;
import com.mouseclient.modules.impl.render.BooleanSetting;
import com.mouseclient.network.PlayerTickEvent;
import com.mouseclient.network.InventoryActions;
import com.mouseclient.network.ItemUseQueue;
import com.mouseclient.network.InventorySwapQueue;
import com.mouseclient.util.entity.CombatTargeting;
import com.mouseclient.util.time.ToastNotifications;
import com.mouseclient.util.time.SwapWheel;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

@ModuleInfo(name = "AutoSwap", description = "Свапает выбранные предметы в левую руку по клавише.", category = ModuleCategory.COMBAT)
public final class AutoSwap extends Module {
   private static AutoSwap INSTANCE;
   public ModeSetting swapMode;
   public ModeSetting firstItem;
   public ModeSetting secondItem;
   public BindSetting cerberusItem;
   public BindSetting whirlwindItem;
   public BindSetting swapWheelBind;
   public BindSetting swapBind;
   public BooleanSetting notifySwap;
   private boolean swapBindWasDown;
   private boolean useSecondItem;
   private Item lastSwappedItem;
   private ItemStack savedOffhand = ItemStack.EMPTY;
   private boolean cerberusBindWasDown;
   private boolean whirlwindBindWasDown;
   private boolean wheelWasHeld;
   private int slotBeingAssigned = -1;
   private final ItemStack[] wheelSlots = new ItemStack[]{ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
   private final EventListener<PlayerTickEvent> playerTickListener = event -> {
      if (minecraftClient.player != null) {
         boolean swapPressed = this.swapMode.isSelected(("Двойной свап"))
            && this.swapBind.isBound()
            && this.swapBind.getBinding().isPressed(minecraftClient.getWindow().getHandle());
         if (swapPressed && !this.swapBindWasDown) {
            this.cycleOffhandItem();
         }

         this.swapBindWasDown = swapPressed;
         boolean cerberusPressed = this.cerberusItem.isBound() && this.cerberusItem.getBinding().isPressed(minecraftClient.getWindow().getHandle());
         if (cerberusPressed && !this.cerberusBindWasDown) {
            this.swapByName(("цербер"));
         }

         this.cerberusBindWasDown = cerberusPressed;
         boolean whirlwindPressed = this.whirlwindItem.isBound() && this.whirlwindItem.getBinding().isPressed(minecraftClient.getWindow().getHandle());
         if (whirlwindPressed && !this.whirlwindBindWasDown) {
            this.swapByName(("вихр"));
         }

         this.whirlwindBindWasDown = whirlwindPressed;
         boolean wheelHeld = this.swapMode.isSelected(("Тройной свап"))
            && this.swapWheelBind.isBound()
            && this.isWheelKeyDown()
            && (minecraftClient.currentScreen == null || minecraftClient.currentScreen instanceof SwapWheel);
         if (wheelHeld
            && !this.wheelWasHeld
            && (minecraftClient.currentScreen == null || minecraftClient.currentScreen instanceof SwapWheel previousWheel && previousWheel.closing())) {
            minecraftClient.setScreen(new SwapWheel(this.wheelSlots, slotIndex -> this.chooseWheelStack(slotIndex)));
         }

         if (!wheelHeld && this.wheelWasHeld && minecraftClient.currentScreen instanceof SwapWheel releasedWheel) {
            if (!releasedWheel.pickerOpen() && releasedWheel.hoveredSlot() != -1 && !this.wheelSlots[releasedWheel.hoveredSlot()].isEmpty()) {
               this.swapByName(Formatting.strip(this.wheelSlots[releasedWheel.hoveredSlot()].getName().getString()).toLowerCase());
            }

            if (!releasedWheel.pickerOpen()) {
               releasedWheel.startClosing();
            }
         }

         this.wheelWasHeld = wheelHeld;
      }
   };
   private final EventListener<SlotClickEvent> slotClickListener = event -> {
      if (this.slotBeingAssigned != -1
         && minecraftClient.player != null
         && event.getSlotId() >= 0
         && event.getSlotId() < minecraftClient.player.currentScreenHandler.slots.size()) {
         event.setCancelled(true);
         if (!minecraftClient.player.currentScreenHandler.getSlot(event.getSlotId()).getStack().isEmpty()) {
            this.wheelSlots[this.slotBeingAssigned] = minecraftClient.player.currentScreenHandler.getSlot(event.getSlotId()).getStack().copy();
         }

         this.slotBeingAssigned = -1;
         minecraftClient.execute(() -> minecraftClient.setScreen(null));
      }
   };

   private MutableText formattedStackName(ItemStack stack) {
      MutableText displayName = Text.empty();
      displayName.append(stack.getName().copy().styled(style -> style.getColor() == null ? style.withColor(stack.getRarity().getFormatting()) : style));
      if (stack.getCount() > 1) {
         displayName.append(Text.literal((" [")).setStyle(Style.EMPTY.withColor(Formatting.GRAY)));
         displayName.append(Text.literal("x" + stack.getCount()).setStyle(Style.EMPTY.withColor(Formatting.WHITE)));
         displayName.append(Text.literal(("]")).setStyle(Style.EMPTY.withColor(Formatting.GRAY)));
      }

      return displayName;
   }

   public static AutoSwap getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new AutoSwap();
      }

      return INSTANCE;
   }

   private int findTotemSlot() {
      int donationSlot = InventoryActions.findSlot(stack -> CombatTargeting.isDonationTotem(stack) && !this.isSpecialTalisman(stack) && !this.isPreservedTotem(stack), 0, 35);
      if (donationSlot != -1) {
         return donationSlot;
      }

      int enchantedSlot = InventoryActions.findSlot(
         enchantedTotem -> enchantedTotem.getItem() == Items.TOTEM_OF_UNDYING && enchantedTotem.hasEnchantments() && !this.isSpecialTalisman(enchantedTotem) && !this.isPreservedTotem(enchantedTotem), 0, 35
      );
      return enchantedSlot != -1
         ? enchantedSlot
         : InventoryActions.findSlot(normalTotem -> normalTotem.getItem() == Items.TOTEM_OF_UNDYING && !this.isSpecialTalisman(normalTotem) && !this.isPreservedTotem(normalTotem), 0, 35);
   }

   public boolean isSpecialTalisman(ItemStack stack) {
      ServerItemCatalog.ensureInitialized();
      return Formatting.strip(stack.getName().getString()).toLowerCase().contains(("цербер"))
         || stack.getItem() == Items.TOTEM_OF_UNDYING
            && ServerItemCatalog.findByName(("Талисман Вихря")) != null
            && ServerItemDefinition.CHECKER.test(stack, ServerItemCatalog.findByName(("Талисман Вихря")));
   }

   private void swapByName(String nameFragment) {
      if (!this.isSwapBlocked()) {
         int inventorySlot = InventoryActions.findSlot(stack -> Formatting.strip(stack.getName().getString()).toLowerCase().contains(nameFragment), 0, 35);
         if (inventorySlot != -1) {
            this.lastSwappedItem = minecraftClient.player.getInventory().getStack(inventorySlot).getItem();
            if (this.notifySwap.getValue()) {
               ToastNotifications.showStyledToast(this.category.iconGlyph(), ("Свап"), this.formattedStackName(minecraftClient.player.getInventory().getStack(inventorySlot)), true);
               ChatMessages.printPrefixed(
                  Text.literal(("Свап "))
                     .setStyle(Style.EMPTY.withColor(Formatting.GRAY))
                     .append(this.formattedStackName(minecraftClient.player.getInventory().getStack(inventorySlot)))
               );
            }

            if (!this.isSpecialTalisman(minecraftClient.player.getOffHandStack())) {
               this.savedOffhand = minecraftClient.player.getOffHandStack().copy();
            }

            InventorySwapQueue.getInstance().queueTrackedSwap(inventorySlot, 40, 1);
         }
      }
   }

   private boolean isWheelKeyDown() {
      return this.swapWheelBind.getBinding().getType() == InputBinding.BindingType.MOUSE
         ? GLFW.glfwGetMouseButton(minecraftClient.getWindow().getHandle(), this.swapWheelBind.getBinding().getCode()) == 1
         : GLFW.glfwGetKey(minecraftClient.getWindow().getHandle(), this.swapWheelBind.getBinding().getCode()) == 1;
   }

   private boolean isSwapBlocked() {
      return minecraftClient.player == null || AutoTotem.getInstance().isProtecting() || InventorySwapQueue.getInstance().hasPendingSwaps() || ItemUseQueue.getInstance().hasPendingUses();
   }

   private int findConfiguredItemSlot(String itemName) {
      return switch (itemName) {
         case ("Золотое яблоко") -> InventoryActions.findSlot(stack -> stack.getItem() == Items.GOLDEN_APPLE && !this.isSpecialTalisman(stack), 0, 35);
         case ("Зачарованное золотое яблоко") -> InventoryActions.findSlot(enchantedApple -> enchantedApple.getItem() == Items.ENCHANTED_GOLDEN_APPLE && !this.isSpecialTalisman(enchantedApple), 0, 35);
         case ("Тотем") -> this.findTotemSlot();
         case ("Щит") -> InventoryActions.findSlot(shield -> shield.getItem() == Items.SHIELD && !this.isSpecialTalisman(shield), 0, 35);
         case ("Шар") -> this.findSphereSlot();
         default -> -1;
      };
   }

   public void setWheelStack(int slotIndex, ItemStack stack) {
      this.wheelSlots[slotIndex] = stack;
   }

   public ItemStack wheelStack(int slotIndex) {
      return this.wheelSlots[slotIndex];
   }

   private boolean isPreservedTotem(ItemStack stack) {
      return AutoTotem.getInstance().useEnchantedWhilePreserving.getValue()
         && !AutoTotem.getInstance().totemName.getText().isEmpty()
         && AutoTotem.getInstance().matchesConfiguredTotem(stack);
   }

   private AutoSwap() {
      this.swapMode = this.addSetting(new ModeSetting(("Режим свапа"), ("Двойной цикл из двух предметов, тройной колесо на три слота."), ("Двойной свап"), ("Двойной свап"), ("Тройной свап")));
      this.firstItem = this.addSetting(new ModeSetting(("Первый предмет"), ("Первый предмет в цикле свапа."), ("Золотое яблоко"), ("Золотое яблоко"), ("Зачарованное золотое яблоко"), ("Тотем"), ("Щит"), ("Шар")));
      this.secondItem = this.addSetting(new ModeSetting(("Второй предмет"), ("Второй предмет в цикле свапа."), ("Тотем"), ("Золотое яблоко"), ("Зачарованное золотое яблоко"), ("Тотем"), ("Щит"), ("Шар")));
      this.swapBind = this.addSetting(new BindSetting(("Свап"), ("Клавиша свапа предмета.")));
      this.cerberusItem = this.addSetting(new BindSetting(("Церберка"), ("Свапает голову цербера в левую руку.")));
      this.whirlwindItem = this.addSetting(new BindSetting(("Вихрь"), ("Свапает вихрь в левую руку.")));
      this.swapWheelBind = this.addSetting(new BindSetting(("Колесо свапа"), ("Удерживай, наводи на слот и отпускай, чтобы свапнуть выбранный предмет.")));
      this.firstItem.setVisibility(() -> this.swapMode.isSelected(("Двойной свап")));
      this.secondItem.setVisibility(() -> this.swapMode.isSelected(("Двойной свап")));
      this.swapBind.setVisibility(() -> this.swapMode.isSelected(("Двойной свап")));
      this.swapWheelBind.setVisibility(() -> this.swapMode.isSelected(("Тройной свап")));
      this.notifySwap = this.addSetting(new BooleanSetting(("Уведомлять о свапе"), ("Показывать нотификацию с именем предмета, на который свапнулся."), false));
   }

   private int findSphereSlot() {
      int namedSphereSlot = InventoryActions.findSlot(
         stack -> stack.getItem() == Items.PLAYER_HEAD
            && stack.contains(DataComponentTypes.ATTRIBUTE_MODIFIERS)
            && !stack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS).modifiers().isEmpty()
            && !this.isSpecialTalisman(stack)
            && !Formatting.strip(stack.getName().getString()).toLowerCase().contains(("голова")),
         0,
         35
      );
      return namedSphereSlot != -1
         ? namedSphereSlot
         : InventoryActions.findSlot(
            candidate -> candidate.getItem() == Items.PLAYER_HEAD
               && candidate.contains(DataComponentTypes.ATTRIBUTE_MODIFIERS)
               && !candidate.get(DataComponentTypes.ATTRIBUTE_MODIFIERS).modifiers().isEmpty()
               && !this.isSpecialTalisman(candidate),
            0,
            35
         );
   }

   private void cycleOffhandItem() {
      if (!this.isSwapBlocked()) {
         if (this.isSpecialTalisman(minecraftClient.player.getOffHandStack()) && !this.savedOffhand.isEmpty()) {
            int restoreSlot = InventoryActions.findSlot(stack -> ItemStack.areItemsAndComponentsEqual(stack, this.savedOffhand), 0, 35);
            if (restoreSlot != -1) {
               this.savedOffhand = ItemStack.EMPTY;
               this.lastSwappedItem = minecraftClient.player.getInventory().getStack(restoreSlot).getItem();
               if (this.notifySwap.getValue()) {
                  ToastNotifications.showStyledToast(this.category.iconGlyph(), ("Свап"), this.formattedStackName(minecraftClient.player.getInventory().getStack(restoreSlot)), true);
                  ChatMessages.printPrefixed(
                     Text.literal(("Свап "))
                        .setStyle(Style.EMPTY.withColor(Formatting.GRAY))
                        .append(this.formattedStackName(minecraftClient.player.getInventory().getStack(restoreSlot)))
                  );
               }

               InventorySwapQueue.getInstance().queueTrackedSwap(restoreSlot, 40, 1);
               return;
            }
         }

         if (minecraftClient.player.getOffHandStack().getItem() != this.lastSwappedItem) {
            this.useSecondItem = this.findConfiguredItemSlot(this.firstItem.getSelectedOption()) == -1;
         }

         int inventorySlot = this.findConfiguredItemSlot(this.useSecondItem ? this.secondItem.getSelectedOption() : this.firstItem.getSelectedOption());
         if (inventorySlot != -1) {
            this.useSecondItem = !this.useSecondItem;
            this.lastSwappedItem = minecraftClient.player.getInventory().getStack(inventorySlot).getItem();
            if (this.notifySwap.getValue()) {
               ToastNotifications.showStyledToast(this.category.iconGlyph(), ("Свап"), this.formattedStackName(minecraftClient.player.getInventory().getStack(inventorySlot)), true);
               ChatMessages.printPrefixed(
                  Text.literal(("Свап "))
                     .setStyle(Style.EMPTY.withColor(Formatting.GRAY))
                     .append(this.formattedStackName(minecraftClient.player.getInventory().getStack(inventorySlot)))
               );
            }

            InventorySwapQueue.getInstance().queueTrackedSwap(inventorySlot, 40, 1);
         }
      }
   }

   public void chooseWheelStack(int slotIndex) {
      this.slotBeingAssigned = slotIndex;
      minecraftClient.setScreen(new InventoryScreen(minecraftClient.player));
   }
}
