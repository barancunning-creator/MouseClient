package com.mouseclient.modules.impl.movement;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.modules.impl.combat.ModeSetting;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.network.PlayerTickEvent;
import net.minecraft.item.CrossbowItem;

@ModuleInfo(name = "NoSlow", description = "Убирает замедление при использовании предметов.", category = ModuleCategory.MOVEMENT)
public final class NoSlow extends Module {
   private static NoSlow INSTANCE;
   private final ModeSetting mode;
   private int itemUseTicks;
   private final EventListener<PlayerTickEvent> playerTickListener;
   private final EventListener<ItemUseSlowdownEvent> itemUseSlowdownListener;

   private NoSlow() {
      this.mode = this.addSetting(new ModeSetting(("Режим"), ("Способ обхода замедления."), ("Grim Ticks"), ("Vanilla"), ("Grim Ticks"), ("Арбалет")));
      this.playerTickListener = event -> {
         if (minecraftClient.player != null) {
            if (this.mode.isSelected(("Grim Ticks"))) {
               if (minecraftClient.player.isUsingItem()) {
                  this.itemUseTicks++;
               } else {
                  this.itemUseTicks = 0;
               }
            }
         }
      };
      this.itemUseSlowdownListener = slowdownEvent -> {
         if (minecraftClient.player != null) {
            minecraftClient.player.setSprinting(minecraftClient.player.input.hasForwardMovement());
            if (this.mode.isSelected(("Grim Ticks"))) {
               if (this.itemUseTicks > 1) {
                  slowdownEvent.markCancelled();
                  this.itemUseTicks = 0;
               }
            } else if (this.mode.isSelected(("Арбалет"))) {
               if (minecraftClient.player.getMainHandStack().getItem() instanceof CrossbowItem) {
                  slowdownEvent.markCancelled();
               }
            } else if (this.mode.isSelected(("Vanilla"))) {
               slowdownEvent.markCancelled();
            }
         }
      };
   }

   public static NoSlow getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new NoSlow();
      }

      return INSTANCE;
   }
}
