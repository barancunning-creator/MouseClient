package com.mouseclient.render.pipeline;

import com.mouseclient.gui.ScissorStack;
import com.mouseclient.gui.GlowRenderState;
import com.mouseclient.internal.GuiScale;
import com.mouseclient.internal.PaletteTransition;
import com.mouseclient.mixin.DrawContextAccessor;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.RenderSystem.ShapeIndexBuffer;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

public final class GuiGlowRenderer implements AutoCloseable {

   private static volatile GuiGlowRenderer INSTANCE;
   public static final RenderPipeline COMPOSITE_PIPELINE = RenderPipeline.builder()
      .withLocation(id(GuiGlowRenderer.COMPOSITE_PIPELINE_PATH))
      .withVertexShader(id(("ui/glow/glow_composite")))
      .withFragmentShader(id(("ui/glow/glow_composite")))
      .withVertexFormat(VertexFormats.POSITION_COLOR_LINE_WIDTH, DrawMode.QUADS)
      .withBlend(BlendFunction.LIGHTNING)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withCull(false)
      .withSampler(("Sampler0"))
      .withUniform(("DynamicTransforms"), UniformType.UNIFORM_BUFFER)
      .withUniform(("Projection"), UniformType.UNIFORM_BUFFER)
      .withUniform(("GlowParamsArray"), UniformType.UNIFORM_BUFFER)
      .build();
   private static final RenderPipeline SHAPE_PIPELINE = RenderPipeline.builder()
      .withLocation(id(("pipeline/glow_shape")))
      .withVertexShader(id(("ui/glow/glow_shape")))
      .withFragmentShader(id(("ui/glow/glow_shape")))
      .withVertexFormat(VertexFormats.POSITION, DrawMode.QUADS)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withCull(false)
      .withUniform(("GlowParamsArray"), UniformType.UNIFORM_BUFFER)
      .withUniform(("SplitParams"), UniformType.UNIFORM_BUFFER)
      .build();
   private static final RenderPipeline SOURCE_PIPELINE = RenderPipeline.builder()
      .withLocation(id(("pipeline/glow_source")))
      .withVertexShader(id(GuiGlowRenderer.SOURCE_VERTEX_SHADER_PATH))
      .withFragmentShader(id(("ui/glow/glow_shape")))
      .withVertexFormat(VertexFormats.POSITION, DrawMode.QUADS)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withCull(false)
      .withUniform(("GlowParamsArray"), UniformType.UNIFORM_BUFFER)
      .withUniform(("SplitParams"), UniformType.UNIFORM_BUFFER)
      .build();
   private static final RenderPipeline DOWNSAMPLE_PIPELINE = RenderPipeline.builder()
      .withLocation(id(GuiGlowRenderer.DOWNSAMPLE_PIPELINE_PATH))
      .withVertexShader(id(("ui/glow/glow_blur")))
      .withFragmentShader(id(("ui/glow/glow_blur_down")))
      .withVertexFormat(VertexFormats.POSITION, DrawMode.QUADS)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withCull(false)
      .withSampler(("Sampler0"))
      .withUniform(("KawaseParams"), UniformType.UNIFORM_BUFFER)
      .build();
   private static final RenderPipeline UPSAMPLE_PIPELINE = RenderPipeline.builder()
      .withLocation(id(("pipeline/glow_blur_up")))
      .withVertexShader(id(("ui/glow/glow_blur")))
      .withFragmentShader(id(("ui/glow/glow_blur_up")))
      .withVertexFormat(VertexFormats.POSITION, DrawMode.QUADS)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withCull(false)
      .withSampler(GuiGlowRenderer.UPSAMPLE_SAMPLER)
      .withUniform(("KawaseParams"), UniformType.UNIFORM_BUFFER)
      .build();
   private static final RenderPipeline CUTOUT_PIPELINE = RenderPipeline.builder()
      .withLocation(id(("pipeline/glow_cutout")))
      .withVertexShader(id(("ui/glow/glow_blur")))
      .withFragmentShader(id(("ui/glow/glow_cutout")))
      .withVertexFormat(VertexFormats.POSITION, DrawMode.QUADS)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withCull(false)
      .withSampler(("Sampler0"))
      .withSampler(("Sampler1"))
      .withUniform(GuiGlowRenderer.KAWASE_PARAMS_UNIFORM, UniformType.UNIFORM_BUFFER)
      .build();
   private final List<SimpleFramebuffer[]> downsampleChains = new ArrayList<>(224);
   private final List<SimpleFramebuffer[]> upsampleChains = new ArrayList<>(224);
   private final List<SimpleFramebuffer> shapeFramebuffers = new ArrayList<>(224);
   private final List<SimpleFramebuffer> blurredFramebuffers = new ArrayList<>(224);
   private final List<SimpleFramebuffer> resultFramebuffers = new ArrayList<>(224);
   private int retainedAtlasHeight;
   private long atlasHeightUpdatedAtNanos;
   private final List<GuiGlowRenderer.GlowSubmission> submissions = new ArrayList<>(32);
   private final List<Glow> compositeGlows = new ArrayList<>(32);
   private final List<GlowAtlasCapture> compositeCaptures = new ArrayList<>(32);
   private int[] atlasX = new int[224];
   private int[] atlasY = new int[224];
   private int[] atlasWidths = new int[224];
   private int[] atlasHeights = new int[224];
   private DrawContext drawContext;
   private GpuBuffer compositeParams;
   private GpuBuffer kawaseParams;
   private GpuBuffer screenQuad;
   private GpuBuffer sourceVertices;
   private GpuBuffer sourceParams;
   private ByteBuffer vertexScratchBuffer;
   private ByteBuffer paramsScratchBuffer;
   private boolean paramsDirty = true;
   private boolean unusedFrameFlag;
   private final GlowAtlasCapture unusedCapture = new GlowAtlasCapture();
   
