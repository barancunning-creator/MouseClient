package com.mouseclient.render.font;

import com.mouseclient.internal.PixelAlignment;
import com.mouseclient.render.avatar.FontAtlasPage;
import com.mouseclient.render.avatar.LayoutGlyph;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphMetrics;
import java.awt.font.GlyphVector;
import java.awt.font.LineMetrics;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.text.Bidi;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public final class RasterFont implements AutoCloseable {
   private final FontFamily family;
   private final float size;
   private final int pixelScale;
   private final int padding;
   private final FontRenderContext fontRenderContext;
   private final Map<RasterFont.GlyphKey, RasterGlyph> glyphCache = new HashMap<>();
   private final Map<Font, Font> scaledFontCache = new HashMap<>();
   private final Map<String, TextLayout> layoutCache;
   private final Map<String, Float> widthCache;
   private final List<FontAtlasPage> atlasPages;
   private final float ascent;
   private final float lineHeight;
   private final float spaceAdvance;

   public void uploadDirtyPages() {
      for (FontAtlasPage page : this.atlasPages) {
         page.uploadDirtyRegion();
      }
   }

   private FontAtlasPage.AtlasSlot allocateAtlasSlot(int width, int height) {
      for (FontAtlasPage page : this.atlasPages) {
         FontAtlasPage.AtlasSlot slot = page.allocate(width, height);
         if (slot != null) {
            return slot;
         }
      }

      FontAtlasPage newPage = new FontAtlasPage(512);
      this.atlasPages.add(newPage);
      return newPage.allocate(width, height);
   }

   private TextLayout buildLayout(String text) {
      LinkedHashMap<FontAtlasPage, List<LayoutGlyph>> glyphsByPage = new LinkedHashMap<>();
      float penX = 0.0F;
      float lineOffsetY = 0.0F;
      float maximumLineWidth = 0.0F;
      float baseline = this.ascent;
      int textIndex = 0;

      while (textIndex < text.length()) {
         int codePoint = text.codePointAt(textIndex);
         if (codePoint == 10) {
            textIndex += Character.charCount(codePoint);
            maximumLineWidth = Math.max(maximumLineWidth, penX);
            penX = 0.0F;
            lineOffsetY += this.lineHeight;
         } else if (codePoint == 9) {
            textIndex += Character.charCount(codePoint);
            penX += this.getGlyphForCodePoint(9).advance();
         } else if (Character.isISOControl(codePoint)) {
            textIndex += Character.charCount(codePoint);
         } else {
            Font runFont = this.getFontForCodePoint(codePoint);
            int runStart = textIndex;
            textIndex += Character.charCount(codePoint);

            while (textIndex < text.length()) {
               int nextCodePoint = text.codePointAt(textIndex);
               if (nextCodePoint == 10 || nextCodePoint == 9 || Character.isISOControl(nextCodePoint)) {
                  break;
               }

               Font nextFont = this.getFontForCodePoint(nextCodePoint);
               if (!FontFamily.fontKey(runFont).equals(FontFamily.fontKey(nextFont))) {
                  break;
               }

               textIndex += Character.charCount(nextCodePoint);
            }

            RasterFont.ShapedRun shapedRun = this.shapeRun(runFont, text.substring(runStart, textIndex));

            for (RasterFont.PositionedGlyph positionedGlyph : shapedRun.glyphs()) {
               RasterGlyph glyph = positionedGlyph.glyph();
               if (glyph.drawable()) {
                  float left = penX + positionedGlyph.x() + glyph.xOffset();
                  float top = baseline + lineOffsetY + positionedGlyph.y() + glyph.yOffset();
                  float right = left + glyph.width();
                  float bottom = top + glyph.height();
                  glyphsByPage.computeIfAbsent(glyph.page(), page -> new ArrayList<>())
                     .add(
                        new LayoutGlyph(
                           glyph.page(), left, top, right, bottom, glyph.uMin(), glyph.vMin(), glyph.uMax(), glyph.vMax()
                        )
                     );
               }
            }

            penX += shapedRun.advance();
         }
      }

      maximumLineWidth = Math.max(maximumLineWidth, penX);
      ArrayList pageLayouts = new ArrayList(glyphsByPage.size());

      for (Entry pageEntry : glyphsByPage.entrySet()) {
         pageLayouts.add(new TextLayout.PageGlyphs((FontAtlasPage)pageEntry.getKey(), (List<LayoutGlyph>)pageEntry.getValue()));
      }

      return new TextLayout(pageLayouts, maximumLineWidth, lineOffsetY + this.lineHeight);
   }

   private float measureRun(Font font, String text) {
      if (text != null && !text.isEmpty()) {
         Font scaledFont = this.getScaledFont(font);
         char[] characters = text.toCharArray();
         int layoutFlags = Bidi.requiresBidi(characters, 0, characters.length) && new Bidi(text, -2).isRightToLeft() ? 1 : 0;
         GlyphVector glyphVector = scaledFont.layoutGlyphVector(this.fontRenderContext, characters, 0, characters.length, layoutFlags);
         int glyphCount = glyphVector.getNumGlyphs();
         if (glyphCount == 0) {
            return 0.0F;
         }

         float advance = Math.abs((float)glyphVector.getGlyphPosition(glyphCount).getX() / this.pixelScale);
         return advance > 0.0F ? advance : (float)scaledFont.getStringBounds(text, this.fontRenderContext).getWidth() / this.pixelScale;
      } else {
         return 0.0F;
      }
   }

   public RasterFont.ShapedRun shapeRun(Font font, String text) {
      if (text != null && !text.isEmpty()) {
         Font scaledFont = this.getScaledFont(font);
         char[] characters = text.toCharArray();
         int layoutFlags = Bidi.requiresBidi(characters, 0, characters.length) && new Bidi(text, -2).isRightToLeft() ? 1 : 0;
         GlyphVector glyphVector = scaledFont.layoutGlyphVector(this.fontRenderContext, characters, 0, characters.length, layoutFlags);
         int glyphCount = glyphVector.getNumGlyphs();
         if (glyphCount == 0) {
            return RasterFont.ShapedRun.EMPTY;
         }

         ArrayList<RasterFont.PositionedGlyph> positionedGlyphs = new ArrayList(glyphCount);
         float minimumX = 0.0F;
         float maximumX = 0.0F;

         for (int glyphIndex = 0; glyphIndex < glyphCount; glyphIndex++) {
            Point2D glyphPosition = glyphVector.getGlyphPosition(glyphIndex);
            Point2D nextGlyphPosition = glyphVector.getGlyphPosition(glyphIndex + 1);
            float glyphX = (float)glyphPosition.getX() / this.pixelScale;
            float glyphY = (float)glyphPosition.getY() / this.pixelScale;
            float glyphAdvance = Math.abs((float)(nextGlyphPosition.getX() - glyphPosition.getX()) / this.pixelScale);
            RasterGlyph glyph = this.getGlyph(font, glyphVector.getGlyphCode(glyphIndex), glyphAdvance);
            if (glyph.drawable()) {
               minimumX = Math.min(minimumX, glyphX + glyph.xOffset());
               maximumX = Math.max(maximumX, glyphX + glyph.xOffset() + glyph.width());
            } else {
               maximumX = Math.max(maximumX, glyphX + glyph.advance());
            }

            positionedGlyphs.add(new RasterFont.PositionedGlyph(glyph, glyphX, glyphY));
         }

         float runAdvance = Math.max(Math.abs((float)glyphVector.getGlyphPosition(glyphCount).getX() / this.pixelScale), maximumX - minimumX);
         float bearingCorrection = minimumX < 0.0F ? -minimumX : 0.0F;
         if (bearingCorrection > 0.0F) {
            ArrayList shiftedGlyphs = new ArrayList(positionedGlyphs.size());

            for (RasterFont.PositionedGlyph positionedGlyph : positionedGlyphs) {
               shiftedGlyphs.add(new RasterFont.PositionedGlyph(positionedGlyph.glyph(), positionedGlyph.x() + bearingCorrection, positionedGlyph.y()));
            }

            positionedGlyphs = shiftedGlyphs;
            runAdvance += bearingCorrection;
         }

         return new RasterFont.ShapedRun(positionedGlyphs, runAdvance);
      } else {
         return RasterFont.ShapedRun.EMPTY;
      }
   }

   public Font getFontForCodePoint(int codePoint) {
      return this.family.getFontForCodePoint(codePoint);
   }

   public float getTextWidth(String text) {
      return text != null && !text.isEmpty() ? this.widthCache.computeIfAbsent(text, this::measureText) : 0.0F;
   }

   public float getAscent() {
      return this.ascent;
   }

   private float measureText(String text) {
      float lineWidth = 0.0F;
      float maximumLineWidth = 0.0F;
      int textIndex = 0;

      while (textIndex < text.length()) {
         int codePoint = text.codePointAt(textIndex);
         if (codePoint == 10) {
            textIndex += Character.charCount(codePoint);
            maximumLineWidth = Math.max(maximumLineWidth, lineWidth);
            lineWidth = 0.0F;
         } else if (codePoint == 9) {
            textIndex += Character.charCount(codePoint);
            lineWidth += this.spaceAdvance * (4.0F);
         } else if (Character.isISOControl(codePoint)) {
            textIndex += Character.charCount(codePoint);
         } else {
            Font runFont = this.getFontForCodePoint(codePoint);
            int runStart = textIndex;
            textIndex += Character.charCount(codePoint);

            while (true) {
               if (textIndex < text.length()) {
                  int nextCodePoint = text.codePointAt(textIndex);
                  if (nextCodePoint != 10 && nextCodePoint != 9 && !Character.isISOControl(nextCodePoint)) {
                     Font nextFont = this.getFontForCodePoint(nextCodePoint);
                     if (FontFamily.fontKey(runFont).equals(FontFamily.fontKey(nextFont))) {
                        textIndex += Character.charCount(nextCodePoint);
                        continue;
                     }
                  }
               }

               lineWidth += this.measureRun(runFont, text.substring(runStart, textIndex));
               break;
            }
         }
      }

      return Math.max(maximumLineWidth, lineWidth);
   }

   private RasterGlyph getGlyph(Font font, int glyphCode, float fallbackAdvance) {
      RasterFont.GlyphKey cacheKey = new RasterFont.GlyphKey(FontFamily.fontKey(font), glyphCode);
      return this.glyphCache.computeIfAbsent(cacheKey, unusedCacheKey -> this.rasterizeGlyph(font, glyphCode, fallbackAdvance));
   }

   @Override
   public void close() {
      this.glyphCache.clear();
      this.scaledFontCache.clear();
      this.layoutCache.clear();
      this.widthCache.clear();

      for (FontAtlasPage page : this.atlasPages) {
         page.close();
      }

      this.atlasPages.clear();
   }

   public float getLineHeight() {
      return this.lineHeight;
   }

   private Font getScaledFont(Font font) {
      return this.scaledFontCache.computeIfAbsent(font, sourceFont -> sourceFont.deriveFont(0, this.size * this.pixelScale));
   }

   private RasterGlyph rasterizeGlyph(Font font, int glyphCode, float fallbackAdvance) {
      Font scaledFont = this.getScaledFont(font);
      GlyphVector glyphVector = scaledFont.createGlyphVector(this.fontRenderContext, new int[]{glyphCode});
      GlyphMetrics metrics = glyphVector.getGlyphMetrics(0);
      float advance = Math.max(fallbackAdvance > 0.0F ? fallbackAdvance : metrics.getAdvance() / this.pixelScale, 0.0F);
      Rectangle pixelBounds = glyphVector.getPixelBounds(this.fontRenderContext, 0.0F, 0.0F);
      if (pixelBounds.width > 0 && pixelBounds.height > 0) {
         int imageWidth = pixelBounds.width + this.padding * 2;
         int imageHeight = pixelBounds.height + this.padding * 2;
         if (imageWidth > 0 && imageHeight > 0 && imageWidth <= 512 && imageHeight <= 512) {
            BufferedImage image = new BufferedImage(imageWidth, imageHeight, 2);
            Graphics2D graphics = image.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
            graphics.setColor(Color.WHITE);
            graphics.drawGlyphVector(glyphVector, this.padding - pixelBounds.x, this.padding - pixelBounds.y);
            graphics.dispose();
            FontAtlasPage.AtlasSlot atlasSlot = this.allocateAtlasSlot(imageWidth, imageHeight);
            if (atlasSlot == null) {
               return RasterGlyph.empty(advance);
            }

            atlasSlot.page().writeImage(image, atlasSlot.x(), atlasSlot.y());
            float xOffset = (float)(pixelBounds.x - this.padding) / this.pixelScale;
            float yOffset = (float)(pixelBounds.y - this.padding) / this.pixelScale;
            float width = (float)imageWidth / this.pixelScale;
            float height = (float)imageHeight / this.pixelScale;
            return new RasterGlyph(atlasSlot.page(), atlasSlot.uMin(), atlasSlot.vMin(), atlasSlot.uMax(), atlasSlot.vMax(), xOffset, yOffset, width, height, advance);
         } else {
            return RasterGlyph.empty(advance);
         }
      } else {
         return RasterGlyph.empty(advance);
      }
   }

   public TextLayout layout(String text) {
      return text != null && !text.isEmpty() ? this.layoutCache.computeIfAbsent(text, this::buildLayout) : TextLayout.EMPTY;
   }

   public RasterGlyph getGlyphForCodePoint(int codePoint) {
      if (codePoint == 9) {
         RasterGlyph glyph = this.getGlyphForCodePoint(32);
         return glyph.withAdvance(glyph.advance() * (4.0F));
      } else {
         Font resolvedFont = this.family.getFontForCodePoint(codePoint);
         Font scaledFont = this.getScaledFont(resolvedFont);
         GlyphVector glyphVector = scaledFont.createGlyphVector(this.fontRenderContext, new String(Character.toChars(codePoint)));
         int glyphCode = glyphVector.getGlyphCode(0);
         float advance = glyphVector.getGlyphMetrics(0).getAdvance() / this.pixelScale;
         return this.getGlyph(resolvedFont, glyphCode, advance);
      }
   }

   public RasterFont(FontFamily family, float size) {
      this.layoutCache = new LinkedHashMap<String, TextLayout>(256, (0.75F), true) {
         @Override
         protected boolean removeEldestEntry(Entry<String, TextLayout> eldestEntry) {
            return this.size() > 1024;
         }
      };
      this.widthCache = new LinkedHashMap<String, Float>(256, (0.75F), true) {
         @Override
         protected boolean removeEldestEntry(Entry<String, Float> eldestEntry) {
            return this.size() > 1024;
         }
      };
      this.atlasPages = new ArrayList<>();
      this.family = family;
      this.size = size;
      this.pixelScale = PixelAlignment.getPixelScale(size);
      this.padding = PixelAlignment.padTextureSize(this.pixelScale);
      this.fontRenderContext = new FontRenderContext(new AffineTransform(), RenderingHints.VALUE_TEXT_ANTIALIAS_ON, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
      Font scaledFont = family.getPrimaryFont().deriveFont(0, size * this.pixelScale);
      LineMetrics lineMetrics = scaledFont.getLineMetrics(("Mg"), this.fontRenderContext);
      this.ascent = (float)Math.round(lineMetrics.getAscent()) / this.pixelScale;
      this.lineHeight = (float)Math.round(Math.max(size * this.pixelScale, lineMetrics.getHeight())) / this.pixelScale;
      this.spaceAdvance = Math.max(scaledFont.createGlyphVector(this.fontRenderContext, (" ")).getGlyphMetrics(0).getAdvance() / this.pixelScale, size * (0.25F));
   }

   private record GlyphKey(String fontKey, int glyphCode) {
   }

   public record PositionedGlyph(RasterGlyph glyph, float x, float y) {
   }

   public record ShapedRun(List<RasterFont.PositionedGlyph> glyphs, float advance) {
      public static final RasterFont.ShapedRun EMPTY = new RasterFont.ShapedRun(List.of(), 0.0F);
   }
}
