package com.mouseclient.network;

import com.mouseclient.gui.ChatMessages;
import com.mouseclient.internal.StaffRegistry;
import java.util.ArrayList;
import java.util.Arrays;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.scoreboard.Team;

public class StaffCommand implements ClientCommand {

   @Override
   public String name() {
      return ("staff");
   }

   @Override
   public ArrayList<String> suggestions(String[] arguments) {
      if (arguments.length <= 1) {
         return new ArrayList<>(Arrays.asList(("name"), ("prefix"), ("del"), ("list"), ("clear")));
      }

      if (arguments.length != 2) {
         return new ArrayList<>();
      }

      if (!arguments[0].equalsIgnoreCase(("del")) && !arguments[0].equalsIgnoreCase(("remove")) && !arguments[0].equalsIgnoreCase(("delete"))) {
         ArrayList candidates = new ArrayList();
         if (MinecraftClient.getInstance().getNetworkHandler() != null && MinecraftClient.getInstance().world != null) {
            for (PlayerListEntry playerEntry : MinecraftClient.getInstance().getNetworkHandler().getPlayerList()) {
               if (arguments[0].equalsIgnoreCase(("name")) && !StaffRegistry.containsName(playerEntry.getProfile().name())) {
                  candidates.add(playerEntry.getProfile().name());
               }

               if (arguments[0].equalsIgnoreCase(("prefix"))) {
                  Team team = MinecraftClient.getInstance().world.getScoreboard().getScoreHolderTeam(playerEntry.getProfile().name());
                  if (team != null && !team.getPrefix().getString().trim().isEmpty() && !candidates.contains(team.getPrefix().getString().trim())) {
                     candidates.add(team.getPrefix().getString().trim());
                  }
               }
            }

            return candidates;
         } else {
            return candidates;
         }
      } else {
         java.util.ArrayList<java.lang.String> savedNamesAndPrefixes = StaffRegistry.names();
         savedNamesAndPrefixes.addAll(StaffRegistry.prefixes());
         return savedNamesAndPrefixes;
      }
   }

   @Override
   public void execute(String[] arguments) {
      if (arguments.length != 0 && !arguments[0].equalsIgnoreCase(("list"))) {
         if (arguments[0].equalsIgnoreCase(("clear"))) {
            StaffRegistry.clear();
            ChatMessages.printPrefixed(("Список своих стаффов очищен."));
         } else if (arguments.length < 2) {
            ChatMessages.printPrefixed(("Использование: &7.staff name <ник>&r, &7.staff prefix <префикс>&r, &7.staff del <ник или префикс>&r, &7.staff list&r, &7.staff clear"));
         } else {
            switch (arguments[0].toLowerCase()) {
               case ("name"):
               case ("nick"):
                  ChatMessages.printPrefixed(
                     StaffRegistry.addName(String.join((" "), Arrays.copyOfRange(arguments, 1, arguments.length)))
                        ? "Ник &7" + String.join((" "), Arrays.copyOfRange(arguments, 1, arguments.length)) + "&r теперь всегда в стафф-листе."
                        : "Ник &7" + String.join((" "), Arrays.copyOfRange(arguments, 1, arguments.length)) + "&r уже добавлен."
                  );
                  break;
               case ("prefix"):
               case ("tag"):
                  ChatMessages.printPrefixed(
                     StaffRegistry.addPrefix(String.join((" "), Arrays.copyOfRange(arguments, 1, arguments.length)))
                        ? "Префикс &7" + String.join((" "), Arrays.copyOfRange(arguments, 1, arguments.length)) + "&r теперь считается стаффским."
                        : "Префикс &7" + String.join((" "), Arrays.copyOfRange(arguments, 1, arguments.length)) + "&r уже добавлен."
                  );
                  break;
               case ("del"):
               case ("remove"):
               case ("delete"):
                  ChatMessages.printPrefixed(
                     StaffRegistry.remove(String.join((" "), Arrays.copyOfRange(arguments, 1, arguments.length)))
                        ? "&7" + String.join((" "), Arrays.copyOfRange(arguments, 1, arguments.length)) + "&r убран из своих стаффов."
                        : "&7" + String.join((" "), Arrays.copyOfRange(arguments, 1, arguments.length)) + "&r не найден."
                  );
                  break;
               default:
                  ChatMessages.printPrefixed(("Использование: &7.staff name <ник>&r, &7.staff prefix <префикс>&r, &7.staff del <ник или префикс>&r, &7.staff list&r, &7.staff clear"));
            }
         }
      } else if (StaffRegistry.names().isEmpty() && StaffRegistry.prefixes().isEmpty()) {
         ChatMessages.printPrefixed(("Своих стаффов пока нет. Пример: &7.staff name Spa1k1&r, &7.staff prefix ГМ"));
      } else {
         ChatMessages.printLines(
            ("&fСвои стаффы:"),
            "Ники: &7" + (StaffRegistry.names().isEmpty() ? ("нет") : String.join((", "), StaffRegistry.names())),
            "Префиксы: &7" + (StaffRegistry.prefixes().isEmpty() ? ("нет") : String.join((", "), StaffRegistry.prefixes()))
         );
      }
   }

   @Override
   public String description() {
      return ("Свои стаффы (.staff name <ник> / prefix <префикс> / del <ник или префикс> / list / clear)");
   }
}
