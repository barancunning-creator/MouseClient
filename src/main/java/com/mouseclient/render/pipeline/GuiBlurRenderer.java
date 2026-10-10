package com.mouseclient.render.pipeline;

import com.mouseclient.gui.ScissorStack;
import com.mouseclient.gui.BlurRenderState;
import com.mouseclient.internal.GuiScale;
import com.mouseclient.internal.GuiBlurState;
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
import java.util.Arrays;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.Window;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2f;
import org.lwjgl.system.MemoryStack;

public final class GuiBlurRenderer implements AutoCloseable {

   private static volatile GuiBlurRenderer INSTANCE;
   private static float fallbackRed = (0.55F);
   private static float fallbackGreen = (0.65F);
   private static float fallbackBlue = (0.78F);
   private static float screenBlurLightness;
   public static final RenderPipeline COMPOSITE_PIPELINE = RenderPipeline.builder()
      .withLocation(id(("pipeline/batched_blur")))
      .withVertexShader(id(("ui/batched_blur/batched_blur")))
      .withFragmentShader(id(("ui/batched_blur/batched_blur")))
      .withVertexFormat(VertexFormats.POSITION_COLOR_LINE_WIDTH, DrawMode.QUADS)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withCull(false)
      .withSampler(("Sampler0"))
      .withUniform(("DynamicTransforms"), UniformType.UNIFORM_BUFFER)
      .withUniform(("Projection"), UniformType.UNIFORM_BUFFER)
      .withUniform(("BlurParamsArray"), UniformType.UNIFORM_BUFFER)
      .build();
   public static final RenderPipeline DOWNSAMPLE_PIPELINE = RenderPipeline.builder()
      .withLocation(id(("pipeline/kawase_down")))
      .withVertexShader(id(("ui/kawase/down")))
      .withFragmentShader(id(("ui/kawase/down")))
      .withVertexFormat(VertexFormats.POSITION, DrawMode.QUADS)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withCull(false)
      .withSampler(("Sampler0"))
      .withUniform(("KawaseParams"), UniformType.UNIFORM_BUFFER)
      .build();
   public static final RenderPipeline UPSAMPLE_PIPELINE = RenderPipeline.builder()
      .withLocation(id(("pipeline/kawase_up")))
      .withVertexShader(id(("ui/kawase/down")))
      .withFragmentShader(id(("ui/kawase/up")))
      .withVertexFormat(VertexFormats.POSITION, DrawMode.QUADS)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withCull(false)
      .withSampler(("Sampler0"))
      .withUniform(("KawaseParams"), UniformType.UNIFORM_BUFFER)
      .build();
   public static final RenderPipeline GAUSSIAN_PIPELINE = RenderPipeline.builder()
      .withLocation(id(("pipeline/gaussian_blur")))
      .withVertexShader(id(("ui/kawase/down")))
      .withFragmentShader(id(("ui/kawase/gaussian")))
      .withVertexFormat(VertexFormats.POSITION, DrawMode.QUADS)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withCull(false)
      .withSampler(("Sampler0"))
      .withUniform(("KawaseParams"), UniformType.UNIFORM_BUFFER)
      .build();

   private final List<GuiBlurRenderer.BlurRequest> requests = new ArrayList<>(32);
   private final List<GuiBlurRenderer.BlurParamEntry> paramEntries = new ArrayList<>(32);
   private DrawContext drawContext;
   private final GuiBlurRenderer.BlurFramebufferPool resultPool;
   private final GuiBlurRenderer.BlurFramebufferPool scratchPool;
   private final SimpleFramebuffer[] downsampleChain;
   private final SimpleFramebuffer[] upsampleChain;
   private SimpleFramebuffer gaussianScratch;
   private long resultFrameId;
   private long scratchPassId;
   private boolean heavyBeforeBlurLayer;
   private boolean heavyAfterBlurLayer;
   private boolean heavyRefreshPending;
   private long primaryUpdatedAtNanos;
   private long secondaryUpdatedAtNanos;
   private long screenBlurUpdatedAtNanos;
   private long heavyUpdatedAtNanos;
   private SimpleFramebuffer primaryGlobalFramebuffer;
   private boolean primaryAvailable;
   private float primaryRadius;
   private SimpleFramebuffer secondaryGlobalFramebuffer;
   private boolean secondaryAvailable;
   private float secondaryRadius;
   private int globalLayer;
   private SimpleFramebuffer screenBlurFramebuffer;
   private SimpleFramebuffer heavyBlurFramebuffer;
   private SimpleFramebuffer unusedFramebuffer;
   
   private GpuBuffer screenQuad;
   private GpuBuffer compositeParams;
   private GpuBuffer kawaseParams;
   private boolean requestsPrepared;
   private boolean heavyAvailable;
   private boolean useGrayscaleFallback;
   private boolean paramsDirty;
   private int frameBlurIterations;

   private void submit(DrawContext context, BlurSpec spec) {
      if (spec != null && spec.visible()) {
         if (context != null) {
            try {
               BlurSpec clampedSpec = this.clampSpec(spec);
               Matrix3x2f transform = GuiScale.getScaledMatrices(context);
               ScreenRect bounds = ScissorStack.current();
               BlurTextureRegion blurRegion = new BlurTextureRegion();
               this.requests.add(new GuiBlurRenderer.BlurRequest(clampedSpec, transform, bounds, blurRegion, false));
               ((DrawContextAccessor)(Object)context).getGuiRenderState().addSimpleElement(new BlurRenderState(transform, clampedSpec, bounds, blurRegion));
            } catch (RuntimeException error) {
            }
         }
      }
   }

   public void renderHeavyBlur(BlurSpec spec) {
      DrawContext context = this.drawContext;
      if (spec != null && spec.visible() && context != null) {
         try {
            BlurSpec clampedSpec = this.clampSpec(spec);
            Matrix3x2f transform = GuiScale.getScaledMatrices(context);
            ScreenRect bounds = ScissorStack.current();
            BlurTextureRegion blurRegion = new BlurTextureRegion();
            this.requests.add(new GuiBlurRenderer.BlurRequest(clampedSpec, transform, bounds, blurRegion, false, true));
            ((DrawContextAccessor)(Object)context).getGuiRenderState().addSimpleElement(new BlurRenderState(transform, clampedSpec, bounds, blurRegion));
         } catch (RuntimeException error) {
         }
      }
   }

   private static int clampInt(int value, int min, int max) {
      return Math.max(min, Math.min(max, value));
   }

