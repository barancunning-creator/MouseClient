package com.mouseclient.network;

import com.mouseclient.gui.ChatMessages;
import com.mouseclient.util.entity.MinecraftAccess;
import java.util.ArrayList;
import java.util.Arrays;
import net.minecraft.util.Util;

public class ResourcePackCommand implements MinecraftAccess, ClientCommand {

   @Override
   public String name() {
      return ("rp");
   }

   @Override
   public String description() {
      return ("Открывает папку с ресурспаками (.rp dir)");
   }

   @Override
   public void execute(String[] arguments) {
      if (arguments.length > 0 && arguments[0].equalsIgnoreCase(("dir"))) {
         Util.getOperatingSystem().open(minecraftClient.getResourcePackDir());
         ChatMessages.printPrefixed(("Папка ресурспаков открыта."));
      } else {
         ChatMessages.printPrefixed(("Использование: &7.rp dir"));
      }
   }

   @Override
   public ArrayList<String> suggestions(String[] arguments) {
      return arguments.length <= 1 ? new ArrayList<>(Arrays.asList(("dir"))) : new ArrayList<>();
   }
}
