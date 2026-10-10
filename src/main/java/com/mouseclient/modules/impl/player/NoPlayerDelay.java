package com.mouseclient.modules.impl.player;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.mixin.MinecraftCooldownAccessor;
import com.mouseclient.mixin.LivingEntityAccessor;
import com.mouseclient.modules.impl.combat.MultiSelectSetting;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.network.PlayerTickEvent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ExperienceBottleItem;
import net.minecraft.item.GlassBottleItem;

@ModuleInfo(name = "NoPlayerDelay", description = "Убирает выбранные задержки действий игрока.", category = ModuleCategory.PLAYER)
public final class NoPlayerDelay extends Module {
   private static NoPlayerDelay INSTANCE;
   public MultiSelectSetting delays;
   private final EventListener<PlayerTickEvent> playerTickListener = event -> {
      if (minecraftClient.player != null) {
         if (this.delays.matchesOption(("Прыжок"))) {
            ((LivingEntityAccessor)(Object)minecraftClient.player).setNoJumpDelay(0);
         }

         if (minecraftClient.player.getMainHandStack().getItem() instanceof BlockItem && this.delays.matchesOption(("Ставка блоков"))
            || minecraftClient.player.getMainHandStack().getItem() instanceof ExperienceBottleItem && this.delays.matchesOption(("Пузырьки опыта"))
            || minecraftClient.player.getMainHandStack().getItem() instanceof GlassBottleItem && this.delays.matchesOption(("Бутыльки без воды"))) {
            ((MinecraftCooldownAccessor)(Object)minecraftClient).setRightClickDelay(0);
         }
      }
   };

   private NoPlayerDelay() {
      this.delays = this.addSetting(new MultiSelectSetting(("Задержки"), ("Где отключать задержку."), new String[]{("Ставка блоков"), ("Прыжок"), ("Пузырьки опыта"), ("Бутыльки без воды")}));
   }

   public static NoPlayerDelay getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new NoPlayerDelay();
      }

      return INSTANCE;
   }
}
