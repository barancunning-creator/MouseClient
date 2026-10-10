package com.mouseclient.network;

import com.mouseclient.gui.ChatMessages;
import com.mouseclient.internal.PacketEvent;
import com.mouseclient.mixin.BossBarHudAccessor;
import com.mouseclient.modules.impl.misc.EventListener;
import com.mouseclient.modules.impl.misc.EventBus;
import com.mouseclient.util.entity.MinecraftAccess;
import com.mouseclient.util.time.Stopwatch;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

public class ReconnectCommand implements ClientCommand, MinecraftAccess {
   private static final Pattern ANARCHY_NUMBER_PATTERN = Pattern.compile(("(?:#|№)\\s*(\\d+)(?!\\d)"));
   private final Stopwatch menuTimer = new Stopwatch();
   private boolean leavingForHub;
   private boolean categorySelected;
   private boolean anarchySelected;
   private int targetAnarchy;
   private long rejoinAtMillis;
   private final EventListener<PacketEvent> packetListener = event -> {
      if (this.targetAnarchy != 0 && event.getPacket() instanceof GameMessageS2CPacket messagePacket && event.isIncoming()) {
         String messageText = messagePacket.content().getString().toLowerCase();
         if (!messageText.contains(("хаб")) && messageText.contains(("не удалось"))) {
            ChatMessages.printPrefixed(("&7На эту анархию нельзя зайти"));
            this.reset();
            EventBus.getInstance().unsubscribe(this);
         }
      }
   };
   private final EventListener<PlayerTickEvent> playerTickListener = event -> {
      if (this.rejoinAtMillis == 0L) {
         if (this.targetAnarchy != 0) {
            if (!this.serverType().equals(("HolyWorld"))) {
               this.reset();
               EventBus.getInstance().unsubscribe(this);
            } else if (!this.leavingForHub && this.currentAnarchy(("HolyWorld")) == this.targetAnarchy) {
               this.reset();
               EventBus.getInstance().unsubscribe(this);
            } else if (this.leavingForHub) {
               if (this.currentAnarchy(("HolyWorld")) == -1) {
                  this.leavingForHub = false;
               } else {
                  minecraftClient.player.networkHandler.sendChatCommand(("hub"));
               }
            } else if (minecraftClient.currentScreen instanceof GenericContainerScreen containerScreen && containerScreen.getTitle().getString().equals(("Выбор Лайт анархии:"))) {
               if (containerScreen.getScreenHandler().getInventory().size() < 10) {
                  if (!this.categorySelected) {
                     InventoryActions.clickSlot(categorySlot(this.targetAnarchy), 0, SlotActionType.PICKUP, false);
                     this.categorySelected = true;
                  }
               } else {
                  if (!this.anarchySelected) {
                     int anarchySlot = findAnarchySlot(containerScreen, this.targetAnarchy);
                     if (anarchySlot < 0) {
                        anarchySlot = fallbackAnarchySlot(this.targetAnarchy);
                     }

                     InventoryActions.clickSlot(anarchySlot, 0, SlotActionType.PICKUP, false);
                     this.anarchySelected = true;
                  }
               }
            } else {
               if (this.menuTimer.hasElapsed((500L))) {
                  this.menuTimer.reset();
                  minecraftClient.player.networkHandler.sendChatCommand(("lite"));
               }
            }
         }
      } else if (minecraftClient.player != null && minecraftClient.world != null && System.currentTimeMillis() >= this.rejoinAtMillis) {
         int currentAnarchyNumber = this.currentAnarchy(this.serverType());
         if (currentAnarchyNumber < 1 || currentAnarchyNumber > 63) {
            currentAnarchyNumber = Math.abs(this.targetAnarchy);
         }

         minecraftClient.player.networkHandler.sendChatCommand("an" + currentAnarchyNumber);
         this.reset();
         EventBus.getInstance().unsubscribe(this);
      }
   };

   private String serverType() {
      if (minecraftClient.player != null
         && minecraftClient.world != null
         && minecraftClient.getNetworkHandler() != null
         && minecraftClient.getNetworkHandler().getServerInfo() != null
         && minecraftClient.getNetworkHandler().getBrand() != null) {
         String address = minecraftClient.getNetworkHandler().getServerInfo().address.toLowerCase(Locale.ROOT);
         String brand = minecraftClient.getNetworkHandler().getBrand().toLowerCase(Locale.ROOT);
         if (brand.contains(("botfilter"))) {
            return ("FunTime");
         } else if (address.contains(("funtime")) || address.contains(("skytime")) || address.contains(("space-times")) || address.contains(("funsky")) || address.contains(("spookytime"))) {
            return ("CopyTime");
         } else if (brand.contains(("holyworld")) || brand.contains(("leaf")) || brand.contains(("vk.com/idwok")) || brand.contains(("(proxy "))) {
            return ("HolyWorld");
         } else {
            return address.contains(("reallyworld")) ? ("ReallyWorld") : ("Vanilla");
         }
      } else {
         return ("Vanilla");
      }
   }

