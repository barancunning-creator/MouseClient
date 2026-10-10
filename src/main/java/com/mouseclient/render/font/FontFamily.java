package com.mouseclient.render.font;

import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.function.Supplier;

public final class FontFamily {
   private static final FontRenderContext fontRenderContext = new FontRenderContext(
      new AffineTransform(), RenderingHints.VALUE_TEXT_ANTIALIAS_ON, RenderingHints.VALUE_FRACTIONALMETRICS_OFF
   );
   private final String name;
   private final Supplier<Font> primaryFontSupplier;
   private final List<Supplier<Font>> fallbackSuppliers;
   private final List<Font> fallbackFonts;
   private final IntFunction<List<Font>> scriptFallbackResolver;
   private final Map<Integer, Font> resolvedFonts = new HashMap<>();
   private Font primaryFont;

   private static boolean canDisplay(Font font, int codePoint) {
      try {
         if (font != null && font.canDisplay(codePoint)) {
            GlyphVector glyphVector = font.createGlyphVector(fontRenderContext, new String(Character.toChars(codePoint)));
            return glyphVector.getNumGlyphs() > 0 && glyphVector.getGlyphCode(0) != font.getMissingGlyphCode();
         } else {
            return false;
         }
      } catch (IllegalArgumentException error) {
         return false;
      }
   }

   public FontFamily(String name, Supplier<Font> primaryFontSupplier, List<Supplier<Font>> fallbackSuppliers, IntFunction<List<Font>> scriptFallbackResolver) {
      this.name = name;
      this.primaryFontSupplier = primaryFontSupplier;
      this.fallbackSuppliers = new ArrayList<>(fallbackSuppliers);
      this.fallbackFonts = new ArrayList<>(fallbackSuppliers.size());

      for (int fallbackIndex = 0; fallbackIndex < fallbackSuppliers.size(); fallbackIndex++) {
         this.fallbackFonts.add(null);
      }

      this.scriptFallbackResolver = scriptFallbackResolver;
   }

   public static String fontKey(Font font) {
      return font == null ? ("missing") : font.getPSName() + "/" + font.getFontName(Locale.ROOT);
   }

   public Font getFontForCodePoint(int codePoint) {
      return this.resolvedFonts.computeIfAbsent(codePoint, this::resolveFont);
   }

   private Font resolveFont(int codePoint) {
      if (!Character.isValidCodePoint(codePoint)) {
         return this.getPrimaryFont();
      }

      Font primary = this.getPrimaryFont();
      if (canDisplay(primary, codePoint)) {
         return primary;
      }

      for (int fallbackIndex = 0; fallbackIndex < this.fallbackSuppliers.size(); fallbackIndex++) {
         Font fallback = this.fallbackFonts.get(fallbackIndex);
         if (fallback == null) {
            fallback = this.fallbackSuppliers.get(fallbackIndex).get();
            this.fallbackFonts.set(fallbackIndex, fallback);
         }

         if (canDisplay(fallback, codePoint)) {
            return fallback;
         }
      }

      if (this.scriptFallbackResolver != null) {
         for (Font scriptFont : this.scriptFallbackResolver.apply(codePoint)) {
            if (canDisplay(scriptFont, codePoint)) {
               return scriptFont;
            }
         }
      }

      return primary;
   }

   public Font getPrimaryFont() {
      if (this.primaryFont == null) {
         this.primaryFont = this.primaryFontSupplier.get();
      }

      return this.primaryFont;
   }

   public String getName() {
      return this.name;
   }
}