   private void releaseParamsBuffer() {
      if (this.compositeParams != null) {
         this.compositeParams.close();
         this.compositeParams = null;
      }
   }

   private static int alignUp(int value, int alignment) {
      return -Math.floorDiv(-value, alignment) * alignment;
   }

   private void blurRegion(int passIndex, Framebuffer sourceFramebuffer, SimpleFramebuffer outputFramebuffer, GuiBlurRenderer.PixelRect rect, float radius) {
      if (outputFramebuffer != null && rect != null) {
         int levels = this.frameBlurIterations > 0 ? this.frameBlurIterations : this.blurIterations(radius);
         this.useGrayscaleFallback = passIndex == 65 || passIndex == 66;
         this.allocateBlurChain(outputFramebuffer.textureWidth, outputFramebuffer.textureHeight, levels);
         SimpleFramebuffer[] downChain = this.downsampleChain;
         SimpleFramebuffer[] upChain = this.upsampleChain;
         float sampleOffset = 1.0F;
         GpuSampler sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
         GpuTextureView sourceView = sourceFramebuffer.getColorAttachmentView();
         int sourceWidth = sourceFramebuffer.textureWidth;
         int sourceHeight = sourceFramebuffer.textureHeight;
         float u = (float)rect.x() / Math.max(sourceFramebuffer.textureWidth, 1);
         float v = (float)rect.y() / Math.max(sourceFramebuffer.textureHeight, 1);
         float uSize = (float)rect.width() / Math.max(sourceFramebuffer.textureWidth, 1);
         float vSize = (float)rect.height() / Math.max(sourceFramebuffer.textureHeight, 1);

         for (int downLevel = 0; downLevel < levels; downLevel++) {
            SimpleFramebuffer downFramebuffer = downChain[downLevel];
            this.renderKawaseToFramebuffer(DOWNSAMPLE_PIPELINE, sourceView, sourceWidth, sourceHeight, downFramebuffer, sampleOffset, u, v, uSize, vSize, sampler);
            sourceView = downFramebuffer.getColorAttachmentView();
            sourceWidth = downFramebuffer.textureWidth;
            sourceHeight = downFramebuffer.textureHeight;
            u = 0.0F;
            v = 0.0F;
            uSize = 1.0F;
            vSize = 1.0F;
         }

         SimpleFramebuffer lastMip = downChain[levels - 1];
         SimpleFramebuffer gaussianFramebuffer = this.gaussianScratch;
         if (lastMip != null && gaussianFramebuffer != null && gaussianFramebuffer.getColorAttachmentView() != null) {
            float screenSigma = radius * (0.5F) * (Math.max(sourceFramebuffer.textureHeight, 1) / (1080.0F));
            float mipSigma = screenSigma * ((float)lastMip.textureHeight / Math.max(rect.height(), 1));
            mipSigma = clampFloat(mipSigma, (0.6F), (12.0F));
            int kernelRadius = Math.min(31, Math.max(2, (int)Math.ceil(mipSigma * (3.0F))));
            boolean heavyPass = passIndex == 66;
            this.renderGaussianPass(
               lastMip.getColorAttachmentView(),
               gaussianFramebuffer.getColorAttachmentView(),
               1.0F / Math.max(lastMip.textureWidth, 1),
               0.0F,
               mipSigma,
               kernelRadius,
               1.0F,
               1.0F,
               0.0F,
               sampler
            );
            this.renderGaussianPass(
               gaussianFramebuffer.getColorAttachmentView(),
               lastMip.getColorAttachmentView(),
               0.0F,
               1.0F / Math.max(lastMip.textureHeight, 1),
               mipSigma,
               kernelRadius,
               heavyPass ? (1.25F) : 1.0F,
               heavyPass ? 1.0F : 1.0F,
               heavyPass ? (0.78F) : 0.0F,
               sampler
            );
            sourceView = lastMip.getColorAttachmentView();
            sourceWidth = lastMip.textureWidth;
            sourceHeight = lastMip.textureHeight;
         }

         for (int upLevel = levels - 2; upLevel >= 0; upLevel--) {
            SimpleFramebuffer upFramebuffer = upChain[upLevel];
            this.renderKawaseToFramebuffer(UPSAMPLE_PIPELINE, sourceView, sourceWidth, sourceHeight, upFramebuffer, sampleOffset, 0.0F, 0.0F, 1.0F, 1.0F, sampler);
            sourceView = upFramebuffer.getColorAttachmentView();
            sourceWidth = upFramebuffer.textureWidth;
            sourceHeight = upFramebuffer.textureHeight;
         }

         this.renderKawaseToFramebuffer(UPSAMPLE_PIPELINE, sourceView, sourceWidth, sourceHeight, outputFramebuffer, sampleOffset, 0.0F, 0.0F, 1.0F, 1.0F, sampler);
      }
   }

   private SimpleFramebuffer heavyFramebuffer(int width, int height) {
      int scaledWidth = Math.max(Math.round(width * (0.25F)), 1);
      int scaledHeight = Math.max(Math.round(height * (0.25F)), 1);
      this.heavyBlurFramebuffer = this.ensureFramebuffer(this.heavyBlurFramebuffer, ("mouseclient_blur_stratum_heavy"), scaledWidth, scaledHeight);
      return this.heavyBlurFramebuffer;
   }