   private static final long ATLAS_RETENTION_NANOS = 3000000000L;
   private static final float BLUR_RADIUS_SCALE = 20.0F;

   private static final float MIN_UV_SIZE = 1.0E-6F;

   private static final String GLOW_PARAMS_UNIFORM = "GlowParamsArray";

   private static final String SOURCE_SAMPLER = "Sampler0";
   
   private static final long KAWASE_PARAMS_BYTES = 48L;

   private static final float COLOR_CHANNEL_MAX = 255.0F;

   private static final String KAWASE_BUFFER_LABEL = "mouseclient_glow_kawase_params";

   private static final String SOURCE_PASS_LABEL = "mouseclient_glow_source";
   
   private static final String COMPOSITE_PIPELINE_PATH = "pipeline/glow_composite";

   private static final String SOURCE_VERTEX_SHADER_PATH = "ui/glow/glow_source";

   private static final String DOWNSAMPLE_PIPELINE_PATH = "pipeline/glow_blur_down";

   private static final String UPSAMPLE_SAMPLER = "Sampler0";

   private static final String KAWASE_PARAMS_UNIFORM = "KawaseParams";

   public void beginFrame() {
      this.compositeGlows.clear();
      this.compositeCaptures.clear();
      this.paramsDirty = false;
      this.unusedFrameFlag = false;
   }

   private ByteBuffer vertexScratch(int requiredBytes) {
      if (this.vertexScratchBuffer == null || this.vertexScratchBuffer.capacity() < requiredBytes) {
         if (this.vertexScratchBuffer != null) {
            MemoryUtil.memFree(this.vertexScratchBuffer);
         }

         this.vertexScratchBuffer = MemoryUtil.memAlloc(Math.max(requiredBytes, 64 * VertexFormats.POSITION.getVertexSize()));
      }

      this.vertexScratchBuffer.clear();
      return this.vertexScratchBuffer;
   }

   public void begin(DrawContext drawContext) {
      this.drawContext = drawContext;
   }

   @Override
   public void close() {
      this.compositeGlows.clear();
      this.drawContext = null;
      this.retainedAtlasHeight = 0;
      this.releaseCompositeParamsBuffer();
      this.releaseKawaseParamsBuffer();
      if (this.screenQuad != null) {
         this.screenQuad.close();
         this.screenQuad = null;
      }

      if (this.sourceVertices != null) {
         this.sourceVertices.close();
         this.sourceVertices = null;
      }

      if (this.sourceParams != null) {
         this.sourceParams.close();
         this.sourceParams = null;
      }

      if (this.vertexScratchBuffer != null) {
         MemoryUtil.memFree(this.vertexScratchBuffer);
         this.vertexScratchBuffer = null;
      }

      if (this.paramsScratchBuffer != null) {
         MemoryUtil.memFree(this.paramsScratchBuffer);
         this.paramsScratchBuffer = null;
      }

      for (SimpleFramebuffer framebuffer : this.shapeFramebuffers) {
         if (framebuffer != null) {
            framebuffer.delete();
         }
      }

      for (SimpleFramebuffer blurredFramebuffer : this.blurredFramebuffers) {
         if (blurredFramebuffer != null) {
            blurredFramebuffer.delete();
         }
      }

      for (SimpleFramebuffer resultFramebuffer : this.resultFramebuffers) {
         if (resultFramebuffer != null) {
            resultFramebuffer.delete();
         }
      }

      this.shapeFramebuffers.clear();
      this.blurredFramebuffers.clear();
      this.resultFramebuffers.clear();
      deleteBlurChains(this.downsampleChains);
      deleteBlurChains(this.upsampleChains);
   }

   private GpuBuffer ensureCompositeParamsBuffer() {
      if (this.compositeParams != null && !this.compositeParams.isClosed() && this.compositeParams.size() >= (17920L)) {
         return this.compositeParams;
      }

      this.releaseCompositeParamsBuffer();

      try {
         this.compositeParams = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_glow_params"), 136, (17920L));
         return this.compositeParams;
      } catch (RuntimeException error) {
         return null;
      }
   }

   private static void writePosition(ByteBuffer data, int byteOffset, float x, float y) {
      data.putFloat(byteOffset, x);
      data.putFloat(byteOffset + 4, y);
      data.putFloat(byteOffset + 8, 0.0F);
   }

   private SimpleFramebuffer blurredFramebuffer(int poolIndex, int width, int height) {
      while (this.blurredFramebuffers.size() <= poolIndex) {
         this.blurredFramebuffers.add(null);
      }

      SimpleFramebuffer framebuffer = this.ensureFramebuffer(this.blurredFramebuffers.get(poolIndex), "mouseclient_glow_blur_" + poolIndex, width, height);
      this.blurredFramebuffers.set(poolIndex, framebuffer);
      return framebuffer;
   }

   public void renderInContext(DrawContext context, Glow glow) {
      this.begin(context);
      this.render(glow);
      this.end();
   }

   private SimpleFramebuffer shapeFramebuffer(int poolIndex, int width, int height) {
      while (this.shapeFramebuffers.size() <= poolIndex) {
         this.shapeFramebuffers.add(null);
      }

      SimpleFramebuffer framebuffer = this.ensureFramebuffer(this.shapeFramebuffers.get(poolIndex), "mouseclient_glow_shape_" + poolIndex, width, height);
      this.shapeFramebuffers.set(poolIndex, framebuffer);
      return framebuffer;
   }

