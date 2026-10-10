package com.mouseclient.network;

import com.mouseclient.gui.ChatMessages;
import java.util.ArrayList;
import java.util.Arrays;
import lombok.Generated;
import net.minecraft.util.Formatting;

public class ClientCommandDispatcher {
   private final ArrayList<ClientCommand> commands;
   private final String prefix;

   public ArrayList<String> suggestions(String input) {
      ArrayList candidates = new ArrayList();
      if (!input.startsWith(this.prefix)) {
         return candidates;
      }

      String[] tokens = input.substring(this.prefix.length()).split((" "), -1);
      if (tokens.length == 1) {
         for (ClientCommand commandCandidate : this.commands) {
            if (commandCandidate.name().startsWith(tokens[0].toLowerCase())) {
               candidates.add(this.prefix + commandCandidate.name());
            }
         }

         return candidates;
      } else {
         for (ClientCommand command : this.commands) {
            if (command.name().equalsIgnoreCase(tokens[0])) {
               for (String candidate : command.suggestions(Arrays.copyOfRange(tokens, 1, tokens.length))) {
                  if (candidate.toLowerCase().startsWith(tokens[tokens.length - 1].toLowerCase())) {
                     candidates.add(candidate);
                  }
               }
            }
         }

         return candidates;
      }
   }

   @Generated
   public ClientCommandDispatcher(ArrayList<ClientCommand> commands, String prefix) {
      this.commands = commands;
      this.prefix = prefix;
   }

   public ArrayList<ClientCommand> matchingCommands(String input) {
      ArrayList matches = new ArrayList();

      for (ClientCommand command : this.commands) {
         if ((this.prefix + command.name()).startsWith(input) || input.startsWith(this.prefix + command.name() + " ")) {
            matches.add(command);
         }
      }

      return matches;
   }

   @Generated
   public ArrayList<ClientCommand> commands() {
      return this.commands;
   }

   public boolean dispatch(String input) {
      if (!input.startsWith(this.prefix)) {
         return false;
      }

      String[] tokens = input.substring(this.prefix.length()).trim().split((" +"));

      for (ClientCommand command : this.commands) {
         if (command.name().equalsIgnoreCase(tokens[0])) {
            command.execute(Arrays.copyOfRange(tokens, 1, tokens.length));
            return true;
         }
      }

      ChatMessages.printPrefixed("Неизвестная команда, введи " + Formatting.GRAY + this.prefix + "help");
      return true;
   }

   @Generated
   public String prefix() {
      return this.prefix;
   }

   public String completeFirst(String input) {
      return this.suggestions(input).isEmpty()
         ? input
         : input.substring(0, input.length() - input.substring(input.lastIndexOf(32) + 1).length()) + this.suggestions(input).get(0) + " ";
   }
}
