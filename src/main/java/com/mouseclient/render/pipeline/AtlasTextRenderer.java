package com.mouseclient.render.pipeline;

import com.mouseclient.gui.ScissorStack;
import com.mouseclient.gui.RasterTextBatchRenderState;
import com.mouseclient.internal.GuiScale;
import com.mouseclient.mixin.DrawContextAccessor;
import com.mouseclient.network.ClientInitializer;
import com.mouseclient.render.avatar.FontAtlasPage;
import com.mouseclient.render.avatar.LayoutGlyph;
import com.mouseclient.render.font.RasterFontRegistry;
import com.mouseclient.render.font.TextLayout;
import com.mouseclient.render.font.RasterFont;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2f;

public final class AtlasTextRenderer implements AutoCloseable {
   private static volatile AtlasTextRenderer INSTANCE;
   public static final RenderPipeline TEXT_PIPELINE = RenderPipeline.builder()
      .withLocation(id(("pipeline/text_atlas")))
      .withVertexShader(id(("core/text_atlas")))
      .withFragmentShader(id(("core/text_atlas")))
      .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, DrawMode.QUADS)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withCull(false)
      .withSampler(("Sampler0"))
      .withUniform(("DynamicTransforms"), UniformType.UNIFORM_BUFFER)
      .withUniform(("Projection"), UniformType.UNIFORM_BUFFER)
      .build();
   public static final RenderPipeline FADE_PIPELINE = RenderPipeline.builder()
      .withLocation(id(("pipeline/text_atlas_fade")))
      .withVertexShader(id(("core/text_atlas_fade")))
      .withFragmentShader(id(("core/text_atlas_fade")))
      .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, DrawMode.QUADS)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withCull(false)
      .withSampler(("Sampler0"))
      .withUniform(("DynamicTransforms"), UniformType.UNIFORM_BUFFER)
      .withUniform(("Projection"), UniformType.UNIFORM_BUFFER)
      .build();
   private final RasterFontRegistry fontCache = new RasterFontRegistry();
   private final Map<AtlasTextRenderer.BatchKey, RasterTextBatchRenderState> batches = new LinkedHashMap<>(64);
   private DrawContext drawContext;

   private AtlasTextSpec normalizeColors(AtlasTextSpec spec) {
      return new AtlasTextSpec(
         spec.fontName(),
         spec.text(),
         spec.x(),
         spec.y(),
         spec.size(),
         normalizeColor(spec.colorTopLeft()),
         normalizeColor(spec.colorTopRight()),
         normalizeColor(spec.colorBottomRight()),
         normalizeColor(spec.colorBottomLeft()),
         spec.rotationDegrees(),
         spec.rotationOriginX(),
         spec.rotationOriginY(),
         spec.fadeLeft(),
         spec.fadeRight(),
         spec.fadeLeftX(),
         spec.fadeRightX(),
         spec.fadeWidth(),
         spec.fadeLeftStrength(),
         spec.fadeRightStrength(),
         spec.snapOrigin()
      );
   }

   private static boolean shouldForceOpaqueAlpha(int color) {
      return false;
   }

   public float getTextWidth(String fontName, String text, float size) {
      return text != null && !text.isEmpty() ? this.fontCache.getRasterFont(fontName, size).getTextWidth(text) : 0.0F;
   }

   private static int normalizeColor(int color) {
      return shouldForceOpaqueAlpha(color) ? color | (-16777216) : color;
   }

   private boolean requiresColorNormalization(AtlasTextSpec spec) {
      return shouldForceOpaqueAlpha(spec.colorTopLeft()) || shouldForceOpaqueAlpha(spec.colorTopRight()) || shouldForceOpaqueAlpha(spec.colorBottomRight()) || shouldForceOpaqueAlpha(spec.colorBottomLeft());
   }

   private static Identifier id(String path) {
      return Identifier.of(("mouseclient"), path);
   }

   public void render(DrawContext context, AtlasTextSpec spec) {
      this.begin(context);
      this.render(spec);
      this.end();
   }

   public void end() {
      this.drawContext = null;
      this.batches.clear();
   }

   public void render(AtlasTextSpec spec) {
      this.submit(this.drawContext, spec);
   }

   private void submit(DrawContext context, AtlasTextSpec spec) {
      if (context != null && spec != null) {
         AtlasTextSpec normalizedSpec = this.requiresColorNormalization(spec) ? this.normalizeColors(spec) : spec;
         if (normalizedSpec.visible()) {
            try {
               RasterFont font = this.fontCache.getRasterFont(normalizedSpec.fontName(), normalizedSpec.size());
               Matrix3x2f transform = GuiScale.getScaledMatrices(context);
               TextLayout layout = font.layout(normalizedSpec.text());
               if (layout.isEmpty()) {
                  return;
               }

               font.uploadDirtyPages();
               GuiRenderState state = ((DrawContextAccessor)(Object)context).getGuiRenderState();
               int layerSerial = ((GuiLayerTracker)state).getLayerSerial();
               AtlasTextRenderer.TransformKey transformKey = AtlasTextRenderer.TransformKey.of(transform);
               ScreenRect bounds = ScissorStack.current();
               RenderPipeline pipeline = normalizedSpec.hasHorizontalFade() ? FADE_PIPELINE : TEXT_PIPELINE;

               for (TextLayout.PageGlyphs pageGlyphs : layout.getPages()) {
                  if (!pageGlyphs.glyphs().isEmpty()) {
                     AtlasTextRenderer.BatchKey batchKey = new AtlasTextRenderer.BatchKey(state, layerSerial, pageGlyphs.page(), transformKey, bounds, pipeline);
                     RasterTextBatchRenderState textBatch = this.batches.get(batchKey);
                     if (textBatch == null) {
                        textBatch = new RasterTextBatchRenderState(transform, pageGlyphs.page(), bounds, pipeline);
                        this.batches.put(batchKey, textBatch);
                        state.addPreparedTextElement(textBatch);
                     }

                     textBatch.add(
                        pageGlyphs.glyphs(),
                        normalizedSpec.x(),
                        normalizedSpec.y(),
                        normalizedSpec.colorTopLeft(),
                        normalizedSpec.colorTopRight(),
                        normalizedSpec.colorBottomRight(),
                        normalizedSpec.colorBottomLeft(),
                        normalizedSpec.rotationDegrees(),
                        normalizedSpec.rotationOriginX(),
                        normalizedSpec.rotationOriginY(),
                        normalizedSpec.fadeLeft(),
                        normalizedSpec.fadeRight(),
                        normalizedSpec.fadeLeftX(),
                        normalizedSpec.fadeRightX(),
                        normalizedSpec.fadeWidth(),
                        normalizedSpec.fadeLeftStrength(),
                        normalizedSpec.fadeRightStrength(),
                        normalizedSpec.snapOrigin()
                     );
                  }
               }
            } catch (RuntimeException error) {
               ClientInitializer.logger().warn(("[FontRenderer] Failed to submit text: {}"), normalizedSpec.text(), error);
            }
         }
      }
   }

   public void begin(DrawContext drawContext) {
      if (this.drawContext != drawContext) {
         this.batches.clear();
         this.fontCache.closeEvictedFonts();
      }

      this.drawContext = drawContext;
   }

   private AtlasTextRenderer() {
   }

   public AtlasTextRenderer.PreparedText prepareText(String fontName, String text, float size) {
      if (text != null && !text.isEmpty() && !(size <= 0.0F)) {
         try {
            RasterFont font = this.fontCache.getRasterFont(fontName, size);
            TextLayout layout = font.layout(text);
            if (layout.isEmpty()) {
               return AtlasTextRenderer.PreparedText.EMPTY;
            }

            font.uploadDirtyPages();
            ArrayList preparedPages = new ArrayList(layout.getPages().size());

            for (TextLayout.PageGlyphs pageGlyphs : layout.getPages()) {
               ArrayList preparedGlyphs = new ArrayList(pageGlyphs.glyphs().size());

               for (LayoutGlyph glyph : pageGlyphs.glyphs()) {
                  preparedGlyphs.add(
                     new AtlasTextRenderer.PreparedGlyph(
                        glyph.left(),
                        glyph.top(),
                        glyph.right(),
                        glyph.bottom(),
                        glyph.uMin(),
                        glyph.vMin(),
                        glyph.uMax(),
                        glyph.vMax()
                     )
                  );
               }

               preparedPages.add(new AtlasTextRenderer.PreparedTextPage(pageGlyphs.page().getTextureSetup(), preparedGlyphs));
            }

            return new AtlasTextRenderer.PreparedText(preparedPages, layout.getWidth(), layout.getHeight());
         } catch (RuntimeException error) {
            ClientInitializer.logger().warn(("[FontRenderer] Failed to layout glyph pattern: {}"), text, error);
            return AtlasTextRenderer.PreparedText.EMPTY;
         }
      } else {
         return AtlasTextRenderer.PreparedText.EMPTY;
      }
   }

   public static AtlasTextRenderer getInstance() {
      AtlasTextRenderer renderer = INSTANCE;
      if (renderer == null) {
         synchronized (AtlasTextRenderer.class) {
            renderer = INSTANCE;
            if (renderer == null) {
               renderer = new AtlasTextRenderer();
               INSTANCE = renderer;
            }
         }
      }

      return renderer;
   }

   public static void closeInstance() {
      AtlasTextRenderer renderer = INSTANCE;
      if (renderer != null) {
         renderer.close();
         INSTANCE = null;
      }
   }

   @Override
   public void close() {
      this.drawContext = null;
      this.batches.clear();
      this.fontCache.close();
   }

   private record TransformKey(float m00, float m01, float m10, float m11, float m20, float m21) {
      public static AtlasTextRenderer.TransformKey of(Matrix3x2f transform) {
         return new AtlasTextRenderer.TransformKey(transform.m00(), transform.m01(), transform.m10(), transform.m11(), transform.m20(), transform.m21());
      }
   }

   public record PreparedText(List<AtlasTextRenderer.PreparedTextPage> pages, float width, float height) {
      public static final AtlasTextRenderer.PreparedText EMPTY = new AtlasTextRenderer.PreparedText(List.of(), 0.0F, 0.0F);

      public boolean empty() {
         return this.pages.isEmpty();
      }

      public PreparedText(List<AtlasTextRenderer.PreparedTextPage> pages, float width, float height) {
         pages = List.copyOf(pages);
         this.pages = pages;
         this.width = width;
         this.height = height;
      }
   }

   public record PreparedGlyph(
      float left, float top, float right, float bottom, float uMin, float vMin, float uMax, float vMax
   ) {
      public float uMin() {
         return this.uMin;
      }

      public float top() {
         return this.top;
      }

      public float uMax() {
         return this.uMax;
      }

      public float vMin() {
         return this.vMin;
      }

      public float bottom() {
         return this.bottom;
      }

      public float right() {
         return this.right;
      }

      public float vMax() {
         return this.vMax;
      }

      public float left() {
         return this.left;
      }
   }

   public record PreparedTextPage(TextureSetup textureSetup, List<AtlasTextRenderer.PreparedGlyph> glyphs) {
      public PreparedTextPage(TextureSetup textureSetup, List<AtlasTextRenderer.PreparedGlyph> glyphs) {
         glyphs = List.copyOf(glyphs);
         this.textureSetup = textureSetup;
         this.glyphs = glyphs;
      }
   }

   private record BatchKey(
      GuiRenderState state, int layerSerial, FontAtlasPage page, AtlasTextRenderer.TransformKey pose, ScreenRect scissorArea, RenderPipeline pipeline
   ) {
   }
}
