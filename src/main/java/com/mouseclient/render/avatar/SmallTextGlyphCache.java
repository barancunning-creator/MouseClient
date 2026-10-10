package com.mouseclient.render.avatar;

import com.mouseclient.internal.GuiScale;
import com.mouseclient.network.ClientInitializer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.resource.Resource;
import org.joml.Matrix3x2f;

public final class SmallTextGlyphCache implements AutoCloseable {
   public static final float SMALL_TEXT_MAX_SIZE = 20.0F;
   public static final int SUBPIXEL_STEPS = 4;

   private static final SmallTextGlyphCache.CachedGlyph EMPTY_GLYPH = new SmallTextGlyphCache.CachedGlyph(null, 0.0F, 0.0F, 0.0F, 0.0F, 0, 0, 0, 0);
   private static final FontRenderContext FONT_CONTEXT = new FontRenderContext(
      new AffineTransform(), RenderingHints.VALUE_TEXT_ANTIALIAS_ON, RenderingHints.VALUE_FRACTIONALMETRICS_OFF
   );
   private static final Font FALLBACK_FONT = new Font(("SansSerif"), 0, 32);
   
   private static final ExecutorService faceLoader = Executors.newSingleThreadExecutor(loaderTask -> {
      Thread loaderThread = new Thread(loaderTask, ("mouseclient-msdf-face-loader"));
      loaderThread.setDaemon(true);
      return loaderThread;
   });
   private static final Map<Integer, Float> fallbackAdvances = new ConcurrentHashMap<>();
   private final Map<MsdfFont, Optional<SmallTextGlyphCache.MsdfAtlasImage>> atlasImages = new ConcurrentHashMap<>();
   private final Map<MsdfFont, Optional<Font>> fontFaces = new ConcurrentHashMap<>();
   private final Set<MsdfFont> scheduledFaces = ConcurrentHashMap.newKeySet();
   private final Map<MsdfFont, Map<Long, SmallTextGlyphCache.CachedGlyph>> fontGlyphs = new HashMap<>();
   private final Map<Long, SmallTextGlyphCache.CachedGlyph> fallbackGlyphs = new HashMap<>();
   private final List<SmallTextGlyphCache.GlyphAtlasPage> pages = new ArrayList<>();
   private boolean resetPending;
   private long currentFrame;
   private long remainingBakeNanos;

   public void clear() {
      this.clearPages();
      this.atlasImages.clear();
      this.fontFaces.clear();
      this.scheduledFaces.clear();
      this.resetPending = false;
   }

   public static boolean supportsFallbackCodePoint(int codePoint) {
      return codePoint > 32 && !Character.isWhitespace(codePoint) && Character.isDefined(codePoint) && FALLBACK_FONT.canDisplay(codePoint);
   }

   private void clearPages() {
      for (SmallTextGlyphCache.GlyphAtlasPage page : this.pages) {
         page.close();
      }

      this.pages.clear();
      this.fontGlyphs.clear();
      this.fallbackGlyphs.clear();
   }

   private SmallTextGlyphCache.CachedGlyph bakeGlyph(MsdfFont font, int codePoint, MsdfGlyph glyph, float size, float subpixelOffset) {
      Font awtFont = this.fontFace(font);
      if (awtFont != null) {
         SmallTextGlyphCache.CachedGlyph fontGlyph = this.bakeFontGlyph(awtFont, codePoint, size, subpixelOffset);
         if (fontGlyph != null) {
            return fontGlyph;
         }
      }

      return this.bakeMsdfGlyph(font, glyph, size, subpixelOffset);
   }

