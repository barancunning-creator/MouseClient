package com.mouseclient.modules.impl.misc;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.gui.ChatMessages;
import com.mouseclient.internal.PacketEvent;
import com.mouseclient.modules.impl.combat.ModeSetting;
import com.mouseclient.network.PlayerTickEvent;
import com.mouseclient.util.time.GuiVisuals;
import java.util.ArrayDeque;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

@ModuleInfo(
   name = "DuelHelper",
   description = "Сам ищет дуэли: встаёт в очередь, перезапускает поиск и выходит после боя.",
   category = ModuleCategory.MISC
)
public final class DuelHelper extends Module {
   private static DuelHelper INSTANCE;
   public ModeSetting mode;
   private final ArrayDeque<DuelHelper.ScheduledDuelCommand> scheduledCommands = new ArrayDeque<>();
   private DuelHelper.DuelSearchState searchState = DuelHelper.DuelSearchState.IDLE;
   private long searchRequestedAtMillis;
   private long queueEnteredAtMillis;
   private long duelStartedAtMillis;
   private String lastMode;
   private final EventListener<PlayerTickEvent> playerTickListener = event -> {
      if (minecraftClient.player != null && minecraftClient.getNetworkHandler() != null) {
         long nowMillis = System.currentTimeMillis();
         if (!this.mode.getSelectedOption().equals(this.lastMode)) {
            this.lastMode = this.mode.getSelectedOption();
            this.printStatus("Режим: " + this.mode.getSelectedOption().toLowerCase());
            this.scheduleRestart(0L);
         }

         while (!this.scheduledCommands.isEmpty() && this.scheduledCommands.peek().runAt() <= nowMillis) {
            String command = this.scheduledCommands.poll().command();
            if (command == null) {
               this.beginSearch();
            } else {
               minecraftClient.getNetworkHandler().sendChatCommand(command);
            }
         }

         if (this.searchState == DuelHelper.DuelSearchState.WAITING_SEARCH_CONFIRM && nowMillis - this.searchRequestedAtMillis >= (2000L)) {
            this.beginSearch();
         } else if (this.searchState == DuelHelper.DuelSearchState.IN_DUEL
            && this.scheduledCommands.isEmpty()
            && nowMillis - this.duelStartedAtMillis >= (2500L)) {
            this.printStatus(("Дуэль началась, модуль выключен"));
            this.setEnabled(false);
         } else if (this.searchState == DuelHelper.DuelSearchState.IN_QUEUE
            && this.scheduledCommands.isEmpty()
            && nowMillis - this.queueEnteredAtMillis >= (65000L)) {
            this.printStatus(("Прошло 1:05 без соперника, ищем заново"));
            this.beginSearch();
         }
      }
   };
   private final EventListener<PacketEvent> packetListener = event -> {
      if (event.isIncoming() && event.getPacket() instanceof GameMessageS2CPacket messagePacket && !messagePacket.overlay()) {
         String message = messagePacket.content().getString();
         minecraftClient.execute(() -> this.handleDuelMessage(message));
      }
   };

   public static DuelHelper getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new DuelHelper();
      }

      return INSTANCE;
   }

   private void beginSearch() {
      this.searchState = DuelHelper.DuelSearchState.WAITING_SEARCH_CONFIRM;
      this.searchRequestedAtMillis = System.currentTimeMillis();
      if (minecraftClient.player != null && minecraftClient.getNetworkHandler() != null) {
         minecraftClient.getNetworkHandler().sendChatCommand(this.mode.isSelected(("Командный")) ? ("duel team search") : ("duel search"));
      }
   }

   private void handleDuelMessage(String message) {
      if (this.isEnabled()) {
         if (message.contains(("Вы начали поиск")) || message.contains(("уже был начат"))) {
            this.searchState = DuelHelper.DuelSearchState.IN_QUEUE;
            this.queueEnteredAtMillis = System.currentTimeMillis();
            this.printStatus(("В очереди, ищем соперника"));
         }

         if (message.contains(("был отменен")) || message.contains(("был отменён")) || message.contains(("не было найдено противников"))) {
            this.printStatus(("Поиск отменён, перезапускаем"));
            this.searchState = DuelHelper.DuelSearchState.IDLE;
            this.scheduledCommands.clear();
            this.scheduledCommands.add(new DuelHelper.ScheduledDuelCommand(null, System.currentTimeMillis() + (300L)));
         }

         if (message.contains(("Дуэль начнётся")) || message.contains(("Дуэль начнется"))) {
            this.duelStartedAtMillis = System.currentTimeMillis();
            if (this.searchState != DuelHelper.DuelSearchState.IN_DUEL) {
               this.printStatus(("Соперник найден, дуэль начинается"));
            }

            this.searchState = DuelHelper.DuelSearchState.IN_DUEL;
         }

         if (message.contains(("покинул дуэль"))
            && this.searchState == DuelHelper.DuelSearchState.IN_DUEL
            && (minecraftClient.player == null || !message.contains(minecraftClient.player.getName().getString()))) {
            this.printStatus(("Соперник ливнул, ищем заново"));
            this.scheduleRestart((300L));
         }

         if (message.contains(("одержала Победу")) || message.contains(("одержал Победу"))) {
            this.printStatus(("Победа, ищем следующую дуэль"));
            this.scheduleRestart((300L));
         }

         if (message.contains(("одержала Поражение")) || message.contains(("одержал Поражение")) || message.contains(("потерпела Поражение"))) {
            this.printStatus(("Поражение, ищем снова"));
            this.scheduleRestart((300L));
         }

         if (message.contains(("Дуэль завершена")) && this.searchState == DuelHelper.DuelSearchState.IN_DUEL) {
            this.scheduleRestart((300L));
         }
      }
   }

   private void printStatus(String message) {
      ChatMessages.print(
         ChatMessages.gradientText(("DuelHelper"), GuiVisuals.accentColor, GuiVisuals.accentGradientColor, true)
            .append(Text.literal(Formatting.DARK_GRAY + " » " + Formatting.RESET))
            .append(Text.literal(message))
      );
   }

   private DuelHelper() {
      this.mode = this.addSetting(new ModeSetting(("Режим"), ("Одиночный /duel search, Командный /duel team search."), ("Одиночный"), ("Одиночный"), ("Командный")));
   }

   @Override
   protected void onEnable() {
      this.scheduledCommands.clear();
      this.lastMode = this.mode.getSelectedOption();
      this.printStatus("Включён, " + this.mode.getSelectedOption().toLowerCase() + " режим");
      this.beginSearch();
   }

   @Override
   protected void onDisable() {
      this.scheduledCommands.clear();
      this.searchState = DuelHelper.DuelSearchState.IDLE;
   }

   private void scheduleRestart(long delayMillis) {
      this.searchState = DuelHelper.DuelSearchState.IDLE;
      this.scheduledCommands.clear();
      long nowMillis = System.currentTimeMillis();
      this.scheduledCommands.add(new DuelHelper.ScheduledDuelCommand(("duel leave"), nowMillis + delayMillis));
      this.scheduledCommands.add(new DuelHelper.ScheduledDuelCommand(null, nowMillis + delayMillis + (300L)));
   }

   private enum DuelSearchState {
      IDLE,
      WAITING_SEARCH_CONFIRM,
      IN_QUEUE,
      IN_DUEL;
   }

   private record ScheduledDuelCommand(String command, long runAt) {
   }
}
