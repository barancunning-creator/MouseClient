package com.mouseclient.modules.impl.player;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.internal.PacketEvent;
import com.mouseclient.modules.impl.combat.MovementInputEvent;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.modules.impl.render.BooleanSetting;
import com.mouseclient.network.PlayerTickEvent;
import com.mouseclient.network.InventoryActions;
import com.mouseclient.network.ItemUseQueue;
import com.mouseclient.network.InventorySwapQueue;
import com.mouseclient.util.time.ToastNotifications;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.AttributeModifiersComponent.Entry;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.PositionAndOnGround;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry.Reference;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

@ModuleInfo(name = "ElytraHelper", description = "Выполняет действия с элитрой по нажатию назначенной клавиши.", category = ModuleCategory.PLAYER)
public final class ElytraHelper extends Module {
   private static ElytraHelper INSTANCE;
   public BindSetting toggleBind;
   public BindSetting fireworkBind;
   public BooleanSetting swapOnUse;
   public BooleanSetting autoStartAfterSwap;
   public BooleanSetting flyCommandOnSwap;
   public BooleanSetting autoFirework;
   private boolean startPending;
   private boolean flyPermissionDenied;
   private int startTick;
   private boolean toggleKeyHeld;
   private boolean fireworkKeyHeld;
   private final EventListener<PlayerTickEvent> playerTickListener = event -> {
      if (minecraftClient.player != null && minecraftClient.interactionManager != null) {
         if (minecraftClient.currentScreen != null) {
            this.toggleKeyHeld = this.toggleBind.getBinding().isPressed(minecraftClient.getWindow().getHandle());
            this.fireworkKeyHeld = this.fireworkBind.getBinding().isPressed(minecraftClient.getWindow().getHandle());
         } else {
            if (this.toggleBind.getBinding().isPressed(minecraftClient.getWindow().getHandle()) && !this.toggleKeyHeld) {
               this.swapChestItem();
            }

            this.toggleKeyHeld = this.toggleBind.getBinding().isPressed(minecraftClient.getWindow().getHandle());
            if (this.fireworkBind.getBinding().isPressed(minecraftClient.getWindow().getHandle()) && !this.fireworkKeyHeld) {
               this.useFirework();
            }

            this.fireworkKeyHeld = this.fireworkBind.getBinding().isPressed(minecraftClient.getWindow().getHandle());
         }
      }
   };
   private final EventListener<MovementInputEvent> movementInputListener = event -> {
      if (minecraftClient.player != null) {
         if (minecraftClient.player.age < 5) {
            this.startPending = false;
         } else if (this.startPending && this.allowGlideStartAfterFly()) {
            this.updateGlideStart(event);
         }
      }
   };
   public final EventListener<PacketEvent> packetListener = event -> {
      if (event.isIncoming() && event.getPacket() instanceof GameMessageS2CPacket messagePacket && this.startPending && messagePacket.content().getString().contains(("Эту команду могут писать только донатеры выше рангом"))) {
         this.flyPermissionDenied = true;
      }
   };

   private boolean hasElapsedTicks(int ticks) {
      return this.startTick + ticks < minecraftClient.player.age;
   }

   private void updateGlideStart(MovementInputEvent event) {
      boolean elytraEquipped = minecraftClient.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA;
      if (!minecraftClient.player.isGliding() && elytraEquipped) {
         event.setJumping(minecraftClient.player.age % 2 == 0);
         this.startTick = minecraftClient.player.age;
         if (!this.autoFirework.getValue()) {
            this.startPending = false;
         }
      }

      if (minecraftClient.player.isGliding()
         && this.autoFirework.getValue()
         && !minecraftClient.player.isTouchingWater()
         && !minecraftClient.player.isOnGround()
         && this.hasElapsedTicks(2)) {
         this.useFirework();
         this.startPending = false;
      }

      if (this.hasElapsedTicks(10)) {
         this.startPending = false;
      }
   }

   private boolean allowGlideStartAfterFly() {
      if (this.flyCommandOnSwap.getValue() && !this.flyPermissionDenied) {
         if (this.startTick + 1 == minecraftClient.player.age) {
            minecraftClient.player.networkHandler.sendChatCommand(("fly"));
         }

         if (minecraftClient.player.getAbilities().allowFlying && this.startTick <= minecraftClient.player.age && minecraftClient.player.isOnGround()) {
            double x = minecraftClient.player.getX();
            double raisedY = minecraftClient.player.getY() + (0.19999997317790985D);
            double z = minecraftClient.player.getZ();
            minecraftClient.player.networkHandler.sendPacket(new PositionAndOnGround(x, raisedY, z, false, minecraftClient.player.horizontalCollision));
            minecraftClient.player.setPosition(x, raisedY, z);
            this.startTick = minecraftClient.player.age + 9;
         }

         if (minecraftClient.player.getAbilities().allowFlying && !minecraftClient.player.isOnGround()) {
            minecraftClient.player.getAbilities().flying = true;
            minecraftClient.player.setVelocity(Vec3d.ZERO);
            minecraftClient.player.sendAbilitiesUpdate();
            this.startPending = false;
         }

         if (!this.hasElapsedTicks(15) && (!this.hasElapsedTicks(3) || minecraftClient.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA)) {
            return false;
         }

         this.startPending = false;
         return false;
      } else {
         return true;
      }
   }