   private void submit(DrawContext context, Glow glow) {
      if (context != null && glow != null && glow.visible()) {
         try {
            Matrix3x2f transform = GuiScale.getScaledMatrices(context);
            GlowAtlasCapture atlasCapture = new GlowAtlasCapture();
            if (this.submissions.size() < 224) {
               atlasCapture.submissionIndex = this.submissions.size();
               this.submissions.add(new GuiGlowRenderer.GlowSubmission(glow, atlasCapture, transform));
            }

            ((DrawContextAccessor)(Object)context).getGuiRenderState().addSimpleElement(new GlowRenderState(transform, glow, ScissorStack.current(), atlasCapture));
         } catch (RuntimeException error) {
         }
      }
   }

   private int packAtlas(int width) {
      int submissionCount = this.submissions.size();
      if (this.atlasX.length < submissionCount) {
         this.atlasX = new int[submissionCount];
         this.atlasY = new int[submissionCount];
         this.atlasWidths = new int[submissionCount];
         this.atlasHeights = new int[submissionCount];
      }

      float maxPadding = (8.0F);

      for (int paddingIndex = 0; paddingIndex < submissionCount; paddingIndex++) {
         maxPadding = Math.max(maxPadding, this.submissions.get(paddingIndex).glow().effectivePad());
      }

      int padding = Math.max(8, Math.round(maxPadding * 1.0F));
      int cursorX = padding;
      int cursorY = padding;
      int rowHeight = 0;

      for (int submissionIndex = 0; submissionIndex < submissionCount; submissionIndex++) {
         Glow glow = this.submissions.get(submissionIndex).glow();
         float glowPadding = glow.effectivePad();
         int paddedWidth = Math.max(Math.round((glow.width() + glowPadding * 2.0F) * 1.0F), 1);
         int paddedHeight = Math.max(Math.round((glow.height() + glowPadding * 2.0F) * 1.0F), 1);
         if (cursorX + paddedWidth + padding > width) {
            cursorX = padding;
            cursorY += rowHeight + padding;
            rowHeight = 0;
         }

         if (paddedWidth + padding * 2 <= width && cursorY + paddedHeight + padding <= 2048) {
            this.atlasX[submissionIndex] = cursorX;
            this.atlasY[submissionIndex] = cursorY;
            this.atlasWidths[submissionIndex] = paddedWidth;
            this.atlasHeights[submissionIndex] = paddedHeight;
            cursorX += paddedWidth + padding;
            rowHeight = Math.max(rowHeight, paddedHeight);
         } else {
            this.atlasWidths[submissionIndex] = 0;
            this.atlasHeights[submissionIndex] = 0;
         }
      }

      return cursorY + rowHeight + padding;
   }

   private void renderAtlasSources(SimpleFramebuffer framebuffer, int atlasWidth, int atlasHeight) {
      int submissionCount = this.submissions.size();
      if (framebuffer != null && submissionCount != 0) {
         int uniformAlignment = Math.max(1, RenderSystem.getDevice().getUniformOffsetAlignment());
         int uniformStride = (1248 + uniformAlignment - 1) / uniformAlignment * uniformAlignment;
         int quadBytes = 4 * VertexFormats.POSITION.getVertexSize();
         ByteBuffer vertexData = this.vertexScratch(submissionCount * quadBytes);
         ByteBuffer paramsData = this.paramsScratch(submissionCount * uniformStride);
         int drawCount = 0;

         for (int submissionIndex = 0; submissionIndex < submissionCount; submissionIndex++) {
            GuiGlowRenderer.GlowSubmission submission = this.submissions.get(submissionIndex);
            GlowAtlasCapture atlasCapture = submission.capture();
            if (this.atlasWidths[submissionIndex] > 0 && this.atlasHeights[submissionIndex] > 0) {
               Glow glow = submission.glow();
               int x = this.atlasX[submissionIndex];
               int y = this.atlasY[submissionIndex];
               float padding = glow.effectivePad();
               float paddedWidth = Math.min((glow.width() + padding * 2.0F) * 1.0F, this.atlasWidths[submissionIndex]);
               float paddedHeight = Math.min((glow.height() + padding * 2.0F) * 1.0F, this.atlasHeights[submissionIndex]);
               atlasCapture.u = (float)x / atlasWidth;
               atlasCapture.v = (float)y / atlasHeight;
               atlasCapture.uSize = Math.max(paddedWidth / atlasWidth, (1.0E-6F));
               atlasCapture.vSize = Math.max(paddedHeight / atlasHeight, MIN_UV_SIZE);
               float minClipX = (float)x / atlasWidth * 2.0F - 1.0F;
               float minClipY = (float)y / atlasHeight * 2.0F - 1.0F;
               float maxClipX = (x + paddedWidth) / atlasWidth * 2.0F - 1.0F;
               float maxClipY = (y + paddedHeight) / atlasHeight * 2.0F - 1.0F;
               int vertexOffset = drawCount * quadBytes;
               writePosition(vertexData, vertexOffset, minClipX, minClipY);
               writePosition(vertexData, vertexOffset + 12, minClipX, maxClipY);
               writePosition(vertexData, vertexOffset + 24, maxClipX, maxClipY);
               writePosition(vertexData, vertexOffset + 36, maxClipX, minClipY);
               ByteBuffer glowData = paramsData.slice(drawCount * uniformStride, 1248).order(paramsData.order());
               MemoryUtil.memSet(MemoryUtil.memAddress(glowData), 0, (1248L));
               writeGlowParams(glowData, 0, glow, 0.0F, 0.0F, 1.0F, 1.0F);
               glowData.putFloat(48, glow.splitIndex());
               writeSpans(glowData, glow);
               writePalette(glowData);
               drawCount++;
            } else {
               atlasCapture.u = 0.0F;
               atlasCapture.v = 0.0F;
               atlasCapture.uSize = 0.0F;
               atlasCapture.vSize = 0.0F;
            }
         }

         if (drawCount != 0) {
            int vertexBytes = drawCount * quadBytes;
            int paramsBytes = drawCount * uniformStride;

            try {
               CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
               GpuBuffer buffer = this.ensureSourceVertexBuffer(vertexBytes);
               GpuBuffer paramsBuffer = this.ensureSourceParamsBuffer(paramsBytes);
               vertexData.position(0).limit(vertexBytes);
               encoder.writeToBuffer(buffer.slice(0L, vertexBytes), vertexData);
               paramsData.position(0).limit(paramsBytes);
               encoder.writeToBuffer(paramsBuffer.slice(0L, paramsBytes), paramsData);
               vertexData.clear();
               paramsData.clear();
               ShapeIndexBuffer indexBuffer = RenderSystem.getSequentialBuffer(DrawMode.QUADS);
               GpuBuffer indices = indexBuffer.getIndexBuffer(6 * drawCount);

               try (RenderPass renderPass = encoder.createRenderPass(() -> SOURCE_PASS_LABEL, framebuffer.getColorAttachmentView(), OptionalInt.empty(), null, OptionalDouble.empty())) {
                  renderPass.setPipeline(SOURCE_PIPELINE);
                  GpuBuffer splitBuffer = GuiShapeSplitBuffer.buffer();
                  if (splitBuffer != null) {
                     renderPass.setUniform(("SplitParams"), splitBuffer);
                  }

                  renderPass.setVertexBuffer(0, buffer);
                  renderPass.setIndexBuffer(indices, indexBuffer.getIndexType());

                  for (int drawIndex = 0; drawIndex < drawCount; drawIndex++) {
                     renderPass.setUniform(("GlowParamsArray"), paramsBuffer.slice((long)drawIndex * uniformStride, (1248L)));
                     renderPass.drawIndexed(0, drawIndex * 6, 6, 1);
                  }
               }
            } catch (RuntimeException error) {
               vertexData.clear();
               paramsData.clear();
            }
         }
      }
   }

