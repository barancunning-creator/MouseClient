package com.mouseclient.util.time;

public final class TextSettingRenderer implements SettingRenderer {

   @Override
   public float getHeight(ClickGuiContext context, Setting<?> setting, float width) {
      return (7.0F) * context.scale;
   }

   @Override
   public boolean supports(Setting<?> setting) {
      return setting instanceof TextSetting;
   }

   @Override
   public float render(ClickGuiContext context, Setting<?> setting, float enabledProgress, float x, float y, float width, float[] clip) {
      TextSetting textSetting = (TextSetting)setting;
      float scale = context.scale;
      boolean focused = context.focusedText == textSetting;
      boolean empty = textSetting.getValue().isEmpty();
      String placeholder = textSetting.getPlaceholder().isEmpty() ? ("Введите текст") : textSetting.getPlaceholder();
      float maximumValueWidth = width * (0.55F);
      float valueWidth = Math.min(
         GuiTextRenderer.getWidth(GuiFont.INSTANCE, empty ? placeholder : textSetting.getValue(), (6.0F) * scale) + (focused ? 2.0F * scale : 0.0F), maximumValueWidth
      );
      context.drawMarqueeText(
         textSetting.name,
         x,
         y,
         (6.0F) * scale,
         GuiVisuals.lerpColor(ClickGuiTheme.disabledSettingLabel, ClickGuiTheme.enabledSettingLabel, enabledProgress),
         width - valueWidth - (5.0F) * scale
      );
      context.offerTooltip(textSetting.description, x, y - 1.0F * scale, width * (0.4F), (8.0F) * scale);
      float placeholderProgress = context.animateValue("inputHint:" + System.identityHashCode(textSetting), empty ? 1.0F : 0.0F);
      if (placeholderProgress > (0.01F)) {
         float placeholderWidth = Math.min(GuiTextRenderer.getWidth(GuiFont.INSTANCE, placeholder, (6.0F) * scale), maximumValueWidth);
         context.drawMarqueeText(
            placeholder,
            x + width - placeholderWidth - (focused ? 2.0F * scale : 0.0F),
            y,
            (6.0F) * scale,
            GuiVisuals.multiplyAlpha(GuiVisuals.lerpColor(ClickGuiTheme.disabledSettingIcon, ClickGuiTheme.enabledSettingIcon, enabledProgress), (focused ? (0.55F) : 1.0F) * placeholderProgress),
            maximumValueWidth
         );
      }

      if (focused) {
         float inputWidth = Math.min(GuiTextRenderer.getWidth(GuiFont.INSTANCE, textSetting.getValue(), (6.0F) * scale), maximumValueWidth);
         context.drawTextInput(
            textSetting, x + width - inputWidth - 2.0F * scale, y, (6.0F) * scale, GuiVisuals.lerpColor(ClickGuiTheme.disabledValueText, ClickGuiTheme.accent, enabledProgress)
         );
      } else if (!empty) {
         context.drawMarqueeText(
            textSetting.getValue(),
            x + width - valueWidth,
            y,
            (6.0F) * scale,
            GuiVisuals.lerpColor(ClickGuiTheme.disabledValueText, ClickGuiTheme.accent, enabledProgress),
            maximumValueWidth
         );
      }

      context.registerClick(x + width / 2.0F, y - (1.5F) * scale, width / 2.0F, (9.0F) * scale, clip, () -> {
         context.focusedText = textSetting;
         textSetting.moveToEnd(false);
      });
      return (7.0F) * scale;
   }
}
