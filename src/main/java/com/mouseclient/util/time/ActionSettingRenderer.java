package com.mouseclient.util.time;

import com.mouseclient.gui.Render2D;

public final class ActionSettingRenderer implements SettingRenderer {

   @Override
   public float render(ClickGuiContext context, Setting<?> setting, float enabledProgress, float x, float y, float width, float[] clip) {
      ActionSetting actionSetting = (ActionSetting)setting;
      float scale = context.scale;
      float buttonWidth = Math.max((18.0F) * scale, GuiTextRenderer.getWidth(GuiFont.INSTANCE, actionSetting.getLabel(), (6.0F) * scale) + (9.5F) * scale);
      context.drawMarqueeText(
         actionSetting.name,
         x,
         y + (1.3F) * scale,
         (6.0F) * scale,
         GuiVisuals.lerpColor(ClickGuiTheme.disabledSettingLabel, ClickGuiTheme.enabledSettingLabel, enabledProgress),
         width - buttonWidth - (4.0F) * scale
      );
      context.offerTooltip(actionSetting.description, x, y, width * (0.5F), (9.6F) * scale);
      float buttonX = x + width - buttonWidth;
      float hoverProgress = context.animateBoolean(
         "btn:" + System.identityHashCode(actionSetting), GuiVisuals.containsPoint(context.mouseX, context.mouseY, buttonX, y, buttonWidth, (9.57F) * scale)
      );
      Render2D.fillRoundedRectangle(
         buttonX,
         y,
         buttonWidth,
         (9.57F) * scale,
         (2.5F) * scale,
         GuiVisuals.lerpColor(
            GuiVisuals.multiplyAlpha(ClickGuiTheme.accent, (0.55F) + (0.45F) * enabledProgress),
            GuiVisuals.lerpColor(ClickGuiTheme.accent, GuiVisuals.white, (0.12F)),
            hoverProgress
         )
      );
      Render2D.drawOutline(buttonX, y, buttonWidth, (9.57F) * scale, (2.5F) * scale, (0.5F), ClickGuiTheme.actionButtonOutline);
      GuiTextRenderer.draw(
         GuiFont.INSTANCE,
         actionSetting.getLabel(),
         buttonX + (buttonWidth - GuiTextRenderer.getWidth(GuiFont.INSTANCE, actionSetting.getLabel(), (6.0F) * scale)) / 2.0F,
         y + (1.8F) * scale,
         (6.0F) * scale,
         GuiVisuals.white
      );
      context.registerClick(buttonX, y, buttonWidth, (9.57F) * scale, clip, actionSetting::execute);
      return (9.6F) * scale;
   }

   @Override
   public float getHeight(ClickGuiContext context, Setting<?> setting, float width) {
      return (9.6F) * context.scale;
   }

   @Override
   public boolean supports(Setting<?> setting) {
      return setting instanceof ActionSetting;
   }
}