   private SmallTextGlyphCache.CachedGlyph bakeFontGlyph(Font awtFont, int codePoint, float size, float subpixelOffset) {
      float pixelSize = size * GuiScale.getBaseScale();
      byte supersampleScale = 3;
      Font sizedFont = awtFont.deriveFont(0, pixelSize * supersampleScale);
      GlyphVector glyphVector = sizedFont.createGlyphVector(FONT_CONTEXT, new String(Character.toChars(codePoint)));
      if (glyphVector.getNumGlyphs() == 0) {
         return EMPTY_GLYPH;
      }

      float scaledOffset = subpixelOffset * supersampleScale;
      Rectangle pixelBounds = glyphVector.getPixelBounds(FONT_CONTEXT, scaledOffset, 0.0F);
      if (pixelBounds.width > 0 && pixelBounds.height > 0) {
         int left = Math.floorDiv(pixelBounds.x, supersampleScale);
         int top = Math.floorDiv(pixelBounds.y, supersampleScale);
         int right = -Math.floorDiv(-(pixelBounds.x + pixelBounds.width), supersampleScale);
         int bottom = -Math.floorDiv(-(pixelBounds.y + pixelBounds.height), supersampleScale);
         int width = right - left + 4;
         int height = bottom - top + 4;
         if (width <= 1024 && height <= 1024) {
            int supersampledWidth = width * supersampleScale;
            int supersampledHeight = height * supersampleScale;
            BufferedImage glyphImage = new BufferedImage(supersampledWidth, supersampledHeight, 2);
            Graphics2D graphics = glyphImage.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
            graphics.setColor(Color.WHITE);
            graphics.drawGlyphVector(glyphVector, (2 - left) * supersampleScale + scaledOffset, (2 - top) * supersampleScale);
            graphics.dispose();
            int[] argbPixels = glyphImage.getRGB(0, 0, supersampledWidth, supersampledHeight, null, 0, supersampledWidth);
            int[] coverage = new int[width * height];
            int samplesPerPixel = supersampleScale * supersampleScale;

            for (int y = 0; y < height; y++) {
               for (int x = 0; x < width; x++) {
                  int alphaSum = 0;
                  int sampleStart = y * supersampleScale * supersampledWidth + x * supersampleScale;

                  for (int sampleY = 0; sampleY < supersampleScale; sampleY++) {
                     int rowStart = sampleStart + sampleY * supersampledWidth;

                     for (int sampleX = 0; sampleX < supersampleScale; sampleX++) {
                        alphaSum += argbPixels[rowStart + sampleX] >>> 24 & 0xFF;
                     }
                  }

                  coverage[y * width + x] = coverageToAlpha((float)alphaSum / samplesPerPixel);
               }
            }

            SmallTextGlyphCache.AtlasAllocation allocation = this.allocate(width, height);
            if (allocation == null) {
               return EMPTY_GLYPH;
            }

            allocation.page.writeCoverage(coverage, width, height, allocation.x, allocation.y);
            float uvScale = (9.765625E-4F);
            return new SmallTextGlyphCache.CachedGlyph(
               allocation.page, allocation.x * uvScale, allocation.y * uvScale, (allocation.x + width) * uvScale, (allocation.y + height) * uvScale, left - 2, -top + 2, width, height
            );
         } else {
            return EMPTY_GLYPH;
         }
      } else {
         return EMPTY_GLYPH;
      }
   }

   public static boolean isBaseScaleTransform(Matrix3x2f transform) {
      float baseScale = GuiScale.getBaseScale();
      int windowScale = GuiScale.getWindowScale();
      return Math.abs(transform.m01()) < (1.0E-4F)
         && Math.abs(transform.m10()) < (1.0E-4F)
         && Math.abs(transform.m00() * windowScale - baseScale) < (0.001F)
         && Math.abs(transform.m11() * windowScale - baseScale) < (0.001F);
   }

   public SmallTextGlyphCache.CachedGlyph fallbackGlyph(int codePoint, float size, int subpixelStep) {
      long cacheKey = (long)codePoint << 16 | (long)Math.round(size * (4.0F)) << 2 | subpixelStep;
      SmallTextGlyphCache.CachedGlyph cachedGlyph = this.fallbackGlyphs.get(cacheKey);
      if (cachedGlyph == null) {
         long startedNanos = System.nanoTime();
         cachedGlyph = this.bakeFontGlyph(FALLBACK_FONT, codePoint, size, subpixelStep / (4.0F));
         this.remainingBakeNanos = this.remainingBakeNanos - (System.nanoTime() - startedNanos);
         this.fallbackGlyphs.put(cacheKey, cachedGlyph);
      }

      if (cachedGlyph.page() != null) {
         cachedGlyph.page().lastUsedFrame = this.currentFrame;
      }

      return cachedGlyph;
   }

   public void uploadDirtyPages() {
      for (SmallTextGlyphCache.GlyphAtlasPage page : this.pages) {
         page.uploadDirtyRegion();
      }
   }

