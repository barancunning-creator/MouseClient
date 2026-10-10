package com.mouseclient.render.pipeline;

import net.minecraft.client.gui.DrawContext;

public record ColorPickerSpec(float x, float y, float width, float height, int mode, float radius, float smoothness, float alpha, int solidColor, float checkerPx) {
   public void render(DrawContext context) {
      ColorPickerRenderer.getInstance().render(context, this);
   }

   public static ColorPickerSpec hue(float x, float y, float width, float height, float alpha) {
      return new ColorPickerSpec(x, y, width, height, 0, height * 0.25F, 0.75F, alpha, 0, height);
   }

   public float width() {
      return this.width;
   }

   public boolean visible() {
      return this.width > 0.0F && this.height > 0.0F && this.alpha > 0.0F;
   }

   public static ColorPickerSpec alpha(float x, float y, float width, float height, int solidColor, float alpha) {
      return new ColorPickerSpec(x, y, width, height, 1, height * 0.25F, 0.75F, alpha, solidColor, height * 0.5F);
   }

   public float height() {
      return this.height;
   }
}
