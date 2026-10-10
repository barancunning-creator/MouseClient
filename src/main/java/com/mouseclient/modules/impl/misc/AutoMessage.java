package com.mouseclient.modules.impl.misc;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.internal.PacketEvent;
import com.mouseclient.modules.impl.combat.ModeSetting;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.util.Formatting;

@ModuleInfo(name = "AutoMessage", description = "Автоматически отправляет сообщение в личку убитому игроку.", category = ModuleCategory.MISC)
public final class AutoMessage extends Module {
   private static AutoMessage INSTANCE;
   private static final Pattern KILL_REWARD_PATTERN = Pattern.compile(("\\[\\$] Вы подобрали \\$\\d+ игрока (.+)"));
   public ModeSetting mode;
   public FilteredTextSetting messageText;
   private final EventListener<PacketEvent> packetListener = event -> {
      if (minecraftClient.player != null && minecraftClient.world != null && event.isIncoming() && event.getPacket() instanceof GameMessageS2CPacket messagePacket) {
         Matcher rewardMatcher = KILL_REWARD_PATTERN.matcher(Formatting.strip(messagePacket.content().getString()));
         if (rewardMatcher.find()) {
            if (this.mode.isSelected(("Дуэль"))) {
               minecraftClient.player.networkHandler.sendChatMessage(this.messageText.getValue().trim());
            } else {
               minecraftClient.player.networkHandler.sendChatCommand("m " + rewardMatcher.group(1).trim() + " " + this.messageText.getValue().trim());
            }
         }
      }
   };

   private AutoMessage() {
      this.mode = this.addSetting(new ModeSetting(("Режим"), ("РТП пишет убитому в личку, Дуэль — в общий чат."), ("РТП"), ("РТП"), ("Дуэль")));
      this.messageText = this.addSetting(new FilteredTextSetting(("Текст сообщения"), ("Что написать убитому."), ("ez"), 256));
   }

   public static AutoMessage getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new AutoMessage();
      }

      return INSTANCE;
   }
}
