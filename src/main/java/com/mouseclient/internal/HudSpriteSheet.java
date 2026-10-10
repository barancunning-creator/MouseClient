package com.mouseclient.internal;

import com.mouseclient.gui.Render2D;

public enum HudSpriteSheet {
   MAIN("main", 62, 8, 8, 30.0F),
   TOGGLE("toggle", 22, 5, 5, 25.0F);

   public final String texture;
   public final int frameCount;
   public final int columns;
   public final int rows;
   public final float framesPerSecond;

   HudSpriteSheet(String name, int frameCount, int columns, int rows, float framesPerSecond) {
      this.texture = "assets/mouseclient/textures/gui/hud/" + name + ".png";
      this.frameCount = frameCount;
      this.columns = columns;
      this.rows = rows;
      this.framesPerSecond = framesPerSecond;
   }

   public int frameIndex(float progress) {
      int var2 = this.frameCount - 1;
      int var3 = Math.round(progress * var2);
      return Math.max(0, Math.min(var2, var3));
   }

   public void draw(float x, float y, float width, float height, int color, float progress) {
      int var7 = this.frameIndex(progress);
      int var8 = var7 % this.columns;
      int var9 = var7 / this.columns;
      float var10 = 1.0F / this.columns;
      float var11 = 1.0F / this.rows;
      Render2D.drawImageRegion(this.texture, x, y, width, height, 0.0F, var8 * var10, var9 * var11, var8 * var10 + var10, var9 * var11 + var11, color);
   }
}
