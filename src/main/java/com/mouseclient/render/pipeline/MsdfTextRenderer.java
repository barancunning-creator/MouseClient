package com.mouseclient.render.pipeline;

import com.mouseclient.gui.ScissorStack;
import com.mouseclient.gui.MsdfTextBatchRenderState;
import com.mouseclient.gui.AtlasQuadBatchRenderState;
import com.mouseclient.internal.GuiScale;
import com.mouseclient.internal.RoundedClipStack;
import com.mouseclient.mixin.DrawContextAccessor;
import com.mouseclient.network.ClientInitializer;
import com.mouseclient.render.avatar.MsdfFontRegistry;
import com.mouseclient.render.avatar.MsdfFont;
import com.mouseclient.render.avatar.SmallTextGlyphCache;
import com.mouseclient.render.avatar.MsdfGlyph;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2f;

public final class MsdfTextRenderer implements AutoCloseable {
   private static volatile MsdfTextRenderer INSTANCE;
   private static final VertexFormat TEXT_VERTEX_FORMAT = VertexFormat.builder()
      .add(("Position"), VertexFormatElement.POSITION)
      .add(("UV0"), VertexFormatElement.UV0)
      .add(("Color"), VertexFormatElement.COLOR)
      .add(("LineWidth"), VertexFormatElement.LINE_WIDTH)
      .build();
   private static final VertexFormat SHIMMER_VERTEX_FORMAT = VertexFormat.builder()
      .add(("Position"), VertexFormatElement.POSITION)
      .add(("UV0"), VertexFormatElement.UV0)
      .add(("Color"), VertexFormatElement.COLOR)
      .add(("LineWidth"), VertexFormatElement.LINE_WIDTH)
      .build();
   public static final RenderPipeline TEXT_PIPELINE = RenderPipeline.builder()
      .withLocation(id(("pipeline/msdf_text")))
      .withVertexShader(id(("core/msdf_text")))
      .withFragmentShader(id(("core/msdf_text")))
      .withVertexFormat(TEXT_VERTEX_FORMAT, DrawMode.QUADS)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withCull(false)
      .withUniform(("DynamicTransforms"), UniformType.UNIFORM_BUFFER)
      .withUniform(("Projection"), UniformType.UNIFORM_BUFFER)
      .withSampler(("Sampler0"))
      .build();
   public static final RenderPipeline SHIMMER_PIPELINE = RenderPipeline.builder()
      .withLocation(id(("pipeline/msdf_shimmer")))
      .withVertexShader(id(("core/msdf_shimmer")))
      .withFragmentShader(id(("core/msdf_shimmer")))
      .withVertexFormat(SHIMMER_VERTEX_FORMAT, DrawMode.QUADS)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withCull(false)
      .withUniform(("DynamicTransforms"), UniformType.UNIFORM_BUFFER)
      .withUniform(("Projection"), UniformType.UNIFORM_BUFFER)
      .withSampler(("Sampler0"))
      .build();
   private final Map<MsdfTextRenderer.GlyphBatchKey, MsdfTextBatchRenderState> glyphBatches = new LinkedHashMap<>(32);
   private final Map<MsdfTextRenderer.IconBatchKey, AtlasQuadBatchRenderState> iconBatches = new LinkedHashMap<>(32);
   private final Map<Identifier, MsdfTextRenderer.TextureCacheEntry> textureCache = new HashMap<>();
   private final SmallTextGlyphCache smallTextCache = new SmallTextGlyphCache();
   private DrawContext drawContext;
   private boolean forceAtlasGlyphs;
   private int warmupFontIndex;

   public float getTextWidth(String fontName, String text, float size) {
      MsdfFont font = MsdfFontRegistry.getFont(fontName);
      return font == null ? 0.0F : font.getTextWidth(text, size);
   }

   public void endFrame() {
      this.smallTextCache.uploadDirtyPages();
      this.drawContext = null;
      this.glyphBatches.clear();
      this.iconBatches.clear();
   }

   public boolean warmupNextFont() {
      String[] fontNames = MsdfFontRegistry.getWarmupFontNames();
      if (this.warmupFontIndex >= fontNames.length) {
         return false;
      }

      MsdfFont font = MsdfFontRegistry.getFont(fontNames[this.warmupFontIndex++]);
      if (font != null) {
         this.smallTextCache.isFaceReady(font);
      }

      return true;
   }

