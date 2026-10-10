package com.mouseclient.render.avatar;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class MsdfFontRegistry {
   public static final String DEFAULT_FONT = "montserrat";
   private static final Map<String, String> fontPaths = new HashMap<>();
   private static final Map<String, MsdfFont> loadedFonts = new HashMap<>();
   private static final Map<String, Boolean> failedFonts = new HashMap<>();
   private static final String[] WARMUP_FONTS = new String[]{
      ("semi"), ("semi_bold"), ("bold"), ("mini"), ("thin"), ("icons"), ("nametagicon")
   };
   private static int warmupIndex;

   public static void clear() {
      loadedFonts.clear();
      failedFonts.clear();
      warmupIndex = 0;
   }

   private MsdfFontRegistry() {
   }

   static {
      register(("montserrat"), ("fonts/monsterat/montserrat-regular"));
      register(("montserrat-regular"), ("fonts/monsterat/montserrat-regular"));
      register(("montserrat-medium"), ("fonts/monsterat/montserrat-medium"));
      register(("montserrat-semibold"), ("fonts/monsterat/montserrat-semibold"));
      register(("montserrat-bold"), ("fonts/monsterat/montserrat-bold"));
      register(("montserrat-extrabold"), ("fonts/monsterat/montserrat-extrabold"));
      register(("montserrat-black"), ("fonts/monsterat/montserrat-black"));
      register(("montserrat-light"), ("fonts/monsterat/montserrat-light"));
      register(("montserrat-extralight"), ("fonts/monsterat/montserrat-extralight"));
      register(("montserrat-thin"), ("fonts/monsterat/montserrat-thin"));
      register(("icons"), ("msdf/icons"));
      register(("nametagicon"), ("msdf/nametagicon"));
      register(("test"), ("msdf/test"));
      register(("thin"), ("msdf/thin"));
      register(("mini"), ("msdf/mini"));
      register(("semi"), ("msdf/semi"));
      register(("semi_bold"), ("msdf/semi_bold"));
      register(("semi-bold"), ("msdf/semi_bold"));
      register(("bold"), ("msdf/bold"));
      register(("kimiko"), ("fonts/kimiko/kimiko"));
      register(("event-icons"), ("fonts/event-icons/event-icons"));
      register(("heart"), ("fonts/heart/heart"));
      register(("sf"), ("fonts/sf-pro/sf-pro-regular"));
      register(("sf-regular"), ("fonts/sf-pro/sf-pro-regular"));
      register(("sf-medium"), ("fonts/sf-pro/sf-pro-medium"));
      register(("sf-bold"), ("fonts/sf-pro/sf-pro-bold"));
      register(("small-pixel"), ("fonts/smallpixel/small-pixel"));
   }

   public static boolean warmNextFont() {
      if (warmupIndex >= WARMUP_FONTS.length) {
         return false;
      }

      getFont(WARMUP_FONTS[warmupIndex++]);
      return true;
   }

   public static MsdfFont getFont(String fontName) {
      String normalizedName = fontName != null && !fontName.isBlank() ? fontName.toLowerCase(Locale.ROOT) : ("montserrat");
      MsdfFont font = loadedFonts.get(normalizedName);
      if (font != null) {
         return font;
      } else if (Boolean.TRUE.equals(failedFonts.get(normalizedName))) {
         return normalizedName.equals(("montserrat")) ? null : getFont(("montserrat"));
      } else {
         String resourcePath = fontPaths.get(normalizedName);
         if (resourcePath == null) {
            failedFonts.put(normalizedName, true);
            return normalizedName.equals(("montserrat")) ? null : getFont(("montserrat"));
         } else {
            MsdfFont loadedFont = MsdfFontLoader.load(resourcePath);
            if (loadedFont == null) {
               failedFonts.put(normalizedName, true);
               return normalizedName.equals(("montserrat")) ? null : getFont(("montserrat"));
            } else {
               loadedFonts.put(normalizedName, loadedFont);
               return loadedFont;
            }
         }
      }
   }

   public static String[] getWarmupFontNames() {
      return WARMUP_FONTS;
   }

   private static void register(String fontName, String resourcePath) {
      fontPaths.put(fontName.toLowerCase(Locale.ROOT), resourcePath);
   }
}
