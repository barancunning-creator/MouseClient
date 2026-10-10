package com.mouseclient.mode;

public record ItemRenderOptions(float alpha, int color, GlintMode glintMode, boolean showCount, boolean showDurability, float glintStrength) {
   private static final ItemRenderOptions DEFAULTS = new ItemRenderOptions(1.0F, -1, GlintMode.AUTO, true, true, 0.62F);

   private static float clamp(float value, float minimum, float maximum) {
      return Math.max(minimum, Math.min(maximum, value));
   }

   public static ItemRenderOptions noDecorations(float alpha) {
      return DEFAULTS.alpha(alpha).count(false).durability(false);
   }

   private static int normalizeColor(int color) {
      return (color & 0xFF000000) == 0 && (color & 16777215) != 0 ? color | 0xFF000000 : color;
   }

   public ItemRenderOptions glintStrength(float strength) {
      return new ItemRenderOptions(this.alpha, this.color, this.glintMode, this.showCount, this.showDurability, strength);
   }

   public ItemRenderOptions count(boolean showCount) {
      return new ItemRenderOptions(this.alpha, this.color, this.glintMode, showCount, this.showDurability, this.glintStrength);
   }

   public ItemRenderOptions alpha(float alpha) {
      return new ItemRenderOptions(alpha, this.color, this.glintMode, this.showCount, this.showDurability, this.glintStrength);
   }

   public ItemRenderOptions(float alpha, int color, GlintMode glintMode, boolean showCount, boolean showDurability, float glintStrength) {
      alpha = clamp(alpha, 0.0F, 1.0F);
      color = normalizeColor(color);
      glintMode = glintMode == null ? GlintMode.AUTO : glintMode;
      glintStrength = clamp(glintStrength, 0.0F, 1.0F);
      this.alpha = alpha;
      this.color = color;
      this.glintMode = glintMode;
      this.showCount = showCount;
      this.showDurability = showDurability;
      this.glintStrength = glintStrength;
   }

   public ItemRenderOptions glint(GlintMode mode) {
      return new ItemRenderOptions(this.alpha, this.color, mode, this.showCount, this.showDurability, this.glintStrength);
   }

   public ItemRenderOptions durability(boolean showDurability) {
      return new ItemRenderOptions(this.alpha, this.color, this.glintMode, this.showCount, showDurability, this.glintStrength);
   }

   public static ItemRenderOptions decorated(float alpha) {
      return DEFAULTS.alpha(alpha).count(true).durability(true);
   }

   public static ItemRenderOptions defaults() {
      return DEFAULTS;
   }

   public ItemRenderOptions color(int color) {
      return new ItemRenderOptions(this.alpha, color, this.glintMode, this.showCount, this.showDurability, this.glintStrength);
   }
}
