package com.mouseclient.modules.impl.misc;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.gui.ChatMessages;
import com.mouseclient.modules.impl.combat.ModeSetting;
import com.mouseclient.network.PlayerTickEvent;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.entity.boss.BossBar.Color;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;

@ModuleInfo(name = "YTMODE", description = "Прячет фортуну, маскирует боссбар под дуэль и рисует невидимкам 1000 хп.", category = ModuleCategory.MISC)
public final class YoutubeModeModule extends Module {
   private static YoutubeModeModule INSTANCE;
   public ModeSetting mode;
   private long reconnectAtMillis;
   private ServerInfo reconnectServer;
   private final EventListener<PlayerTickEvent> playerTickListener = event -> {
      if (this.reconnectAtMillis != 0L && System.currentTimeMillis() >= this.reconnectAtMillis && this.reconnectServer != null && minecraftClient.world != null) {
         this.reconnectAtMillis = 0L;
         ServerInfo server = this.reconnectServer;
         minecraftClient.send(() -> {
            if (minecraftClient.world != null) {
               minecraftClient.world.disconnect(Text.literal(("YTMODE: смена скина")));
               minecraftClient.disconnectWithProgressScreen();
               ConnectScreen.connect(new MultiplayerScreen(new TitleScreen()), minecraftClient, ServerAddress.parse(server.address), server, false, null);
            }
         });
      }
   };

   public Text bossBarName() {
      return this.mode.isSelected(("2x2")) ? ChatMessages.gradientText(("Командная дуэль"), (47084), (3047398)) : ChatMessages.gradientText(("Обычная дуэль"), (47084), (3047398));
   }

   public Color bossBarColor() {
      return Color.BLUE;
   }

   public boolean shouldReplaceBossBar() {
      return this.isEnabled() && !this.mode.isSelected(("Rtp"));
   }

   private YoutubeModeModule() {
      this.mode = this.addSetting(new ModeSetting(("Режим"), ("Подо что маскируется картинка."), ("1x1"), ("1x1"), ("Rtp"), ("2x2")));
   }

   public static YoutubeModeModule getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new YoutubeModeModule();
      }

      return INSTANCE;
   }

   public boolean isFortuneScore(ScoreboardEntry entry) {
      return minecraftClient.world == null
         ? false
         : Team.decorateName(minecraftClient.world.getScoreboard().getScoreHolderTeam(entry.owner()), entry.name()).getString().toLowerCase().contains(("фортун"));
   }

   @Override
   protected void onEnable() {
      if (minecraftClient.player != null && minecraftClient.getNetworkHandler() != null && !minecraftClient.isConnectedToLocalServer()) {
         minecraftClient.player.networkHandler.sendChatCommand(("skin s_FunTime_1"));
         this.reconnectServer = minecraftClient.getCurrentServerEntry();
         this.reconnectAtMillis = System.currentTimeMillis() + (1000L);
      }
   }

   public boolean shouldHideChatMessage(String message) {
      return !message.contains(("\n")) || !message.toLowerCase().contains(("spookytime")) && !message.replaceAll(("(?i)§[0-9a-fk-or]"), "").strip().startsWith(("**"))
         ? !message.replaceAll(("(?i)§[0-9a-fk-or]"), "").strip().isEmpty() && ("╔╠╚").indexOf(message.replaceAll(("(?i)§[0-9a-fk-or]"), "").strip().charAt(0)) >= 0
         : true;
   }

   @Override
   protected void onDisable() {
      this.reconnectAtMillis = 0L;
      this.reconnectServer = null;
   }
}