   @Override
   public String name() {
      return ("rct");
   }

   private static int anarchyNumberFromItem(ItemStack stack) {
      int anarchy = parseAnarchyNumber(stack.getName().getString());
      if (anarchy > 0) {
         return anarchy;
      }

      LoreComponent lore = stack.get(DataComponentTypes.LORE);
      if (lore == null) {
         return -1;
      }

      for (Text text : lore.lines()) {
         anarchy = parseAnarchyNumber(text.getString());
         if (anarchy > 0) {
            return anarchy;
         }
      }

      return -1;
   }

   private static boolean usesAnarchyChatCommand(String serverType) {
      return serverType.equals(("CopyTime")) || serverType.equals(("FunTime"));
   }

   private int currentAnarchy(String serverType) {
      Scoreboard scoreboard = minecraftClient.world.getScoreboard();
      ScoreboardObjective sidebarObjective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
      switch (serverType) {
         case ("FunTime"):
         case ("CopyTime"):
            if (sidebarObjective != null) {
               String[] titleParts = sidebarObjective.getDisplayName().getString().split(("-"));
               if (titleParts.length > 1) {
                  return Integer.parseInt(titleParts[1]);
               }
            }
            break;
         case ("HolyWorld"):
            if (sidebarObjective == null) {
               return -1;
            }

            for (ScoreboardEntry entry : scoreboard.getScoreboardEntries(sidebarObjective)) {
               String decoratedName = Team.decorateName(scoreboard.getScoreHolderTeam(entry.owner()), entry.name()).getString();
               int numberStart = decoratedName.indexOf(35);
               int numberEnd = decoratedName.indexOf((" -◆-"), numberStart + 1);
               if (numberStart != -1 && numberEnd > numberStart + 1) {
                  return Integer.parseInt(decoratedName.substring(numberStart + 1, numberEnd));
               }
            }
      }

      return -1;
   }

   @Override
   public String description() {
      return ("Переподключение к текущей анархии (HolyWorld / CopyTime)");
   }

   private static int categorySlot(int anarchy) {
      return anarchy < 15 ? 0 : (anarchy < 33 ? 1 : (anarchy < 48 ? 2 : 3));
   }

   @Override
   public void execute(String[] arguments) {
      String serverType = this.serverType();
      if (!serverType.equals(("HolyWorld")) && !usesAnarchyChatCommand(serverType)) {
         ChatMessages.printPrefixed(("&7Не работает на этом сервере"));
      } else if (serverType.equals(("HolyWorld"))
         && ((BossBarHudAccessor)(Object)minecraftClient.inGameHud.getBossBarHud())
            .getEvents()
            .values()
            .stream()
            .map(bossBar -> bossBar.getName().getString().toLowerCase())
            .anyMatch(bossBarText -> bossBarText.contains(("pvp")) || bossBarText.contains(("пвп")))) {
         ChatMessages.printPrefixed(("&7Вы в режиме PvP"));
      } else {
         int anarchy = this.currentAnarchy(serverType);
         if (anarchy > 0 && (anarchy < 64 || usesAnarchyChatCommand(serverType))) {
            EventBus.getInstance().subscribe(this);
            this.categorySelected = false;
            this.anarchySelected = false;
            this.targetAnarchy = anarchy;
            if (usesAnarchyChatCommand(serverType)) {
               this.leavingForHub = false;
               this.rejoinAtMillis = System.currentTimeMillis() + (900L);
               minecraftClient.player.networkHandler.sendChatCommand(("hub"));
            } else {
               this.leavingForHub = true;
               this.rejoinAtMillis = 0L;
            }
         } else {
            ChatMessages.printPrefixed(("&7Не верный лайт"));
         }
      }
   }

   private static int parseAnarchyNumber(String text) {
      if (text != null && !text.isEmpty()) {
         Matcher matcher = ANARCHY_NUMBER_PATTERN.matcher(text);
         return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
      } else {
         return -1;
      }
   }

   private static int fallbackAnarchySlot(int anarchy) {
      int firstAnarchy = anarchy < 15 ? 1 : (anarchy < 33 ? 15 : (anarchy < 48 ? 33 : 48));
      return 18 + anarchy - firstAnarchy;
   }

   private void reset() {
      this.targetAnarchy = 0;
      this.leavingForHub = false;
      this.rejoinAtMillis = 0L;
      this.categorySelected = false;
      this.anarchySelected = false;
   }

   private static int findAnarchySlot(GenericContainerScreen screen, int anarchy) {
      for (Slot slot : screen.getScreenHandler().slots) {
         if (slot.id >= 18 && !slot.getStack().isEmpty() && anarchyNumberFromItem(slot.getStack()) == anarchy) {
            return slot.id;
         }
      }

      return -1;
   }
}
