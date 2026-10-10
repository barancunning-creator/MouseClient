package com.mouseclient.util.time;

public enum GuiFont {
   THIN("thin"),
   MINI("mini"),
   SEMI("semi"),
   SEMI_BOLD("semi_bold"),
   BOLD("bold"),
   INSTANCE("sf"),
   SF_MEDIUM("sf_medium"),
   ICONS("icons");

   private final String fontKey;

   GuiFont(String fontKey) {
      this.fontKey = fontKey;
   }

   public String getFontKey() {
      return this.fontKey;
   }
}
