package com.mouseclient.network;

import com.mouseclient.gui.ChatMessages;
import com.mouseclient.util.entity.MinecraftAccess;
import com.mouseclient.util.time.WaypointManager;
import java.util.ArrayList;
import java.util.Arrays;

public class WaypointCommand implements MinecraftAccess, ClientCommand {

   @Override
   public ArrayList<String> suggestions(String[] arguments) {
      if (arguments.length <= 1) {
         return new ArrayList<>(Arrays.asList(("add"), ("del"), ("clear")));
      } else if (arguments.length != 2 || !arguments[0].equalsIgnoreCase(("del")) && !arguments[0].equalsIgnoreCase(("remove"))) {
         return arguments.length == 2 && arguments[0].equalsIgnoreCase(("add")) && minecraftClient.player != null
            ? new ArrayList<>(
               Arrays.asList(minecraftClient.player.getBlockX() + " " + minecraftClient.player.getBlockY() + " " + minecraftClient.player.getBlockZ())
            )
            : new ArrayList<>();
      } else {
         return WaypointManager.getInstance().getNames();
      }
   }

   @Override
   public String name() {
      return ("way");
   }

   @Override
   public void execute(String[] arguments) {
      if (arguments.length == 0) {
         ChatMessages.printPrefixed(("Использование: &7.way add [название] <x y z>&r, &7.way del <название>&r, &7.way clear"));
      } else if (arguments[0].equalsIgnoreCase(("clear"))) {
         ChatMessages.printPrefixed(WaypointManager.getInstance().size() == 0 ? ("Меток нет.") : "Удалено меток: &7" + WaypointManager.getInstance().size());
         WaypointManager.getInstance().clear();
      } else if (!arguments[0].equalsIgnoreCase(("del")) && !arguments[0].equalsIgnoreCase(("remove"))) {
         if (!arguments[0].equalsIgnoreCase(("add"))) {
            ChatMessages.printPrefixed(("Использование: &7.way add [название] <x y z>&r, &7.way del <название>&r, &7.way clear"));
         } else if (arguments.length == 1 && minecraftClient.player != null) {
            WaypointManager.getInstance().add(("Метка"), minecraftClient.player.getBlockX(), minecraftClient.player.getBlockY(), minecraftClient.player.getBlockZ());
            ChatMessages.printPrefixed(
               "Метка &7Метка&r поставлена на &7"
                  + minecraftClient.player.getBlockX()
                  + " "
                  + minecraftClient.player.getBlockY()
                  + " "
                  + minecraftClient.player.getBlockZ()
            );
         } else if (arguments.length != 4 && arguments.length != 5) {
            ChatMessages.printPrefixed(("Использование: &7.way add [название] <x y z>"));
         } else {
            try {
               WaypointManager.getInstance()
                  .add(
                     arguments.length == 5 ? arguments[1] : ("Метка"),
                     Integer.parseInt(arguments[arguments.length - 3]),
                     Integer.parseInt(arguments[arguments.length - 2]),
                     Integer.parseInt(arguments[arguments.length - 1])
                  );
               ChatMessages.printPrefixed(
                  "Метка &7"
                     + (arguments.length == 5 ? arguments[1] : ("Метка"))
                     + "&r поставлена на &7"
                     + arguments[arguments.length - 3]
                     + " "
                     + arguments[arguments.length - 2]
                     + " "
                     + arguments[arguments.length - 1]
               );
            } catch (NumberFormatException error) {
               ChatMessages.printPrefixed(("Координаты должны быть целыми числами."));
            }
         }
      } else if (arguments.length < 2) {
         ChatMessages.printPrefixed(("Использование: &7.way del <название>"));
      } else {
         ChatMessages.printPrefixed(WaypointManager.getInstance().remove(arguments[1]) ? "Метка &7" + arguments[1] + "&r удалена." : "Метка &7" + arguments[1] + "&r не найдена.");
      }
   }

   @Override
   public String description() {
      return ("Метки (.way add [название] <x y z> / del <название> / clear)");
   }
}
