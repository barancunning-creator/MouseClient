package com.mouseclient.modules.impl.player;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.internal.FriendManager;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.modules.impl.render.BooleanSetting;
import com.mouseclient.network.PlayerTickEvent;
import com.mouseclient.network.InventoryActions;
import com.mouseclient.network.ItemUseQueue;
import com.mouseclient.network.InventorySwapQueue;
import com.mouseclient.util.time.ToastNotifications;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;

@ModuleInfo(name = "ClickAction", description = "Бросок привязанных предметов из хотбара или инвентаря по клавише.", category = ModuleCategory.PLAYER)
public final class ClickAction extends Module {
   private static ClickAction INSTANCE;
   public BindSetting enderPearl;
   public BindSetting experienceBottles;
   public BooleanSetting clickFriend;
   public BindSetting friendBind;
   private boolean pearlKeyHeld;
   private boolean friendKeyHeld;
   private int previousHotbarSlot = -1;
   private int experienceInventorySlot = -1;
   private int experienceHotbarSlot = -1;
   private boolean experienceMissingNotified;
   private final EventListener<PlayerTickEvent> playerTickListener = event -> {
      if (minecraftClient.player != null && minecraftClient.interactionManager != null) {
         this.updateFriendKey();
         if (minecraftClient.currentScreen == null && !minecraftClient.player.isUsingItem()) {
            if (this.enderPearl.getBinding().isPressed(minecraftClient.getWindow().getHandle()) && !this.pearlKeyHeld) {
               this.useBoundItem(Items.ENDER_PEARL);
            }

            this.pearlKeyHeld = this.enderPearl.getBinding().isPressed(minecraftClient.getWindow().getHandle());
            if (this.experienceBottles.getBinding().isPressed(minecraftClient.getWindow().getHandle())) {
               this.useExperienceBottle();
            } else {
               this.restoreExperienceSlots();
            }
         } else {
            this.pearlKeyHeld = this.enderPearl.getBinding().isPressed(minecraftClient.getWindow().getHandle());
            this.restoreExperienceSlots();
         }
      }
   };

   private void useBoundItem(Item item) {
      if (!ItemUseQueue.getInstance().hasUsableItem(item.getDefaultStack())) {
         ToastNotifications.showStyledToast(
            this.category.iconGlyph(),
            item.getDefaultStack().getName().getString(),
            Text.literal(("нет в инвентаре")).setStyle(Style.EMPTY.withColor(Formatting.RED)),
            true
         );
      } else if (!minecraftClient.player.getItemCooldownManager().isCoolingDown(item.getDefaultStack())) {
         ItemUseQueue.getInstance().use(item.getDefaultStack());
      }
   }

   private ClickAction() {
      this.enderPearl = this.addSetting(new BindSetting(("Эндер жемчуг"), ("Кидает жемчуг по нажатию бинда.")));
      this.experienceBottles = new BindSetting(("Пузырьки опыта"), ("Кидает пузырьки, пока бинд зажат."));
      this.clickFriend = this.addSetting(new BooleanSetting(("Друг по клику"), ("Клик по игроку выбранной кнопкой добавляет или убирает его из друзей."), false));
      this.friendBind = this.addSetting(new BindSetting(("Кнопка друга"), ("Какой кнопкой отмечать друга."), InputBinding.mouse(2)));
      this.friendBind.setVisibility(() -> this.clickFriend.getValue());
   }

   private void restoreExperienceSlots() {
      this.experienceMissingNotified = false;
      if (this.previousHotbarSlot != -1) {
         InventoryActions.selectHotbarSlot(this.previousHotbarSlot, false);
         this.previousHotbarSlot = -1;
      }

      if (this.experienceInventorySlot != -1) {
         if (this.experienceHotbarSlot == -1) {
            if (!InventorySwapQueue.getInstance().hasPendingSwaps() && !ItemUseQueue.getInstance().hasPendingUses()) {
               this.experienceInventorySlot = -1;
            }
         } else if (!InventorySwapQueue.getInstance().hasPendingSwaps()
            && !ItemUseQueue.getInstance().hasPendingUses()
            && minecraftClient.player.currentScreenHandler == minecraftClient.player.playerScreenHandler) {
            InventorySwapQueue.getInstance().queueSwap(this.experienceInventorySlot, this.experienceHotbarSlot, 1);
            this.experienceHotbarSlot = -1;
            this.experienceInventorySlot = -1;
         }
      }
   }

