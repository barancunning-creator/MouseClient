package com.mouseclient.render.pipeline;

import com.mouseclient.gui.RectHalfIconBatchRenderState;
import com.mouseclient.internal.GuiScale;
import com.mouseclient.mixin.DrawContextAccessor;
import com.mouseclient.network.ClientInitializer;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2f;
import org.lwjgl.system.MemoryStack;

public final class RectHalfIconRenderer implements AutoCloseable {

   private static final VertexFormat vertexFormat = VertexFormat.builder()
      .add(("Position"), VertexFormatElement.POSITION)
      .add(("UV0"), VertexFormatElement.UV0)
      .add(("Color"), VertexFormatElement.COLOR)
      .add(("LineWidth"), VertexFormatElement.LINE_WIDTH)
      .build();
   private static volatile RectHalfIconRenderer INSTANCE;
   public static final RenderPipeline renderPipeline = RenderPipeline.builder()
      .withLocation(id(("pipeline/rect_half_icon")))
      .withVertexShader(id(("core/rect_half_icon")))
      .withFragmentShader(id(("core/rect_half_icon")))
      .withVertexFormat(vertexFormat, DrawMode.QUADS)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withCull(false)
      .withSampler(("Sampler0"))
      .withUniform(("DynamicTransforms"), UniformType.UNIFORM_BUFFER)
      .withUniform(("Projection"), UniformType.UNIFORM_BUFFER)
      .withUniform(("RectHalfIconParamsArray"), UniformType.UNIFORM_BUFFER)
      .build();
   private final List<IconPatternSpec> params = new ArrayList<>(32);
   private final Map<RectHalfIconRenderer.BatchKey, RectHalfIconBatchRenderState> batches = new LinkedHashMap<>(32);
   private DrawContext drawContext;
   private GpuBuffer paramsBuffer;
   private boolean paramsDirty = true;

   private static int multiplyAlpha(int color, float alpha) {
      int scaledAlpha = Math.round((color >>> 24 & 0xFF) * clamp(alpha, 0.0F, 1.0F));
      return scaledAlpha << 24 | color & (16777215);
   }

   private static float jitter(long seed, int column, int row, int channel) {
      long mixedSeed = seed ^ column * (-7046029254386353131L) ^ row * (-4658895280553007687L) ^ channel * (-7723592293110705685L);
      mixedSeed ^= mixedSeed >>> 30;
      mixedSeed *= (-4658895280553007687L);
      mixedSeed ^= mixedSeed >>> 27;
      mixedSeed *= (-7723592293110705685L);
      mixedSeed ^= mixedSeed >>> 31;
      return (float)(mixedSeed & (16777215L)) / (1.6777215E7F) * 2.0F - 1.0F;
   }