   public static void closeInstance() {
      MsdfTextRenderer renderer = INSTANCE;
      if (renderer != null) {
         renderer.close();
         INSTANCE = null;
      }
   }

   public void beginFrame() {
      this.smallTextCache.beginFrame();
   }

   private static int unitToByte(float value) {
      return Math.round(clampUnit(value) * (255.0F));
   }

   private int applyHorizontalFade(int color, float x, MsdfTextSpec spec) {
      if (!spec.hasHorizontalFade()) {
         return color;
      }

      float fadeOpacity = 1.0F;
      if (spec.fadeLeftStrength() > (0.001F)) {
         float leftProgress = smoothstepUnit(clampUnit((x - spec.fadeLeftX()) / spec.fadeWidth()));
         fadeOpacity = Math.min(fadeOpacity, 1.0F - spec.fadeLeftStrength() * (1.0F - leftProgress));
      }

      if (spec.fadeRightStrength() > (0.001F)) {
         float rightProgress = smoothstepUnit(clampUnit((spec.fadeRightX() - x) / spec.fadeWidth()));
         fadeOpacity = Math.min(fadeOpacity, 1.0F - spec.fadeRightStrength() * (1.0F - rightProgress));
      }

      int originalAlpha = color >>> 24 & 0xFF;
      return Math.round(originalAlpha * fadeOpacity) << 24 | color & (16777215);
   }

   public void render(MsdfTextSpec spec) {
      this.submitText(this.drawContext, spec, false, 0.0F, 0.0F, 0.0F, 0.0F);
   }

   private static int multiplyAlpha(int color, float opacity) {
      if (opacity >= (0.999F)) {
         return color;
      }

      if (opacity <= (0.001F)) {
         return color & (16777215);
      }

      int scaledAlpha = Math.round((color >>> 24 & 0xFF) * opacity);
      return scaledAlpha << 24 | color & (16777215);
   }

   public float[] getInkBounds(String fontName, String text, float size) {
      MsdfFont font = MsdfFontRegistry.getFont(fontName);
      return font == null ? new float[]{0.0F, 0.0F} : font.getInkBounds(text, size);
   }

