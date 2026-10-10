package com.mouseclient.util.time;

import com.mouseclient.gui.Render2D;
import com.mouseclient.modules.impl.player.BindSetting;

public final class BindSettingRenderer implements SettingRenderer {

   @Override
   public boolean supports(Setting<?> setting) {
      return setting instanceof BindSetting;
   }

   @Override
   public float render(ClickGuiContext context, Setting<?> setting, float enabledProgress, float x, float y, float width, float[] clip) {
      BindSetting bindSetting = (BindSetting)setting;
      float scale = context.scale;
      String bindingLabel = context.bindingSetting == bindSetting ? ("Нажми клавишу") : (bindSetting.isBound() ? bindSetting.getBindingName() : ("—"));
      float bindingWidth = GuiTextRenderer.getWidth(GuiFont.INSTANCE, bindingLabel, (6.0F) * scale) + (context.bindingSetting == bindSetting ? (6.6F) * scale : 0.0F);
      context.drawMarqueeText(
         bindSetting.name,
         x,
         y,
         (6.0F) * scale,
         GuiVisuals.lerpColor(ClickGuiTheme.disabledSettingLabel, ClickGuiTheme.enabledSettingLabel, enabledProgress),
         width - bindingWidth - (4.0F) * scale
      );
      context.offerTooltip(bindSetting.description, x, y - 1.0F * scale, width * (0.5F), (8.0F) * scale);
      if (context.bindingSetting == bindSetting) {
         String capturePrompt = ("Нажми клавишу");
         float capturePromptWidth = GuiTextRenderer.getWidth(GuiFont.INSTANCE, capturePrompt, (6.0F) * scale);
         GuiTextRenderer.draw(
            GuiFont.INSTANCE,
            capturePrompt,
            x + width - capturePromptWidth,
            y,
            (6.0F) * scale,
            GuiVisuals.lerpColor(ClickGuiTheme.disabledSettingIcon, ClickGuiTheme.enabledSettingIcon, enabledProgress)
         );
         Render2D.drawRoundedImage(
            ("assets/mouseclient/textures/gui/clickgui/bindkey.png"),
            x + width - capturePromptWidth - (6.6F) * scale,
            y + (0.4F) * scale,
            (5.1F) * scale,
            (5.1F) * scale,
            0.0F,
            GuiVisuals.lerpColor(ClickGuiTheme.disabledSettingIcon, ClickGuiTheme.enabledSettingIcon, enabledProgress)
         );
      } else if (bindSetting.isBound()) {
         float boundKeyWidth = GuiTextRenderer.getWidth(GuiFont.INSTANCE, bindSetting.getBindingName(), (6.0F) * scale);
         GuiTextRenderer.draw(
            GuiFont.INSTANCE,
            bindSetting.getBindingName(),
            x + width - boundKeyWidth,
            y,
            (6.0F) * scale,
            GuiVisuals.lerpColor(ClickGuiTheme.disabledValueText, ClickGuiTheme.accent, enabledProgress)
         );
      } else {
         float unboundLabelWidth = GuiTextRenderer.getWidth(GuiFont.INSTANCE, ("—"), (6.0F) * scale);
         GuiTextRenderer.draw(
            GuiFont.INSTANCE,
            ("—"),
            x + width - unboundLabelWidth,
            y,
            (6.0F) * scale,
            GuiVisuals.lerpColor(ClickGuiTheme.disabledSettingIcon, ClickGuiTheme.enabledSettingIcon, enabledProgress)
         );
      }

      context.registerClick(x + width / 2.0F, y - (1.5F) * scale, width / 2.0F, (9.0F) * scale, clip, () -> context.bindingSetting = bindSetting, () -> bindSetting.withKeyCode(-1));
      return (7.0F) * scale;
   }

   @Override
   public float getHeight(ClickGuiContext context, Setting<?> setting, float width) {
      return (7.0F) * context.scale;
   }
}
