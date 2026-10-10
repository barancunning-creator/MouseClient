package com.mouseclient.network;

import com.mouseclient.gui.ChatMessages;
import com.mouseclient.render.pipeline.BlockEsp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public class BlockEspCommand implements ClientCommand {

   @Override
   public String description() {
      return ("Свои блоки в BlockEsp (.blockesp add <блок> / del <блок> / list / clear)");
   }

   @Override
   public ArrayList<String> suggestions(String[] arguments) {
      if (arguments.length <= 1) {
         return new ArrayList<>(Arrays.asList(("add"), ("del"), ("list"), ("clear")));
      }

      if (arguments.length != 2) {
         return new ArrayList<>();
      }

      ArrayList candidates = new ArrayList();
      String prefix = arguments[1].toLowerCase(Locale.ROOT);
      if (!arguments[0].equalsIgnoreCase(("del")) && !arguments[0].equalsIgnoreCase(("remove"))) {
         for (Identifier id : Registries.BLOCK.getIds()) {
            if (id.getPath().startsWith(prefix)) {
               candidates.add(id.getPath());
            }

            if (candidates.size() >= 40) {
               break;
            }
         }

         return candidates;
      } else {
         for (Block block : BlockEsp.getInstance().customBlocks()) {
            candidates.add(Registries.BLOCK.getId(block).getPath());
         }

         return candidates;
      }
   }

   private Block resolveBlock(String blockName) {
      Identifier id = Identifier.tryParse(
         blockName.toLowerCase(Locale.ROOT).contains((":")) ? blockName.toLowerCase(Locale.ROOT) : "minecraft:" + blockName.toLowerCase(Locale.ROOT)
      );
      if (id == null) {
         return null;
      }

      Block block = Registries.BLOCK.get(id);
      return block == Blocks.AIR && !id.getPath().equals(("air")) ? null : block;
   }

   @Override
   public String name() {
      return ("blockesp");
   }

   @Override
   public void execute(String[] arguments) {
      BlockEsp blockEsp = BlockEsp.getInstance();
      if (arguments.length == 0 || arguments[0].equalsIgnoreCase(("list"))) {
         ArrayList blockNames = new ArrayList();

         for (Block storedBlock : blockEsp.customBlocks()) {
            blockNames.add(Registries.BLOCK.getId(storedBlock).getPath());
         }

         ChatMessages.printPrefixed(blockNames.isEmpty() ? ("Своих блоков пока нет. Пример: &7.blockesp add diamond_ore") : "Свои блоки: &7" + String.join((", "), blockNames));
      } else if (arguments[0].equalsIgnoreCase(("clear"))) {
         blockEsp.clearCustomBlocks();
         ChatMessages.printPrefixed(("Список своих блоков очищен."));
      } else if (arguments.length < 2) {
         ChatMessages.printPrefixed(("Использование: &7.blockesp add <блок>&r, &7.blockesp del <блок>&r, &7.blockesp list&r, &7.blockesp clear"));
      } else {
         Block block = this.resolveBlock(arguments[1]);
         if (block == null) {
            ChatMessages.printPrefixed("Блок &7" + arguments[1] + "&r не найден.");
         } else {
            String blockName = Registries.BLOCK.getId(block).getPath();
            switch (arguments[0].toLowerCase(Locale.ROOT)) {
               case ("add"):
                  ChatMessages.printPrefixed(blockEsp.addCustomBlock(block) ? "Блок &7" + blockName + "&r теперь подсвечивается." : "Блок &7" + blockName + "&r уже добавлен.");
                  break;
               case ("del"):
               case ("remove"):
               case ("delete"):
                  ChatMessages.printPrefixed(blockEsp.removeCustomBlock(block) ? "Блок &7" + blockName + "&r убран." : "Блока &7" + blockName + "&r нет в списке.");
                  break;
               default:
                  ChatMessages.printPrefixed(("Использование: &7.blockesp add <блок>&r, &7.blockesp del <блок>&r, &7.blockesp list&r, &7.blockesp clear"));
            }
         }
      }
   }
}