   private void releaseCompositeParamsBuffer() {
      if (this.compositeParams != null) {
         this.compositeParams.close();
         this.compositeParams = null;
      }
   }

   private BuiltBuffer buildScreenQuad(BufferAllocator allocator) {
      BufferBuilder builder = new BufferBuilder(allocator, DrawMode.QUADS, VertexFormats.POSITION);
      builder.vertex((-1.0F), (-1.0F), 0.0F);
      builder.vertex((-1.0F), 1.0F, 0.0F);
      builder.vertex(1.0F, 1.0F, 0.0F);
      builder.vertex(1.0F, (-1.0F), 0.0F);
      return builder.end();
   }

   private GpuBuffer ensureKawaseParamsBuffer() {
      if (this.kawaseParams != null && !this.kawaseParams.isClosed() && this.kawaseParams.size() >= (48L)) {
         return this.kawaseParams;
      }

      this.releaseKawaseParamsBuffer();

      try {
         this.kawaseParams = RenderSystem.getDevice().createBuffer(() -> KAWASE_BUFFER_LABEL, 136, (48L));
         return this.kawaseParams;
      } catch (RuntimeException error) {
         return null;
      }
   }

   private static float clamp(float value, float min, float max) {
      return Math.max(min, Math.min(max, value));
   }

   private static Identifier id(String path) {
      return Identifier.of(("mouseclient"), path);
   }

   private GuiGlowRenderer() {
   }

   public int allocateParams(Glow glow, GlowAtlasCapture atlasCapture) {
      int index = this.compositeGlows.size();
      if (index >= 224) {
         return -1;
      }

      this.compositeGlows.add(glow);
      this.compositeCaptures.add(atlasCapture);
      this.paramsDirty = true;
      return index;
   }

   private ByteBuffer packCompositeParams(MemoryStack stack) {
      int entryCount = Math.max(1, this.compositeGlows.size());
      int byteCount = entryCount * 5 * 4 * 4;
      ByteBuffer data = stack.calloc(byteCount);

      for (int index = 0; index < this.compositeGlows.size(); index++) {
         Glow glow = this.compositeGlows.get(index);
         GlowAtlasCapture atlasCapture = index < this.compositeCaptures.size() ? this.compositeCaptures.get(index) : null;
         float u = atlasCapture != null ? atlasCapture.u : 0.0F;
         float v = atlasCapture != null ? atlasCapture.v : 0.0F;
         float uSize = atlasCapture != null ? atlasCapture.uSize : 1.0F;
         float vSize = atlasCapture != null ? atlasCapture.vSize : 1.0F;
         writeGlowParams(data, index, glow, u, v, uSize, vSize);
      }

      data.position(0);
      return data;
   }

   private void clearFramebuffer(SimpleFramebuffer framebuffer) {
      if (framebuffer != null) {
         CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
         RenderPass renderPass = encoder.createRenderPass(() -> ("mouseclient_glow_clear"), framebuffer.getColorAttachmentView(), OptionalInt.of(0), null, OptionalDouble.empty());
         if (renderPass != null) {
            renderPass.close();
         }
      }
   }