   private SmallTextGlyphCache.MsdfAtlasImage atlasImage(MsdfFont font) {
      Optional loadedImage = this.atlasImages.get(font);
      return loadedImage == null ? null : (SmallTextGlyphCache.MsdfAtlasImage)loadedImage.orElse(null);
   }

   public void beginFrame() {
      this.remainingBakeNanos = (2000000L);
      this.currentFrame++;
   }

   private static int coverageToAlpha(float value) {
      double coverage = clampUnit(value / (255.0F));
      return (int)Math.min((255L), Math.round(Math.pow(coverage, (0.72D)) * (255.0D)));
   }

   public boolean prepareGlyph(MsdfFont font, int codePoint, MsdfGlyph glyph, float size, int subpixelStep) {
      java.util.Map<java.lang.Long,com.mouseclient.render.avatar.SmallTextGlyphCache.CachedGlyph> cachedGlyphs = this.fontGlyphs.computeIfAbsent(font, cacheFont -> new HashMap<>());
      long cacheKey = (long)codePoint << 16 | (long)Math.round(size * (4.0F)) << 2 | subpixelStep;
      if (cachedGlyphs.containsKey(cacheKey)) {
         return true;
      }

      if (this.remainingBakeNanos <= 0L) {
         return false;
      }

      long startedNanos = System.nanoTime();
      cachedGlyphs.put(cacheKey, this.bakeGlyph(font, codePoint, glyph, size, subpixelStep / (4.0F)));
      this.remainingBakeNanos = this.remainingBakeNanos - (System.nanoTime() - startedNanos);
      return true;
   }

   public boolean hasBakeBudget() {
      return this.remainingBakeNanos > 0L;
   }

   private static float sampleSignedDistance(
      SmallTextGlyphCache.MsdfAtlasImage atlas, MsdfFont font, MsdfGlyph glyph, float atlasLeft, float atlasRight, float atlasTop, float atlasBottom, float planeX, float planeY, float edgeMargin
   ) {
      float planeWidth = glyph.planeRight() - glyph.planeLeft();
      float planeHeight = glyph.planeTop() - glyph.planeBottom();
      float atlasX = atlasLeft + (planeX - glyph.planeLeft()) / planeWidth * (atlasRight - atlasLeft);
      float atlasY = atlasTop + (glyph.planeTop() - planeY) / planeHeight * (atlasBottom - atlasTop);
      boolean nearEdge = planeX < glyph.planeLeft() + edgeMargin || planeX > glyph.planeRight() - edgeMargin || planeY < glyph.planeBottom() + edgeMargin || planeY > glyph.planeTop() - edgeMargin;
      atlasX = clamp(atlasX, atlasLeft + (0.5F), atlasRight - (0.5F));
      atlasY = clamp(atlasY, atlasTop + (0.5F), atlasBottom - (0.5F));
      float median = atlas.medianDistance(atlasX, atlasY);
      float signedDistance = (median - (0.5F)) * font.getPxRange();
      return nearEdge ? Math.min(signedDistance, (-0.01F)) : signedDistance;
   }

   public boolean prepareFallbackGlyph(int codePoint, float size, int subpixelStep) {
      long cacheKey = (long)codePoint << 16 | (long)Math.round(size * (4.0F)) << 2 | subpixelStep;
      if (this.fallbackGlyphs.containsKey(cacheKey)) {
         return true;
      }

      if (this.remainingBakeNanos <= 0L) {
         return false;
      }

      long startedNanos = System.nanoTime();
      this.fallbackGlyphs.put(cacheKey, this.bakeFontGlyph(FALLBACK_FONT, codePoint, size, subpixelStep / (4.0F)));
      this.remainingBakeNanos = this.remainingBakeNanos - (System.nanoTime() - startedNanos);
      return true;
   }

   private static float clamp(float value, float minimum, float maximum) {
      return Math.max(minimum, Math.min(maximum, value));
   }

