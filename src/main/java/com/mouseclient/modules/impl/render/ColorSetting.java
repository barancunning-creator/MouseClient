package com.mouseclient.modules.impl.render;

import com.mouseclient.util.time.Setting;
import java.awt.Color;

public final class ColorSetting extends Setting<ColorSetting> {
   private int argb;

   private float[] toHsb() {
      Color color = new Color(this.argb, true);
      return Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
   }

   public int getValue() {
      return this.argb;
   }

   public int getOpaqueRgb() {
      return (-16777216) | this.argb & (16777215);
   }

   public float getSaturation() {
      return this.toHsb()[1];
   }

   public ColorSetting setHsb(float hue, float saturation, float brightness) {
      int alpha = this.getAlpha();
      int rgb = Color.HSBtoRGB(hue, saturation, brightness) & (16777215);
      return this.setArgb(alpha << 24 | rgb);
   }

   public float getBrightness() {
      return this.toHsb()[2];
   }

   public ColorSetting(String name, String description, Color color) {
      super(name, description);
      this.argb = color.getRGB();
   }

   public ColorSetting(String name, String description) {
      this(name, description, new Color(255, 255, 255, 255));
   }

   public ColorSetting withColor(int color) {
      return this.setArgb(color);
   }

   public int getArgb() {
      return this.argb;
   }

   public ColorSetting setOpacity(float opacity) {
      int alpha = Math.round(Math.max(0.0F, Math.min(1.0F, opacity)) * (255.0F));
      return this.setArgb(alpha << 24 | this.argb & (16777215));
   }

   public int getAlpha() {
      return this.argb >> 24 & 0xFF;
   }

   public ColorSetting setArgb(int argb) {
      if (argb != this.argb) {
         this.argb = argb;
         this.notifyChanged();
      }

      return this;
   }

   public float getHue() {
      return this.toHsb()[0];
   }
}