   public void uploadParams() {
      if (!this.params.isEmpty() && this.paramsDirty) {
         GpuBuffer buffer = this.ensureBuffer();
         if (buffer != null) {
            try {
               MemoryStack stack = MemoryStack.stackPush();

               try {
                  ByteBuffer packedParams = this.packParams(stack, this.params);
                  RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(0L, packedParams.remaining()), packedParams);
                  this.paramsDirty = false;
               } catch (Throwable uploadFailure) {
                  if (stack != null) {
                     try {
                        stack.close();
                     } catch (Throwable closeFailure) {
                        uploadFailure.addSuppressed(closeFailure);
                     }
                  }

                  throw uploadFailure;
               }

               if (stack != null) {
                  stack.close();
               }
            } catch (RuntimeException error) {
               this.paramsDirty = true;
            }
         }
      }
   }

   private static float clamp(float value, float minimum, float maximum) {
      return Math.max(minimum, Math.min(maximum, value));
   }

   private void submit(DrawContext context, IconPatternSpec spec) {
      if (context != null && spec != null && spec.iconsVisible()) {
         try {
            IconPatternSpec normalizedSpec = this.normalize(spec);
            AtlasTextRenderer.PreparedText preparedText = AtlasTextRenderer.getInstance().prepareText(normalizedSpec.fontName(), normalizedSpec.icon(), normalizedSpec.iconSize());
            if (preparedText.empty()) {
               return;
            }

            Matrix3x2f transform = GuiScale.getScaledMatrices(context);
            GuiRenderState renderState = ((DrawContextAccessor)(Object)context).getGuiRenderState();
            int layerSerial = ((GuiLayerTracker)renderState).getLayerSerial();
            RectHalfIconRenderer.TransformKey transformKey = RectHalfIconRenderer.TransformKey.of(transform);

            for (AtlasTextRenderer.PreparedTextPage page : preparedText.pages()) {
               if (!page.glyphs().isEmpty()) {
                  java.util.List<com.mouseclient.gui.RectHalfIconBatchRenderState.IconQuad> iconQuads = this.buildIconQuads(normalizedSpec, preparedText, page);
                  if (!iconQuads.isEmpty()) {
                     RectHalfIconRenderer.BatchKey batchKey = new RectHalfIconRenderer.BatchKey(renderState, layerSerial, page.textureSetup(), transformKey);
                     RectHalfIconBatchRenderState batch = this.batches.get(batchKey);
                     if (batch == null) {
                        batch = new RectHalfIconBatchRenderState(transform, page.textureSetup());
                        batch.add(normalizedSpec, iconQuads);
                        this.batches.put(batchKey, batch);
                        renderState.addSimpleElement(batch);
                     } else {
                        batch.add(normalizedSpec, iconQuads);
                     }
                  }
               }
            }
         } catch (RuntimeException error) {
            ClientInitializer.logger().warn(("[HalfIconRectangle] Failed to submit icon rectangle"), error);
         }
      }
   }

   private List<RectHalfIconBatchRenderState.IconQuad> buildIconQuads(IconPatternSpec spec, AtlasTextRenderer.PreparedText preparedText, AtlasTextRenderer.PreparedTextPage page) {
      ArrayList quads = new ArrayList(64);
      float stepX = Math.max(spec.spacingX(), Math.max(preparedText.width(), 1.0F));
      float stepY = Math.max(spec.spacingY(), Math.max(preparedText.height(), 1.0F));
      float minimumCenterX = spec.x() + spec.paddingX() + preparedText.width() * (0.5F);
      float minimumCenterY = spec.y() + spec.paddingY() + preparedText.height() * (0.5F);
      float maximumCenterX = spec.x() + spec.width() - spec.paddingX() - preparedText.width() * (0.5F);
      float maximumCenterY = spec.y() + spec.height() - spec.paddingY() - preparedText.height() * (0.5F);
      if (!(maximumCenterX < minimumCenterX) && !(maximumCenterY < minimumCenterY)) {
         int rowCount = Math.min(96, Math.max(1, (int)Math.floor((maximumCenterY - minimumCenterY) / stepY) + 1));
         int columnCount = Math.min(96, Math.max(1, (int)Math.floor((maximumCenterX - minimumCenterX) / stepX) + 1));

         for (int row = 0; row < rowCount; row++) {
            for (int column = 0; column < columnCount; column++) {
               float centerX = minimumCenterX + column * stepX + jitter(spec.seed(), column, row, 1) * spec.jitterX();
               float centerY = minimumCenterY + row * stepY + jitter(spec.seed(), column, row, 2) * spec.jitterY();
               float rotationRadians = (float)Math.toRadians(jitter(spec.seed(), column, row, 3) * spec.rotationMaxDegrees());
               float sin = (float)Math.sin(rotationRadians);
               float cos = (float)Math.cos(rotationRadians);
               float textLeft = centerX - preparedText.width() * (0.5F);
               float textTop = centerY - preparedText.height() * (0.5F);
               int iconColor = multiplyAlpha(spec.iconColor(), 1.0F - clamp((centerY - spec.y()) / Math.max(spec.height(), 1.0F), 0.0F, 1.0F) * (0.92F));

               for (AtlasTextRenderer.PreparedGlyph glyph : page.glyphs()) {
                  float left = textLeft + glyph.left();
                  float top = textTop + glyph.top();
                  float right = textLeft + glyph.right();
                  float bottom = textTop + glyph.bottom();
                  RectHalfIconRenderer.RotatedPoint topLeft = rotate(left, top, centerX, centerY, sin, cos);
                  RectHalfIconRenderer.RotatedPoint bottomLeft = rotate(left, bottom, centerX, centerY, sin, cos);
                  RectHalfIconRenderer.RotatedPoint bottomRight = rotate(right, bottom, centerX, centerY, sin, cos);
                  RectHalfIconRenderer.RotatedPoint topRight = rotate(right, top, centerX, centerY, sin, cos);
                  quads.add(
                     new RectHalfIconBatchRenderState.IconQuad(
                        topLeft.x(),
                        topLeft.y(),
                        glyph.uMin(),
                        glyph.vMin(),
                        bottomLeft.x(),
                        bottomLeft.y(),
                        glyph.uMin(),
                        glyph.vMax(),
                        bottomRight.x(),
                        bottomRight.y(),
                        glyph.uMax(),
                        glyph.vMax(),
                        topRight.x(),
                        topRight.y(),
                        glyph.uMax(),
                        glyph.vMin(),
                        iconColor
                     )
                  );
               }
            }
         }

         return quads;
      } else {
         return List.of();
      }
   }

   private ByteBuffer packParams(MemoryStack stack, List<IconPatternSpec> specs) {
      ByteBuffer packedParams = stack.calloc(Math.min(12288, specs.size() * 3 * 4 * 4));

      for (int specIndex = 0; specIndex < specs.size(); specIndex++) {
         IconPatternSpec spec = (IconPatternSpec)specs.get(specIndex);
         int byteOffset = specIndex * 3 * 4 * 4;
         packedParams.putFloat(byteOffset, spec.x());
         packedParams.putFloat(byteOffset + 4, spec.y());
         packedParams.putFloat(byteOffset + 8, spec.width());
         packedParams.putFloat(byteOffset + 12, spec.height());
         packedParams.putFloat(byteOffset + 16, spec.radiusTopLeft());
         packedParams.putFloat(byteOffset + 20, spec.radiusTopRight());
         packedParams.putFloat(byteOffset + 24, spec.radiusBottomRight());
         packedParams.putFloat(byteOffset + 28, spec.radiusBottomLeft());
         packedParams.putFloat(byteOffset + 32, spec.smoothness());
         packedParams.putFloat(byteOffset + 36, 0.0F);
         packedParams.putFloat(byteOffset + 40, 0.0F);
         packedParams.putFloat(byteOffset + 44, 0.0F);
      }

      packedParams.position(0);
      return packedParams;
   }

   private static RectHalfIconRenderer.RotatedPoint rotate(float x, float y, float originX, float originY, float sin, float cos) {
      float deltaX = x - originX;
      float deltaY = y - originY;
      return new RectHalfIconRenderer.RotatedPoint(originX + deltaX * cos - deltaY * sin, originY + deltaX * sin + deltaY * cos);
   }

   private IconPatternSpec normalize(IconPatternSpec spec) {
      float maximumRadius = Math.max(0.0F, Math.min(spec.width(), spec.height()) * (0.5F));
      float iconSize = Math.max(spec.iconSize(), 1.0F);
      return new IconPatternSpec(
         spec.x(),
         spec.y(),
         spec.width(),
         spec.height(),
         clamp(spec.radiusTopLeft(), 0.0F, maximumRadius),
         clamp(spec.radiusTopRight(), 0.0F, maximumRadius),
         clamp(spec.radiusBottomRight(), 0.0F, maximumRadius),
         clamp(spec.radiusBottomLeft(), 0.0F, maximumRadius),
         spec.colorTopLeft(),
         spec.colorTopRight(),
         spec.colorBottomRight(),
         spec.colorBottomLeft(),
         Math.max(spec.smoothness(), 0.0F),
         spec.fontName(),
         spec.icon(),
         iconSize,
         spec.iconColor(),
         Math.max(spec.spacingX(), iconSize),
         Math.max(spec.spacingY(), iconSize),
         Math.max(spec.paddingX(), 0.0F),
         Math.max(spec.paddingY(), 0.0F),
         Math.max(spec.jitterX(), 0.0F),
         Math.max(spec.jitterY(), 0.0F),
         clamp(spec.rotationMaxDegrees(), 0.0F, (90.0F)),
         spec.seed()
      );
   }

   private void releaseBuffer() {
      if (this.paramsBuffer != null) {
         this.paramsBuffer.close();
         this.paramsBuffer = null;
      }
   }

   public static RectHalfIconRenderer getInstance() {
      RectHalfIconRenderer instance = INSTANCE;
      if (instance == null) {
         synchronized (RectHalfIconRenderer.class) {
            instance = INSTANCE;
            if (instance == null) {
               instance = new RectHalfIconRenderer();
               INSTANCE = instance;
            }
         }
      }

      return instance;
   }

   private GpuBuffer getUploadedBuffer() {
      if (!this.paramsDirty && this.paramsBuffer != null) {
         return this.paramsBuffer;
      }

      this.uploadParams();
      if (!this.paramsDirty && this.paramsBuffer != null) {
         return this.paramsBuffer;
      }

      this.releaseBuffer();
      this.uploadParams();
      return this.paramsBuffer;
   }

   public int allocateParams(IconPatternSpec spec) {
      int parameterIndex = this.params.size();
      if (parameterIndex == 256) {
         return -1;
      }

      this.params.add(spec);
      this.paramsDirty = true;
      return parameterIndex;
   }

   public void bindUniforms(RenderPass renderPass) {
      if (renderPass != null && !this.params.isEmpty()) {
         GpuBuffer buffer = this.getUploadedBuffer();
         if (buffer != null) {
            renderPass.setUniform(("RectHalfIconParamsArray"), buffer);
         }
      }
   }

   private static Identifier id(String path) {
      return Identifier.of(("mouseclient"), path);
   }

   public boolean usesPipeline(RenderPipeline pipeline) {
      return pipeline == renderPipeline;
   }

   public void render(IconPatternSpec spec) {
      this.submit(this.drawContext, spec);
   }

   public static void closeInstance() {
      RectHalfIconRenderer instance = INSTANCE;
      if (instance != null) {
         instance.close();
         INSTANCE = null;
      }
   }

   @Override
   public void close() {
      this.params.clear();
      this.batches.clear();
      this.drawContext = null;
      this.releaseBuffer();
   }

   private GpuBuffer ensureBuffer() {
      if (this.paramsBuffer != null && !this.paramsBuffer.isClosed() && this.paramsBuffer.size() >= (12288L)) {
         return this.paramsBuffer;
      }

      this.releaseBuffer();

      try {
         this.paramsBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_rect_half_icon_params"), 136, (12288L));
         return this.paramsBuffer;
      } catch (RuntimeException error) {
         return null;
      }
   }

   private RectHalfIconRenderer() {
   }

   public void render(DrawContext context, IconPatternSpec spec) {
      this.begin(context);
      this.render(spec);
      this.end();
   }

   public void clearParams() {
      this.params.clear();
      this.paramsDirty = false;
   }

   public void begin(DrawContext drawContext) {
      if (this.drawContext != drawContext) {
         this.batches.clear();
      }

      this.drawContext = drawContext;
   }

   public void end() {
      this.drawContext = null;
      this.batches.clear();
   }

   private record BatchKey(GuiRenderState state, int layerSerial, TextureSetup textureSetup, RectHalfIconRenderer.TransformKey pose) {
   }

   private record RotatedPoint(float x, float y) {
   }

   private record TransformKey(float m00, float m01, float m10, float m11, float m20, float m21) {
      public static RectHalfIconRenderer.TransformKey of(Matrix3x2f transform) {
         return new RectHalfIconRenderer.TransformKey(transform.m00(), transform.m01(), transform.m10(), transform.m11(), transform.m20(), transform.m21());
      }
   }
}