   private SmallTextGlyphCache.CachedGlyph bakeMsdfGlyph(MsdfFont font, MsdfGlyph glyph, float size, float subpixelOffset) {
      SmallTextGlyphCache.MsdfAtlasImage atlas = this.atlasImage(font);
      if (atlas == null) {
         return EMPTY_GLYPH;
      }

      float planeWidth = glyph.planeRight() - glyph.planeLeft();
      float planeHeight = glyph.planeTop() - glyph.planeBottom();
      if (!(planeWidth <= 0.0F) && !(planeHeight <= 0.0F)) {
         float atlasLeft = glyph.uMin() * atlas.width;
         float atlasRight = glyph.uMax() * atlas.width;
         float atlasTop = glyph.vMin() * atlas.height;
         float atlasBottom = glyph.vMax() * atlas.height;
         float atlasPixelsPerUnit = (atlasRight - atlasLeft) / planeWidth;
         if (atlasPixelsPerUnit <= 0.0F) {
            return EMPTY_GLYPH;
         }

         float pixelSize = size * GuiScale.getBaseScale();
         int offsetX = Math.round(glyph.planeLeft() * pixelSize + subpixelOffset) - 2;
         int offsetY = Math.round(glyph.planeTop() * pixelSize) + 2;
         int width = (int)Math.ceil(planeWidth * pixelSize) + 4 + 2;
         int height = (int)Math.ceil(planeHeight * pixelSize) + 4 + 1;
         if (width <= 1024 && height <= 1024) {
            float distanceScale = pixelSize / atlasPixelsPerUnit;
            float edgeMargin = 1.0F / atlasPixelsPerUnit;
            int[] coverage = new int[width * height];

            for (int y = 0; y < height; y++) {
               for (int x = 0; x < width; x++) {
                  float coverageSum = 0.0F;

                  for (int sampleY = 0; sampleY < 4; sampleY++) {
                     for (int sampleX = 0; sampleX < 4; sampleX++) {
                        float sampleOffsetX = (sampleX + (0.5F)) / (4.0F) - (0.5F);
                        float sampleOffsetY = (sampleY + (0.5F)) / (4.0F) - (0.5F);
                        float planeX = (offsetX + x + (0.5F) + sampleOffsetX - subpixelOffset) / pixelSize;
                        float planeY = (offsetY - y - (0.5F) - sampleOffsetY) / pixelSize;
                        float signedDistance = sampleSignedDistance(atlas, font, glyph, atlasLeft, atlasRight, atlasTop, atlasBottom, planeX, planeY, edgeMargin);
                        float scaledDistance = signedDistance * distanceScale;
                        coverageSum += clampUnit(scaledDistance * (4.0F) + (0.5F));
                     }
                  }

                  coverage[y * width + x] = coverageToAlpha(coverageSum / (16.0F) * (255.0F));
               }
            }

            SmallTextGlyphCache.AtlasAllocation allocation = this.allocate(width, height);
            if (allocation == null) {
               return EMPTY_GLYPH;
            }

            allocation.page.writeCoverage(coverage, width, height, allocation.x, allocation.y);
            float uvScale = (9.765625E-4F);
            return new SmallTextGlyphCache.CachedGlyph(
               allocation.page, allocation.x * uvScale, allocation.y * uvScale, (allocation.x + width) * uvScale, (allocation.y + height) * uvScale, offsetX, offsetY, width, height
            );
         } else {
            return EMPTY_GLYPH;
         }
      } else {
         return EMPTY_GLYPH;
      }
   }

   @Override
   public void close() {
      this.clear();
   }

   private static Font loadFontFace(MsdfFont font) {
      String resourcePath = fontResourcePath(font);
      if (resourcePath == null) {
         return null;
      }

      try (InputStream input = SmallTextGlyphCache.class.getResourceAsStream(resourcePath)) {
         if (input == null) {
            ClientInitializer.logger().warn(("[MSDF] Inter OTF not found for small-text bake: {}"), resourcePath);
            return null;
         } else {
            return Font.createFont(0, input).deriveFont(0, 1.0F);
         }
      } catch (Exception error) {
         ClientInitializer.logger().warn(("[MSDF] Failed to load Inter OTF {}"), resourcePath, error);
         return null;
      }
   }

   private void scheduleFaceLoad(MsdfFont font) {
      if (this.scheduledFaces.add(font)) {
         faceLoader.submit(() -> {
            try {
               FALLBACK_FONT.canDisplay('A');
               if (MinecraftClient.getInstance() == null) {
                  this.scheduledFaces.remove(font);
                  return;
               }

               Optional loadedFace = Optional.ofNullable(loadFontFace(font));
               this.atlasImages.put(font, loadedFace.isPresent() ? Optional.empty() : Optional.ofNullable(SmallTextGlyphCache.MsdfAtlasImage.load(font)));
               this.fontFaces.put(font, loadedFace);
            } catch (Throwable loadFailure) {
               this.atlasImages.putIfAbsent(font, Optional.empty());
               this.fontFaces.putIfAbsent(font, Optional.empty());
            }
         });
      }
   }

