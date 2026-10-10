package com.mouseclient.util.time;

import com.mouseclient.gui.Render2D;
import com.mouseclient.render.pipeline.SliderSetting;

public final class SliderSettingRenderer implements SettingRenderer {

   @Override
   public float render(ClickGuiContext context, Setting<?> setting, float enabledProgress, float x, float y, float width, float[] clip) {
      SliderSetting sliderSetting = (SliderSetting)setting;
      float scale = context.scale;
      String formattedValue = sliderSetting.getFormattedValue();
      context.drawMarqueeText(
         sliderSetting.name,
         x,
         y,
         (6.0F) * scale,
         GuiVisuals.lerpColor(ClickGuiTheme.disabledSettingLabel, ClickGuiTheme.enabledSettingLabel, enabledProgress),
         width - GuiTextRenderer.getWidth(GuiFont.INSTANCE, formattedValue, (6.0F) * scale) - (4.0F) * scale
      );
      context.offerTooltip(sliderSetting.description, x, y - 1.0F * scale, width * (0.6F), (8.0F) * scale);
      GuiTextRenderer.draw(
         GuiFont.INSTANCE,
         formattedValue,
         x + width - GuiTextRenderer.getWidth(GuiFont.INSTANCE, formattedValue, (6.0F) * scale),
         y,
         (6.0F) * scale,
         GuiVisuals.lerpColor(
            GuiVisuals.lerpColor(ClickGuiTheme.disabledValueText, ClickGuiTheme.accent, enabledProgress),
            (-43691),
            context.animateValue("sliderDanger:" + System.identityHashCode(sliderSetting), sliderSetting.isTextEntryEnabled() ? 1.0F : 0.0F)
         )
      );
      float trackY = y + (9.65F) * scale;
      float trackHeight = (1.63F) * scale;
      Render2D.fillRoundedRectangle(x, trackY, width, trackHeight, trackHeight / 2.0F, GuiVisuals.lerpColor(ClickGuiTheme.disabledSliderTrack, ClickGuiTheme.enabledSliderTrack, enabledProgress));
      float animatedValue = context.animateValue("slider:" + System.identityHashCode(sliderSetting), sliderSetting.getNormalizedValue());
      float fillWidth = Math.max((3.0F) * scale, animatedValue * width);
      Render2D.fillRoundedRectangle(x, trackY, fillWidth, trackHeight, trackHeight / 2.0F, GuiVisuals.lerpColor(ClickGuiTheme.disabledSliderFill, ClickGuiTheme.accent, enabledProgress));
      float thumbDiameter = (2.97F) * scale;
      Render2D.fillRoundedRectangle(
         x + fillWidth - thumbDiameter / 2.0F - (0.5F) * scale, trackY + trackHeight / 2.0F - thumbDiameter / 2.0F, thumbDiameter, thumbDiameter, thumbDiameter / 2.0F, GuiVisuals.white
      );
      context.sliderBounds.put(sliderSetting, new float[]{x, width});
      context.registerClick(
         x,
         trackY - (2.5F) * scale,
         width,
         trackHeight + (5.0F) * scale,
         clip,
         () -> context.draggedSlider = sliderSetting,
         () -> sliderSetting.setValue(context.stateFor(sliderSetting, sliderSetting.floatValue()))
      );
      return (13.3F) * scale;
   }

   @Override
   public float getHeight(ClickGuiContext context, Setting<?> setting, float width) {
      return (13.3F) * context.scale;
   }

   @Override
   public boolean supports(Setting<?> setting) {
      return setting instanceof SliderSetting;
   }
}
