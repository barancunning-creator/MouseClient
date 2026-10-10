package com.mouseclient.render.avatar;

import java.util.Arrays;
import java.util.Map;
import net.minecraft.util.Identifier;

public final class MsdfFont {
   private static final MsdfGlyph EMPTY_GLYPH = com.mouseclient.render.avatar.MsdfGlyph.nonDrawable(0.0F);
   private final Identifier atlasTexture;
   private final int atlasWidth;
   private final int atlasHeight;
   private final float pxRange;
   private final float lineHeight;
   private final float ascender;
   private final float descender;
   private final Map<Integer, MsdfGlyph> glyphs;
   private final Map<Long, Float> kerning;
   private final MsdfGlyph missingGlyph;

   public float[] getInkBounds(String text, float size) {
      if (text != null && !text.isEmpty()) {
         float baselineY = this.ascender * size;
         float minX = (3.4028235E38F);
         float maxX = (-3.4028235E38F);
         float minY = (3.4028235E38F);
         float maxY = (-3.4028235E38F);
         boolean hasInk = false;
         float penX = 0.0F;
         int previousCodePoint = -1;
         int textIndex = 0;

         while (textIndex < text.length()) {
            int codePoint = text.codePointAt(textIndex);
            textIndex += Character.charCount(codePoint);
            if (codePoint == 10) {
               previousCodePoint = -1;
            } else {
               MsdfGlyph glyph = this.getGlyph(codePoint);
               if (previousCodePoint != -1) {
                  penX += this.getKerning(previousCodePoint, codePoint) * size;
               }

               if (!this.hasGlyph(codePoint) && SmallTextGlyphCache.supportsFallbackCodePoint(codePoint)) {
                  minX = Math.min(minX, penX);
                  maxX = Math.max(maxX, penX + SmallTextGlyphCache.fallbackAdvance(codePoint) * size);
                  minY = Math.min(minY, baselineY - (0.74F) * size);
                  maxY = Math.max(maxY, baselineY);
                  hasInk = true;
               } else if (glyph.drawable()) {
                  minX = Math.min(minX, penX + glyph.planeLeft() * size);
                  maxX = Math.max(maxX, penX + glyph.planeRight() * size);
                  minY = Math.min(minY, baselineY - glyph.planeTop() * size);
                  maxY = Math.max(maxY, baselineY - glyph.planeBottom() * size);
                  hasInk = true;
               }

               penX += (!this.hasGlyph(codePoint) && SmallTextGlyphCache.supportsFallbackCodePoint(codePoint) ? SmallTextGlyphCache.fallbackAdvance(codePoint) : glyph.advance()) * size;
               previousCodePoint = codePoint;
            }
         }

         return hasInk ? new float[]{minX, minY, maxX, maxY} : new float[]{0.0F, 0.0F, 0.0F, 0.0F};
      } else {
         return new float[]{0.0F, 0.0F, 0.0F, 0.0F};
      }
   }

   public int getAtlasWidth() {
      return this.atlasWidth;
   }

   public int getAtlasHeight() {
      return this.atlasHeight;
   }

   public float getKerning(int leftCodePoint, int rightCodePoint) {
      if (this.kerning.isEmpty()) {
         return 0.0F;
      }

      Float advance = this.kerning.get((long)leftCodePoint << 32 | rightCodePoint & (4294967295L));
      return advance == null ? 0.0F : advance;
   }

   public float getLineHeight(float size) {
      return this.lineHeight * size;
   }

   public float getDescender(float size) {
      return this.descender * size;
   }

   public float getAscender(float size) {
      return this.ascender * size;
   }

   public boolean hasGlyph(int codePoint) {
      return this.glyphs.containsKey(codePoint);
   }

   public float[] buildGlyphQuads(String text, float size) {
      if (text != null && !text.isEmpty()) {
         float[] quads = new float[text.length() * 8];
         float penX = 0.0F;
         float baselineY = this.getAscender(size);
         int quadOffset = 0;
         int previousCodePoint = -1;
         int textIndex = 0;

         while (textIndex < text.length()) {
            int codePoint = text.codePointAt(textIndex);
            textIndex += Character.charCount(codePoint);
            MsdfGlyph glyph = this.getGlyph(codePoint);
            if (previousCodePoint != -1) {
               penX += this.getKerning(previousCodePoint, codePoint) * size;
            }

            if (glyph.drawable()) {
               quads[quadOffset] = penX + glyph.planeLeft() * size;
               quads[quadOffset + 1] = baselineY - glyph.planeTop() * size;
               quads[quadOffset + 2] = penX + glyph.planeRight() * size;
               quads[quadOffset + 3] = baselineY - glyph.planeBottom() * size;
               quads[quadOffset + 4] = glyph.uMin();
               quads[quadOffset + 5] = glyph.vMin();
               quads[quadOffset + 6] = glyph.uMax();
               quads[quadOffset + 7] = glyph.vMax();
               quadOffset += 8;
            }

            penX += glyph.advance() * size;
            previousCodePoint = codePoint;
         }

         return Arrays.copyOf(quads, quadOffset);
      } else {
         return new float[0];
      }
   }

   public float getTextWidth(String text, float size) {
      if (text != null && !text.isEmpty()) {
         float maximumLineWidth = 0.0F;
         float lineWidth = 0.0F;
         int previousCodePoint = -1;
         int textIndex = 0;

         while (textIndex < text.length()) {
            int codePoint = text.codePointAt(textIndex);
            textIndex += Character.charCount(codePoint);
            if (codePoint == 10) {
               maximumLineWidth = Math.max(maximumLineWidth, lineWidth);
               lineWidth = 0.0F;
               previousCodePoint = -1;
            } else {
               MsdfGlyph glyph = this.getGlyph(codePoint);
               if (previousCodePoint != -1) {
                  lineWidth += this.getKerning(previousCodePoint, codePoint) * size;
               }

               lineWidth += (!this.hasGlyph(codePoint) && SmallTextGlyphCache.supportsFallbackCodePoint(codePoint) ? SmallTextGlyphCache.fallbackAdvance(codePoint) : glyph.advance()) * size;
               previousCodePoint = codePoint;
            }
         }

         return Math.max(maximumLineWidth, lineWidth);
      } else {
         return 0.0F;
      }
   }

   public MsdfGlyph getGlyph(int codePoint) {
      MsdfGlyph glyph = this.glyphs.get(codePoint);
      if (glyph != null) {
         return glyph;
      } else {
         return codePoint > 32 && !Character.isWhitespace(codePoint) && Character.isDefined(codePoint) ? this.missingGlyph : EMPTY_GLYPH;
      }
   }

   public MsdfFont(Identifier identifier, int atlasWidth, int atlasHeight, float pxRange, float lineHeight, float ascender, float descender, Map<Integer, MsdfGlyph> glyphs, Map<Long, Float> kerning) {
      this.atlasTexture = identifier;
      this.atlasWidth = atlasWidth;
      this.atlasHeight = atlasHeight;
      this.pxRange = pxRange;
      this.lineHeight = lineHeight;
      this.ascender = ascender;
      this.descender = descender;
      this.glyphs = glyphs;
      this.kerning = kerning;
      MsdfGlyph glyph = (MsdfGlyph)glyphs.get(63);
      this.missingGlyph = glyph != null ? glyph : EMPTY_GLYPH;
   }

   public Identifier getAtlasTexture() {
      return this.atlasTexture;
   }

   public float getPxRange() {
      return this.pxRange;
   }
}
