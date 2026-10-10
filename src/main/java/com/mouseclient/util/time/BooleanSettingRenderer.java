package com.mouseclient.util.time;

import com.mouseclient.gui.Render2D;
import com.mouseclient.modules.impl.render.BooleanSetting;
import com.mouseclient.render.pipeline.RoundedRectangleSpec;

public final class BooleanSettingRenderer implements SettingRenderer {

   @Override
   public float getHeight(ClickGuiContext context, Setting<?> setting, float width) {
      return (7.0F) * context.scale;
   }

   @Override
   public boolean supports(Setting<?> setting) {
      return setting instanceof BooleanSetting;
   }

   @Override
   public float render(ClickGuiContext context, Setting<?> setting, float enabledProgress, float x, float y, float width, float[] clip) {
      BooleanSetting booleanSetting = (BooleanSetting)setting;
      float scale = context.scale;
      float checkedProgress = context.animateBoolean("cb:" + System.identityHashCode(booleanSetting), booleanSetting.getValue());
      context.drawMarqueeText(
         booleanSetting.name,
         x,
         y,
         (6.0F) * scale,
         GuiVisuals.lerpColor(ClickGuiTheme.disabledSettingLabel, ClickGuiTheme.enabledSettingLabel, enabledProgress),
         width - (15.0F) * scale
      );
      context.offerTooltip(booleanSetting.description, x, y - 1.0F * scale, width - (15.0F) * scale, (8.0F) * scale);
      float trackWidth = (10.74F) * scale;
      float trackHeight = (6.64F) * scale;
      float trackX = x + width - trackWidth;
      float trackY = y;
      int inactiveTrackColor = GuiVisuals.lerpColor(ClickGuiTheme.disabledSwitchTrack, ClickGuiTheme.enabledSwitchTrack, enabledProgress);
      int activeTrackColor = GuiVisuals.lerpColor(ClickGuiTheme.disabledSwitchThumb, ClickGuiTheme.accent, enabledProgress);
      Render2D.fillRoundedRectangle(
         new RoundedRectangleSpec(
               trackX,
               trackY,
               trackWidth,
               trackHeight,
               trackHeight / 2.0F,
               trackHeight / 2.0F,
               trackHeight / 2.0F,
               trackHeight / 2.0F,
               Render2D.applyAlpha(GuiVisuals.lerpColor(inactiveTrackColor, activeTrackColor, checkedProgress))
            )
            .withSmoothness((0.5F))
      );
      float thumbDiameter = (4.88F) * scale;
      float thumbX = trackX + (0.93F) * scale + checkedProgress * (trackWidth - thumbDiameter - (1.86F) * scale);
      Render2D.fillCircle(
         thumbX + thumbDiameter / 2.0F,
         trackY + trackHeight / 2.0F,
         thumbDiameter / 2.0F,
         (0.5F),
         Render2D.applyAlpha(GuiVisuals.lerpColor(GuiVisuals.lerpColor(ClickGuiTheme.disabledSwitchOutline, ClickGuiTheme.enabledSwitchOutline, enabledProgress), GuiVisuals.white, checkedProgress))
      );
      context.registerClick(trackX - 2.0F * scale, trackY - 2.0F * scale, trackWidth + (4.0F) * scale, trackHeight + (4.0F) * scale, clip, booleanSetting::toggle);
      return (7.0F) * scale;
   }
}
