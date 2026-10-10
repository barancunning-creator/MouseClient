package com.mouseclient.render.font;

import com.mouseclient.network.ClientInitializer;
import java.awt.Font;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Supplier;

public final class RasterFontRegistry implements AutoCloseable {

   private final Map<String, FontFamily> families = new LinkedHashMap<>();
   private final List<Supplier<Font>> fallbackSuppliers = new ArrayList<>();
   private final Map<String, Font> loadedFonts = new HashMap<>();
   private final ArrayList<RasterFont> evictedFonts = new ArrayList<>();
   private final Map<RasterFontRegistry.FontSizeKey, RasterFont> rasterFonts;
   private final Font fallbackFont;
   private Supplier<Font> defaultFontSupplier;

   public void closeEvictedFonts() {
      for (RasterFont font : this.evictedFonts) {
         font.close();
      }

      this.evictedFonts.clear();
   }

   @Override
   public void close() {
      for (RasterFont font : this.rasterFonts.values()) {
         font.close();
      }

      this.rasterFonts.clear();
      this.families.clear();
      this.fallbackSuppliers.clear();
      this.loadedFonts.clear();
   }

   private Font getFallbackFont(String assetName) {
      return this.getFont(assetName, () -> this.defaultFontSupplier != null ? this.defaultFontSupplier.get() : this.fallbackFont);
   }

   private void registerDefaults() {
      Supplier segoeSupplier = this.fontSupplier(("SegoeProDisplay-Semibold.ttf"), () -> this.fallbackFont);
      Supplier semiboldSupplier = this.fontSupplier(("semi_bold.otf"), segoeSupplier);
      Supplier openSansBoldSupplier = this.fontSupplier(("open-sans.bold.ttf"), segoeSupplier);
      Supplier openSansSemiboldSupplier = this.fontSupplier(("open-sans.semibold.ttf"), openSansBoldSupplier);
      Supplier boldSupplier = this.fontSupplier(("bold.otf"), openSansBoldSupplier);
      Supplier mediumSupplier = this.fontSupplier(("medium.otf"), semiboldSupplier);
      Supplier zenterSupplier = this.fontSupplier(("ZenterSPDemo-Black.otf"), segoeSupplier);
      Supplier iconsSupplier = this.fontSupplier(("icons.ttf"), segoeSupplier);
      Supplier supplier = this.fontSupplier(("NotoSans-Regular.ttf"), segoeSupplier);
      Supplier symbolsSupplier = this.fontSupplier(("NotoSansSymbols-Regular.ttf"), segoeSupplier);
      Supplier symbols2Supplier = this.fontSupplier(("NotoSansSymbols2-Regular.ttf"), segoeSupplier);
      Supplier arabicSupplier = this.fontSupplier(("NotoNaskhArabic-Regular.ttf"), segoeSupplier);
      this.defaultFontSupplier = supplier;
      this.setFallbackFonts(iconsSupplier, symbolsSupplier, symbols2Supplier, arabicSupplier, segoeSupplier, supplier);
      this.registerFamily(("default"), segoeSupplier);
      this.registerFamily(("segoe"), segoeSupplier);
      this.registerFamily(("segoe_semibold"), segoeSupplier);
      this.registerFamily(("semi_bold"), semiboldSupplier);
      this.registerFamily(("semibold"), semiboldSupplier);
      this.registerFamily(("open_sans_bold"), openSansBoldSupplier);
      this.registerFamily(("opensans_bold"), openSansBoldSupplier);
      this.registerFamily(("open_sans_semibold"), openSansSemiboldSupplier);
      this.registerFamily(("opensans_semibold"), openSansSemiboldSupplier);
      this.registerFamily(("open_sans"), openSansSemiboldSupplier);
      this.registerFamily(("opensans"), openSansSemiboldSupplier);
      this.registerFamily(("bold"), boldSupplier);
      this.registerFamily(("bold_otf"), boldSupplier);
      this.registerFamily(("medium"), mediumSupplier);
      this.registerFamily(("medium_otf"), mediumSupplier);
      this.registerFamily(("zenter"), zenterSupplier);
      this.registerFamily(("zenter_black"), zenterSupplier);
      this.registerFamily(("noto"), supplier);
      this.registerFamily(("noto_sans"), supplier);
      this.registerFamily(("noto_symbols"), symbolsSupplier);
      this.registerFamily(("noto_symbols_1"), symbolsSupplier);
      this.registerFamily(("noto_symbols_2"), symbols2Supplier);
      this.registerFamily(("noto_symbols2"), symbols2Supplier);
      this.registerFamily(("noto_arabic"), arabicSupplier);
      this.registerFamily(("arabic"), arabicSupplier);
      this.registerFamily(("noto_cjk_sc"), supplier);
      this.registerFamily(("noto_chinese"), supplier);
      this.registerFamily(("chinese"), supplier);
      this.registerFamily(("noto_cjk_kr"), supplier);
      this.registerFamily(("noto_korean"), supplier);
      this.registerFamily(("korean"), supplier);
      this.registerFamily(("noto_cjk_jp"), supplier);
      this.registerFamily(("noto_japanese"), supplier);
      this.registerFamily(("japanese"), supplier);
      this.registerFamily(("icons"), iconsSupplier);
      this.registerFamily(("icon"), iconsSupplier);
   }

