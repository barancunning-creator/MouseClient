package com.mouseclient.util.time;

import com.mouseclient.gui.Render2D;
import java.util.List;

public final class ChoiceSettingRenderer implements SettingRenderer {

   @Override
   public boolean supports(Setting<?> setting) {
      return setting instanceof ChoiceSetting;
   }

   private float getOptionWidth(ClickGuiContext context, ChoiceSetting choiceSetting, int optionIndex, float maximumWidth) {
      float selectedProgress = context.animateBoolean("seg:" + System.identityHashCode(choiceSetting) + ":" + optionIndex, choiceSetting.getSelectedIndex() == optionIndex);
      return Math.min(
         maximumWidth,
         GuiTextRenderer.getWidth(GuiFont.INSTANCE, choiceSetting.getOptions().get(optionIndex), (6.0F) * context.scale)
            + (8.6F) * context.scale
            + selectedProgress * (6.2F) * context.scale
      );
   }

   @Override
   public float render(ClickGuiContext context, Setting<?> setting, float enabledProgress, float x, float y, float width, float[] clip) {
      ChoiceSetting choiceSetting = (ChoiceSetting)setting;
      float scale = context.scale;
      float selectedTextWidth = Math.min(GuiTextRenderer.getWidth(GuiFont.INSTANCE, choiceSetting.getSelectedOption(), (6.0F) * scale), width * (0.5F));
      context.drawMarqueeText(
         choiceSetting.name,
         x,
         y,
         (6.0F) * scale,
         GuiVisuals.lerpColor(ClickGuiTheme.disabledSettingLabel, ClickGuiTheme.enabledSettingLabel, enabledProgress),
         width - selectedTextWidth - (4.0F) * scale
      );
      context.offerTooltip(choiceSetting.description, x, y - 1.0F * scale, width - selectedTextWidth - (4.0F) * scale, (8.0F) * scale);
      context.drawMarqueeText(
         choiceSetting.getSelectedOption(),
         x + width - selectedTextWidth,
         y,
         (6.0F) * scale,
         GuiVisuals.lerpColor(ClickGuiTheme.choiceText, ClickGuiTheme.activeChoiceText, enabledProgress),
         width * (0.5F)
      );
      float optionX = x;
      float optionY = y + (9.55F) * scale;
      java.util.List<java.lang.String> options = choiceSetting.getOptions();

      for (int optionIndex = 0; optionIndex < options.size(); optionIndex++) {
         float optionWidth = this.getOptionWidth(context, choiceSetting, optionIndex, width);
         if (optionX + optionWidth > x + width + (0.01F) && optionX > x) {
            optionX = x;
            optionY += (11.320001F) * scale;
         }

         float selectedProgress = context.animateBoolean("seg:" + System.identityHashCode(choiceSetting) + ":" + optionIndex, choiceSetting.getSelectedIndex() == optionIndex);
         Render2D.fillRoundedRectangle(
            optionX, optionY, optionWidth, (9.77F) * scale, 2.0F * scale, GuiVisuals.lerpColor(ClickGuiTheme.choiceFill, ClickGuiTheme.hoveredChoiceFill, selectedProgress)
         );
         Render2D.drawOutline(
            optionX,
            optionY,
            optionWidth,
            (9.77F) * scale,
            2.0F * scale,
            (0.5F),
            GuiVisuals.lerpColor(ClickGuiTheme.choiceOutline, ClickGuiTheme.hoveredChoiceOutline, selectedProgress)
         );
         if (selectedProgress > (0.01F)) {
            Render2D.drawRoundedImage(
               ("assets/mouseclient/textures/gui/clickgui/check.png"),
               optionX + (3.9F) * scale,
               optionY + ((9.77F) * scale - (4.7F) * scale) / 2.0F,
               (4.7F) * scale,
               (4.7F) * scale,
               0.0F,
               GuiVisuals.multiplyAlpha(ClickGuiTheme.activeChoiceText, selectedProgress)
            );
         }

         context.drawMarqueeText(
            (String)options.get(optionIndex),
            optionX + (4.3F) * scale + selectedProgress * (6.2F) * scale,
            optionY + (1.4F) * scale,
            (6.0F) * scale,
            GuiVisuals.lerpColor(ClickGuiTheme.choiceText, ClickGuiTheme.activeChoiceText, selectedProgress),
            optionWidth - (8.6F) * scale - selectedProgress * (6.2F) * scale
         );
         int clickedOptionIndex = optionIndex;
         context.registerClick(optionX, optionY, optionWidth, (9.77F) * scale, clip, () -> choiceSetting.setSelectedIndex(clickedOptionIndex));
         optionX += optionWidth + (1.55F) * scale;
      }

      return (7.0F) * scale + (2.55F) * scale + this.getOptionsHeight(context, choiceSetting, width);
   }

   @Override
   public float getHeight(ClickGuiContext context, Setting<?> setting, float width) {
      return (7.0F) * context.scale + (2.55F) * context.scale + this.getOptionsHeight(context, (ChoiceSetting)setting, width);
   }

   private float getOptionsHeight(ClickGuiContext context, ChoiceSetting choiceSetting, float width) {
      float scale = context.scale;
      float rowCount = 1.0F;
      float rowWidth = 0.0F;

      for (int optionIndex = 0; optionIndex < choiceSetting.getOptions().size(); optionIndex++) {
         if (rowWidth + this.getOptionWidth(context, choiceSetting, optionIndex, width) > width + (0.01F) && rowWidth > 0.0F) {
            rowCount++;
            rowWidth = 0.0F;
         }

         rowWidth += this.getOptionWidth(context, choiceSetting, optionIndex, width) + (1.55F) * scale;
      }

      return rowCount * (9.77F) * scale + (rowCount - 1.0F) * (1.55F) * scale;
   }
}
