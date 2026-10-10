package com.mouseclient.modules.impl.misc;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.internal.FriendManager;
import com.mouseclient.mixin.ClientCommonNetworkHandlerAccessor;
import com.mouseclient.modules.impl.combat.MultiSelectSetting;
import com.mouseclient.modules.impl.render.BooleanSetting;
import com.mouseclient.modules.impl.render.DecimalSetting;
import com.mouseclient.network.PlayerTickEvent;
import com.mouseclient.util.time.ToastNotifications;
import com.mouseclient.util.time.StaffHud;
import java.util.Locale;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;

@ModuleInfo(name = "AutoLeave", description = "Сам уходит с сервера: мало ХП, модератор в табе или чужой игрок рядом.", category = ModuleCategory.MISC)
public final class AutoLeave extends Module {
   private static AutoLeave INSTANCE;
   public MultiSelectSetting conditions;
   public BooleanSetting disconnectMode;
   public FilteredTextSetting command;
   public DecimalSetting minHealth;
   public DecimalSetting distance;
   private boolean leaveTriggered;
   private final EventListener<PlayerTickEvent> playerTickListener = event -> {
      if (!this.leaveTriggered
         && minecraftClient.player != null
         && minecraftClient.world != null
         && minecraftClient.getNetworkHandler() != null
         && !minecraftClient.isConnectedToLocalServer()) {
         if (this.conditions.matchesOption(("Мало ХП")) && minecraftClient.player.getHealth() <= this.minHealth.getValue()) {
            this.leaveServer("Здоровье " + String.format(Locale.US, ("%.1f"), minecraftClient.player.getHealth()) + " хп");
         } else {
            if (this.conditions.matchesOption(("Модератор"))) {
               for (PlayerListEntry entry : minecraftClient.getNetworkHandler().getPlayerList()) {
                  if (StaffHud.PLAYER_NAME_PATTERN.matcher(entry.getProfile().name()).matches()) {
                     Team team = minecraftClient.world.getScoreboard().getScoreHolderTeam(entry.getProfile().name());
                     String rankText = team != null && !team.getPrefix().getString().trim().isEmpty()
                        ? team.getPrefix().getString().trim()
                        : (entry.getDisplayName() == null ? "" : entry.getDisplayName().getString().replace(entry.getProfile().name(), "").trim());
                     if (!rankText.isEmpty() && StaffHud.STAFF_PREFIX_PATTERN.matcher(rankText.toLowerCase(Locale.ROOT)).matches()) {
                        this.leaveServer("Модератор " + entry.getProfile().name() + " в табе");
                        return;
                     }
                  }
               }
            }

            if (this.conditions.matchesOption(("Игрок рядом"))) {
               for (AbstractClientPlayerEntity player : minecraftClient.world.getPlayers()) {
                  if (player != minecraftClient.player
                     && !FriendManager.contains(player.getGameProfile().name())
                     && !(player.distanceTo(minecraftClient.player) > this.distance.getValue())) {
                     this.leaveServer(
                        "Игрок " + player.getGameProfile().name() + " в " + String.format(Locale.US, ("%.1f"), player.distanceTo(minecraftClient.player)) + " бл"
                     );
                     return;
                  }
               }
            }
         }
      }
   };

   private void leaveServer(String reason) {
      this.leaveTriggered = true;
      if (this.disconnectMode.getValue()) {
         ((ClientCommonNetworkHandlerAccessor)(Object)minecraftClient.player.networkHandler).connection().disconnect(Text.literal("[AutoLeave] " + reason));
      } else if (!this.command.getText().isBlank()) {
         minecraftClient.player
            .networkHandler
            .sendChatCommand(
               this.command.getText().startsWith(("/")) ? this.command.getText().substring(1) : this.command.getText()
            );
         ToastNotifications.showIconToast(this.category.iconGlyph(), ("AutoLeave"), reason, true);
      }

      this.setEnabled(false);
   }

   @Override
   protected void onEnable() {
      this.leaveTriggered = false;
   }

   private AutoLeave() {
      this.conditions = this.addSetting(new MultiSelectSetting(("Условия"), ("Когда уходить: мало ХП, модератор появился в табе, не-друг подошёл близко."), new String[]{("Мало ХП"), ("Модератор"), ("Игрок рядом")}));
      this.disconnectMode = this.addSetting(new BooleanSetting(("Разрыв соединения"), ("Полностью выйти с сервера вместо команды."), false));
      this.command = this.addSetting(new FilteredTextSetting(("Команда"), ("Команда ухода, когда разрыв выключен."), ("/hub"), 32));
      this.command.setVisibility(() -> !this.disconnectMode.getValue());
      this.minHealth = this.addSetting(new DecimalSetting(("Мин. ХП"), ("Уходить, когда здоровье не выше этого."), (5.0D), 1.0, (20.0D), (0.5D)));
      this.minHealth.setVisibility(() -> this.conditions.matchesOption(("Мало ХП")));
      this.distance = this.addSetting(new DecimalSetting(("Дистанция"), ("Уходить, когда не-друг ближе этого расстояния."), (10.0D), 1.0, (100.0D), 1.0));
      this.distance.setVisibility(() -> this.conditions.matchesOption(("Игрок рядом")));
   }

   public static AutoLeave getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new AutoLeave();
      }

      return INSTANCE;
   }
}