   public void prepareAtlas() {
      if (!this.submissions.isEmpty()) {
         MinecraftClient client = MinecraftClient.getInstance();
         if (client != null && client.getWindow() != null) {
            int requiredHeight = this.packAtlas(2048);
            short atlasWidth = 2048;
            int roundedHeight = Math.max(256, Math.min(2048, (requiredHeight + 256 - 1) / 256 * 256));
            long nowNanos = System.nanoTime();
            if (roundedHeight >= this.retainedAtlasHeight) {
               this.retainedAtlasHeight = roundedHeight;
               this.atlasHeightUpdatedAtNanos = nowNanos;
            } else if (nowNanos - this.atlasHeightUpdatedAtNanos > ATLAS_RETENTION_NANOS) {
               this.retainedAtlasHeight = roundedHeight;
               this.atlasHeightUpdatedAtNanos = nowNanos;
            }

            int atlasHeight = this.retainedAtlasHeight;
            SimpleFramebuffer framebuffer = this.shapeFramebuffer(0, atlasWidth, atlasHeight);
            SimpleFramebuffer blurredFramebuffer = this.blurredFramebuffer(0, atlasWidth, atlasHeight);
            SimpleFramebuffer outputFramebuffer = this.resultFramebuffer(0, atlasWidth, atlasHeight);
            if (framebuffer != null && blurredFramebuffer != null && outputFramebuffer != null) {
               GpuSampler sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
               this.clearFramebuffer(framebuffer);
               float maxBlurRadius = 0.0F;

               for (int submissionIndex = 0; submissionIndex < this.submissions.size(); submissionIndex++) {
                  maxBlurRadius = Math.max(maxBlurRadius, this.submissions.get(submissionIndex).glow().blurRadius());
               }

               this.renderAtlasSources(framebuffer, atlasWidth, atlasHeight);
               byte levels = 3;
               float sampleOffset = clamp(maxBlurRadius / BLUR_RADIUS_SCALE, (0.5F), (4.0F)) * 1.0F;
               this.ensureBlurChains(0, atlasWidth, atlasHeight, levels);
               SimpleFramebuffer[] downsampleChain = this.downsampleChains.get(0);
               SimpleFramebuffer[] upsampleChain = this.upsampleChains.get(0);
               GpuTextureView textureView = framebuffer.getColorAttachmentView();
               int sourceWidth = framebuffer.textureWidth;
               int sourceHeight = framebuffer.textureHeight;

               for (int downsampleLevel = 0; downsampleLevel < levels; downsampleLevel++) {
                  this.renderKawasePass(DOWNSAMPLE_PIPELINE, textureView, sourceWidth, sourceHeight, downsampleChain[downsampleLevel], sampleOffset, 0.0F, 0.0F, 1.0F, 1.0F, sampler);
                  textureView = downsampleChain[downsampleLevel].getColorAttachmentView();
                  sourceWidth = downsampleChain[downsampleLevel].textureWidth;
                  sourceHeight = downsampleChain[downsampleLevel].textureHeight;
               }

               for (int upsampleLevel = levels - 2; upsampleLevel >= 0; upsampleLevel--) {
                  this.renderKawasePass(UPSAMPLE_PIPELINE, textureView, sourceWidth, sourceHeight, upsampleChain[upsampleLevel], sampleOffset, 0.0F, 0.0F, 1.0F, 1.0F, sampler);
                  textureView = upsampleChain[upsampleLevel].getColorAttachmentView();
                  sourceWidth = upsampleChain[upsampleLevel].textureWidth;
                  sourceHeight = upsampleChain[upsampleLevel].textureHeight;
               }

               this.renderKawasePass(UPSAMPLE_PIPELINE, textureView, sourceWidth, sourceHeight, blurredFramebuffer, sampleOffset, 0.0F, 0.0F, 1.0F, 1.0F, sampler);
               this.renderCutout(blurredFramebuffer, framebuffer, outputFramebuffer, sampler);
               TextureSetup textureSetup = TextureSetup.of(outputFramebuffer.getColorAttachmentView(), sampler);

               for (int captureIndex = 0; captureIndex < this.submissions.size(); captureIndex++) {
                  this.submissions.get(captureIndex).capture().textureSetup = textureSetup;
               }

               this.submissions.clear();
            } else {
               this.submissions.clear();
            }
         } else {
            this.submissions.clear();
         }
      }
   }

   private GpuBuffer ensureSourceVertexBuffer(int requiredBytes) {
      if (this.sourceVertices == null || this.sourceVertices.isClosed() || this.sourceVertices.size() < requiredBytes) {
         if (this.sourceVertices != null) {
            this.sourceVertices.close();
         }

         int capacity = Math.max(requiredBytes, 64 * VertexFormats.POSITION.getVertexSize());
         capacity = Integer.highestOneBit(capacity - 1) << 1;
         this.sourceVertices = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_glow_src_quads"), 40, capacity);
      }

      return this.sourceVertices;
   }

   public static void closeInstance() {
      GuiGlowRenderer renderer = INSTANCE;
      if (renderer != null) {
         renderer.close();
         INSTANCE = null;
      }
   }