   private void useExperienceBottle() {
      if (this.experienceInventorySlot != -1) {
         if (this.experienceHotbarSlot == -1) {
            if (!InventorySwapQueue.getInstance().hasPendingSwaps() && !ItemUseQueue.getInstance().hasPendingUses()) {
               this.experienceInventorySlot = -1;
            }
         } else if (InventoryActions.selectedHotbarSlot() != this.experienceHotbarSlot) {
            InventoryActions.selectHotbarSlot(this.experienceHotbarSlot, false);
         } else if (minecraftClient.player.getMainHandStack().getItem() != Items.EXPERIENCE_BOTTLE) {
            this.restoreExperienceSlots();
         } else if (!minecraftClient.player.getItemCooldownManager().isCoolingDown(minecraftClient.player.getMainHandStack())) {
            InventoryActions.interactItem(Hand.MAIN_HAND);
         }
      } else if (this.previousHotbarSlot != -1) {
         if (minecraftClient.player.getMainHandStack().getItem() != Items.EXPERIENCE_BOTTLE) {
            InventoryActions.selectHotbarSlot(this.previousHotbarSlot, false);
            this.previousHotbarSlot = -1;
         } else if (!minecraftClient.player.getItemCooldownManager().isCoolingDown(minecraftClient.player.getMainHandStack())) {
            InventoryActions.interactItem(Hand.MAIN_HAND);
         }
      } else if (InventoryActions.findHotbarItem(Items.EXPERIENCE_BOTTLE) != -1) {
         this.previousHotbarSlot = InventoryActions.selectedHotbarSlot();
         InventoryActions.selectHotbarSlot(InventoryActions.findHotbarItem(Items.EXPERIENCE_BOTTLE), false);
      } else if (InventoryActions.findMainInventoryItem(Items.EXPERIENCE_BOTTLE) != -1) {
         this.experienceInventorySlot = InventoryActions.findMainInventoryItem(Items.EXPERIENCE_BOTTLE);
         this.experienceHotbarSlot = InventoryActions.selectedHotbarSlot();
         InventorySwapQueue.getInstance().queueSwap(this.experienceInventorySlot, this.experienceHotbarSlot, 1);
      } else {
         if (!InventoryActions.extractBundleItem(Items.EXPERIENCE_BOTTLE) && !this.experienceMissingNotified) {
            this.experienceMissingNotified = true;
            ToastNotifications.showStyledToast(
               this.category.iconGlyph(),
               Items.EXPERIENCE_BOTTLE.getDefaultStack().getName().getString(),
               Text.literal(("нет в инвентаре")).setStyle(Style.EMPTY.withColor(Formatting.RED)),
               true
            );
         }
      }
   }

   @Override
   protected void onDisable() {
      this.pearlKeyHeld = false;
      this.restoreExperienceSlots();
   }

   public static ClickAction getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new ClickAction();
      }

      return INSTANCE;
   }

   private void updateFriendKey() {
      if (this.clickFriend.getValue() && minecraftClient.currentScreen == null) {
         boolean friendKeyPressed = this.friendBind.isBound() && this.friendBind.getBinding().isPressed(minecraftClient.getWindow().getHandle());
         if (friendKeyPressed && !this.friendKeyHeld && minecraftClient.targetedEntity instanceof AbstractClientPlayerEntity player && player != minecraftClient.player) {
            if (FriendManager.contains(player.getGameProfile().name())) {
               FriendManager.remove(player.getGameProfile().name());
               ToastNotifications.showIconToast(this.category.iconGlyph(), ("ClickAction"), player.getGameProfile().name() + " больше не друг", true);
            } else {
               FriendManager.add(player.getGameProfile().name());
               ToastNotifications.showIconToast(this.category.iconGlyph(), ("ClickAction"), player.getGameProfile().name() + " теперь друг", true);
            }
         }

         this.friendKeyHeld = friendKeyPressed;
      } else {
         this.friendKeyHeld = false;
      }
   }
}
