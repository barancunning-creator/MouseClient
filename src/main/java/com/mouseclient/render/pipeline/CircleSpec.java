package com.mouseclient.render.pipeline;

import net.minecraft.client.gui.DrawContext;

public record CircleSpec(float x, float y, float radius, float thickness, float smoothness, int color) {
   public static final float DEFAULT_SMOOTHNESS = 0.5F;

   public void render(DrawContext context) {
      CircleRenderer.getInstance().render(context, this);
   }

   public boolean visible() {
      return this.radius > 0.0F && this.color >>> 24 != 0;
   }

   public CircleSpec withSmoothness(float smoothness) {
      return new CircleSpec(this.x, this.y, this.radius, this.thickness, smoothness, this.color);
   }

   public CircleSpec(float x, float y, float radius, int color) {
      this(x, y, radius, 0.0F, 0.5F, color);
   }

   public CircleSpec withThickness(float thickness) {
      return new CircleSpec(this.x, this.y, this.radius, thickness, this.smoothness, this.color);
   }
}
