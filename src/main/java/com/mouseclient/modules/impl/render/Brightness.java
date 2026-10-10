package com.mouseclient.modules.impl.render;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.modules.impl.combat.ModeSetting;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.network.PlayerTickEvent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

@ModuleInfo(name = "Brightness", description = "Убирает темноту: поднимает гамму мира или вешает ночное зрение.", category = ModuleCategory.RENDER)
public final class Brightness extends Module {
   private static Brightness INSTANCE;
   public ModeSetting lighting;
   public DecimalSetting gammaStrength;
   private boolean appliedNightVision;
   private final EventListener<PlayerTickEvent> playerTickListener = event -> {
      if (minecraftClient.player != null) {
         if (this.lighting.isSelected(("Зелье"))) {
            minecraftClient.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 300, 0, false, false));
            this.appliedNightVision = true;
         } else if (this.appliedNightVision) {
            minecraftClient.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
            this.appliedNightVision = false;
         }
      }
   };

   public static Brightness getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new Brightness();
      }

      return INSTANCE;
   }

   @Override
   protected void onDisable() {
      if (minecraftClient.player != null && this.appliedNightVision) {
         minecraftClient.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
      }

      this.appliedNightVision = false;
   }

   private Brightness() {
      this.lighting = this.addSetting(new ModeSetting(("Освещение"), ("Чем светить в темноте."), ("Гамма"), ("Гамма"), ("Зелье")));
      this.gammaStrength = this.addSetting(new DecimalSetting(("Сила гаммы"), ("Во сколько раз поднять яркость мира."), (5.0D), 1.0, (15.0D), (0.1D)));
      this.gammaStrength.setVisibility(() -> this.lighting.isSelected(("Гамма")));
   }
}
