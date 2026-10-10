package com.mouseclient.modules.impl.misc;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.gui.ChatMessages;
import com.mouseclient.internal.PacketEvent;
import java.util.Arrays;
import net.minecraft.network.packet.s2c.play.MapUpdateS2CPacket;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

@ModuleInfo(name = "StreamerMod", description = "Что.", category = ModuleCategory.MISC)
public final class StreamerMod extends Module {
   private static StreamerMod INSTANCE;
   public final EventListener<PacketEvent> packetListener = event -> {
      if (event.isIncoming() && event.getPacket() instanceof MapUpdateS2CPacket mapPacket && !mapPacket.updateData().isEmpty()) {
         int mapId = mapPacket.mapId().id();
         if (mapId >= 8000 && mapId < 8044) {
            Arrays.fill(mapPacket.updateData().get().colors(), (byte)25);
         }
      }
   };

   public Text maskSidebarTitle(Text title) {
      return Text.literal(title.getString().isEmpty() ? "" : title.getString().charAt(0) + " ")
         .formatted(Formatting.GOLD)
         .append(Text.literal(("Дуэли #4")).formatted(Formatting.RED, Formatting.BOLD));
   }

   public static StreamerMod getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new StreamerMod();
      }

      return INSTANCE;
   }

   public boolean shouldHideSidebarEntry(ScoreboardEntry entry) {
      return minecraftClient.world == null
         ? false
         : Team.decorateName(minecraftClient.world.getScoreboard().getScoreHolderTeam(entry.owner()), entry.name()).getString().toLowerCase().contains(("фортун"));
   }

   public Text bossBarTitle() {
      return ChatMessages.gradientText(("Обычная дуэль"), (47084), (3047398));
   }

   public boolean shouldHideChatMessage(String message) {
      return !message.contains(("\n")) || !message.toLowerCase().contains(("spookytime")) && !message.replaceAll(("(?i)§[0-9a-fk-or]"), "").strip().startsWith(("**"))
         ? !message.replaceAll(("(?i)§[0-9a-fk-or]"), "").strip().isEmpty() && ("╔╠╚").indexOf(message.replaceAll(("(?i)§[0-9a-fk-or]"), "").strip().charAt(0)) >= 0
         : true;
   }
}