   public void prepareRequests() {
      if (!this.requests.isEmpty()) {
         MinecraftClient client = MinecraftClient.getInstance();
         Framebuffer framebuffer = client == null ? null : client.getFramebuffer();
         if (framebuffer != null && framebuffer.getColorAttachment() != null) {
            this.frameBlurIterations = this.blurIterations(0.0F);
            long nowNanos = System.nanoTime();
            long frameId = ++this.resultFrameId;
            GpuSampler sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
            this.primaryAvailable = false;
            this.secondaryAvailable = false;
            float maxPrimaryRadius = (-1.0F);
            float maxSecondaryRadius = (-1.0F);

            for (GuiBlurRenderer.BlurRequest globalRequest : this.requests) {
               if (globalRequest.global()) {
                  if (globalRequest.globalLayer() == 0) {
                     maxPrimaryRadius = Math.max(maxPrimaryRadius, globalRequest.spec().blurRadius());
                  } else {
                     maxSecondaryRadius = Math.max(maxSecondaryRadius, globalRequest.spec().blurRadius());
                  }
               }
            }

            if (maxPrimaryRadius >= 0.0F) {
               float primaryScale = this.resolutionScale(maxPrimaryRadius, framebuffer.textureWidth, framebuffer.textureHeight);
               int primaryWidth = Math.max(Math.round(framebuffer.textureWidth * primaryScale), 1);
               int primaryHeight = Math.max(Math.round(framebuffer.textureHeight * primaryScale), 1);
               this.primaryGlobalFramebuffer = this.ensureFramebuffer(this.primaryGlobalFramebuffer, ("mouseclient_blur_global"), primaryWidth, primaryHeight);
               this.primaryUpdatedAtNanos = nowNanos;
               if (this.primaryGlobalFramebuffer != null && this.primaryGlobalFramebuffer.getColorAttachmentView() != null) {
                  this.primaryAvailable = true;
                  this.primaryRadius = maxPrimaryRadius;
                  TextureSetup textureSetup = TextureSetup.of(this.primaryGlobalFramebuffer.getColorAttachmentView(), sampler);

                  for (GuiBlurRenderer.BlurRequest primaryRequest : this.requests) {
                     if (primaryRequest.global() && primaryRequest.globalLayer() == 0) {
                        BlurTextureRegion blurRegion = primaryRequest.capture();
                        blurRegion.x = 0.0F;
                        blurRegion.y = 0.0F;
                        blurRegion.width = framebuffer.textureWidth;
                        blurRegion.height = framebuffer.textureHeight;
                        blurRegion.textureSetup = textureSetup;
                     }
                  }

                  this.requestsPrepared = true;
               }
            }

            if (maxSecondaryRadius >= 0.0F) {
               float secondaryScale = this.resolutionScale(maxSecondaryRadius, framebuffer.textureWidth, framebuffer.textureHeight);
               int secondaryWidth = Math.max(Math.round(framebuffer.textureWidth * secondaryScale), 1);
               int secondaryHeight = Math.max(Math.round(framebuffer.textureHeight * secondaryScale), 1);
               this.secondaryGlobalFramebuffer = this.ensureFramebuffer(this.secondaryGlobalFramebuffer, ("mouseclient_blur_global2"), secondaryWidth, secondaryHeight);
               this.secondaryUpdatedAtNanos = nowNanos;
               if (this.secondaryGlobalFramebuffer != null && this.secondaryGlobalFramebuffer.getColorAttachmentView() != null) {
                  this.secondaryAvailable = true;
                  this.secondaryRadius = maxSecondaryRadius;
                  TextureSetup secondaryTextureSetup = TextureSetup.of(this.secondaryGlobalFramebuffer.getColorAttachmentView(), sampler);

                  for (GuiBlurRenderer.BlurRequest secondaryRequest : this.requests) {
                     if (secondaryRequest.global() && secondaryRequest.globalLayer() == 1) {
                        BlurTextureRegion secondaryCapture = secondaryRequest.capture();
                        secondaryCapture.x = 0.0F;
                        secondaryCapture.y = 0.0F;
                        secondaryCapture.width = framebuffer.textureWidth;
                        secondaryCapture.height = framebuffer.textureHeight;
                        secondaryCapture.textureSetup = secondaryTextureSetup;
                     }
                  }

                  this.requestsPrepared = true;
               }
            }

            boolean hasHeavyRequest = false;

            for (GuiBlurRenderer.BlurRequest heavyRequestCheck : this.requests) {
               if (heavyRequestCheck.heavy()) {
                  hasHeavyRequest = true;
                  break;
               }
            }

            if (hasHeavyRequest) {
               SimpleFramebuffer heavyFramebuffer = this.heavyFramebuffer(framebuffer.textureWidth, framebuffer.textureHeight);
               this.heavyUpdatedAtNanos = nowNanos;
               if (heavyFramebuffer != null && heavyFramebuffer.getColorAttachmentView() != null) {
                  this.heavyAvailable = true;
                  TextureSetup heavyTextureSetup = TextureSetup.of(heavyFramebuffer.getColorAttachmentView(), sampler);

                  for (GuiBlurRenderer.BlurRequest heavyRequest : this.requests) {
                     if (heavyRequest.heavy()) {
                        BlurTextureRegion heavyCapture = heavyRequest.capture();
                        heavyCapture.x = 0.0F;
                        heavyCapture.y = 0.0F;
                        heavyCapture.width = framebuffer.textureWidth;
                        heavyCapture.height = framebuffer.textureHeight;
                        heavyCapture.textureSetup = heavyTextureSetup;
                     }
                  }

                  this.requestsPrepared = true;
               }
            }

            for (int requestIndex = 0; requestIndex < this.requests.size() && requestIndex < 64; requestIndex++) {
               GuiBlurRenderer.BlurRequest regionRequest = this.requests.get(requestIndex);
               if (!regionRequest.global() && !regionRequest.heavy()) {
                  BlurTextureRegion regionCapture = regionRequest.capture();
                  GuiBlurRenderer.PixelRect rect = this.captureRect(regionRequest, client, framebuffer);
                  if (rect != null) {
                     float regionScale = this.resolutionScale(regionRequest.spec().blurRadius(), rect.width(), rect.height());
                     int regionWidth = Math.max(Math.round(rect.width() * regionScale), 1);
                     int regionHeight = Math.max(Math.round(rect.height() * regionScale), 1);
                     SimpleFramebuffer regionFramebuffer = this.resultPool.acquire(regionWidth, regionHeight, frameId, nowNanos);
                     if (regionFramebuffer != null && regionFramebuffer.getColorAttachmentView() != null) {
                        regionRequest.setFramebuffer(regionFramebuffer);
                        regionRequest.setPixelRect(rect);
                        regionCapture.x = rect.x();
                        regionCapture.y = rect.y();
                        regionCapture.width = rect.width();
                        regionCapture.height = rect.height();
                        regionCapture.textureSetup = TextureSetup.of(regionFramebuffer.getColorAttachmentView(), sampler);
                        this.requestsPrepared = true;
                     }
                  }
               }
            }
         } else {
            this.requests.clear();
         }
      }
   }

   private GpuBuffer ensureParamsBuffer() {
      if (this.compositeParams != null && !this.compositeParams.isClosed() && this.compositeParams.size() >= (24576L)) {
         return this.compositeParams;
      }

      this.releaseParamsBuffer();

      try {
         this.compositeParams = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_blur_params"), 136, (24576L));
         return this.compositeParams;
      } catch (RuntimeException error) {
         return null;
      }
   }

