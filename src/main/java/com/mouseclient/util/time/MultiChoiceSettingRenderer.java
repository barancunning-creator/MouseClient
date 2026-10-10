package com.mouseclient.util.time;

import com.mouseclient.gui.ChoicePopupRenderer;
import com.mouseclient.gui.Render2D;
import com.mouseclient.modules.impl.misc.MultiChoiceSetting;

public final class MultiChoiceSettingRenderer implements SettingRenderer {

   @Override
   public boolean supports(Setting<?> setting) {
      return setting instanceof MultiChoiceSetting;
   }

   @Override
   public float render(ClickGuiContext context, Setting<?> setting, float enabledProgress, float x, float y, float width, float[] clip) {
      MultiChoiceSetting multiChoiceSetting = (MultiChoiceSetting)setting;
      float scale = context.scale;
      context.drawMarqueeText(
         multiChoiceSetting.name,
         x,
         y,
         (6.0F) * scale,
         GuiVisuals.lerpColor(ClickGuiTheme.disabledSettingLabel, ClickGuiTheme.enabledSettingLabel, enabledProgress),
         width - (8.0F) * scale
      );
      context.offerTooltip(multiChoiceSetting.description, x, y - 1.0F * scale, width * (0.6F), (8.0F) * scale);
      GuiTextRenderer.draw(
         GuiFont.INSTANCE,
         "Выбрано: " + multiChoiceSetting.getSelectedCount(),
         x,
         y + (7.5F) * scale,
         (5.5F) * scale,
         GuiVisuals.lerpColor(ClickGuiTheme.disabledSettingIcon, ClickGuiTheme.enabledSettingIcon, enabledProgress)
      );
      float popupProgress = context.animateValue("mspop:" + System.identityHashCode(multiChoiceSetting), context.popupOwner == multiChoiceSetting ? 1.0F : 0.0F);
      float chevronCenterX = x + width - (2.2F) * scale;
      float chevronCenterY = y + (6.0F) * scale;
      Render2D.drawRotatedImage(
         ("assets/mouseclient/textures/gui/clickgui/chevright.png"),
         chevronCenterX - (2.75F) * scale,
         chevronCenterY - (2.75F) * scale,
         (5.5F) * scale,
         0.0F,
         (180.0F) - popupProgress * (180.0F),
         chevronCenterX,
         chevronCenterY,
         GuiVisuals.lerpColor(ClickGuiTheme.disabledControlOutline, ClickGuiTheme.enabledControlOutline, enabledProgress)
      );
      context.registerClick(x, y - (1.5F) * scale, width, (15.0F) * scale, clip, () -> context.popupOwner = context.popupOwner == multiChoiceSetting ? null : multiChoiceSetting);
      if (popupProgress > (0.01F)) {
         ChoicePopupRenderer.enqueue(context, x + width, y, multiChoiceSetting.getOptions(), multiChoiceSetting::isSelectedAt, multiChoiceSetting::toggleOptionAt, false, popupProgress, context.popupOwner == multiChoiceSetting);
      }

      return (13.5F) * scale;
   }

   @Override
   public float getHeight(ClickGuiContext context, Setting<?> setting, float width) {
      return (13.5F) * context.scale;
   }
}
