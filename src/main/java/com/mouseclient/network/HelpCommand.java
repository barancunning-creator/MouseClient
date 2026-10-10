package com.mouseclient.network;

import com.mouseclient.gui.ChatMessages;
import java.util.ArrayList;
import lombok.Generated;

public class HelpCommand implements ClientCommand {
   private final ArrayList<ClientCommand> commands;

   @Generated
   public HelpCommand(ArrayList<ClientCommand> commands) {
      this.commands = commands;
   }

   @Override
   public void execute(String[] arguments) {
      for (ClientCommand command : this.commands) {
         if (arguments.length > 0 && command.name().equalsIgnoreCase(arguments[0])) {
            ChatMessages.printLines("&f." + command.name() + ":", "&f" + command.description());
            return;
         }
      }

      String[] helpLines = new String[this.commands.size()];

      for (int commandIndex = 0; commandIndex < this.commands.size(); commandIndex++) {
         helpLines[commandIndex] = "&7." + this.commands.get(commandIndex).name() + " &8— &f" + this.commands.get(commandIndex).description();
      }

      ChatMessages.printLines(("&fКоманды клиента:"), helpLines);
   }

   @Override
   public String description() {
      return ("Показывает список всех команд");
   }

   @Override
   public ArrayList<String> suggestions(String[] arguments) {
      ArrayList candidates = new ArrayList();
      if (arguments.length <= 1) {
         for (ClientCommand command : this.commands) {
            candidates.add(command.name());
         }
      }

      return candidates;
   }

   @Override
   public String name() {
      return ("help");
   }
}