   private boolean drawCachedGlyphs(GuiRenderState state, int layerSerial, Matrix3x2f transform, ScreenRect bounds, MsdfFont font, MsdfTextSpec spec) {
      float fontSize = spec.size();
      float cacheSize = SmallTextGlyphCache.quantizeSize(fontSize);
      float baseScale = GuiScale.getBaseScale();
      int windowScale = GuiScale.getWindowScale();
      float translationPixelsX = transform.m20() * windowScale;
      float translationPixelsY = transform.m21() * windowScale;
      float startX = spec.x();
      float preparePenX = startX;
      int preparePreviousCodePoint = -1;
      String prepareText = spec.text();
      int prepareTextIndex = 0;

      while (prepareTextIndex < prepareText.length()) {
         int prepareCodePoint = prepareText.codePointAt(prepareTextIndex);
         prepareTextIndex += Character.charCount(prepareCodePoint);
         if (prepareCodePoint != 10) {
            boolean prepareFallback = !font.hasGlyph(prepareCodePoint) && SmallTextGlyphCache.supportsFallbackCodePoint(prepareCodePoint);
            MsdfGlyph glyph = font.getGlyph(prepareCodePoint);
            if (preparePreviousCodePoint != -1) {
               preparePenX += font.getKerning(preparePreviousCodePoint, prepareCodePoint) * fontSize;
            }

            if (glyph.drawable() || prepareFallback) {
               float preparePixelX = preparePenX * baseScale + translationPixelsX;
               int prepareSubpixelPhase = Math.round((preparePixelX - (int)Math.floor(preparePixelX)) * (4.0F));
               if (prepareSubpixelPhase == 4) {
                  prepareSubpixelPhase = 0;
               }

               if (prepareFallback ? !this.smallTextCache.prepareFallbackGlyph(prepareCodePoint, cacheSize, prepareSubpixelPhase) : !this.smallTextCache.prepareGlyph(font, prepareCodePoint, glyph, cacheSize, prepareSubpixelPhase)) {
                  return false;
               }
            }

            preparePenX += (prepareFallback ? SmallTextGlyphCache.fallbackAdvance(prepareCodePoint) : glyph.advance()) * fontSize;
            preparePreviousCodePoint = prepareCodePoint;
         } else {
            preparePenX = startX;
            preparePreviousCodePoint = -1;
         }
      }

      float baseline = spec.y() + font.getAscender(fontSize);
      float lineHeight = font.getLineHeight(fontSize);
      MsdfTextRenderer.TransformKey transformKey = MsdfTextRenderer.TransformKey.of(transform);
      float drawPenX = startX;
      float baselinePixels = Math.round(baseline * baseScale + translationPixelsY);
      int lineIndex = 0;
      int previousCodePoint = -1;
      String drawText = spec.text();
      int textIndex = 0;

      while (textIndex < drawText.length()) {
         int codePoint = drawText.codePointAt(textIndex);
         textIndex += Character.charCount(codePoint);
         if (codePoint == 10) {
            drawPenX = startX;
            baselinePixels = Math.round((baseline + ++lineIndex * lineHeight) * baseScale + translationPixelsY);
            previousCodePoint = -1;
         } else {
            boolean fallback = !font.hasGlyph(codePoint) && SmallTextGlyphCache.supportsFallbackCodePoint(codePoint);
            MsdfGlyph sourceGlyph = font.getGlyph(codePoint);
            if (previousCodePoint != -1) {
               drawPenX += font.getKerning(previousCodePoint, codePoint) * fontSize;
            }

            if (sourceGlyph.drawable() || fallback) {
               float pixelX = drawPenX * baseScale + translationPixelsX;
               int pixelFloorX = (int)Math.floor(pixelX);
               int subpixelPhase = Math.round((pixelX - pixelFloorX) * (4.0F));
               if (subpixelPhase == 4) {
                  subpixelPhase = 0;
                  pixelFloorX++;
               }

               SmallTextGlyphCache.CachedGlyph cachedGlyph = fallback ? this.smallTextCache.fallbackGlyph(codePoint, cacheSize, subpixelPhase) : this.smallTextCache.glyph(font, codePoint, sourceGlyph, cacheSize, subpixelPhase);
               if (cachedGlyph.drawable()) {
                  float snappedPixelX = pixelFloorX;
                  float glyphLeft = (snappedPixelX + cachedGlyph.offsetX() - translationPixelsX) / baseScale;
                  float glyphTop = (baselinePixels - cachedGlyph.offsetY() - translationPixelsY) / baseScale;
                  float glyphRight = glyphLeft + cachedGlyph.width() / baseScale;
                  float glyphBottom = glyphTop + cachedGlyph.height() / baseScale;
                  MsdfTextRenderer.IconBatchKey batchKey = new MsdfTextRenderer.IconBatchKey(state, layerSerial, cachedGlyph.page(), transformKey, bounds);
                  AtlasQuadBatchRenderState cachedBatch = this.iconBatches.get(batchKey);
                  if (cachedBatch == null) {
                     cachedBatch = new AtlasQuadBatchRenderState(transform, cachedGlyph.page().textureSetup(), bounds);
                     this.iconBatches.put(batchKey, cachedBatch);
                     state.addPreparedTextElement(cachedBatch);
                  }

                  AtlasQuadBatchRenderState quadBatch = cachedBatch;
                  clipGlyphQuad(
                     glyphLeft,
                     glyphTop,
                     glyphRight,
                     glyphBottom,
                     cachedGlyph.minU(),
                     cachedGlyph.minV(),
                     cachedGlyph.maxU(),
                     cachedGlyph.maxV(),
                     (left, top, right, bottom, minU, minV, maxU, maxV, topAlpha, bottomAlpha) -> {
                        int topLeftColor = multiplyAlpha(this.applyHorizontalFade(spec.colorTopLeft(), left, spec), topAlpha);
                        int topRightColor = multiplyAlpha(this.applyHorizontalFade(spec.colorTopRight(), right, spec), topAlpha);
                        int bottomRightColor = multiplyAlpha(this.applyHorizontalFade(spec.colorBottomRight(), right, spec), bottomAlpha);
                        int bottomLeftColor = multiplyAlpha(this.applyHorizontalFade(spec.colorBottomLeft(), left, spec), bottomAlpha);
                        if ((topLeftColor | topRightColor | bottomRightColor | bottomLeftColor) >>> 24 != 0) {
                           quadBatch.add(left, top, right, bottom, minU, minV, maxU, maxV, topLeftColor, topRightColor, bottomRightColor, bottomLeftColor);
                        }
                     }
                  );
               }
            }

            drawPenX += (fallback ? SmallTextGlyphCache.fallbackAdvance(codePoint) : sourceGlyph.advance()) * fontSize;
            previousCodePoint = codePoint;
         }
      }

      return true;
   }

