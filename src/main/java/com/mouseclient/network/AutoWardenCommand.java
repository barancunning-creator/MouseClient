package com.mouseclient.network;

import com.mouseclient.gui.ChatMessages;
import java.util.ArrayList;
import java.util.Arrays;

public class AutoWardenCommand implements ClientCommand {

   @Override
   public String name() {
      return ("autowarden");
   }

   @Override
   public void execute(String[] arguments) {
      if (arguments.length != 0 && !arguments[0].equalsIgnoreCase(("list"))) {
         if (arguments[0].equalsIgnoreCase(("clear"))) {
            AutoWarden.getInstance().travelAnarchies.clear();
            ChatMessages.printPrefixed(("Список анархий очищен, бот останется на основной."));
         } else if (!arguments[0].equalsIgnoreCase(("del")) && !arguments[0].equalsIgnoreCase(("remove"))) {
            if (!arguments[0].matches(("\\d+"))) {
               ChatMessages.printPrefixed(("Использование: &7.autowarden <номер>&r, &7.autowarden del <номер>&r, &7.autowarden list&r, &7.autowarden clear"));
            } else if (AutoWarden.getInstance().travelAnarchies.contains(Integer.valueOf(arguments[0]))) {
               ChatMessages.printPrefixed("Анархия &7" + arguments[0] + "&r уже в списке.");
            } else {
               AutoWarden.getInstance().travelAnarchies.add(Integer.valueOf(arguments[0]));
               ChatMessages.printPrefixed("Анархия &7" + arguments[0] + "&r добавлена, бот уйдёт туда после двух кругов на основной.");
            }
         } else if (arguments.length >= 2 && arguments[1].matches(("\\d+"))) {
            ChatMessages.printPrefixed(
               AutoWarden.getInstance().travelAnarchies.remove(Integer.valueOf(arguments[1]))
                  ? "Анархия &7" + arguments[1] + "&r убрана из списка."
                  : "Анархии &7" + arguments[1] + "&r в списке нет."
            );
         } else {
            ChatMessages.printPrefixed(("Использование: &7.autowarden del <номер анархии>"));
         }
      } else {
         ChatMessages.printPrefixed(
            AutoWarden.getInstance().travelAnarchies.isEmpty()
               ? ("Список анархий пуст, лут только на основной. Пример: &7.autowarden 302")
               : "Анархии для лута по очереди: &7" + AutoWarden.getInstance().travelAnarchies.toString().replaceAll(("[\\[\\]]"), "")
         );
      }
   }

   @Override
   public ArrayList<String> suggestions(String[] arguments) {
      if (arguments.length <= 1) {
         return new ArrayList<>(Arrays.asList(("del"), ("list"), ("clear")));
      }

      if (arguments.length == 2 && (arguments[0].equalsIgnoreCase(("del")) || arguments[0].equalsIgnoreCase(("remove")))) {
         ArrayList candidates = new ArrayList();

         for (Integer anarchy : AutoWarden.getInstance().travelAnarchies) {
            candidates.add(String.valueOf(anarchy));
         }

         return candidates;
      } else {
         return new ArrayList<>();
      }
   }

   @Override
   public String description() {
      return ("Анархии для AutoWarden (.autowarden 302 / del 302 / list / clear)");
   }
}
