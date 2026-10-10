package com.mouseclient.modules.impl.misc;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.modules.impl.combat.ModeSetting;
import com.mouseclient.modules.impl.render.BooleanSetting;
import com.mouseclient.network.PlayerTickEvent;
import com.mouseclient.network.InventoryActions;
import com.mouseclient.util.time.Stopwatch;
import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.inventory.Inventory;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

@ModuleInfo(name = "ChestStealer", description = "Автоматически забирает все предметы из сундуков.", category = ModuleCategory.MISC)
public final class ChestStealer extends Module {
   private static ChestStealer INSTANCE;
   public ModeSetting useMode;
   public BooleanSetting disableHitboxes;
   public BooleanSetting autoClose;
   private final Stopwatch transferTimer = new Stopwatch();
   private boolean movedAnyItem;
   public final EventListener<PlayerTickEvent> playerTickListener = event -> {
      if (minecraftClient.player != null && minecraftClient.currentScreen != null) {
         if (!this.useMode.isSelected(("FunTime")) || !minecraftClient.currentScreen.getTitle().getString().contains(("☃ Аукцион "))) {
            if (minecraftClient.player.currentScreenHandler instanceof GenericContainerScreenHandler containerHandler) {
               Inventory containerInventory = containerHandler.getInventory();
               ArrayList occupiedSlots = new ArrayList();

               for (int scanSlot = 0; scanSlot < containerInventory.size(); scanSlot++) {
                  if (!containerInventory.getStack(scanSlot).isEmpty()) {
                     occupiedSlots.add(scanSlot);
                  }
               }

               if (!occupiedSlots.isEmpty() && this.transferTimer.hasElapsed(this.useMode.isSelected(("FunTime")) ? (400L) : (10L))) {
                  InventoryActions.clickSlot((Integer)occupiedSlots.get(ThreadLocalRandom.current().nextInt(occupiedSlots.size())), 0, SlotActionType.QUICK_MOVE, false);
                  this.movedAnyItem = true;
                  this.transferTimer.reset();
               }

               if (this.autoClose.getValue() && this.movedAnyItem) {
                  boolean hasRemainingItems = false;

                  for (int remainingSlot = 0; remainingSlot < containerInventory.size(); remainingSlot++) {
                     if (!containerInventory.getStack(remainingSlot).isEmpty()) {
                        hasRemainingItems = true;
                        break;
                     }
                  }

                  if (!hasRemainingItems) {
                     minecraftClient.player.closeHandledScreen();
                     this.movedAnyItem = false;
                  }
               }
            }
         }
      }
   };

   private ChestStealer() {
      this.useMode = this.addSetting(new ModeSetting(("Режим использования"), ("Пауза между кликами под сервер."), ("FunTime"), ("Vanilla"), ("FunTime")));
      this.disableHitboxes = this.addSetting(new BooleanSetting(("Отключить хитбоксы"), ("Не целиться в сущности, пока модуль включён."), true));
      this.autoClose = this.addSetting(new BooleanSetting(("Авто-закрытие"), ("Закрывает сундук, когда он опустел."), true));
   }

   public static ChestStealer getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new ChestStealer();
      }

      return INSTANCE;
   }
}