   private void renderKawaseToFramebuffer(
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
      if (framebuffer != null) {
         this.renderKawasePass(pipeline, textureView, sourceWidth, sourceHeight, framebuffer.getColorAttachmentView(), sampleOffset, u, v, uSize, vSize, sampler, true, 1.0F);
      }
   }

   private BlurSpec clampSpec(BlurSpec spec) {
      float maxRadius = Math.max(0.0F, Math.min(spec.width(), spec.height()) * (0.5F));
      float topLeft = clampFloat(spec.radiusTopLeft(), 0.0F, maxRadius);
      float topRight = clampFloat(spec.radiusTopRight(), 0.0F, maxRadius);
      float bottomRight = clampFloat(spec.radiusBottomRight(), 0.0F, maxRadius);
      float bottomLeft = clampFloat(spec.radiusBottomLeft(), 0.0F, maxRadius);
      float smoothness = Math.max(0.0F, spec.smoothness());
      float blurRadius = Math.max(0.0F, spec.blurRadius());
      return topLeft == spec.radiusTopLeft()
            && topRight == spec.radiusTopRight()
            && bottomRight == spec.radiusBottomRight()
            && bottomLeft == spec.radiusBottomLeft()
            && smoothness == spec.smoothness()
            && blurRadius == spec.blurRadius()
         ? spec
         : new BlurSpec(
            spec.x(),
            spec.y(),
            spec.width(),
            spec.height(),
            topLeft,
            topRight,
            bottomRight,
            bottomLeft,
            smoothness,
            blurRadius,
            spec.colorTopLeft(),
            spec.colorTopRight(),
            spec.colorBottomRight(),
            spec.colorBottomLeft()
         );
   }

   public void refreshHeavyBlurIfPending() {
      MinecraftClient client = MinecraftClient.getInstance();
      Framebuffer framebuffer = client == null ? null : client.getFramebuffer();
      if (framebuffer != null
         && framebuffer.getColorAttachmentView() != null
         && this.heavyAvailable
         && this.heavyBlurFramebuffer != null
         && this.heavyBlurFramebuffer.getColorAttachmentView() != null) {
         if (this.heavyRefreshPending) {
            this.heavyRefreshPending = false;
            this.blurRegion(66, framebuffer, this.heavyBlurFramebuffer, new GuiBlurRenderer.PixelRect(0, 0, framebuffer.textureWidth, framebuffer.textureHeight), (72.0F));
         }
      }
   }

   public void processAllRequests() {
      this.processRequests((2147483647), 0);
   }