   private static String fontResourcePath(MsdfFont font) {
      String atlasPath = font.getAtlasTexture().getPath();
      String fontName = atlasPath.substring(atlasPath.lastIndexOf(47) + 1);
      if (fontName.endsWith((".png"))) {
         fontName = fontName.substring(0, fontName.length() - 4);
      }

      String fontDirectory = ("/assets/mouseclient/fonts/inter/");

      return switch (fontName.toLowerCase(Locale.ROOT)) {
         case ("thin") -> fontDirectory + "Inter-Thin.otf";
         case ("mini") -> fontDirectory + "Inter-Light.otf";
         case ("semi") -> fontDirectory + "Inter-Medium.otf";
         case ("semi_bold") -> fontDirectory + "Inter-SemiBold.otf";
         case ("bold") -> fontDirectory + "Inter-Bold.otf";
         default -> null;
      };
   }

   public SmallTextGlyphCache() {
      this.remainingBakeNanos = (2000000L);
   }

   private SmallTextGlyphCache.GlyphAtlasPage recycleOldestPage() {
      SmallTextGlyphCache.GlyphAtlasPage oldestPage = null;

      for (SmallTextGlyphCache.GlyphAtlasPage candidatePage : this.pages) {
         if (candidatePage.lastUsedFrame != this.currentFrame && (oldestPage == null || candidatePage.lastUsedFrame < oldestPage.lastUsedFrame)) {
            oldestPage = candidatePage;
         }
      }

      if (oldestPage == null) {
         return null;
      }

      SmallTextGlyphCache.GlyphAtlasPage recycledPage = oldestPage;

      for (Map<Long, SmallTextGlyphCache.CachedGlyph> cachedGlyphs : this.fontGlyphs.values()) {
         cachedGlyphs.values().removeIf(cachedGlyph -> cachedGlyph.page() == recycledPage);
      }

      this.fallbackGlyphs.values().removeIf(cachedGlyph -> cachedGlyph.page() == recycledPage);
      recycledPage.clear();
      return recycledPage;
   }

   public static float fallbackAdvance(int codePoint) {
      return fallbackAdvances.computeIfAbsent(
         codePoint, fallbackCodePoint -> FALLBACK_FONT.createGlyphVector(FONT_CONTEXT, new String(Character.toChars(fallbackCodePoint))).getGlyphMetrics(0).getAdvance() / (32.0F)
      );
   }

   private static float clampUnit(float value) {
      return Math.max(0.0F, Math.min(1.0F, value));
   }

   private Font fontFace(MsdfFont font) {
      Optional loadedFace = this.fontFaces.get(font);
      return loadedFace == null ? null : (Font)loadedFace.orElse(null);
   }

   public void applyPendingReset() {
      if (this.resetPending) {
         this.resetPending = false;
         this.clearPages();
      }
   }

   public static float quantizeSize(float value) {
      return Math.max((0.5F), Math.round(value * (4.0F)) / (4.0F));
   }

   public SmallTextGlyphCache.CachedGlyph glyph(MsdfFont font, int codePoint, MsdfGlyph glyph, float size, int subpixelStep) {
      java.util.Map<java.lang.Long,com.mouseclient.render.avatar.SmallTextGlyphCache.CachedGlyph> cachedGlyphs = this.fontGlyphs.computeIfAbsent(font, cacheFont -> new HashMap<>());
      long cacheKey = (long)codePoint << 16 | (long)Math.round(size * (4.0F)) << 2 | subpixelStep;
      SmallTextGlyphCache.CachedGlyph cachedGlyph = (SmallTextGlyphCache.CachedGlyph)cachedGlyphs.get(cacheKey);
      if (cachedGlyph == null) {
         long startedNanos = System.nanoTime();
         cachedGlyph = this.bakeGlyph(font, codePoint, glyph, size, subpixelStep / (4.0F));
         this.remainingBakeNanos = this.remainingBakeNanos - (System.nanoTime() - startedNanos);
         cachedGlyphs.put(cacheKey, cachedGlyph);
      }

      if (cachedGlyph.page() != null) {
         cachedGlyph.page().lastUsedFrame = this.currentFrame;
      }

      return cachedGlyph;
   }