   private void swapChestItem() {
      if (!InventorySwapQueue.getInstance().hasPendingSwaps() && !ItemUseQueue.getInstance().hasPendingUses()) {
         boolean elytraEquipped = minecraftClient.player.getEquippedStack(EquipmentSlot.CHEST).getItem() == Items.ELYTRA;
         int itemSlot = elytraEquipped ? this.bestChestplateSlot() : InventoryActions.findItem(Items.ELYTRA);
         if (itemSlot == -1) {
            ToastNotifications.showStyledToast(
               this.category.iconGlyph(),
               (elytraEquipped ? Items.NETHERITE_CHESTPLATE : Items.ELYTRA).getDefaultStack().getName().getString(),
               Text.literal(("нет в инвентаре")).setStyle(Style.EMPTY.withColor(Formatting.RED)),
               true
            );
         } else {
            if (this.swapOnUse.getValue()) {
               ItemUseQueue.getInstance().use(minecraftClient.player.getInventory().getStack(itemSlot).copy(), true);
            } else {
               InventorySwapQueue.getInstance().queueScreenSlotSwap(itemSlot, 6, 1);
            }

            if (this.autoStartAfterSwap.getValue() && !elytraEquipped) {
               this.startTick = minecraftClient.player.age;
               this.startPending = true;
            }
         }
      }
   }

   private void useFirework() {
      if (minecraftClient.player.isGliding()) {
         if (!ItemUseQueue.getInstance().hasUsableItem(Items.FIREWORK_ROCKET.getDefaultStack())) {
            ToastNotifications.showStyledToast(
               this.category.iconGlyph(),
               Items.FIREWORK_ROCKET.getDefaultStack().getName().getString(),
               Text.literal(("нет в инвентаре")).setStyle(Style.EMPTY.withColor(Formatting.RED)),
               true
            );
         } else {
            ItemUseQueue.getInstance().use(Items.FIREWORK_ROCKET.getDefaultStack(), true);
         }
      }
   }

   public static ElytraHelper getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new ElytraHelper();
      }

      return INSTANCE;
   }

   @Override
   protected void onDisable() {
      this.startPending = false;
   }

   private int bestChestplateSlot() {
      Reference protectionEnchantment = minecraftClient.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION);
      int bestSlot = -1;
      double bestScore = 0.0;

      for (int var5 = 0; var5 < 36; var5++) {
         ItemStack stack = minecraftClient.player.getInventory().getStack(var5);
         if (!stack.isEmpty()
            && stack.getItem() != Items.ELYTRA
            && stack.get(DataComponentTypes.EQUIPPABLE) != null
            && stack.get(DataComponentTypes.EQUIPPABLE).slot() == EquipmentSlot.CHEST) {
            double score = EnchantmentHelper.getLevel(protectionEnchantment, stack);
            AttributeModifiersComponent attributes = stack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
            if (attributes != null) {
               for (Entry attribute : attributes.modifiers()) {
                  if (attribute.attribute() == EntityAttributes.ARMOR || attribute.attribute() == EntityAttributes.ARMOR_TOUGHNESS) {
                     score += attribute.modifier().value();
                  }
               }
            }

            if (score > bestScore) {
               bestScore = score;
               bestSlot = var5;
            }
         }
      }

      return bestSlot;
   }

   private ElytraHelper() {
      this.toggleBind = this.addSetting(new BindSetting(("Кнопка переключения"), ("Надевает элитры вместо нагрудника и обратно.")));
      this.fireworkBind = this.addSetting(new BindSetting(("Кнопка фейерверка"), ("Использует фейерверк в полёте по нажатию.")));
      this.swapOnUse = this.addSetting(new BooleanSetting(("Свап использованием"), ("Меняет нагрудник правым кликом по надеваемому предмету (1.21.2+) вместо трёх кликов по слотам брони."), true));
      this.autoStartAfterSwap = this.addSetting(new BooleanSetting(("Автостарт после свапа"), ("Сам взлетает после свапа на элитры."), false));
      this.flyCommandOnSwap = this.addSetting(new BooleanSetting(("Использовать /fly при свапе"), ("Взлетает через /fly, пока сервер разрешает."), false).setVisibility(() -> this.autoStartAfterSwap.getValue()));
      this.autoFirework = this.addSetting(new BooleanSetting(("Автофейерверк"), ("Запускает фейерверк, как только полёт начался."), false).setVisibility(() -> this.autoStartAfterSwap.getValue()));
   }
}
