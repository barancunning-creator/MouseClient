package com.mouseclient.modules.impl.misc;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.internal.FriendManager;
import com.mouseclient.modules.impl.render.BooleanSetting;
import com.mouseclient.network.PlayerTickEvent;
import com.mouseclient.network.InventoryActions;
import com.mouseclient.util.time.ToastNotifications;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

@ModuleInfo(name = "AutoAccept", description = "Сам принимает телепорты, приглашения в клан и командные дуэли.", category = ModuleCategory.MISC)
public final class AutoAccept extends Module {
   private static AutoAccept INSTANCE;
   public MultiChoiceSetting inviteTypes;
   public BooleanSetting friendsOnly;
   private static final String[] TELEPORT_INVITE_MARKERS = new String[]{
      ("просит телепортироваться"),
      ("хочет телепортироваться"),
      ("Заявка буудет автоматически отменена через"),
      ("просит телепортироваться к Вам."),
      ("120 секунд"),
      ("has requested to teleport"),
      ("teleport to you"),
      ("This request will timeout after"),
      ("120 seconds")
   };
   private static final Pattern CLAN_INVITE_PATTERN = Pattern.compile(("\\[⚔] (\\w+) приглашает Вас в клан"));
   private static final Pattern DUEL_INVITE_PATTERN = Pattern.compile(("\\[⚔] Игрок (.+?) приглашает вас в свою дуэльную команду!"));
   private long lastAcceptMillis;
   private boolean awaitingDuelConfirmation;
   public final EventListener<PlayerTickEvent> playerTickListener = event -> {
      if (this.awaitingDuelConfirmation && minecraftClient.player != null && minecraftClient.currentScreen != null) {
         if (minecraftClient.player.currentScreenHandler instanceof GenericContainerScreenHandler handler) {
            if (minecraftClient.currentScreen.getTitle().getString().contains(("Приглашение в команду"))) {
               Inventory inventory = handler.getInventory();

               for (int var4 = 0; var4 < inventory.size(); var4++) {
                  if (inventory.getStack(var4).getItem() == Items.GREEN_STAINED_GLASS_PANE) {
                     InventoryActions.clickSlot(var4, 0, SlotActionType.QUICK_MOVE, true);
                     minecraftClient.player.closeHandledScreen();
                     break;
                  }
               }
            }

            this.awaitingDuelConfirmation = false;
         }
      }
   };

   private boolean containsFriendName(String message) {
      for (String friendName : FriendManager.names()) {
         if (message.toLowerCase(Locale.ROOT).contains(friendName.toLowerCase(Locale.ROOT))) {
            return true;
         }
      }

      return false;
   }

   private AutoAccept() {
      this.inviteTypes = this.addSetting(new MultiChoiceSetting(("Принимать что/куда?"), ("Какие приглашения принимать самому.")).withOptions(("Телепортации"), ("В клан"), ("В командные дуэли")).withSelectedOptions(("Телепортации"), ("В клан"), ("В командные дуэли")));
      this.friendsOnly = this.addSetting(new BooleanSetting(("Только друзья"), ("Принимать, только если в сообщении есть ник друга."), true));
   }

   public static AutoAccept getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new AutoAccept();
      }

      return INSTANCE;
   }

   public void handleInviteMessage(String message) {
      if (this.isEnabled() && minecraftClient.player != null && message != null) {
         if (!this.friendsOnly.getValue() || this.containsFriendName(message)) {
            if (this.inviteTypes.matchesOption(("Телепортации"))) {
               for (String teleportMarker : TELEPORT_INVITE_MARKERS) {
                  if (message.contains(teleportMarker)) {
                     this.lastAcceptMillis = System.currentTimeMillis();
                     minecraftClient.player.networkHandler.sendChatCommand(("tpaccept"));
                     ToastNotifications.showIconToast(this.category.iconGlyph(), ("AutoAccept"), ("Принял телепорт"), true);
                     return;
                  }
               }
            }

            if (this.inviteTypes.matchesOption(("В клан"))) {
               Matcher clanInvite = CLAN_INVITE_PATTERN.matcher(message);
               if (clanInvite.find()) {
                  this.lastAcceptMillis = System.currentTimeMillis();
                  minecraftClient.player.networkHandler.sendChatCommand("clan accept " + clanInvite.group(1));
                  ToastNotifications.showIconToast(this.category.iconGlyph(), ("AutoAccept"), "Принял в клан от " + clanInvite.group(1), true);
                  return;
               }
            }

            if (this.inviteTypes.matchesOption(("В командные дуэли"))) {
               Matcher duelInvite = DUEL_INVITE_PATTERN.matcher(message);
               if (duelInvite.find()) {
                  this.lastAcceptMillis = System.currentTimeMillis();
                  minecraftClient.player.networkHandler.sendChatCommand("duel team accept " + duelInvite.group(1));
                  this.awaitingDuelConfirmation = true;
                  ToastNotifications.showIconToast(this.category.iconGlyph(), ("AutoAccept"), "Принял дуэль от " + duelInvite.group(1), true);
               }
            }
         }
      }
   }
}