   private SmallTextGlyphCache.AtlasAllocation allocate(int width, int height) {
      for (SmallTextGlyphCache.GlyphAtlasPage page : this.pages) {
         SmallTextGlyphCache.AtlasAllocation allocation = page.allocate(width, height);
         if (allocation != null) {
            return allocation;
         }
      }

      if (this.pages.size() >= 16) {
         SmallTextGlyphCache.GlyphAtlasPage recycledPage = this.recycleOldestPage();
         if (recycledPage == null) {
            this.resetPending = true;
            ClientInitializer.logger().warn(("[MSDF] Small-text atlas overflow ({} pages), reset scheduled"), this.pages.size() + 1);
            return null;
         } else {
            return recycledPage.allocate(width, height);
         }
      } else {
         SmallTextGlyphCache.GlyphAtlasPage newPage = new SmallTextGlyphCache.GlyphAtlasPage();
         this.pages.add(newPage);
         return newPage.allocate(width, height);
      }
   }

   public boolean isFaceReady(MsdfFont font) {
      Optional loadedFace = this.fontFaces.get(font);
      Optional loadedAtlas = this.atlasImages.get(font);
      if (loadedFace != null && loadedAtlas != null) {
         return loadedFace.isPresent() || loadedAtlas.isPresent();
      }

      this.scheduleFaceLoad(font);
      return false;
   }

   public record CachedGlyph(
      SmallTextGlyphCache.GlyphAtlasPage page, float minU, float minV, float maxU, float maxV, int offsetX, int offsetY, int width, int height
   ) {
      public float minU() {
         return this.minU;
      }

      public float minV() {
         return this.minV;
      }

      public float maxV() {
         return this.maxV;
      }

      public float maxU() {
         return this.maxU;
      }

      public boolean drawable() {
         return this.page != null && this.width > 0 && this.height > 0;
      }
   }

   public static final class GlyphAtlasPage implements AutoCloseable {
      private static int textureSerial;
      private final NativeImage image;
      private final NativeImageBackedTexture texture;
      private final TextureSetup textureSetup;
      private int shelfX = 1;
      private int shelfY = 1;
      private int shelfHeight;
      private long lastUsedFrame;
      private int dirtyMinX;
      private int dirtyMinY;
      private int dirtyMaxX;
      private int dirtyMaxY;

      @Override
      public void close() {
         this.texture.close();
      }

      private void writeCoverage(int[] coverage, int width, int height, int x, int y) {
         for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
               int alpha = coverage[row * width + column];
               this.image.setColor(x + column, y + row, alpha << 24 | (16777215));
            }
         }

