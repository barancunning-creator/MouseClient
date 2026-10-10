package com.mouseclient.hud;

import com.mouseclient.gui.Render2D;
import com.mouseclient.network.HudStyle;

public enum HudIcon {
   KEYBINDS("z", null),
   POTIONS(null, "hud_potions"),
   COOLDOWNS("ξ", null),
   STAFF("ν", null),
   INVENTORY(null, "hud_inventory"),
   USER("E", null),
   FPS("x", null),
   TIME("B", null),
   PING(null, "hud_ping"),
   COORDS(null, "hud_coords"),
   ENVIRONMENT(null, "hud_environment"),
   ARROW(null, "hud_arrow"),
   COPY(null, "hud_copy"),
   CHECK(null, "hud_check");

   public final String glyph;
   public final String texture;

   HudIcon(String glyph, String textureName) {
      this.glyph = glyph;
      this.texture = textureName == null ? null : "assets/mouseclient/textures/gui/hud/" + textureName + ".png";
   }

   public void draw(float x, float y, float size, int color) {
      if (this.glyph != null) {
         HudStyle.drawIcon(this.glyph, x + size / 2.0F, y + size / 2.0F, size, color);
      } else {
         Render2D.drawRoundedImage(this.texture, x, y, size, size, 0.0F, color);
      }
   }
}