   public void uploadParams() {
      if (!this.paramEntries.isEmpty() && this.paramsDirty) {
         GpuBuffer buffer = this.ensureParamsBuffer();
         if (buffer != null) {
            try {
               MemoryStack stack = MemoryStack.stackPush();

               try {
                  ByteBuffer data = this.packParams(stack, this.paramEntries);
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

   public void renderInContext(DrawContext context, BlurSpec spec) {
      this.begin(context);
      this.render(spec);
      this.end();
   }

   private static float clampFloat(float value, float min, float max) {
      return Math.max(min, Math.min(max, value));
   }

   public void begin(DrawContext drawContext) {
      this.drawContext = drawContext;
   }

   private GuiBlurRenderer.PixelRect captureRect(GuiBlurRenderer.BlurRequest request, MinecraftClient client, Framebuffer framebuffer) {
      Window window = client.getWindow();
      int scaledWidth = Math.max(window.getScaledWidth(), 1);
      int scaledHeight = Math.max(window.getScaledHeight(), 1);
      float scaleX = (float)framebuffer.textureWidth / scaledWidth;
      float scaleY = (float)framebuffer.textureHeight / scaledHeight;
      BlurSpec spec = request.spec();
      float padding = GuiScale.toGuiUnits(Math.max((16.0F), spec.blurRadius() * 2.0F));
      ScreenRect bounds = new ScreenRect(Math.round(spec.x()), Math.round(spec.y()), Math.round(spec.width()), Math.round(spec.height()))
         .transformEachVertex(request.transform());
      if (request.scissor() != null) {
         bounds = request.scissor().intersection(bounds);
         if (bounds == null) {
            return null;
         }
      }

      float minGuiX = clampFloat(bounds.getLeft() - padding, 0.0F, scaledWidth);
      float minGuiY = clampFloat(bounds.getTop() - padding, 0.0F, scaledHeight);
      float maxGuiX = clampFloat(bounds.getRight() + padding, minGuiX + 1.0F, scaledWidth);
      float maxGuiY = clampFloat(bounds.getBottom() + padding, minGuiY + 1.0F, scaledHeight);
      byte alignment = 64;
      int minX = clampInt(alignDown((int)Math.floor(minGuiX * scaleX), alignment), 0, framebuffer.textureWidth - 1);
      int maxX = clampInt(alignUp((int)Math.ceil(maxGuiX * scaleX), alignment), minX + 1, framebuffer.textureWidth);
      int minY = clampInt(alignDown((int)Math.floor((scaledHeight - maxGuiY) * scaleY), alignment), 0, framebuffer.textureHeight - 1);
      int maxY = clampInt(alignUp((int)Math.ceil((scaledHeight - minGuiY) * scaleY), alignment), minY + 1, framebuffer.textureHeight);
      int width = Math.max(maxX - minX, 1);
      int height = Math.max(maxY - minY, 1);
      return new GuiBlurRenderer.PixelRect(minX, minY, width, height);
   }

   private void allocateBlurChain(int width, int height, int levels) {
      long passId = ++this.scratchPassId;
      long nowNanos = System.nanoTime();
      int levelWidth = Math.max(width, 1);
      int levelHeight = Math.max(height, 1);
      int lastWidth = levelWidth;
      int lastHeight = levelHeight;
      Arrays.fill(this.downsampleChain, null);
      Arrays.fill(this.upsampleChain, null);

      for (int level = 0; level < levels; level++) {
         this.downsampleChain[level] = this.scratchPool.acquire(levelWidth, levelHeight, passId, nowNanos);
         if (level < levels - 1) {
            this.upsampleChain[level] = this.scratchPool.acquire(levelWidth, levelHeight, passId, nowNanos);
         }

         lastWidth = levelWidth;
         lastHeight = levelHeight;
         levelWidth = Math.max(levelWidth / 2, 1);
         levelHeight = Math.max(levelHeight / 2, 1);
      }

      this.gaussianScratch = this.scratchPool.acquire(lastWidth, lastHeight, passId, nowNanos);
   }

   public void beginFrame() {
      this.paramEntries.clear();
      this.heavyBeforeBlurLayer = false;
      this.heavyAfterBlurLayer = false;
      this.heavyRefreshPending = true;
      this.evictUnused(System.nanoTime());
      this.requestsPrepared = false;
      this.heavyAvailable = false;
      this.secondaryAvailable = false;
      this.globalLayer = 0;
      this.paramsDirty = false;
      this.frameBlurIterations = 0;
   }

   private GpuBuffer ensureKawaseParamsBuffer() {
      if (this.kawaseParams == null || this.kawaseParams.isClosed() || this.kawaseParams.size() < (64L)) {
         this.releaseKawaseParamsBuffer();
         this.kawaseParams = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_kawase_params"), 136, (64L));
      }

      return this.kawaseParams;
   }

   @Override
   public void close() {
      this.requests.clear();
      this.paramEntries.clear();
      this.drawContext = null;
      this.requestsPrepared = false;
      this.releaseParamsBuffer();
      this.releaseKawaseParamsBuffer();
      this.resultPool.close();
      this.scratchPool.close();
      Arrays.fill(this.downsampleChain, null);
      Arrays.fill(this.upsampleChain, null);
      this.gaussianScratch = null;
      if (this.primaryGlobalFramebuffer != null) {
         this.primaryGlobalFramebuffer.delete();
         this.primaryGlobalFramebuffer = null;
      }

      if (this.secondaryGlobalFramebuffer != null) {
         this.secondaryGlobalFramebuffer.delete();
         this.secondaryGlobalFramebuffer = null;
      }

      if (this.screenBlurFramebuffer != null) {
         this.screenBlurFramebuffer.delete();
         this.screenBlurFramebuffer = null;
      }

      if (this.heavyBlurFramebuffer != null) {
         this.heavyBlurFramebuffer.delete();
         this.heavyBlurFramebuffer = null;
      }

      if (this.unusedFramebuffer != null) {
         this.unusedFramebuffer.delete();
         this.unusedFramebuffer = null;
      }

      this.primaryAvailable = false;
      if (this.screenQuad != null) {
         this.screenQuad.close();
         this.screenQuad = null;
      }
   }

   public void bindUniforms(RenderPass renderPass) {
      if (renderPass != null && !this.paramEntries.isEmpty()) {
         GpuBuffer buffer = this.paramsBuffer();
         if (buffer != null) {
            renderPass.setUniform(("BlurParamsArray"), buffer);
         }
      }
   }

   private GpuBuffer screenQuadBuffer() {
      if (this.screenQuad != null) {
         return this.screenQuad;
      }

      try (
         BufferAllocator allocator = new BufferAllocator(4 * VertexFormats.POSITION.getVertexSize());
         BuiltBuffer meshData = this.buildScreenQuad(allocator);
      ) {
         this.screenQuad = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_blur_fullscreen_quad"), 32, meshData.getBuffer());
         return this.screenQuad;
      }
   }

   private void releaseKawaseParamsBuffer() {
      if (this.kawaseParams != null) {
         this.kawaseParams.close();
         this.kawaseParams = null;
      }
   }

   public static void setScreenBlurLightness(float lightness) {
      screenBlurLightness = Math.max(0.0F, Math.min(1.0F, lightness));
   }

   private void renderKawasePass(
      RenderPipeline pipeline,
      GpuTextureView textureView,
      int sourceWidth,
      int sourceHeight,
      GpuTextureView outputView,
      float sampleOffset,
      float u,
      float v,
      float uSize,
      float vSize,
      GpuSampler sampler,
      boolean clear,
      float opacity
   ) {
      if (textureView != null && outputView != null) {
         GpuBuffer buffer = this.screenQuadBuffer();
         if (buffer != null) {
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            ShapeIndexBuffer indexBuffer = RenderSystem.getSequentialBuffer(DrawMode.QUADS);
            GpuBuffer indices = indexBuffer.getIndexBuffer(6);
            MemoryStack stack = MemoryStack.stackPush();

            label91: {
               try {
                  ByteBuffer data = stack.calloc(64);
                  data.putFloat(0, u);
                  data.putFloat(4, v);
                  data.putFloat(8, uSize);
                  data.putFloat(12, vSize);
                  data.putFloat(16, sampleOffset / Math.max(sourceWidth, 1));
                  data.putFloat(20, sampleOffset / Math.max(sourceHeight, 1));
                  float grayFallback = screenBlurLightness * (0.97F);
                  data.putFloat(32, this.useGrayscaleFallback ? grayFallback : fallbackRed);
                  data.putFloat(36, this.useGrayscaleFallback ? grayFallback : fallbackGreen);
                  data.putFloat(40, this.useGrayscaleFallback ? grayFallback : fallbackBlue);
                  data.putFloat(44, opacity);
                  data.position(0);
                  GpuBuffer paramsBuffer = this.ensureKawaseParamsBuffer();
                  if (paramsBuffer == null) {
                     break label91;
                  }

                  encoder.writeToBuffer(paramsBuffer.slice(0L, (64L)), data);

                  try (RenderPass renderPass = encoder.createRenderPass(
                        () -> ("mouseclient_kawase_blur"), outputView, clear ? OptionalInt.of(0) : OptionalInt.empty(), null, OptionalDouble.empty()
                     )) {
                     renderPass.setPipeline(pipeline);
                     renderPass.bindTexture(("Sampler0"), textureView, sampler);
                     renderPass.setUniform(("KawaseParams"), paramsBuffer);
                     renderPass.setVertexBuffer(0, buffer);
                     renderPass.setIndexBuffer(indices, indexBuffer.getIndexType());
                     renderPass.drawIndexed(0, 0, 6, 1);
                  }
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

               return;
            }

            if (stack != null) {
               stack.close();
            }
         }
      }
   }

   public void processRequests(int blurLayer, int drawCount) {
      boolean hasVanillaBlurLayer = drawCount > blurLayer && (GuiBlurState.isEnabled() || GuiBlurState.isRequested());
      this.heavyRefreshPending = !hasVanillaBlurLayer || this.heavyAfterBlurLayer;
      if (!this.requestsPrepared) {
         this.requests.clear();
      } else {
         MinecraftClient client = MinecraftClient.getInstance();
         Framebuffer framebuffer = client == null ? null : client.getFramebuffer();
         if (framebuffer != null && framebuffer.getColorAttachmentView() != null) {
            if (this.primaryAvailable && this.primaryGlobalFramebuffer != null) {
               this.blurRegion(
                  64, framebuffer, this.primaryGlobalFramebuffer, new GuiBlurRenderer.PixelRect(0, 0, framebuffer.textureWidth, framebuffer.textureHeight), this.primaryRadius
               );
            }

            if (this.heavyAvailable
               && (this.heavyBeforeBlurLayer || !hasVanillaBlurLayer)
               && this.heavyBlurFramebuffer != null
               && this.heavyBlurFramebuffer.getColorAttachmentView() != null) {
               this.blurRegion(
                  66, framebuffer, this.heavyBlurFramebuffer, new GuiBlurRenderer.PixelRect(0, 0, framebuffer.textureWidth, framebuffer.textureHeight), (72.0F)
               );
            }

            for (int requestIndex = 0; requestIndex < this.requests.size() && requestIndex < 64; requestIndex++) {
               GuiBlurRenderer.BlurRequest request = this.requests.get(requestIndex);
               if (!request.global() && !request.heavy()) {
                  GuiBlurRenderer.PixelRect rect = request.pixelRect();
                  SimpleFramebuffer regionFramebuffer = request.framebuffer();
                  if (rect != null && regionFramebuffer != null) {
                     this.blurRegion(requestIndex, framebuffer, regionFramebuffer, rect, request.spec().blurRadius());
                  }
               }
            }

            this.requests.clear();
         } else {
            this.requests.clear();
         }
      }
   }

   public static GuiBlurRenderer getInstance() {
      GuiBlurRenderer renderer = INSTANCE;
      if (renderer == null) {
         synchronized (GuiBlurRenderer.class) {
            renderer = INSTANCE;
            if (renderer == null) {
               renderer = new GuiBlurRenderer();
               INSTANCE = renderer;
            }
         }
      }

      return renderer;
   }

   public static void setFallbackColor(float red, float green, float blue) {
      if (Float.isFinite(red) && Float.isFinite(green) && Float.isFinite(blue)) {
         fallbackRed = clampFloat(red, 0.0F, 1.0F);
         fallbackGreen = clampFloat(green, 0.0F, 1.0F);
         fallbackBlue = clampFloat(blue, 0.0F, 1.0F);
      }
   }

   private GuiBlurRenderer() {
      this.resultPool = new GuiBlurRenderer.BlurFramebufferPool(("mouseclient_blur_result"));
      this.scratchPool = new GuiBlurRenderer.BlurFramebufferPool(("mouseclient_blur_scratch"));
      this.downsampleChain = new SimpleFramebuffer[4];
      this.upsampleChain = new SimpleFramebuffer[4];
      this.heavyRefreshPending = true;
      this.paramsDirty = true;
   }

   public void renderGlobalBlur(BlurSpec spec) {
      DrawContext context = this.drawContext;
      if (spec != null && spec.visible() && context != null) {
         try {
            BlurSpec clampedSpec = this.clampSpec(spec);
            Matrix3x2f transform = GuiScale.getScaledMatrices(context);
            ScreenRect bounds = ScissorStack.current();
            BlurTextureRegion blurRegion = new BlurTextureRegion();
            GuiBlurRenderer.BlurRequest request = new GuiBlurRenderer.BlurRequest(clampedSpec, transform, bounds, blurRegion, true);
            request.setGlobalLayer(this.globalLayer);
            this.requests.add(request);
            ((DrawContextAccessor)(Object)context).getGuiRenderState().addSimpleElement(new BlurRenderState(transform, clampedSpec, bounds, blurRegion));
         } catch (RuntimeException error) {
         }
      }
   }

   public int allocateParams(BlurSpec spec, BlurTextureRegion blurRegion) {
      int index = this.paramEntries.size();
      if (index == 512) {
         return -1;
      }

      this.paramEntries.add(new GuiBlurRenderer.BlurParamEntry(spec, blurRegion));
      this.paramsDirty = true;
      return index;
   }

   public static void setGlobalLayer(int layer) {
      getInstance().globalLayer = Math.max(0, Math.min(1, layer));
   }

   private float resolutionScale(float radius, int width, int height) {
      return radius >= (40.0F) ? (0.25F) : (0.5F);
   }

   public boolean usesPipeline(RenderPipeline pipeline) {
      return pipeline == COMPOSITE_PIPELINE;
   }

   private static int alignDown(int value, int alignment) {
      return Math.floorDiv(value, alignment) * alignment;
   }

   private static Identifier id(String path) {
      return Identifier.of(("mouseclient"), path);
   }

   private BuiltBuffer buildScreenQuad(BufferAllocator allocator) {
      BufferBuilder builder = new BufferBuilder(allocator, DrawMode.QUADS, VertexFormats.POSITION);
      builder.vertex((-1.0F), (-1.0F), 0.0F);
      builder.vertex((-1.0F), 1.0F, 0.0F);
      builder.vertex(1.0F, 1.0F, 0.0F);
      builder.vertex(1.0F, (-1.0F), 0.0F);
      return builder.end();
   }

   public void trackHeavyDraw(RenderPipeline pipeline, TextureSetup textureSetup, int preparationIndex, int blurLayer) {
      if (this.heavyAvailable && pipeline == COMPOSITE_PIPELINE && textureSetup != null && this.heavyBlurFramebuffer != null) {
         GpuTextureView textureView = this.heavyBlurFramebuffer.getColorAttachmentView();
         if (textureView != null && textureSetup.texure0() == textureView) {
            if (preparationIndex < blurLayer) {
               this.heavyBeforeBlurLayer = true;
            } else {
               this.heavyAfterBlurLayer = true;
            }
         }
      }
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

   private int blurIterations(float unusedRadius) {
      return 2;
   }

   private void evictUnused(long nowNanos) {
      this.resultPool.evictUnused(nowNanos, (5000000000L));
      this.scratchPool.evictUnused(nowNanos, (5000000000L));
      if (this.primaryGlobalFramebuffer != null && nowNanos - this.primaryUpdatedAtNanos > (5000000000L)) {
         this.primaryGlobalFramebuffer.delete();
         this.primaryGlobalFramebuffer = null;
      }

      if (this.secondaryGlobalFramebuffer != null && nowNanos - this.secondaryUpdatedAtNanos > (5000000000L)) {
         this.secondaryGlobalFramebuffer.delete();
         this.secondaryGlobalFramebuffer = null;
      }

      if (this.screenBlurFramebuffer != null && nowNanos - this.screenBlurUpdatedAtNanos > (5000000000L)) {
         this.screenBlurFramebuffer.delete();
         this.screenBlurFramebuffer = null;
      }

      if (this.heavyBlurFramebuffer != null && nowNanos - this.heavyUpdatedAtNanos > (5000000000L)) {
         this.heavyBlurFramebuffer.delete();
         this.heavyBlurFramebuffer = null;
      }
   }

   private GpuBuffer paramsBuffer() {
      if (this.paramsDirty) {
         this.uploadParams();
      }

      if (!this.paramsDirty && this.compositeParams != null) {
         return this.compositeParams;
      }

      if (this.paramsDirty) {
         this.releaseParamsBuffer();
         MemoryStack stack = MemoryStack.stackPush();

         try {
            ByteBuffer data = this.packParams(stack, this.paramEntries);
            this.compositeParams = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_blur_params"), 136, data);
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

         this.paramsDirty = false;
         return this.compositeParams;
      } else {
         if (this.compositeParams == null || this.compositeParams.isClosed() || this.compositeParams.size() < (24576L)) {
            this.releaseParamsBuffer();
            this.compositeParams = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_blur_params"), 136, (24576L));
         }

         return this.compositeParams;
      }
   }

   public static void closeInstance() {
      GuiBlurRenderer renderer = INSTANCE;
      if (renderer != null) {
         renderer.close();
         INSTANCE = null;
      }
   }

   public void refreshSecondaryGlobalBlur() {
      if (this.secondaryAvailable && this.secondaryGlobalFramebuffer != null && this.secondaryGlobalFramebuffer.getColorAttachmentView() != null) {
         MinecraftClient client = MinecraftClient.getInstance();
         Framebuffer framebuffer = client == null ? null : client.getFramebuffer();
         if (framebuffer != null && framebuffer.getColorAttachmentView() != null) {
            this.blurRegion(
               67, framebuffer, this.secondaryGlobalFramebuffer, new GuiBlurRenderer.PixelRect(0, 0, framebuffer.textureWidth, framebuffer.textureHeight), this.secondaryRadius
            );
            this.secondaryAvailable = false;
         }
      }
   }

   public void submitGlobalCapture(DrawContext context, BlurSpec spec, BlurTextureRegion blurRegion) {
      if (context != null && spec != null && blurRegion != null && spec.visible()) {
         try {
            BlurSpec clampedSpec = this.clampSpec(spec);
            Matrix3x2f transform = GuiScale.getScaledMatrices(context);
            this.requests.add(new GuiBlurRenderer.BlurRequest(clampedSpec, transform, ScissorStack.current(), blurRegion, true));
         } catch (RuntimeException error) {
         }
      }
   }

   public void render(BlurSpec spec) {
      this.submit(this.drawContext, spec);
   }

   private ByteBuffer packParams(MemoryStack stack, List<GuiBlurRenderer.BlurParamEntry> entries) {
      int byteCount = Math.max(1, entries.size()) * 3 * 4 * 4;
      ByteBuffer data = stack.calloc(byteCount);

      for (int index = 0; index < entries.size(); index++) {
         GuiBlurRenderer.BlurParamEntry entry = (GuiBlurRenderer.BlurParamEntry)entries.get(index);
         BlurSpec spec = entry.blur();
         BlurTextureRegion blurRegion = entry.capture();
         int byteOffset = index * 3 * 4 * 4;
         data.putFloat(byteOffset, spec.radiusTopLeft());
         data.putFloat(byteOffset + 4, spec.radiusTopRight());
         data.putFloat(byteOffset + 8, spec.radiusBottomRight());
         data.putFloat(byteOffset + 12, spec.radiusBottomLeft());
         data.putFloat(byteOffset + 16, spec.width());
         data.putFloat(byteOffset + 20, spec.height());
         data.putFloat(byteOffset + 24, spec.smoothness());
         data.putFloat(byteOffset + 28, spec.blurRadius());
         data.putFloat(byteOffset + 32, blurRegion.x);
         data.putFloat(byteOffset + 36, blurRegion.y);
         data.putFloat(byteOffset + 40, blurRegion.width);
         data.putFloat(byteOffset + 44, blurRegion.height);
      }

      data.position(0);
      return data;
   }

   private void renderGaussianPass(
      GpuTextureView textureView, GpuTextureView outputView, float stepU, float stepV, float sigma, int kernelRadius, float saturation, float brightness, float dimAmount, GpuSampler sampler
   ) {
      if (textureView != null && outputView != null) {
         GpuBuffer buffer = this.screenQuadBuffer();
         if (buffer != null) {
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            ShapeIndexBuffer indexBuffer = RenderSystem.getSequentialBuffer(DrawMode.QUADS);
            GpuBuffer indices = indexBuffer.getIndexBuffer(6);
            MemoryStack stack = MemoryStack.stackPush();

            label86: {
               try {
                  ByteBuffer data = stack.calloc(64);
                  data.putFloat(0, 0.0F);
                  data.putFloat(4, 0.0F);
                  data.putFloat(8, 1.0F);
                  data.putFloat(12, 1.0F);
                  data.putFloat(16, stepU);
                  data.putFloat(20, stepV);
                  data.putFloat(24, sigma);
                  data.putFloat(28, kernelRadius);
                  float grayFallback = screenBlurLightness * (0.97F);
                  data.putFloat(32, this.useGrayscaleFallback ? grayFallback : fallbackRed);
                  data.putFloat(36, this.useGrayscaleFallback ? grayFallback : fallbackGreen);
                  data.putFloat(40, this.useGrayscaleFallback ? grayFallback : fallbackBlue);
                  data.putFloat(44, 1.0F);
                  data.putFloat(48, saturation);
                  data.putFloat(52, brightness);
                  data.putFloat(56, dimAmount);
                  data.putFloat(60, screenBlurLightness);
                  data.position(0);
                  GpuBuffer paramsBuffer = this.ensureKawaseParamsBuffer();
                  if (paramsBuffer == null) {
                     break label86;
                  }

                  encoder.writeToBuffer(paramsBuffer.slice(0L, (64L)), data);

                  try (RenderPass renderPass = encoder.createRenderPass(() -> ("mouseclient_gaussian_blur"), outputView, OptionalInt.of(0), null, OptionalDouble.empty())) {
                     renderPass.setPipeline(GAUSSIAN_PIPELINE);
                     renderPass.bindTexture(("Sampler0"), textureView, sampler);
                     renderPass.setUniform(("KawaseParams"), paramsBuffer);
                     renderPass.setVertexBuffer(0, buffer);
                     renderPass.setIndexBuffer(indices, indexBuffer.getIndexType());
                     renderPass.drawIndexed(0, 0, 6, 1);
                  }
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

               return;
            }

            if (stack != null) {
               stack.close();
            }
         }
      }
   }

   public void end() {
      this.drawContext = null;
   }

   public void renderScreenBlur(float radius, float opacity) {
      MinecraftClient client = MinecraftClient.getInstance();
      Framebuffer framebuffer = client == null ? null : client.getFramebuffer();
      if (framebuffer != null && framebuffer.getColorAttachmentView() != null) {
         float boundedOpacity = clampFloat(opacity, 0.0F, 1.0F);
         if (boundedOpacity > (0.004F)) {
            float scale = this.resolutionScale(radius, framebuffer.textureWidth, framebuffer.textureHeight);
            int width = Math.max(Math.round(framebuffer.textureWidth * scale), 1);
            int height = Math.max(Math.round(framebuffer.textureHeight * scale), 1);
            this.screenBlurFramebuffer = this.ensureFramebuffer(this.screenBlurFramebuffer, ("mouseclient_blur_stratum"), width, height);
            this.screenBlurUpdatedAtNanos = System.nanoTime();
            if (this.screenBlurFramebuffer != null && this.screenBlurFramebuffer.getColorAttachmentView() != null) {
               this.blurRegion(65, framebuffer, this.screenBlurFramebuffer, new GuiBlurRenderer.PixelRect(0, 0, framebuffer.textureWidth, framebuffer.textureHeight), radius);
               GpuSampler sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
               float insetPixels = (5.0F) * boundedOpacity * boundedOpacity;
               float insetU = insetPixels / Math.max(framebuffer.textureWidth, 1);
               float insetV = insetPixels / Math.max(framebuffer.textureHeight, 1);
               this.renderKawasePass(
                  UPSAMPLE_PIPELINE,
                  this.screenBlurFramebuffer.getColorAttachmentView(),
                  this.screenBlurFramebuffer.textureWidth,
                  this.screenBlurFramebuffer.textureHeight,
                  framebuffer.getColorAttachmentView(),
                  1.0F,
                  insetU,
                  insetV,
                  1.0F - 2.0F * insetU,
                  1.0F - 2.0F * insetV,
                  sampler,
                  false,
                  boundedOpacity
               );
            }
         }
      }
   }

   private static final class BlurRequest {
      private final BlurSpec spec;
      private final Matrix3x2f transform;
      private final ScreenRect scissor;
      private final BlurTextureRegion capture;
      private final boolean global;
      private final boolean heavy;
      private GuiBlurRenderer.PixelRect pixelRect;
      private SimpleFramebuffer framebuffer;
      private int globalLayer;

      public SimpleFramebuffer framebuffer() {
         return this.framebuffer;
      }

      public BlurSpec spec() {
         return this.spec;
      }

      public void setFramebuffer(SimpleFramebuffer framebuffer) {
         this.framebuffer = framebuffer;
      }

      public BlurTextureRegion capture() {
         return this.capture;
      }

      public ScreenRect scissor() {
         return this.scissor;
      }

      public GuiBlurRenderer.PixelRect pixelRect() {
         return this.pixelRect;
      }

      public void setPixelRect(GuiBlurRenderer.PixelRect rect) {
         this.pixelRect = rect;
      }

      public int globalLayer() {
         return this.globalLayer;
      }

      public Matrix3x2f transform() {
         return this.transform;
      }

      public void setGlobalLayer(int layer) {
         this.globalLayer = layer;
      }

      private BlurRequest(BlurSpec spec, Matrix3x2f transform, ScreenRect scissor, BlurTextureRegion capture, boolean global) {
         this(spec, transform, scissor, capture, global, false);
      }

      public boolean global() {
         return this.global;
      }

      public boolean heavy() {
         return this.heavy;
      }

      private BlurRequest(BlurSpec spec, Matrix3x2f transform, ScreenRect scissor, BlurTextureRegion capture, boolean global, boolean heavy) {
         this.spec = spec;
         this.transform = transform;
         this.scissor = scissor;
         this.capture = capture;
         this.global = global;
         this.heavy = heavy;
      }
   }

   private record BlurParamEntry(BlurSpec blur, BlurTextureRegion capture) {
   }

   private static final class BlurFramebufferPool {
      private final String labelPrefix;
      private final List<GuiBlurRenderer.PooledFramebuffer> entries = new ArrayList<>();
      private int nextId;

      private void evictUnused(long nowNanos, long maxAgeNanos) {
         for (int entryIndex = this.entries.size() - 1; entryIndex >= 0; entryIndex--) {
            GuiBlurRenderer.PooledFramebuffer entry = this.entries.get(entryIndex);
            if (nowNanos - entry.usedAtNanos > maxAgeNanos) {
               entry.framebuffer.delete();
               this.entries.remove(entryIndex);
            }
         }
      }

      private void close() {
         for (GuiBlurRenderer.PooledFramebuffer entry : this.entries) {
            entry.framebuffer.delete();
         }

         this.entries.clear();
      }

      private BlurFramebufferPool(String labelPrefix) {
         this.labelPrefix = labelPrefix;
      }

      private SimpleFramebuffer acquire(int width, int height, long frameId, long nowNanos) {
         if (width > 0 && height > 0) {
            for (int entryIndex = 0; entryIndex < this.entries.size(); entryIndex++) {
               GuiBlurRenderer.PooledFramebuffer entry = this.entries.get(entryIndex);
               if (entry.frameId != frameId && entry.framebuffer.textureWidth == width && entry.framebuffer.textureHeight == height) {
                  entry.frameId = frameId;
                  entry.usedAtNanos = nowNanos;
                  return entry.framebuffer;
               }
            }

            SimpleFramebuffer framebuffer = new SimpleFramebuffer(this.labelPrefix + "_" + this.nextId++, width, height, false);
            this.entries.add(new GuiBlurRenderer.PooledFramebuffer(framebuffer, frameId, nowNanos));
            return framebuffer;
         } else {
            return null;
         }
      }
   }

   private static final class PooledFramebuffer {
      private final SimpleFramebuffer framebuffer;
      private long frameId;
      private long usedAtNanos;

      private PooledFramebuffer(SimpleFramebuffer framebuffer, long frameId, long usedAtNanos) {
         this.framebuffer = framebuffer;
         this.frameId = frameId;
         this.usedAtNanos = usedAtNanos;
      }
   }

   private record PixelRect(int x, int y, int width, int height) {
   }
}
