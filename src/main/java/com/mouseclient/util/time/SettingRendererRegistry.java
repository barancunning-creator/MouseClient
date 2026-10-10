package com.mouseclient.util.time;

import com.mouseclient.gui.ColorSettingRenderer;

public final class SettingRendererRegistry {
   private static final SettingRenderer[] renderers = new SettingRenderer[]{
      new BooleanSettingRenderer(),
      new SliderSettingRenderer(),
      new MultiChoiceSettingRenderer(),
      new ChoiceSettingRenderer(),
      new ColorSettingRenderer(),
      new BindSettingRenderer(),
      new TextSettingRenderer(),
      new ActionSettingRenderer()
   };

   private static SettingRenderer getRenderer(Setting<?> setting) {
      for (SettingRenderer renderer : renderers) {
         if (renderer.supports(setting)) {
            return renderer;
         }
      }

      return null;
   }

   public static float renderSetting(ClickGuiContext context, Setting<?> setting, float enabledProgress, float x, float y, float width, float[] clip) {
      SettingRenderer renderer = getRenderer(setting);
      return renderer != null
         ? renderer.render(context, setting, enabledProgress, x, y, width, clip)
         : Math.max(context.settingLabelHeight(setting.description, width), (14.0F) * context.scale);
   }

   public static float getSettingHeight(ClickGuiContext context, Setting<?> setting, float width) {
      SettingRenderer renderer = getRenderer(setting);
      return renderer != null ? renderer.getHeight(context, setting, width) : Math.max(context.settingLabelHeight(setting.description, width), (14.0F) * context.scale);
   }

   private SettingRendererRegistry() {
   }
}
