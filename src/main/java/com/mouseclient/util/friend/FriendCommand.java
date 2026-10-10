package com.mouseclient.util.friend;

import com.mouseclient.gui.ChatMessages;
import com.mouseclient.internal.FriendManager;
import com.mouseclient.network.ClientCommand;
import java.util.ArrayList;
import java.util.Arrays;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;

public class FriendCommand implements ClientCommand {

   private static String canonicalPlayerName(String name) {
      if (MinecraftClient.getInstance().getNetworkHandler() == null) {
         return name;
      }

      for (PlayerListEntry playerEntry : MinecraftClient.getInstance().getNetworkHandler().getPlayerList()) {
         if (playerEntry.getProfile().name().equalsIgnoreCase(name)) {
            return playerEntry.getProfile().name();
         }
      }

      return name;
   }

   @Override
   public ArrayList<String> suggestions(String[] arguments) {
      if (arguments.length <= 1) {
         return new ArrayList<>(Arrays.asList(("add"), ("remove"), ("list"), ("clear")));
      }

      if (arguments.length != 2) {
         return new ArrayList<>();
      }

      if (!arguments[0].equalsIgnoreCase(("remove")) && !arguments[0].equalsIgnoreCase(("delete"))) {
         ArrayList candidates = new ArrayList();
         if (arguments[0].equalsIgnoreCase(("add")) && MinecraftClient.getInstance().getNetworkHandler() != null) {
            for (PlayerListEntry playerEntry : MinecraftClient.getInstance().getNetworkHandler().getPlayerList()) {
               if (!FriendManager.contains(playerEntry.getProfile().name())) {
                  candidates.add(playerEntry.getProfile().name());
               }
            }
         }

         return candidates;
      } else {
         return FriendManager.names();
      }
   }

   @Override
   public void execute(String[] arguments) {
      if (arguments.length != 0 && !arguments[0].equalsIgnoreCase(("list"))) {
         if (arguments[0].equalsIgnoreCase(("clear"))) {
            FriendManager.clear();
            ChatMessages.printPrefixed(("Список друзей очищен."));
         } else if (arguments.length < 2) {
            ChatMessages.printPrefixed(("Использование: &7.friend add/remove/list/clear <ник>"));
         } else {
            switch (arguments[0].toLowerCase()) {
               case ("add"):
                  ChatMessages.printPrefixed(
                     FriendManager.add(canonicalPlayerName(String.join((" "), Arrays.copyOfRange(arguments, 1, arguments.length))))
                        ? "&7" + canonicalPlayerName(String.join((" "), Arrays.copyOfRange(arguments, 1, arguments.length))) + "&r добавлен в друзья."
                        : "&7" + canonicalPlayerName(String.join((" "), Arrays.copyOfRange(arguments, 1, arguments.length))) + "&r уже в друзьях."
                  );
                  break;
               case ("remove"):
               case ("delete"):
                  ChatMessages.printPrefixed(
                     FriendManager.remove(canonicalPlayerName(String.join((" "), Arrays.copyOfRange(arguments, 1, arguments.length))))
                        ? "&7" + canonicalPlayerName(String.join((" "), Arrays.copyOfRange(arguments, 1, arguments.length))) + "&r удалён из друзей."
                        : "&7" + canonicalPlayerName(String.join((" "), Arrays.copyOfRange(arguments, 1, arguments.length))) + "&r не в друзьях."
                  );
                  break;
               default:
                  ChatMessages.printPrefixed(("Использование: &7.friend add/remove/list/clear <ник>"));
            }
         }
      } else {
         ChatMessages.printPrefixed(FriendManager.names().isEmpty() ? ("Друзей пока нет.") : "Друзья: &7" + String.join((", "), FriendManager.names()));
      }
   }

   @Override
   public String name() {
      return ("friend");
   }

   @Override
   public String description() {
      return ("Друзья (.friend add/remove/list/clear <ник>)");
   }
}