   private void ensureBlurChains(int poolIndex, int width, int height, int levels) {
      while (this.downsampleChains.size() <= poolIndex) {
         this.downsampleChains.add(new SimpleFramebuffer[4]);
         this.upsampleChains.add(new SimpleFramebuffer[4]);
      }

      SimpleFramebuffer[] downsampleChain = this.downsampleChains.get(poolIndex);
      SimpleFramebuffer[] upsampleChain = this.upsampleChains.get(poolIndex);
      int levelWidth = Math.max(width, 1);
      int levelHeight = Math.max(height, 1);

      for (int level = 0; level < levels; level++) {
         downsampleChain[level] = this.ensureFramebuffer(downsampleChain[level], "mouseclient_glow_down_" + poolIndex + "_" + level, levelWidth, levelHeight);
         if (level < levels - 1) {
            upsampleChain[level] = this.ensureFramebuffer(upsampleChain[level], "mouseclient_glow_up_" + poolIndex + "_" + level, levelWidth, levelHeight);
         }

         levelWidth = Math.max(levelWidth / 2, 1);
         levelHeight = Math.max(levelHeight / 2, 1);
      }
   }

   private static void deleteBlurChains(List<SimpleFramebuffer[]> chains) {
      for (SimpleFramebuffer[] chain : chains) {
         for (int level = 0; level < chain.length; level++) {
            if (chain[level] != null) {
               chain[level].delete();
               chain[level] = null;
            }
         }
      }

      chains.clear();
   }

   private static void writePalette(ByteBuffer data) {
      short paletteOffset = 1120;
      int colorCount = Math.max(1, Math.min(MouseClientClientPalette.colorCount(), 6));
      int[] colors = MouseClientClientPalette.colors();
      data.putFloat(paletteOffset, colorCount);
      data.putFloat(paletteOffset + 4, MouseClientClientPalette.phase());
      data.putFloat(paletteOffset + 8, MouseClientClientPalette.styleMode());
      data.putFloat(paletteOffset + 12, PaletteTransition.getProgress());
      int transitionOffset = paletteOffset + 112;
      data.putFloat(transitionOffset, MouseClientClientPalette.previousStyleMode());
      data.putFloat(transitionOffset + 4, MouseClientClientPalette.cycleMix());

      for (int colorIndex = 0; colorIndex < colorCount && colorIndex < colors.length; colorIndex++) {
         int rgb = colors[colorIndex] & (16777215);
         int colorOffset = paletteOffset + (1 + colorIndex) * 4 * 4;
         data.putFloat(colorOffset, (rgb >>> 16 & 0xFF) / (255.0F));
         data.putFloat(colorOffset + 4, (rgb >>> 8 & 0xFF) / (255.0F));
         data.putFloat(colorOffset + 8, (rgb & 0xFF) / (255.0F));
         data.putFloat(colorOffset + 12, 1.0F);
      }
   }