   private TextureSetup atlasTextureSetup(Identifier id) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client == null) {
         return null;
      }

      TextureManager textureManager = client.getTextureManager();
      if (textureManager == null) {
         return null;
      }

      AbstractTexture atlasTexture = textureManager.getTexture(id);
      if (atlasTexture != null && atlasTexture.getGlTextureView() != null) {
         MsdfTextRenderer.TextureCacheEntry cacheEntry = this.textureCache.get(id);
         if (cacheEntry != null && cacheEntry.texture() == atlasTexture) {
            return cacheEntry.setup();
         }

         TextureSetup textureSetup = TextureSetup.of(atlasTexture.getGlTextureView(), RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
         this.textureCache.put(id, new MsdfTextRenderer.TextureCacheEntry(atlasTexture, textureSetup));
         return textureSetup;
      } else {
         return null;
      }
   }

   private static int packShimmerColor(float progress, float halfWidth, float intensity, float opacity) {
      int alphaByte = unitToByte(opacity);
      int progressByte = unitToByte((progress + (0.5F)) / 2.0F);
      int widthByte = unitToByte(halfWidth);
      int intensityByte = unitToByte(intensity);
      return alphaByte << 24 | progressByte << 16 | widthByte << 8 | intensityByte;
   }

   private void emitAtlasGlyphs(
      MsdfTextBatchRenderState batch,
      GuiRenderState state,
      int layerSerial,
      Matrix3x2f transform,
      ScreenRect bounds,
      MsdfFont font,
      MsdfTextSpec spec,
      boolean shimmer,
      int shimmerParams
   ) {
      float fontSize = spec.size();
      float startX = spec.x();
      float baseline = spec.y() + font.getAscender(fontSize);
      float lineHeight = font.getLineHeight(fontSize);
      float[] lineWidths = this.measureLineWidths(font, spec.text(), fontSize);
      float penX = startX;
      float penBaseline = baseline;
      int lineIndex = 0;
      int previousCodePoint = -1;
      String drawText = spec.text();
      int textIndex = 0;

      while (textIndex < drawText.length()) {
         int codePoint = drawText.codePointAt(textIndex);
         textIndex += Character.charCount(codePoint);
         if (codePoint == 10) {
            penX = startX;
            penBaseline += lineHeight;
            lineIndex++;
            previousCodePoint = -1;
         } else {
            MsdfGlyph glyph = font.getGlyph(codePoint);
            if (previousCodePoint != -1) {
               penX += font.getKerning(previousCodePoint, codePoint) * fontSize;
            }

            if (!font.hasGlyph(codePoint) && SmallTextGlyphCache.supportsFallbackCodePoint(codePoint)) {
               if (!this.forceAtlasGlyphs && !shimmer && Math.abs(spec.rotationDegrees()) < (0.001F)) {
                  SmallTextGlyphCache.CachedGlyph fallbackGlyph = this.smallTextCache.fallbackGlyph(codePoint, SmallTextGlyphCache.quantizeSize(fontSize), 0);
                  if (fallbackGlyph.drawable()) {
                     float fallbackLeft = penX + fallbackGlyph.offsetX() / GuiScale.getBaseScale();
                     float fallbackTop = penBaseline - fallbackGlyph.offsetY() / GuiScale.getBaseScale();
                     MsdfTextRenderer.IconBatchKey fallbackBatchKey = new MsdfTextRenderer.IconBatchKey(state, layerSerial, fallbackGlyph.page(), MsdfTextRenderer.TransformKey.of(transform), bounds);
                     AtlasQuadBatchRenderState fallbackBatch = this.iconBatches.get(fallbackBatchKey);
                     if (fallbackBatch == null) {
                        fallbackBatch = new AtlasQuadBatchRenderState(transform, fallbackGlyph.page().textureSetup(), bounds);
                        this.iconBatches.put(fallbackBatchKey, fallbackBatch);
                        state.addPreparedTextElement(fallbackBatch);
                     }

                     AtlasQuadBatchRenderState quadBatch = fallbackBatch;
                     clipGlyphQuad(
                        fallbackLeft,
                        fallbackTop,
                        fallbackLeft + fallbackGlyph.width() / GuiScale.getBaseScale(),
                        fallbackTop + fallbackGlyph.height() / GuiScale.getBaseScale(),
                        fallbackGlyph.minU(),
                        fallbackGlyph.minV(),
                        fallbackGlyph.maxU(),
                        fallbackGlyph.maxV(),
                        (left, top, right, bottom, minU, minV, maxU, maxV, topAlpha, bottomAlpha) -> {
                           int topLeftColor = multiplyAlpha(this.applyHorizontalFade(spec.colorTopLeft(), left, spec), topAlpha);
                           int topRightColor = multiplyAlpha(this.applyHorizontalFade(spec.colorTopRight(), right, spec), topAlpha);
                           int bottomRightColor = multiplyAlpha(this.applyHorizontalFade(spec.colorBottomRight(), right, spec), bottomAlpha);
                           int bottomLeftColor = multiplyAlpha(this.applyHorizontalFade(spec.colorBottomLeft(), left, spec), bottomAlpha);
                           if ((topLeftColor | topRightColor | bottomRightColor | bottomLeftColor) >>> 24 != 0) {
                              quadBatch.add(left, top, right, bottom, minU, minV, maxU, maxV, topLeftColor, topRightColor, bottomRightColor, bottomLeftColor);
                           }
                        }
                     );
                  }
               }

               penX += SmallTextGlyphCache.fallbackAdvance(codePoint) * fontSize;
               previousCodePoint = codePoint;
            } else {
               if (glyph.drawable()) {
                  float glyphLeft = penX + glyph.planeLeft() * fontSize;
                  float glyphRight = penX + glyph.planeRight() * fontSize;
                  float glyphTop = penBaseline - glyph.planeTop() * fontSize;
                  float glyphBottom = penBaseline - glyph.planeBottom() * fontSize;
                  float lineWidth = lineIndex < lineWidths.length ? lineWidths[lineIndex] : 1.0F;
                  float leftLinePosition = shimmer ? clampUnit((glyphLeft - startX) / lineWidth) : 0.0F;
                  float rightLinePosition = shimmer ? clampUnit((glyphRight - startX) / lineWidth) : 0.0F;
                  if (shimmer) {
                     int lineTint = spec.colorTopLeft();
                     leftLinePosition = packLinePositionAndTint(leftLinePosition, lineTint);
                     rightLinePosition = packLinePositionAndTint(rightLinePosition, lineTint);
                  }

                  boolean rotated = Math.abs(spec.rotationDegrees()) >= (0.001F);
                  if (!shimmer && !rotated) {
                     MsdfTextBatchRenderState clippedBatch = batch;
                     float clippedLeftLinePosition = leftLinePosition;
                     float clippedRightLinePosition = rightLinePosition;
                     clipGlyphQuad(
                        glyphLeft,
                        glyphTop,
                        glyphRight,
                        glyphBottom,
                        glyph.uMin(),
                        glyph.vMin(),
                        glyph.uMax(),
                        glyph.vMax(),
                        (left, top, right, bottom, minU, minV, maxU, maxV, topAlpha, bottomAlpha) -> {
                           int topLeftColor = multiplyAlpha(this.applyHorizontalFade(spec.colorTopLeft(), left, spec), topAlpha);
                           int topRightColor = multiplyAlpha(this.applyHorizontalFade(spec.colorTopRight(), right, spec), topAlpha);
                           int bottomRightColor = multiplyAlpha(this.applyHorizontalFade(spec.colorBottomRight(), right, spec), bottomAlpha);
                           int bottomLeftColor = multiplyAlpha(this.applyHorizontalFade(spec.colorBottomLeft(), left, spec), bottomAlpha);
                           if ((topLeftColor | topRightColor | bottomRightColor | bottomLeftColor) >>> 24 != 0) {
                              clippedBatch.addGlyph(
                                 left, top, right, bottom, minU, minV, maxU, maxV, topLeftColor, topRightColor, bottomRightColor, bottomLeftColor, clippedLeftLinePosition, clippedRightLinePosition, 0.0F, 0.0F, 0.0F
                              );
                           }
                        }
                     );
                  } else {
                     int rotatedTopLeftColor = shimmer ? shimmerParams : RoundedClipStack.clipColor(this.applyHorizontalFade(spec.colorTopLeft(), glyphLeft, spec), glyphLeft, glyphTop);
                     int rotatedTopRightColor = shimmer ? shimmerParams : RoundedClipStack.clipColor(this.applyHorizontalFade(spec.colorTopRight(), glyphRight, spec), glyphRight, glyphTop);
                     int rotatedBottomRightColor = shimmer ? shimmerParams : RoundedClipStack.clipColor(this.applyHorizontalFade(spec.colorBottomRight(), glyphRight, spec), glyphRight, glyphBottom);
                     int rotatedBottomLeftColor = shimmer ? shimmerParams : RoundedClipStack.clipColor(this.applyHorizontalFade(spec.colorBottomLeft(), glyphLeft, spec), glyphLeft, glyphBottom);
                     if (shimmer || (rotatedTopLeftColor | rotatedTopRightColor | rotatedBottomRightColor | rotatedBottomLeftColor) >>> 24 != 0) {
                        batch.addGlyph(
                           glyphLeft,
                           glyphTop,
                           glyphRight,
                           glyphBottom,
                           glyph.uMin(),
                           glyph.vMin(),
                           glyph.uMax(),
                           glyph.vMax(),
                           rotatedTopLeftColor,
                           rotatedTopRightColor,
                           rotatedBottomRightColor,
                           rotatedBottomLeftColor,
                           leftLinePosition,
                           rightLinePosition,
                           spec.rotationDegrees(),
                           spec.rotationOriginX(),
                           spec.rotationOriginY()
                        );
                     }
                  }
               }

               penX += glyph.advance() * fontSize;
               previousCodePoint = codePoint;
            }
         }
      }
   }

   private static float packLinePositionAndTint(float linePosition, int tint) {
      int positionBits = Math.round(clampUnit(linePosition) * (63.0F));
      int redBits = Math.round((tint >>> 16 & 0xFF) / (255.0F) * (63.0F));
      int greenBits = Math.round((tint >>> 8 & 0xFF) / (255.0F) * (63.0F));
      int blueBits = Math.round((tint & 0xFF) / (255.0F) * (63.0F));
      return positionBits + blueBits * 64 + greenBits * 4096 + redBits * (262144);
   }

   @Override
   public void close() {
      this.clearCaches();
   }

   private static float clampUnit(float value) {
      return Math.max(0.0F, Math.min(1.0F, value));
   }

   public static void resetInstanceCaches() {
      MsdfTextRenderer renderer = INSTANCE;
      if (renderer != null) {
         renderer.clearCaches();
      } else {
         MsdfFontRegistry.clear();
      }
   }

   private static void clipGlyphQuad(
      float left, float top, float right, float bottom, float minU, float minV, float maxU, float maxV, MsdfTextRenderer.GlyphQuadConsumer consumer
   ) {
      if (!RoundedClipStack.isActive()) {
         consumer.accept(left, top, right, bottom, minU, minV, maxU, maxV, 1.0F, 1.0F);
      } else {
         float clipLeft = RoundedClipStack.getX();
         float clipTop = RoundedClipStack.getY();
         float clipRight = clipLeft + RoundedClipStack.getWidth();
         float clipBottom = clipTop + RoundedClipStack.getHeight();
         if (!(left >= clipRight) && !(right <= clipLeft) && !(top >= clipBottom) && !(bottom <= clipTop) && !(right <= left) && !(bottom <= top)) {
            float quadWidth = right - left;
            float quadHeight = bottom - top;
            float clippedLeft = Math.max(left, clipLeft);
            float clippedRight = Math.min(right, clipRight);
            float clippedTop = Math.max(top, clipTop);
            float clippedBottom = Math.min(bottom, clipBottom);
            float clippedMinU = minU + (maxU - minU) * (clippedLeft - left) / quadWidth;
            float clippedMaxU = minU + (maxU - minU) * (clippedRight - left) / quadWidth;
            float topFadeBoundary = clipTop + 1.0F;
            float bottomFadeBoundary = clipBottom - 1.0F;
            float[] stripBoundaries = new float[]{clippedTop, Math.min(Math.max(topFadeBoundary, clippedTop), clippedBottom), Math.min(Math.max(bottomFadeBoundary, clippedTop), clippedBottom), clippedBottom};
            Arrays.sort(stripBoundaries);

            for (int stripIndex = 0; stripIndex < 3; stripIndex++) {
               float stripTop = stripBoundaries[stripIndex];
               float stripBottom = stripBoundaries[stripIndex + 1];
               if (!(stripBottom - stripTop < (1.0E-4F))) {
                  float stripMinV = minV + (maxV - minV) * (stripTop - top) / quadHeight;
                  float stripMaxV = minV + (maxV - minV) * (stripBottom - top) / quadHeight;
                  consumer.accept(clippedLeft, stripTop, clippedRight, stripBottom, clippedMinU, stripMinV, clippedMaxU, stripMaxV, verticalClipAlpha(stripTop, clipTop, clipBottom), verticalClipAlpha(stripBottom, clipTop, clipBottom));
               }
            }
         }
      }
   }

   private static float verticalClipAlpha(float y, float clipTop, float clipBottom) {
      float edgeDistance = Math.min((y - clipTop) / 1.0F, (clipBottom - y) / 1.0F);
      return Math.max(0.0F, Math.min(1.0F, edgeDistance));
   }

   private void clearCaches() {
      this.warmupFontIndex = 0;
      this.drawContext = null;
      this.glyphBatches.clear();
      this.iconBatches.clear();
      this.textureCache.clear();
      this.smallTextCache.clear();
      MsdfFontRegistry.clear();
   }

   private float[] measureLineWidths(MsdfFont font, String text, float size) {
      int lineCount = 1;

      for (int charIndex = 0; charIndex < text.length(); charIndex++) {
         if (text.charAt(charIndex) == '\n') {
            lineCount++;
         }
      }

      float[] lineWidths = new float[lineCount];
      int lineIndex = 0;
      int previousCodePoint = -1;
      int textIndex = 0;

      while (textIndex < text.length()) {
         int codePoint = text.codePointAt(textIndex);
         textIndex += Character.charCount(codePoint);
         if (codePoint == 10) {
            lineWidths[lineIndex] = Math.max(lineWidths[lineIndex], 1.0F);
            lineIndex++;
            previousCodePoint = -1;
         } else {
            MsdfGlyph glyph = font.getGlyph(codePoint);
            if (previousCodePoint != -1) {
               lineWidths[lineIndex] += font.getKerning(previousCodePoint, codePoint) * size;
            }

            lineWidths[lineIndex] += (!font.hasGlyph(codePoint) && SmallTextGlyphCache.supportsFallbackCodePoint(codePoint) ? SmallTextGlyphCache.fallbackAdvance(codePoint) : glyph.advance()) * size;
            previousCodePoint = codePoint;
         }
      }

      for (int widthIndex = 0; widthIndex < lineWidths.length; widthIndex++) {
         lineWidths[widthIndex] = Math.max(lineWidths[widthIndex], 1.0F);
      }

      return lineWidths;
   }

   private static Identifier id(String path) {
      return Identifier.of(("mouseclient"), path);
   }

   public void renderShimmer(MsdfTextSpec spec, float progress, float halfWidth, float intensity, float opacity) {
      this.submitText(this.drawContext, spec, true, progress, halfWidth, intensity, opacity);
   }

   private MsdfTextRenderer() {
   }

   public void setForceAtlasGlyphs(boolean forceAtlasGlyphs) {
      this.forceAtlasGlyphs = forceAtlasGlyphs;
   }

   public void begin(DrawContext drawContext) {
      if (this.drawContext != drawContext) {
         this.glyphBatches.clear();
         this.iconBatches.clear();
      }

      this.drawContext = drawContext;
      this.smallTextCache.applyPendingReset();
   }

   public void invalidateTextures() {
      this.textureCache.clear();
   }

   public boolean hasGlyph(String fontName, int codePoint) {
      MsdfFont font = MsdfFontRegistry.getFont(fontName);
      return font != null && font.hasGlyph(codePoint);
   }

   public static MsdfTextRenderer getInstance() {
      MsdfTextRenderer renderer = INSTANCE;
      if (renderer == null) {
         synchronized (MsdfTextRenderer.class) {
            renderer = INSTANCE;
            if (renderer == null) {
               renderer = new MsdfTextRenderer();
               INSTANCE = renderer;
            }
         }
      }

      return renderer;
   }

   private void submitText(DrawContext context, MsdfTextSpec spec, boolean shimmer, float progress, float halfWidth, float intensity, float opacity) {
      if (context != null && spec != null && spec.visible()) {
         MsdfFont font = MsdfFontRegistry.getFont(spec.fontName());
         if (font != null) {
            try {
               Matrix3x2f transform = GuiScale.getScaledMatrices(context);
               GuiRenderState state = ((DrawContextAccessor)(Object)context).getGuiRenderState();
               int layerSerial = ((GuiLayerTracker)state).getLayerSerial();
               ScreenRect bounds = ScissorStack.current();
               boolean cacheEligible = !shimmer && Math.abs(spec.rotationDegrees()) < (0.001F) && spec.size() <= (20.0F);
               if (cacheEligible && !this.forceAtlasGlyphs && SmallTextGlyphCache.isBaseScaleTransform(transform) && this.smallTextCache.isFaceReady(font) && this.drawCachedGlyphs(state, layerSerial, transform, bounds, font, spec)
                  )
                {
                  return;
               }

               TextureSetup textureSetup = this.atlasTextureSetup(font.getAtlasTexture());
               if (textureSetup == null) {
                  return;
               }

               int shimmerParams = shimmer ? packShimmerColor(progress, halfWidth, intensity, opacity) : 0;
               MsdfTextRenderer.GlyphBatchKey batchKey = new MsdfTextRenderer.GlyphBatchKey(
                  state, layerSerial, font.getAtlasTexture(), MsdfTextRenderer.TransformKey.of(transform), bounds, shimmer, shimmerParams
               );
               MsdfTextBatchRenderState glyphBatch = this.glyphBatches.get(batchKey);
               if (glyphBatch == null) {
                  glyphBatch = new MsdfTextBatchRenderState(transform, textureSetup, bounds, shimmer, font.getPxRange());
                  this.glyphBatches.put(batchKey, glyphBatch);
                  state.addPreparedTextElement(glyphBatch);
               }

               this.emitAtlasGlyphs(glyphBatch, state, layerSerial, transform, bounds, font, spec, shimmer, shimmerParams);
            } catch (RuntimeException error) {
               ClientInitializer.logger().warn(("[MSDF] Failed to submit text: {}"), spec.text(), error);
            }
         }
      }
   }

   private static float smoothstepUnit(float value) {
      return value * value * ((3.0F) - 2.0F * value);
   }

   private record GlyphBatchKey(
      GuiRenderState state, int layerSerial, Identifier atlas, MsdfTextRenderer.TransformKey pose, ScreenRect scissorArea, boolean shimmer, int shimmerParams
   ) {
   }

   private record TransformKey(float m00, float m01, float m10, float m11, float m20, float m21) {
      public static MsdfTextRenderer.TransformKey of(Matrix3x2f transform) {
         return new MsdfTextRenderer.TransformKey(transform.m00(), transform.m01(), transform.m10(), transform.m11(), transform.m20(), transform.m21());
      }
   }

   private interface GlyphQuadConsumer {
      public void accept(float left, float top, float right, float bottom, float minU, float minV, float maxU, float maxV, float topAlpha, float bottomAlpha);
   }

   private record IconBatchKey(GuiRenderState state, int layerSerial, SmallTextGlyphCache.GlyphAtlasPage page, MsdfTextRenderer.TransformKey pose, ScreenRect scissorArea) {
   }

   private record TextureCacheEntry(AbstractTexture texture, TextureSetup setup) {
   }
}
