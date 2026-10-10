package com.mouseclient.network;

import com.mouseclient.gui.ChatMessages;
import com.mouseclient.modules.impl.misc.PartyModule;
import java.util.ArrayList;
import java.util.Arrays;

public class PartyCommand implements ClientCommand {

   @Override
   public ArrayList<String> suggestions(String[] arguments) {
      return arguments.length <= 1 ? new ArrayList<>(Arrays.asList(("create"), ("join"), ("leave"), ("lock"), ("unlock"), ("kick"), ("list"))) : new ArrayList<>();
   }

   @Override
   public String name() {
      return ("party");
   }

   @Override
   public String description() {
      return ("Пати (.party create/join <код>/leave/lock/unlock/kick <ник>/list)");
   }

   @Override
   public void execute(String[] arguments) {
      PartyModule party = PartyModule.getInstance();
      switch (arguments.length == 0 ? "" : arguments[0].toLowerCase()) {
         case ("create"):
            party.create();
            break;
         case ("join"):
            if (arguments.length < 2) {
               ChatMessages.printPrefixed(("Использование: &7.party join <код>"));
            } else {
               party.join(arguments[1]);
            }
            break;
         case ("leave"):
            party.leave();
            break;
         case ("lock"):
            party.setLocked(true);
            break;
         case ("unlock"):
            party.setLocked(false);
            break;
         case ("kick"):
            if (arguments.length < 2) {
               ChatMessages.printPrefixed(("Использование: &7.party kick <ник>"));
            } else {
               party.kickMember(arguments[1]);
            }
            break;
         default:
            party.printParty();
      }
   }
}