   private void renderShapeMask(int poolIndex, Glow glow, SimpleFramebuffer framebuffer) {
      GpuBuffer buffer = this.screenQuadBuffer();
      if (buffer != null && framebuffer != null) {
         GpuBuffer paramsBuffer = null;

         try {
            MemoryStack stack = MemoryStack.stackPush();

            try {
               ByteBuffer data = stack.calloc(1248);
               writeGlowParams(data, 0, glow, 0.0F, 0.0F, 1.0F, 1.0F);
               data.putFloat(48, glow.splitIndex());
               writeSpans(data, glow);
               writePalette(data);
               data.position(0);
               paramsBuffer = RenderSystem.getDevice().createBuffer(() -> "mouseclient_glow_shape_params_" + poolIndex, 136, data);
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
            return;
         }

         try {
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            ShapeIndexBuffer indexBuffer = RenderSystem.getSequentialBuffer(DrawMode.QUADS);
            GpuBuffer indices = indexBuffer.getIndexBuffer(6);

            try (RenderPass renderPass = encoder.createRenderPass(
                  () -> "mouseclient_glow_shape_" + poolIndex, framebuffer.getColorAttachmentView(), OptionalInt.of(0), null, OptionalDouble.empty()
               )) {
               renderPass.setPipeline(SHAPE_PIPELINE);
               renderPass.setUniform(GLOW_PARAMS_UNIFORM, paramsBuffer);
               GpuBuffer splitBuffer = GuiShapeSplitBuffer.buffer();
               if (splitBuffer != null) {
                  renderPass.setUniform(("SplitParams"), splitBuffer);
               }

               renderPass.setVertexBuffer(0, buffer);
               renderPass.setIndexBuffer(indices, indexBuffer.getIndexType());
               renderPass.drawIndexed(0, 0, 6, 1);
            }
         } finally {
            paramsBuffer.close();
         }
      }
   }

   private void releaseKawaseParamsBuffer() {
      if (this.kawaseParams != null) {
         this.kawaseParams.close();
         this.kawaseParams = null;
      }
   }

   public void end() {
      this.drawContext = null;
   }

   private void renderKawasePass(
      RenderPipeline pipeline,
      GpuTextureView textureView,
      int sourceWidth,
      int sourceHeight,
      SimpleFramebuffer framebuffer,
      float sampleOffset,
      float u,
      float v,
      float uSize,
      float vSize,
      GpuSampler sampler
   ) {
      if (textureView != null && framebuffer != null) {
         GpuBuffer buffer = this.screenQuadBuffer();
         if (buffer != null) {
            GpuBuffer paramsBuffer = this.ensureKawaseParamsBuffer();
            if (paramsBuffer != null) {
               CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
               ShapeIndexBuffer indexBuffer = RenderSystem.getSequentialBuffer(DrawMode.QUADS);
               GpuBuffer indices = indexBuffer.getIndexBuffer(6);
               MemoryStack stack = MemoryStack.stackPush();

               try {
                  ByteBuffer data = stack.calloc(48);
                  data.putFloat(0, u);
                  data.putFloat(4, v);
                  data.putFloat(8, uSize);
                  data.putFloat(12, vSize);
                  data.putFloat(16, sampleOffset / Math.max(sourceWidth, 1));
                  data.putFloat(20, sampleOffset / Math.max(sourceHeight, 1));
                  data.putFloat(32, 0.0F);
                  data.putFloat(36, 0.0F);
                  data.putFloat(40, 0.0F);
                  data.putFloat(44, 0.0F);
                  data.position(0);
                  encoder.writeToBuffer(paramsBuffer.slice(0L, (48L)), data);
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

               try (RenderPass renderPass = encoder.createRenderPass(() -> ("mouseclient_glow_kawase"), framebuffer.getColorAttachmentView(), OptionalInt.of(0), null, OptionalDouble.empty())) {
                  renderPass.setPipeline(pipeline);
                  renderPass.bindTexture(SOURCE_SAMPLER, textureView, sampler);
                  renderPass.setUniform(("KawaseParams"), paramsBuffer);
                  renderPass.setVertexBuffer(0, buffer);
                  renderPass.setIndexBuffer(indices, indexBuffer.getIndexType());
                  renderPass.drawIndexed(0, 0, 6, 1);
               }
            }
         }
      }
   }

   private static void writeSpans(ByteBuffer data, Glow glow) {
      float[] spans = glow.spans();
      int spanCount = glow.spanCount();
      if (spans != null && spanCount > 0) {
         float padding = glow.effectivePad();
         float minRadius = Math.min(Math.min(glow.radiusTopLeft(), glow.radiusTopRight()), Math.min(glow.radiusBottomRight(), glow.radiusBottomLeft()));
         byte metaOffset = 80;
         data.putFloat(metaOffset, Math.min(spanCount, 64));
         data.putFloat(metaOffset + 4, minRadius);
         data.putFloat(metaOffset + 8, glow.leftAligned());
         data.putFloat(metaOffset + 12, glow.bottomAnchored() > (0.5F) ? 0.0F : 1.0F);
         int spansOffset = metaOffset + 16;
         int boundedCount = Math.min(spanCount, 64);
         float paddedHeight = glow.height() + padding * 2.0F;

         for (int spanIndex = 0; spanIndex < boundedCount; spanIndex++) {
            int componentOffset = spanIndex * 4;
            float left = spans[componentOffset] + padding;
            float right = spans[componentOffset + 1] + padding;
            float top = spans[componentOffset + 2] + padding;
            float bottom = spans[componentOffset + 3] + padding;
            float minY = paddedHeight - bottom;
            float maxY = paddedHeight - top;
            int byteOffset = spansOffset + spanIndex * 4 * 4;
            data.putFloat(byteOffset, left);
            data.putFloat(byteOffset + 4, right);
            data.putFloat(byteOffset + 8, minY);
            data.putFloat(byteOffset + 12, maxY);
         }
      }
   }

   public void uploadParams() {
      if (!this.compositeGlows.isEmpty() && this.paramsDirty) {
         GpuBuffer buffer = this.ensureCompositeParamsBuffer();
         if (buffer != null) {
            try {
               MemoryStack stack = MemoryStack.stackPush();

               try {
                  ByteBuffer data = this.packCompositeParams(stack);
                  RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(0L, data.remaining()), data);
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

   public static GuiGlowRenderer getInstance() {
      GuiGlowRenderer renderer = INSTANCE;
      if (renderer == null) {
         synchronized (GuiGlowRenderer.class) {
            renderer = INSTANCE;
            if (renderer == null) {
               renderer = new GuiGlowRenderer();
               INSTANCE = renderer;
            }
         }
      }

      return renderer;
   }

   public boolean usesPipeline(RenderPipeline pipeline) {
      return pipeline == COMPOSITE_PIPELINE;
   }

   private GpuBuffer compositeParamsBuffer() {
      if (!this.paramsDirty && this.compositeParams != null) {
         return this.compositeParams;
      }

      this.uploadParams();
      if (!this.paramsDirty && this.compositeParams != null) {
         return this.compositeParams;
      }

      this.releaseCompositeParamsBuffer();
      MemoryStack stack = MemoryStack.stackPush();

      GpuBuffer buffer;
      try {
         ByteBuffer data = this.packCompositeParams(stack);
         this.compositeParams = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_glow_params"), 128, data);
         this.paramsDirty = false;
         buffer = this.compositeParams;
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

      return buffer;
   }

   private static void writeGlowParams(ByteBuffer data, int index, Glow glow, float u, float v, float uSize, float vSize) {
      int byteOffset = index * 5 * 4 * 4;
      data.putFloat(byteOffset, glow.radiusTopLeft());
      data.putFloat(byteOffset + 4, glow.radiusTopRight());
      data.putFloat(byteOffset + 8, glow.radiusBottomRight());
      data.putFloat(byteOffset + 12, glow.radiusBottomLeft());
      data.putFloat(byteOffset + 16, glow.width());
      data.putFloat(byteOffset + 20, glow.height());
      data.putFloat(byteOffset + 24, glow.effectivePad());
      data.putFloat(byteOffset + 28, 2.0F);
      int color = glow.color();
      data.putFloat(byteOffset + 32, (color >>> 16 & 0xFF) / COLOR_CHANNEL_MAX);
      data.putFloat(byteOffset + 36, (color >>> 8 & 0xFF) / (255.0F));
      data.putFloat(byteOffset + 40, (color & 0xFF) / (255.0F));
      data.putFloat(byteOffset + 44, glow.intensity() * glow.alpha());
      data.putFloat(byteOffset + 48, u);
      data.putFloat(byteOffset + 52, v);
      data.putFloat(byteOffset + 56, uSize);
      data.putFloat(byteOffset + 60, vSize);
      int secondColor = glow.secondColor();
      data.putFloat(byteOffset + 64, (secondColor >>> 16 & 0xFF) / (255.0F));
      data.putFloat(byteOffset + 68, (secondColor >>> 8 & 0xFF) / (255.0F));
      data.putFloat(byteOffset + 72, (secondColor & 0xFF) / (255.0F));
      data.putFloat(byteOffset + 76, glow.colorOffset());
   }

   public void bindUniforms(RenderPass renderPass) {
      if (renderPass != null && !this.compositeGlows.isEmpty()) {
         GpuBuffer buffer = this.compositeParamsBuffer();
         if (buffer != null) {
            renderPass.setUniform(("GlowParamsArray"), buffer);
         }
      }
   }

   private void renderCutout(SimpleFramebuffer blurredFramebuffer, SimpleFramebuffer sourceFramebuffer, SimpleFramebuffer outputFramebuffer, GpuSampler sampler) {
      if (blurredFramebuffer != null && sourceFramebuffer != null && outputFramebuffer != null) {
         GpuBuffer buffer = this.screenQuadBuffer();
         GpuBuffer paramsBuffer = this.ensureKawaseParamsBuffer();
         if (buffer != null && paramsBuffer != null) {
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            ShapeIndexBuffer indexBuffer = RenderSystem.getSequentialBuffer(DrawMode.QUADS);
            GpuBuffer indices = indexBuffer.getIndexBuffer(6);
            MemoryStack stack = MemoryStack.stackPush();

            try {
               ByteBuffer data = stack.calloc(48);
               data.putFloat(0, 0.0F);
               data.putFloat(4, 0.0F);
               data.putFloat(8, 1.0F);
               data.putFloat(12, 1.0F);
               data.position(0);
               encoder.writeToBuffer(paramsBuffer.slice(0L, KAWASE_PARAMS_BYTES), data);
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

            try (RenderPass renderPass = encoder.createRenderPass(() -> ("mouseclient_glow_cutout"), outputFramebuffer.getColorAttachmentView(), OptionalInt.of(0), null, OptionalDouble.empty())) {
               renderPass.setPipeline(CUTOUT_PIPELINE);
               renderPass.bindTexture(("Sampler0"), blurredFramebuffer.getColorAttachmentView(), sampler);
               renderPass.bindTexture(("Sampler1"), sourceFramebuffer.getColorAttachmentView(), sampler);
               renderPass.setUniform(("KawaseParams"), paramsBuffer);
               renderPass.setVertexBuffer(0, buffer);
               renderPass.setIndexBuffer(indices, indexBuffer.getIndexType());
               renderPass.drawIndexed(0, 0, 6, 1);
            }
         }
      }
   }

   public void render(Glow glow) {
      this.submit(this.drawContext, glow);
   }

   private SimpleFramebuffer ensureFramebuffer(SimpleFramebuffer framebuffer, String name, int width, int height) {
      if (framebuffer == null) {
         return new SimpleFramebuffer(name, width, height, false);
      }

      if (framebuffer.textureWidth != width || framebuffer.textureHeight != height) {
         framebuffer.resize(width, height);
      }

      return framebuffer;
   }

   private GpuBuffer ensureSourceParamsBuffer(int requiredBytes) {
      if (this.sourceParams == null || this.sourceParams.isClosed() || this.sourceParams.size() < requiredBytes) {
         if (this.sourceParams != null) {
            this.sourceParams.close();
         }

         int capacity = Integer.highestOneBit(Math.max(requiredBytes, 2) - 1) << 1;
         this.sourceParams = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_glow_src_params"), 136, capacity);
      }

      return this.sourceParams;
   }

   private ByteBuffer paramsScratch(int requiredBytes) {
      if (this.paramsScratchBuffer == null || this.paramsScratchBuffer.capacity() < requiredBytes) {
         if (this.paramsScratchBuffer != null) {
            MemoryUtil.memFree(this.paramsScratchBuffer);
         }

         this.paramsScratchBuffer = MemoryUtil.memAlloc(requiredBytes);
      }

      this.paramsScratchBuffer.clear();
      return this.paramsScratchBuffer;
   }

   private SimpleFramebuffer resultFramebuffer(int poolIndex, int width, int height) {
      while (this.resultFramebuffers.size() <= poolIndex) {
         this.resultFramebuffers.add(null);
      }

      SimpleFramebuffer framebuffer = this.ensureFramebuffer(this.resultFramebuffers.get(poolIndex), "mouseclient_glow_result_" + poolIndex, width, height);
      this.resultFramebuffers.set(poolIndex, framebuffer);
      return framebuffer;
   }

   private GpuBuffer screenQuadBuffer() {
      if (this.screenQuad != null) {
         return this.screenQuad;
      }

      try (
         BufferAllocator allocator = new BufferAllocator(4 * VertexFormats.POSITION.getVertexSize());
         BuiltBuffer meshData = this.buildScreenQuad(allocator);
      ) {
         this.screenQuad = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_glow_fullscreen_quad"), 32, meshData.getBuffer());
      }

      return this.screenQuad;
   }

   private record GlowSubmission(Glow glow, GlowAtlasCapture capture, Matrix3x2f pose) {
   }
}
