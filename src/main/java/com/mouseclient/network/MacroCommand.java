package com.mouseclient.network;

import com.mouseclient.gui.ChatMessages;
import com.mouseclient.modules.impl.player.ChatMacroManager;
import com.mouseclient.modules.impl.player.InputBinding;
import java.util.ArrayList;
import java.util.Arrays;

public class MacroCommand implements ClientCommand {

   @Override
   public void execute(String[] arguments) {
      if (arguments.length != 0 && !arguments[0].equalsIgnoreCase(("list"))) {
         if (arguments[0].equalsIgnoreCase(("clear"))) {
            ChatMessages.printPrefixed(ChatMacroManager.getInstance().macros().isEmpty() ? ("Макросов нет.") : "Удалено макросов: &7" + ChatMacroManager.getInstance().macros().size());
            ChatMacroManager.getInstance().clearMacros();
         } else if (!arguments[0].equalsIgnoreCase(("del")) && !arguments[0].equalsIgnoreCase(("remove")) && !arguments[0].equalsIgnoreCase(("delete"))) {
            if (!arguments[0].equalsIgnoreCase(("add"))) {
               ChatMessages.printPrefixed(("Использование: &7.macro add <название> <клавиша> <текст>&r, &7.macro del <название>&r, &7.macro list&r, &7.macro clear"));
            } else if (arguments.length < 4) {
               ChatMessages.printPrefixed(("Использование: &7.macro add <название> <клавиша> <текст>&r, например &7.macro add дом H /home"));
            } else if (!ChatMacroManager.getInstance().parseKeyName(arguments[2]).isBound()) {
               ChatMessages.printPrefixed("Клавиша &7" + arguments[2] + "&r не найдена. Пиши так: &7H&r, &7F5&r, &7NUM1&r, &7SPACE&r, &7M4");
            } else {
               ChatMacroManager.getInstance().putMacro(arguments[1], ChatMacroManager.getInstance().parseKeyName(arguments[2]), String.join((" "), Arrays.copyOfRange(arguments, 3, arguments.length)));
               ChatMessages.printPrefixed(
                  "Макрос &7"
                     + arguments[1]
                     + "&r на &7"
                     + ChatMacroManager.getInstance().parseKeyName(arguments[2]).getDisplayName()
                     + "&r пишет &7"
                     + String.join((" "), Arrays.copyOfRange(arguments, 3, arguments.length))
               );
            }
         } else if (arguments.length < 2) {
            ChatMessages.printPrefixed(("Использование: &7.macro del <название>"));
         } else {
            ChatMessages.printPrefixed(ChatMacroManager.getInstance().removeMacro(arguments[1]) ? "Макрос &7" + arguments[1] + "&r удалён." : "Макрос &7" + arguments[1] + "&r не найден.");
         }
      } else if (ChatMacroManager.getInstance().macros().isEmpty()) {
         ChatMessages.printPrefixed(("Макросов пока нет. Пример: &7.macro add дом H /home"));
      } else {
         ArrayList<String> macroLines = new ArrayList<>();

         for (ChatMacroManager.ChatMacro macro : ChatMacroManager.getInstance().macros()) {
            macroLines.add("&7" + macro.name() + " &8[" + macro.key().getDisplayName() + "] &8— &f" + macro.chatLine());
         }

         ChatMessages.printLines(("&fМакросы:"), macroLines.toArray(new String[0]));
      }
   }

   @Override
   public ArrayList<String> suggestions(String[] arguments) {
      if (arguments.length <= 1) {
         return new ArrayList<>(Arrays.asList(("add"), ("del"), ("list"), ("clear")));
      }

      if (arguments.length == 3 && arguments[0].equalsIgnoreCase(("add"))) {
         ArrayList keyNames = new ArrayList();

         for (int keyCode = 32; keyCode <= 348; keyCode++) {
            if (!InputBinding.keyboard(keyCode).getDisplayName().startsWith(("KEY")) && !keyNames.contains(InputBinding.keyboard(keyCode).getDisplayName())) {
               keyNames.add(InputBinding.keyboard(keyCode).getDisplayName());
            }
         }

         for (int mouseButton = 1; mouseButton <= 5; mouseButton++) {
            keyNames.add("M" + mouseButton);
         }

         return keyNames;
      } else {
         ArrayList<String> macroNames = new ArrayList<>();
         if (arguments.length == 2 && (arguments[0].equalsIgnoreCase(("del")) || arguments[0].equalsIgnoreCase(("remove")) || arguments[0].equalsIgnoreCase(("delete")))) {
            for (ChatMacroManager.ChatMacro macro : ChatMacroManager.getInstance().macros()) {
               macroNames.add(macro.name());
            }
         }

         return macroNames;
      }
   }

   @Override
   public String name() {
      return ("macro");
   }

   @Override
   public String description() {
      return ("Макросы (.macro add <название> <клавиша> <текст> / del <название> / list / clear)");
   }
}
