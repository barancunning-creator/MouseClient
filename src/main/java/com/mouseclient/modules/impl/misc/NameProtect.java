package com.mouseclient.modules.impl.misc;

import com.mouseclient.annotation.ModuleInfo;
import com.mouseclient.internal.ArgbColor;
import com.mouseclient.util.time.GuiVisuals;
import net.minecraft.text.CharacterVisitor;

@ModuleInfo(name = "NameProtect", description = "Заменяет твой ник во всём интерфейсе.", category = ModuleCategory.MISC)
public final class NameProtect extends Module {
   private static NameProtect INSTANCE;
   public FilteredTextSetting nickname;

   public String replaceNickname(String text) {
      return this.isEnabled()
            && text != null
            && minecraftClient.player != null
            && !this.nickname.getText().isEmpty()
            && text.contains(minecraftClient.player.getNameForScoreboard())
         ? text.replace(minecraftClient.player.getNameForScoreboard(), this.nickname.getText())
         : text;
   }

   private NameProtect() {
      this.nickname = this.addSetting(new FilteredTextSetting(("Ник"), "", ("MouseClient"), 16));
   }

   public static NameProtect getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new NameProtect();
      }

      return INSTANCE;
   }

   public CharacterVisitor nicknameColorVisitor(String text, CharacterVisitor visitor) {
      return !this.nickname.getText().isEmpty() && text.contains(this.nickname.getText())
         ? (characterIndex, style, codePoint) -> {
            int occurrenceStart = text.lastIndexOf(this.nickname.getText(), characterIndex);
            return occurrenceStart >= 0 && characterIndex < occurrenceStart + this.nickname.getText().length()
               ? visitor.accept(
                  characterIndex,
                  style.withColor(
                        ArgbColor.lerp(
                              GuiVisuals.accentColor, GuiVisuals.accentGradientColor, Math.min(1.0F, (characterIndex - occurrenceStart) * (1.1F) / this.nickname.getText().length())
                           )
                           & (16777215)
                     )
                     .withBold(true),
                  codePoint
               )
               : visitor.accept(characterIndex, style, codePoint);
         }
         : visitor;
   }

   public int nicknameColor(int characterIndex, int baseColor) {
      return ArgbColor.lerp(GuiVisuals.accentColor, GuiVisuals.accentGradientColor, Math.min(1.0F, characterIndex * (1.1F) / this.nickname.getText().length()))
            & (16777215)
         | baseColor & (-16777216);
   }
}
