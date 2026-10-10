package com.mouseclient.network;

import com.mouseclient.gui.ChatMessages;
import com.mouseclient.util.time.IrcClient;
import java.util.ArrayList;
import java.util.Arrays;
import ru.dreamix.protection.api.impl.profile.ProfileAPI;
import ru.dreamix.protection.api.impl.profile.objects.RoleObject;

public class IrcCommand implements ClientCommand {

   @Override
   public ArrayList<String> suggestions(String[] arguments) {
      if (arguments.length > 1) {
         return new ArrayList<>();
      }

      java.util.ArrayList<java.lang.String> candidates = new ArrayList<>(Arrays.asList(("on"), ("off")));
      if (this.canListOnlineUsers()) {
         candidates.add(("online"));
      }

      return candidates;
   }

   private boolean canListOnlineUsers() {
      return ProfileAPI.get().getModel().getRole().priorityHigherOrEquals(RoleObject.ADMIN);
   }

   @Override
   public String name() {
      return ("irc");
   }

   @Override
   public String description() {
      return ("Чат клиента (.irc on/off, писать через @сообщение)");
   }

   @Override
   public void execute(String[] arguments) {
      switch (arguments.length == 0 ? "" : arguments[0].toLowerCase()) {
         case ("on"):
            IrcClient.getInstance().setEnabled(true);
            ChatMessages.printPrefixed(("IRC включён, пиши в чат через &7@сообщение"));
            break;
         case ("off"):
            IrcClient.getInstance().setEnabled(false);
            ChatMessages.printPrefixed(("IRC выключен."));
            break;
         case ("online"):
            if (!this.canListOnlineUsers()) {
               ChatMessages.printPrefixed(("&cКоманда доступна только администраторам."));
            } else if (!IrcClient.getInstance().isEnabled()) {
               ChatMessages.printPrefixed(("IRC выключен, включи через &7.irc on"));
            } else {
               IrcClient.getInstance().showOnlineUsers();
            }
            break;
         default:
            ChatMessages.printPrefixed("IRC " + (IrcClient.getInstance().isEnabled() ? ("&aвключён") : ("&cвыключен")) + "&r. Использование: &7.irc on/off&r, сообщение: &7@текст");
      }
   }
}
