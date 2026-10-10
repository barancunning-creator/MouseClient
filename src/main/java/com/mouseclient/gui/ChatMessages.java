package com.mouseclient.gui;

import com.mouseclient.internal.ArgbColor;
import com.mouseclient.util.entity.MinecraftAccess;
import com.mouseclient.util.time.GuiVisuals;
import lombok.Generated;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

public final class ChatMessages implements MinecraftAccess {
   private static final boolean BARITONE_LOADED = FabricLoader.getInstance()
      .getAllMods()
      .stream()
      .anyMatch(modContainer -> modContainer.getMetadata().getId().startsWith(("baritone")));

   public static void print(Object message, Object... arguments) {
      if (!isOutsideWorld()) {
         if (!minecraftClient.isOnThread()) {
            minecraftClient.execute(() -> print(message, arguments));
         } else if (message == null) {
            print(("Object is null"));
         } else {
            minecraftClient.inGameHud.getChatHud().addMessage(Text.literal(message.toString().formatted(arguments).replace('&', '§')));
         }
      }
   }

   private static boolean isOutsideWorld() {
      return minecraftClient.player == null || minecraftClient.world == null;
   }

   public static void sendBaritoneCommand(String command) {
      if (BARITONE_LOADED && minecraftClient.player != null) {
         minecraftClient.player.networkHandler.sendChatMessage(command);
      }
   }

   public static void printPrefixedLiteral(String message) {
      if (minecraftClient.player != null) {
         if (!minecraftClient.isOnThread()) {
            minecraftClient.execute(() -> printPrefixedLiteral(message));
         } else {
            minecraftClient.inGameHud
               .getChatHud()
               .addMessage(
                  gradientText(("маусклиент"), GuiVisuals.accentColor, GuiVisuals.accentGradientColor, true)
                     .append(Text.literal(Formatting.DARK_GRAY + " » " + Formatting.RESET))
                     .append(Text.literal(message))
               );
         }
      }
   }

   public static void print(Text text) {
      if (minecraftClient.player != null) {
         if (!minecraftClient.isOnThread()) {
            minecraftClient.execute(() -> print(text));
         } else {
            minecraftClient.inGameHud.getChatHud().addMessage(text);
         }
      }
   }

   public static void printPrefixed(Text message) {
      if (!isOutsideWorld()) {
         if (!minecraftClient.isOnThread()) {
            minecraftClient.execute(() -> printPrefixed(message));
         } else {
            minecraftClient.inGameHud
               .getChatHud()
               .addMessage(
                  gradientText(("маусклиент"), GuiVisuals.accentColor, GuiVisuals.accentGradientColor, true)
                     .append(Text.literal(Formatting.DARK_GRAY + " » " + Formatting.RESET))
                     .append(message)
               );
         }
      }
   }

   public static MutableText gradientText(String text, int startColor, int endColor, Boolean... bold) {
      MutableText gradient = Text.empty();
      boolean boldEnabled = bold.length > 0 && bold[0];

      for (int characterIndex = 0; characterIndex < text.length(); characterIndex++) {
         float gradientProgress = characterIndex * (1.1F) / text.length();
         gradientProgress = Math.min(1.0F, gradientProgress);
         int characterColor = ArgbColor.lerp(startColor, endColor, gradientProgress) & (16777215);
         Style style = Style.EMPTY.withColor(TextColor.fromRgb(characterColor));
         if (boldEnabled) {
            style = style.withBold(true);
         }

         gradient.append(Text.literal(String.valueOf(text.charAt(characterIndex))).setStyle(style));
      }

      return gradient;
   }

   @Generated
   private ChatMessages() {
      throw new UnsupportedOperationException(("This is a utility class and cannot be instantiated"));
   }

   public static void printStyled(String message, boolean bold, boolean italic, Integer color, boolean lineBold, boolean lineItalic, Integer lineColor, String... lines) {
      if (minecraftClient.player != null) {
         if (!minecraftClient.isOnThread()) {
            minecraftClient.execute(() -> printStyled(message, bold, italic, color, lineBold, lineItalic, lineColor, lines));
         } else {
            MutableText prefixedMessage = gradientText(("маусклиент"), GuiVisuals.accentColor, GuiVisuals.accentGradientColor, true)
               .append(Text.literal(Formatting.DARK_GRAY + " » " + Formatting.RESET));
            MutableText messageText = Text.literal(bold ? message.replace('&', '§').replaceAll(("§[0-9a-fr]"), ("$0§l")) : message.replace('&', '§'));
            Style style = messageText.getStyle();
            if (color != null) {
               style = style.withColor(TextColor.fromRgb(color & (16777215)));
            }

            if (bold) {
               style = style.withBold(true);
            }

            if (italic) {
               style = style.withItalic(true);
            }

            messageText.setStyle(style);
            prefixedMessage.append(messageText);

            for (String lineString : lines) {
               MutableText lineText = Text.literal(lineBold ? lineString.replace('&', '§').replaceAll(("§[0-9a-fr]"), ("$0§l")) : lineString.replace('&', '§'));
               Style lineStyle = lineText.getStyle();
               if (lineColor != null) {
                  lineStyle = lineStyle.withColor(TextColor.fromRgb(lineColor & (16777215)));
               }

               if (lineBold) {
                  lineStyle = lineStyle.withBold(true);
               }

               if (lineItalic) {
                  lineStyle = lineStyle.withItalic(true);
               }

               lineText.setStyle(lineStyle);
               prefixedMessage.append(Text.literal(("\n "))).append(lineText);
            }

            minecraftClient.inGameHud.getChatHud().addMessage(prefixedMessage);
         }
      }
   }

   public static void sendChat(Object message, Object... arguments) {
      if (!isOutsideWorld()) {
         if (message == null) {
            print(("Object is null"));
         } else {
            minecraftClient.player.networkHandler.sendChatMessage(String.format(message.toString(), arguments).replace('§', '&'));
         }
      }
   }

   public static void printPrefixed(String message) {
      if (!isOutsideWorld()) {
         if (!minecraftClient.isOnThread()) {
            minecraftClient.execute(() -> printPrefixed(message));
         } else {
            minecraftClient.inGameHud
               .getChatHud()
               .addMessage(
                  gradientText(("маусклиент"), GuiVisuals.accentColor, GuiVisuals.accentGradientColor, true)
                     .append(Text.literal(Formatting.DARK_GRAY + " » " + Formatting.RESET))
                     .append(Text.literal(message.replace('&', '§')))
               );
         }
      }
   }

   public static void printLines(String message, String... lines) {
      printStyled(message, false, false, null, false, false, null, lines);
   }

   public static void printError(Object message, Object... arguments) {
      print("[" + Formatting.RED + "❌" + Formatting.RESET + "] " + message, arguments);
   }
}
