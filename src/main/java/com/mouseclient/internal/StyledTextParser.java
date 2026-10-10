package com.mouseclient.internal;

import java.util.ArrayList;
import java.util.Optional;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

public final class StyledTextParser {

   private static Style applyFormattingCode(char code, Style style) {
      Formatting formatting = Formatting.byCode(code);
      if (formatting == null) {
         return style;
      } else if (formatting == Formatting.RESET) {
         return Style.EMPTY;
      } else if (formatting == Formatting.BOLD) {
         return style.withBold(true);
      } else if (formatting == Formatting.ITALIC) {
         return style.withItalic(true);
      } else if (formatting == Formatting.UNDERLINE) {
         return style.withUnderline(true);
      } else if (formatting == Formatting.STRIKETHROUGH) {
         return style.withStrikethrough(true);
      } else if (formatting == Formatting.OBFUSCATED) {
         return style.withObfuscated(true);
      } else {
         return formatting.getColorValue() != null ? style.withColor(TextColor.fromFormatting(formatting)) : style;
      }
   }

   private static int parseLegacyHexColor(String text, int startIndex) {
      int rgb = 0;

      for (int digitIndex = 0; digitIndex < 6; digitIndex++) {
         if (text.charAt(startIndex + digitIndex * 2) != 167 || Character.digit(text.charAt(startIndex + digitIndex * 2 + 1), 16) < 0) {
            return -1;
         }

         rgb = rgb << 4 | Character.digit(text.charAt(startIndex + digitIndex * 2 + 1), 16);
      }

      return rgb;
   }

   private static int styleColor(Style style, int fallbackColor) {
      TextColor textColor = style.getColor();
      return textColor != null ? textColor.getRgb() | (-16777216) : fallbackColor;
   }

   public static ArrayList<StyledTextParser.StyledTextRun> parseLegacy(String text, int fallbackColor) {
      ArrayList runs = new ArrayList();
      appendLegacy(runs, text, fallbackColor);
      return runs;
   }

   private static void flushRun(ArrayList<StyledTextParser.StyledTextRun> runs, StringBuilder textBuilder, Style style, int fallbackColor) {
      if (!textBuilder.isEmpty()) {
         runs.add(styledRun(style, textBuilder.toString(), fallbackColor));
         textBuilder.setLength(0);
      }
   }

   public static String stripFormatting(String text) {
      StringBuilder textBuilder = new StringBuilder(text.length());

      for (int textIndex = 0; textIndex < text.length(); textIndex++) {
         if (text.charAt(textIndex) != 167) {
            textBuilder.append(text.charAt(textIndex));
         } else {
            if (textIndex + 1 >= text.length()) {
               break;
            }

            if (text.charAt(textIndex + 1) == 'x' && textIndex + 13 < text.length() && parseLegacyHexColor(text, textIndex + 2) >= 0) {
               textIndex += 13;
            } else {
               textIndex++;
            }
         }
      }

      return textBuilder.toString();
   }

   public static ArrayList<StyledTextParser.StyledTextRun> parseText(Text text, int fallbackColor) {
      ArrayList runs = new ArrayList();
      appendText(runs, text, Style.EMPTY, fallbackColor);
      return runs;
   }

   private static void appendText(ArrayList<StyledTextParser.StyledTextRun> runs, Text text, Style parentStyle, int fallbackColor) {
      Style effectiveStyle = text.getStyle().withParent(parentStyle);
      text.getContent().visit((runStyle, runText) -> {
         if (runText.isEmpty()) {
            return Optional.empty();
         }

         if (runText.indexOf(167) >= 0) {
            appendLegacy(runs, runText, fallbackColor);
         } else {
            runs.add(styledRun(runStyle, runText, fallbackColor));
         }

         return Optional.empty();
      }, effectiveStyle);

      for (Text sibling : text.getSiblings()) {
         appendText(runs, sibling, effectiveStyle, fallbackColor);
      }
   }

   private static StyledTextParser.StyledTextRun styledRun(Style style, String text, int fallbackColor) {
      return new StyledTextParser.StyledTextRun(
         text, styleColor(style, fallbackColor), style.isBold(), style.isItalic(), style.isUnderlined(), style.isStrikethrough(), style.isObfuscated()
      );
   }

   private StyledTextParser() {
   }

   private static void appendLegacy(ArrayList<StyledTextParser.StyledTextRun> runs, String text, int fallbackColor) {
      Style style = Style.EMPTY;
      StringBuilder textBuilder = new StringBuilder();

      for (int textIndex = 0; textIndex < text.length(); textIndex++) {
         if (text.charAt(textIndex) != 167) {
            textBuilder.append(text.charAt(textIndex));
         } else {
            if (textIndex + 1 >= text.length()) {
               break;
            }

            flushRun(runs, textBuilder, style, fallbackColor);
            char code = text.charAt(textIndex + 1);
            if (code == 'x' && textIndex + 13 < text.length()) {
               int hexColor = parseLegacyHexColor(text, textIndex + 2);
               if (hexColor >= 0) {
                  style = Style.EMPTY.withColor(TextColor.fromRgb(hexColor));
                  textIndex += 13;
                  continue;
               }
            }

            style = applyFormattingCode(code, style);
            textIndex++;
         }
      }

      flushRun(runs, textBuilder, style, fallbackColor);
   }

   public record StyledTextRun(String text, int color, boolean bold, boolean italic, boolean underlined, boolean strikethrough, boolean obfuscated) {
   }
}
