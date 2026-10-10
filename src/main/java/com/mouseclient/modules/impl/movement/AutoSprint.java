package com.mouseclient.modules.impl.movement;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.modules.impl.render.BooleanSetting;
import com.mouseclient.network.PlayerTickEvent;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.UseEffectsComponent;

@ModuleInfo(name = "AutoSprint", description = "Автоспринт: не теряет скорость при ударе и умеет игнорить слепоту.", category = ModuleCategory.MOVEMENT)
public final class AutoSprint extends Module {
   private static AutoSprint INSTANCE;
   public BooleanSetting keepSprint;
   public BooleanSetting ignoreBlindness;
   private final EventListener<PlayerTickEvent> playerTickListener = event -> {
      if (minecraftClient.player != null) {
         minecraftClient.player
            .setSprinting(
               minecraftClient.player.input.hasForwardMovement()
                  && !minecraftClient.player.hasBlindnessEffect()
                  && (
                     minecraftClient.player.hasVehicle()
                        ? minecraftClient.player.getVehicle().canSprintAsVehicle() && minecraftClient.player.getVehicle().isLogicalSideForUpdatingMovement()
                        : minecraftClient.player.getHungerManager().canSprint() || minecraftClient.player.getAbilities().allowFlying
                  )
                  && (minecraftClient.player.getAbilities().flying || !minecraftClient.player.isPartlyTouchingWater())
                  && (
                     !minecraftClient.player.isUsingItem()
                        || minecraftClient.player.getActiveItem().getOrDefault(DataComponentTypes.USE_EFFECTS, UseEffectsComponent.DEFAULT).canSprint()
                  )
                  && (!minecraftClient.player.isGliding() || minecraftClient.player.isSubmergedInWater())
                  && (!minecraftClient.player.shouldSlowDown() || minecraftClient.player.isSubmergedInWater())
            );
      }
   };

   private AutoSprint() {
      this.keepSprint = this.addSetting(new BooleanSetting(("Кип спринт"), ("Не сбрасывать скорость спринта при ударе."), false));
      this.ignoreBlindness = this.addSetting(new BooleanSetting(("Игнор слепоты"), ("Спринтить даже под эффектом слепоты."), false));
   }

   public static AutoSprint getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new AutoSprint();
      }

      return INSTANCE;
   }

   public boolean preservesAttackSprint() {
      return this.isEnabled() && this.keepSprint.getValue();
   }

   public boolean ignoresBlindness() {
      return this.isEnabled() && this.ignoreBlindness.getValue();
   }
}
