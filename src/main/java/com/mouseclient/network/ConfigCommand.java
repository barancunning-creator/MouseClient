package com.mouseclient.network;

import com.mouseclient.gui.ChatMessages;
import com.mouseclient.util.time.ConfigStore;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import net.minecraft.util.Util;

public class ConfigCommand implements ClientCommand {

   @Override
   public ArrayList<String> suggestions(String[] arguments) {
      if (arguments.length <= 1) {
         return new ArrayList<>(Arrays.asList(("list"), ("create"), ("save"), ("load"), ("delete"), ("dir")));
      } else {
         return arguments.length != 2 || !arguments[0].equalsIgnoreCase(("load")) && !arguments[0].equalsIgnoreCase(("delete")) && !arguments[0].equalsIgnoreCase(("save"))
            ? new ArrayList<>()
            : ConfigStore.names();
      }
   }

   @Override
   public String description() {
      return ("Управление конфигами (.cfg create/save/load/delete/list/dir)");
   }

   @Override
   public String name() {
      return ("cfg");
   }

   @Override
   public void execute(String[] arguments) {
      if (arguments.length != 0 && !arguments[0].equalsIgnoreCase(("list"))) {
         if (arguments[0].equalsIgnoreCase(("dir"))) {
            try {
               Files.createDirectories(ConfigStore.directory());
            } catch (Exception error) {
            }

            Util.getOperatingSystem().open(ConfigStore.directory());
            ChatMessages.printPrefixed(("Папка конфигов открыта."));
         } else if (arguments.length < 2) {
            ChatMessages.printPrefixed(("Использование: &7.cfg create/save/load/delete <имя>"));
         } else {
            switch (arguments[0].toLowerCase()) {
               case ("create"):
               case ("save"):
                  ChatMessages.printPrefixed(
                     ConfigStore.save(String.join((" "), Arrays.copyOfRange(arguments, 1, arguments.length)))
                        ? "Конфиг &7" + String.join((" "), Arrays.copyOfRange(arguments, 1, arguments.length)) + "&r сохранён."
                        : ("Не удалось сохранить конфиг.")
                  );
                  break;
               case ("load"):
                  ChatMessages.printPrefixed(
                     ConfigStore.load(String.join((" "), Arrays.copyOfRange(arguments, 1, arguments.length)))
                        ? "Конфиг &7" + String.join((" "), Arrays.copyOfRange(arguments, 1, arguments.length)) + "&r загружен."
                        : "Конфиг &7" + String.join((" "), Arrays.copyOfRange(arguments, 1, arguments.length)) + "&r не найден."
                  );
                  break;
               case ("delete"):
                  ChatMessages.printPrefixed(
                     ConfigStore.delete(String.join((" "), Arrays.copyOfRange(arguments, 1, arguments.length)))
                        ? "Конфиг &7" + String.join((" "), Arrays.copyOfRange(arguments, 1, arguments.length)) + "&r удалён."
                        : "Конфиг &7" + String.join((" "), Arrays.copyOfRange(arguments, 1, arguments.length)) + "&r не найден."
                  );
                  break;
               default:
                  ChatMessages.printPrefixed(("Использование: &7.cfg create/save/load/delete <имя>"));
            }
         }
      } else {
         ChatMessages.printPrefixed(ConfigStore.names().isEmpty() ? ("Конфигов пока нет.") : "Конфиги: &7" + String.join((", "), ConfigStore.names()));
      }
   }
}