   public RasterFontRegistry() {
      this.rasterFonts = new LinkedHashMap<RasterFontRegistry.FontSizeKey, RasterFont>(64, (0.75F), true) {
         @Override
         protected boolean removeEldestEntry(Entry<RasterFontRegistry.FontSizeKey, RasterFont> eldestEntry) {
            if (this.size() <= 160) {
               return false;
            }

            RasterFontRegistry.this.evictedFonts.add((RasterFont)eldestEntry.getValue());
            return true;
         }
      };
      this.fallbackFont = new Font(("SansSerif"), 0, 1);
      this.registerDefaults();
   }

   @SafeVarargs
   private void setFallbackFonts(Supplier<Font>... fallbackFonts) {
      this.fallbackSuppliers.clear();

      for (Supplier supplier : fallbackFonts) {
         this.addFallbackFont(supplier);
      }

      this.addFallbackFont(() -> this.fallbackFont);
   }

   private static boolean isHangul(int codePoint) {
      return codePoint >= 4352 && codePoint <= 4607 || codePoint >= 12592 && codePoint <= 12687 || codePoint >= (43360) && codePoint <= (43391) || codePoint >= (44032) && codePoint <= (55215);
   }

   private static boolean isCjk(int codePoint) {
      return codePoint >= 13312 && codePoint <= 19903
         || codePoint >= 19968 && codePoint <= (40959)
         || codePoint >= (63744) && codePoint <= (64255)
         || codePoint >= (131072) && codePoint <= (195103);
   }

   public FontFamily getFamily(String familyName) {
      FontFamily family = this.families.get(normalizeName(familyName));
      return family != null ? family : this.families.get(("default"));
   }

   private void registerFamily(String familyName, Supplier<Font> supplier) {
      this.families.put(normalizeName(familyName), new FontFamily(normalizeName(familyName), supplier, this.fallbackSuppliers, this::getScriptFallbackFonts));
   }

   private Font loadFont(String assetName, Font fallback) {
      try (InputStream input = RasterFontRegistry.class.getResourceAsStream("/assets/client/fonts/" + assetName)) {
         if (input == null) {
            ClientInitializer.logger().warn(("[FontRenderer] Font asset is missing: {}"), assetName);
            return fallback;
         } else {
            return Font.createFont(0, input).deriveFont(0, 1.0F);
         }
      } catch (Exception error) {
         ClientInitializer.logger().warn(("[FontRenderer] Failed to load font asset: {}"), assetName, error);
         return fallback;
      }
   }

   private List<Font> getScriptFallbackFonts(int codePoint) {
      if (isHangul(codePoint)) {
         return Collections.singletonList(this.getFallbackFont(("NotoSansCJKkr-Regular.otf")));
      } else if (isKana(codePoint)) {
         return Collections.singletonList(this.getFallbackFont(("NotoSansCJKjp-Regular.otf")));
      } else {
         return isCjk(codePoint) ? Collections.singletonList(this.getFallbackFont(("NotoSansCJKsc-Regular.otf"))) : List.of();
      }
   }

   private static boolean isKana(int codePoint) {
      return codePoint >= 12352 && codePoint <= 12543 || codePoint >= 12784 && codePoint <= 12799;
   }

   private Supplier<Font> fontSupplier(String assetName, Supplier<Font> fallbackSupplier) {
      return () -> this.getFont(assetName, fallbackSupplier);
   }

   private void addFallbackFont(Supplier<Font> supplier) {
      if (supplier != null) {
         this.fallbackSuppliers.add(supplier);
      }
   }

   private static String normalizeName(String name) {
      return name != null && !name.isBlank() ? name.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_') : ("default");
   }

   private static float normalizeSize(float size) {
      return Math.max(1.0F, Math.min((512.0F), Math.round(size * (4.0F)) / (4.0F)));
   }

   private synchronized Font getFont(String assetName, Supplier<Font> fallbackSupplier) {
      Font cachedFont = this.loadedFonts.get(assetName);
      if (cachedFont != null) {
         return cachedFont;
      }

      Font loadedFont = this.loadFont(assetName, (Font)fallbackSupplier.get());
      this.loadedFonts.put(assetName, loadedFont);
      return loadedFont;
   }

   public RasterFont getRasterFont(String familyName, float size) {
      String normalizedName = normalizeName(familyName);
      FontFamily family = this.getFamily(normalizedName);
      float normalizedSize = normalizeSize(size);
      RasterFontRegistry.FontSizeKey cacheKey = new RasterFontRegistry.FontSizeKey(family.getName(), normalizedSize);
      return this.rasterFonts.computeIfAbsent(cacheKey, unusedCacheKey -> new RasterFont(family, normalizedSize));
   }

   private record FontSizeKey(String name, float size) {
   }
}
