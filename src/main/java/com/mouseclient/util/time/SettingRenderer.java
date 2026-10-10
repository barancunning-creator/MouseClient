package com.mouseclient.util.time;

public interface SettingRenderer {
   public float render(ClickGuiContext context, Setting<?> setting, float enabledProgress, float x, float y, float width, float[] clip);

   public boolean supports(Setting<?> setting);

   public float getHeight(ClickGuiContext context, Setting<?> setting, float width);
}
