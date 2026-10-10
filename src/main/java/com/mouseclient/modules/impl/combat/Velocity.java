package com.mouseclient.modules.impl.combat;

import com.mouseclient.annotation.EventPriority;
import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.internal.PacketEvent;
import com.mouseclient.modules.impl.misc.Module;
import com.mouseclient.modules.impl.misc.ModuleCategory;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.modules.impl.render.BooleanSetting;
import com.mouseclient.modules.impl.render.DecimalSetting;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.sound.SoundEvents;

@ModuleInfo(name = "Velocity", description = "Убирает откидывание от ударов и взрывов.", category = ModuleCategory.COMBAT)
public final class Velocity extends Module {
   private static Velocity INSTANCE;
   public MultiSelectSetting cancelSources;
   public ModeSetting mode;
   public BooleanSetting delayedJump;
   public DecimalSetting jumpIntervalTicks;
   private boolean downwardOnlyVelocity;
   private int ticksSinceJump;
   private final EventListener<PacketEvent> packetListener = event -> {
      if (minecraftClient.player != null && event.isIncoming()) {
         if (event.getPacket() instanceof ExplosionS2CPacket explosionPacket) {
            boolean windCharge = explosionPacket.explosionSound().value() == SoundEvents.ENTITY_WIND_CHARGE_WIND_BURST.value();
            if (windCharge ? this.cancelSources.matchesOption(("Заряд ветра")) : this.cancelSources.matchesOption(("Взрывы"))) {
               event.markCancelled();
            }
         } else if (this.cancelSources.matchesOption(("Удары"))) {
            if (event.getPacket() instanceof EntityVelocityUpdateS2CPacket velocityPacket && velocityPacket.getEntityId() == minecraftClient.player.getId()) {
               if (this.mode.isSelected(("Обычный"))) {
                  event.markCancelled();
               } else {
                  this.downwardOnlyVelocity = velocityPacket.getVelocity().x == 0.0 && velocityPacket.getVelocity().z == 0.0 && velocityPacket.getVelocity().y < 0.0;
               }
            }
         }
      }
   };
   @EventPriority(100)
   private final EventListener<MovementInputEvent> movementInputListener = event -> {
      if (minecraftClient.player != null && this.cancelSources.matchesOption(("Удары")) && this.mode.isSelected(("JumpReset")) && AttackAura.getInstance().isEnabled()) {
         if (!minecraftClient.player.isTouchingWater() && !minecraftClient.player.isSubmergedInWater() && !minecraftClient.player.isInLava()) {
            if (minecraftClient.player.hurtTime != 9
               || !minecraftClient.player.isOnGround()
               || !minecraftClient.player.isSprinting()
               || this.downwardOnlyVelocity
               || this.delayedJump.getValue() && this.ticksSinceJump < (int)this.jumpIntervalTicks.getValue()) {
               this.ticksSinceJump++;
            } else {
               event.setJumping(true);
               this.ticksSinceJump = 0;
            }
         }
      }
   };

   @Override
   protected void onDisable() {
      this.downwardOnlyVelocity = false;
      this.ticksSinceJump = 0;
   }

   private Velocity() {
      this.cancelSources = this.addSetting(new MultiSelectSetting(("Отменять"), ("От чего убирать откидывание."), new String[]{("Удары"), ("Взрывы"), ("Заряд ветра")}, ("Удары")));
      this.mode = this.addSetting(new ModeSetting(("Тип"), ("Как гасить откидывание от ударов."), ("Обычный"), ("Обычный"), ("JumpReset")));
      this.mode.setVisibility(() -> this.cancelSources.matchesOption(("Удары")));
      this.delayedJump = this.addSetting(new BooleanSetting(("Прыгать с задержкой тиков"), ("Прыгать только после паузы между джамп-ресетами."), true));
      this.jumpIntervalTicks = this.addSetting(new DecimalSetting(("Тиков между прыжками"), ("Сколько тиков ждать между джамп-ресетами."), (2.0D), 0.0, (20.0D), 1.0));
      this.delayedJump.setVisibility(() -> this.cancelSources.matchesOption(("Удары")) && this.mode.isSelected(("JumpReset")));
      this.jumpIntervalTicks.setVisibility(() -> this.cancelSources.matchesOption(("Удары")) && this.mode.isSelected(("JumpReset")) && this.delayedJump.getValue());
   }

   public static Velocity getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new Velocity();
      }

      return INSTANCE;
   }
}
