package com.mouseclient.modules.impl.misc;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.modules.impl.combat.ModeSetting;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

@ModuleInfo(name = "InfoReplace", description = "Подменяет ранг, монеты и токены в сайдбаре.", category = ModuleCategory.MISC)
public final class InfoReplace extends Module {
   private static InfoReplace INSTANCE;
   public FilteredTextSetting coinAmount;
   public FilteredTextSetting tokenAmount;
   public ModeSetting rank;

   public MutableText replaceSidebarEntry(MutableText entry) {
      String entryText = entry.getString();
      String[] fieldLabels = new String[]{("Ранг: "), ("Монет: "), ("Токенов: ")};
      Formatting[] fieldColors = new Formatting[]{Formatting.WHITE, Formatting.YELLOW, Formatting.GREEN};

      for (int fieldIndex = 0; fieldIndex < fieldLabels.length; fieldIndex++) {
         int labelStart = entryText.indexOf(fieldLabels[fieldIndex]);
         if (labelStart >= 3) {
            String replacementValue = fieldIndex == 0 ? this.rank.getSelectedOption() : (fieldIndex == 1 ? this.coinAmount : this.tokenAmount).getText();
            if (fieldIndex > 0) {
               String digits = replacementValue.replaceAll(("\\D"), "");
               replacementValue = digits.isEmpty() ? ("0") : String.valueOf(Long.parseLong(digits));
            }

            return Text.empty()
               .append(Text.literal(entryText.substring(0, labelStart - 3)).fillStyle(Style.EMPTY.withColor((16720896))))
               .append(Text.literal(entryText.substring(labelStart - 3, labelStart)).fillStyle(Style.EMPTY.withColor(fieldIndex == 0 ? Formatting.AQUA : fieldColors[fieldIndex])))
               .append(Text.literal(fieldLabels[fieldIndex]).fillStyle(Style.EMPTY.withColor(Formatting.WHITE)))
               .append(Text.literal(replacementValue).fillStyle(fieldIndex == 0 ? this.rankStyle(replacementValue) : Style.EMPTY.withColor(fieldColors[fieldIndex])));
         }
      }

      return entry;
   }

   public static InfoReplace getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new InfoReplace();
      }

      return INSTANCE;
   }

   private InfoReplace() {
      this.coinAmount = this.addSetting(new FilteredTextSetting(("Кол-во монет"), "", "", 16));
      this.tokenAmount = this.addSetting(new FilteredTextSetting(("Кол-во токенов"), "", "", 16));
      this.rank = this.addSetting(
         new ModeSetting(
            ("Привилегия"), "", ("Игрок"), ("Игрок"), ("Барон"), ("Страж"), ("Герой"), ("Аспид"), ("Сквид"), ("Глава"), ("Элита"), ("Титан"), ("Принц"), ("Князь"), ("Герцог")
         )
      );
   }

   public Style rankStyle(String rankName) {
      return switch (rankName) {
         case ("Страж") -> Style.EMPTY.withColor(Formatting.YELLOW);
         case ("Барон") -> Style.EMPTY.withColor(Formatting.AQUA);
         case ("Герой") -> Style.EMPTY.withColor(Formatting.GREEN);
         case ("Аспид") -> Style.EMPTY.withColor(Formatting.DARK_AQUA);
         case ("Сквид") -> Style.EMPTY.withColor(Formatting.AQUA).withItalic(true);
         case ("Глава"), ("Титан") -> Style.EMPTY.withColor(Formatting.GOLD);
         case ("Элита") -> Style.EMPTY.withColor(Formatting.DARK_PURPLE);
         case ("Принц"), ("Князь") -> Style.EMPTY.withColor((16711680));
         case ("Герцог") -> Style.EMPTY.withColor(Formatting.DARK_RED);
         default -> Style.EMPTY.withColor(Formatting.GREEN);
      };
   }
}
