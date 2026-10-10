package com.mouseclient.modules.impl.misc;

public enum ModuleCategory {
   COMBAT("Combat", "k"),
   MOVEMENT("Movement", "l"),
   RENDER("Visuals", "m"),
   PLAYER("Player", "n"),
   MISC("Misc", "o"),
   FUN("Fun", "p");

   private final String displayName;
   private final String iconGlyph;

   ModuleCategory(String displayName, String iconGlyph) {
      this.displayName = displayName;
      this.iconGlyph = iconGlyph;
   }

   public String displayName() {
      return this.displayName;
   }

   public String iconGlyph() {
      return this.iconGlyph;
   }
}