         this.dirtyMinX = Math.min(this.dirtyMinX, x);
         this.dirtyMinY = Math.min(this.dirtyMinY, y);
         this.dirtyMaxX = Math.max(this.dirtyMaxX, x + width);
         this.dirtyMaxY = Math.max(this.dirtyMaxY, y + height);
      }

      private SmallTextGlyphCache.AtlasAllocation allocate(int width, int height) {
         int paddedWidth = width + 1;
         int paddedHeight = height + 1;
         if (this.shelfX + paddedWidth > 1024) {
            this.shelfX = 1;
            this.shelfY = this.shelfY + this.shelfHeight;
            this.shelfHeight = 0;
         }

         if (this.shelfY + paddedHeight <= 1024 && paddedWidth <= 1024) {
            int x = this.shelfX;
            int y = this.shelfY;
            this.shelfX += paddedWidth;
            this.shelfHeight = Math.max(this.shelfHeight, paddedHeight);
            return new SmallTextGlyphCache.AtlasAllocation(this, x, y);
         } else {
            return null;
         }
      }

      private void clear() {
         this.image.fillRect(0, 0, 1024, 1024, 0);
         this.shelfX = 1;
         this.shelfY = 1;
         this.shelfHeight = 0;
         this.dirtyMinX = 0;
         this.dirtyMinY = 0;
         this.dirtyMaxX = 1024;
         this.dirtyMaxY = 1024;
      }

      public GlyphAtlasPage() {
         this.dirtyMinX = (2147483647);
         this.dirtyMinY = (2147483647);
         this.dirtyMaxX = (-2147483648);
         this.dirtyMaxY = (-2147483648);
         this.image = new NativeImage(1024, 1024, true);
         this.texture = new NativeImageBackedTexture(() -> "mouseclient_msdf_small_atlas_" + textureSerial++, this.image);
         this.textureSetup = TextureSetup.of(this.texture.getGlTextureView(), RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
      }

      private void uploadDirtyRegion() {
         if (this.dirtyMaxX > this.dirtyMinX && this.dirtyMaxY > this.dirtyMinY) {
            if (this.texture.getGlTexture() != null) {
               int x = Math.max(0, this.dirtyMinX);
               int y = Math.max(0, this.dirtyMinY);
               int width = Math.min(1024, this.dirtyMaxX) - x;
               int height = Math.min(1024, this.dirtyMaxY) - y;
               if (width > 0 && height > 0) {
                  RenderSystem.getDevice()
                     .createCommandEncoder()
                     .writeToTexture(this.texture.getGlTexture(), this.image, 0, 0, x, y, width, height, x, y);
               }

               this.dirtyMinX = (2147483647);
               this.dirtyMinY = (2147483647);
               this.dirtyMaxX = (-2147483648);
               this.dirtyMaxY = (-2147483648);
            }
         }
      }

      public TextureSetup textureSetup() {
         return this.textureSetup;
      }
   }

   private static final class MsdfAtlasImage {
      public final int width;
      public final int height;
      private final int[] pixels;

      private float channelAt(int x, int y, int channelShift) {
         x = Math.max(0, Math.min(this.width - 1, x));
         y = Math.max(0, Math.min(this.height - 1, y));
         return (this.pixels[y * this.width + x] >>> channelShift & 0xFF) / (255.0F);
      }

      public static SmallTextGlyphCache.MsdfAtlasImage load(MsdfFont font) {
         MinecraftClient client = MinecraftClient.getInstance();
         if (client == null) {
            return null;
         }

         Optional resource = client.getResourceManager().getResource(font.getAtlasTexture());
         if (resource.isEmpty()) {
            ClientInitializer.logger().warn(("[MSDF] Atlas PNG not readable for small-text bake: {}"), font.getAtlasTexture());
            return null;
         }

         try (InputStream input = ((Resource)resource.get()).getInputStream()) {
            BufferedImage atlasImage = ImageIO.read(input);
            if (atlasImage == null) {
               return null;
            }

            int width = atlasImage.getWidth();
            int height = atlasImage.getHeight();
            return new SmallTextGlyphCache.MsdfAtlasImage(width, height, atlasImage.getRGB(0, 0, width, height, null, 0, width));
         } catch (Exception error) {
            ClientInitializer.logger().warn(("[MSDF] Failed to load atlas PNG for small-text bake: {}"), font.getAtlasTexture(), error);
            return null;
         }
      }

      private float bilinearChannel(int x, int y, float fractionX, float fractionY, int channelShift) {
         float topLeft = this.channelAt(x, y, channelShift);
         float topRight = this.channelAt(x + 1, y, channelShift);
         float bottomLeft = this.channelAt(x, y + 1, channelShift);
         float bottomRight = this.channelAt(x + 1, y + 1, channelShift);
         float top = topLeft + (topRight - topLeft) * fractionX;
         float bottom = bottomLeft + (bottomRight - bottomLeft) * fractionX;
         return top + (bottom - top) * fractionY;
      }

      public float medianDistance(float x, float y) {
         x -= (0.5F);
         y -= (0.5F);
         int pixelX = (int)Math.floor(x);
         int pixelY = (int)Math.floor(y);
         float fractionX = x - pixelX;
         float fractionY = y - pixelY;
         float red = this.bilinearChannel(pixelX, pixelY, fractionX, fractionY, 16);
         float green = this.bilinearChannel(pixelX, pixelY, fractionX, fractionY, 8);
         float blue = this.bilinearChannel(pixelX, pixelY, fractionX, fractionY, 0);
         return Math.max(Math.min(red, green), Math.min(Math.max(red, green), blue));
      }

      private MsdfAtlasImage(int width, int height, int[] pixels) {
         this.width = width;
         this.height = height;
         this.pixels = pixels;
      }
   }

   private record AtlasAllocation(SmallTextGlyphCache.GlyphAtlasPage page, int x, int y) {
   }
}
