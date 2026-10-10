package com.mouseclient.gui;

import com.mouseclient.modules.impl.render.ColorSetting;
import com.mouseclient.util.time.ClickGuiContext;
import com.mouseclient.util.time.GuiVisuals;
import com.mouseclient.util.time.Setting;
import com.mouseclient.util.time.ClickGuiTheme;
import com.mouseclient.util.time.SettingRenderer;
import java.awt.Color;

public final class ColorSettingRenderer implements SettingRenderer {

   @Override
   public float getHeight(ClickGuiContext context, Setting<?> setting, float width) {
      return (7.0F) * context.scale;
   }

   @Override
   public float render(ClickGuiContext context, Setting<?> setting, float enabledProgress, float x, float y, float width, float[] clip) {
      ColorSetting colorSetting = (ColorSetting)setting;
      float scale = context.scale;
      context.drawMarqueeText(
         colorSetting.name,
         x,
         y,
         (6.0F) * scale,
         GuiVisuals.lerpColor(ClickGuiTheme.disabledSettingLabel, ClickGuiTheme.enabledSettingLabel, enabledProgress),
         width - (22.0F) * scale
      );
      context.offerTooltip(colorSetting.description, x, y - 1.0F * scale, width * (0.5F), (8.0F) * scale);
      float[] hsba = context.hsbaState(colorSetting);
      int previewColor = opaqueRgb(Color.HSBtoRGB(hsba[0], hsba[1], hsba[2]));
      float previewCenterX = x + width - (3.72F) * scale;
      float previewCenterY = y + (3.0F) * scale;
      Render2D.drawRoundedImage(
         ("assets/mouseclient/textures/gui/clickgui/picker.png"),
         previewCenterX - (13.31F) * scale,
         previewCenterY - (3.05F) * scale,
         (6.1F) * scale,
         (6.1F) * scale,
         0.0F,
         GuiVisuals.multiplyAlpha(ClickGuiTheme.accent, (0.35F))
      );
      Render2D.fillCircle(previewCenterX, previewCenterY, (2.81F) * scale, previewColor);
      Render2D.strokeCircle(previewCenterX, previewCenterY, (3.72F) * scale, (0.5F) * scale, GuiVisuals.multiplyAlpha(previewColor, (0.4F)));
      context.registerClick(
         x + width / 2.0F, y - (1.5F) * scale, width / 2.0F, (9.0F) * scale, clip, () -> context.popupOwner = context.popupOwner == colorSetting ? null : colorSetting
      );
      int settingIdentity = System.identityHashCode(colorSetting);
      boolean popupOpen = context.popupOwner == colorSetting;
      float popupProgress = context.animateValue("colorpop:" + settingIdentity, popupOpen ? 1.0F : 0.0F);
      if (popupProgress < (0.01F)) {
         return (7.0F) * scale;
      }

      float popupAnchorX = previewCenterX + (31.6F) * scale;
      float popupAnchorY = y - 2.0F * scale;
      context.overlayRenderers
         .add(
            () -> {
               float[] pickerHsba = context.hsbaState(colorSetting);
               float animatedSaturation = context.animateValue("colorS:" + settingIdentity, pickerHsba[1]);
               float animatedBrightness = context.animateValue("colorB:" + settingIdentity, pickerHsba[2]);
               float animatedHue = context.animateValue("colorH:" + settingIdentity, pickerHsba[0]);
               float padding = (4.0F) * scale;
               float pickerWidth = (58.0F) * scale;
               float pickerHeight = (40.0F) * scale;
               float hueBarHeight = (3.5F) * scale;
               float popupWidth = pickerWidth + padding * 2.0F;
               float popupHeight = padding + pickerHeight + padding + hueBarHeight + padding;
               float popupX = popupAnchorX - popupWidth;
               float popupY = Math.max((4.0F) * scale, popupAnchorY - popupHeight) + (1.0F - popupProgress) * (5.0F) * scale;
               if (popupOpen) {
                  context.registerClick((-100000.0F), (-100000.0F), (200000.0F), (200000.0F), null, () -> context.popupOwner = null);
               }

               Render2D.pushAlpha(popupProgress);
               Render2D.drawSharedBlur(
                  popupX + (0.4F) * scale,
                  popupY + (0.4F) * scale,
                  popupWidth - (0.8F) * scale,
                  popupHeight - (0.8F) * scale,
                  (4.1F) * scale,
                  (15.0F),
                  1.0F,
                  -1
               );
               Render2D.fillRoundedRectangle(popupX, popupY, popupWidth, popupHeight, (4.5F) * scale, ClickGuiTheme.popupBackground);
               Render2D.drawOutline(popupX, popupY, popupWidth, popupHeight, (4.5F) * scale, (0.5F), ClickGuiTheme.popupOutline);
               float pickerX = popupX + padding;
               float pickerY = popupY + padding;
               int hueColor = opaqueRgb(Color.HSBtoRGB(animatedHue, 1.0F, 1.0F));
               Render2D.fillRoundedGradient(pickerX, pickerY, pickerWidth, pickerHeight, (2.5F) * scale, GuiVisuals.white, hueColor, hueColor, GuiVisuals.white);
               Render2D.fillRoundedGradient(pickerX, pickerY, pickerWidth, pickerHeight, (2.5F) * scale, 0, 0, (-16777216), (-16777216));
               Render2D.drawOutline(pickerX, pickerY, pickerWidth, pickerHeight, (2.5F) * scale, (0.5F), (1023410176));
               float saturationCursorX = pickerX + animatedSaturation * pickerWidth;
               float brightnessCursorY = pickerY + (1.0F - animatedBrightness) * pickerHeight;
               Render2D.fillCircle(saturationCursorX, brightnessCursorY, (2.2F) * scale, opaqueRgb(Color.HSBtoRGB(animatedHue, animatedSaturation, animatedBrightness)));
               Render2D.strokeCircle(saturationCursorX, brightnessCursorY, (2.6F) * scale, (0.8F) * scale, GuiVisuals.white);
               float hueBarY = pickerY + pickerHeight + padding;
               Render2D.drawHuePicker(pickerX, hueBarY, pickerWidth, hueBarHeight, 1.0F);
               float hueCursorX = pickerX + animatedHue * pickerWidth;
               Render2D.fillCircle(hueCursorX, hueBarY + hueBarHeight / 2.0F, (2.2F) * scale, hueColor);
               Render2D.strokeCircle(hueCursorX, hueBarY + hueBarHeight / 2.0F, (2.6F) * scale, (0.8F) * scale, GuiVisuals.white);
               Render2D.popAlpha();
               if (popupOpen) {
                  context.registerClick(pickerX, pickerY, pickerWidth, pickerHeight, null, () -> {
                     context.draggedColor = colorSetting;
                     context.colorDragMode = 0;
                  });
                  context.registerClick(pickerX, hueBarY - 2.0F * scale, pickerWidth, hueBarHeight + (4.0F) * scale, null, () -> {
                     context.draggedColor = colorSetting;
                     context.colorDragMode = 1;
                  });
                  context.colorPickerBounds.put(colorSetting, new float[]{pickerX, pickerY, pickerWidth, pickerHeight, pickerX, hueBarY, pickerWidth, hueBarHeight});
               }
            }
         );
      return (7.0F) * scale;
   }

   @Override
   public boolean supports(Setting<?> setting) {
      return setting instanceof ColorSetting;
   }

   private static int opaqueRgb(int color) {
      return (-16777216) | color & (16777215);
   }
}
