package com.mouseclient.render.pipeline;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.client.util.Window;
import org.lwjgl.system.MemoryStack;

public enum GuiShapeBlurCache {
   INSTANCE;

   private static final float[] QUALITY_OFFSETS = new float[]{1.1F, 1.0F, 1.4F};
   private static final int[] QUALITY_ITERATIONS = new int[]{2, 3, 4};

   private final SimpleFramebuffer[] sharedFramebuffers = new SimpleFramebuffer[3];
   private final boolean[] requestedLevels = new boolean[3];
   private final boolean[] availableLevels = new boolean[3];
   private SimpleFramebuffer[] mipFramebuffers = new SimpleFramebuffer[0];
   private final List<SimpleFramebuffer> regionFramebuffers = new ArrayList<>();
   private final List<GpuTextureView> regionViews = new ArrayList<>();
   private final List<Integer> regionQualities = new ArrayList<>();
   private final List<float[]> regionRects = new ArrayList<>();
   private final List<GuiShapeBlurCache.CachedRegionState> regionStates = new ArrayList<>();
   private final int[] scratchScissor = new int[4];
   private final float[] scratchRect = new float[4];
   private int regionCount;
   private int retainedRegionCount;
   private long retentionUpdatedAtNanos;
   private SimpleFramebuffer clampFramebuffer;
   private GpuBuffer clampRectBuffer;
   private long sharedBlurUpdatedAtNanos;

   public void endFrame() {
      long nowNanos = System.nanoTime();
      if (this.regionCount >= this.retainedRegionCount || nowNanos - this.retentionUpdatedAtNanos > 3000000000L) {
         this.retainedRegionCount = this.regionCount;
         this.retentionUpdatedAtNanos = nowNanos;
      }

      while (this.regionFramebuffers.size() > this.retainedRegionCount) {
         int removeIndex = this.regionFramebuffers.size() - 1;
         this.regionFramebuffers.remove(removeIndex).delete();
         this.regionViews.remove(removeIndex);
         this.regionQualities.remove(removeIndex);
         this.regionRects.remove(removeIndex);
         this.regionStates.remove(removeIndex);
      }

      this.regionCount = 0;
   }

   public void updateSharedBlur() {
      long nowNanos = System.nanoTime();
      if (nowNanos - this.sharedBlurUpdatedAtNanos >= 16666666L) {
         this.sharedBlurUpdatedAtNanos = nowNanos;
         this.renderRequestedLevels();
      }
   }

   private void renderRequestedLevels() {
      Framebuffer framebuffer = mainColorFramebuffer();
      if (framebuffer != null) {
         boolean anyRequested = false;

         for (boolean requested : this.requestedLevels) {
            anyRequested |= requested;
         }

         if (!anyRequested) {
            Arrays.fill(this.availableLevels, false);
         } else {
            Window window = MinecraftClient.getInstance().getWindow();
            int halfWidth = Math.max(1, window.getFramebufferWidth() / 2);
            int halfHeight = Math.max(1, window.getFramebufferHeight() / 2);
            GpuTextureView textureView = framebuffer.getColorAttachmentView();

            try (MouseClientShapeBlurQuad.FramebufferResetScope framebufferScope = MouseClientShapeBlurQuad.framebufferResetScope()) {
               for (int qualityIndex = 0; qualityIndex < 3; qualityIndex++) {
                  this.availableLevels[qualityIndex] = this.requestedLevels[qualityIndex];
                  this.requestedLevels[qualityIndex] = false;
                  if (this.availableLevels[qualityIndex]) {
                     this.sharedFramebuffers[qualityIndex] = ensureFramebuffer(this.sharedFramebuffers[qualityIndex], "mouseclient_shape_blur_l" + qualityIndex, halfWidth, halfHeight);
                     this.blurMipChain(
                        textureView, iterationsForQuality(qualityIndex), QUALITY_OFFSETS[qualityIndex], QUALITY_OFFSETS[qualityIndex] * 2.0F, this.sharedFramebuffers[qualityIndex], null, 0.0F
                     );
                  }
               }
            }
         }
      }
   }

   private static int iterationsForQuality(int qualityIndex) {
      int scaleShift = 31 - Integer.numberOfLeadingZeros(Math.max(1, 2));
      return Math.max(1, QUALITY_ITERATIONS[qualityIndex] + 1 - scaleShift);
   }

   private void blurMipChain(GpuTextureView textureView, int iterations, float blurOffset, float firstOffset, SimpleFramebuffer outputFramebuffer, float[] rect, float padding) {
      this.ensureMipFramebuffers(iterations, outputFramebuffer.textureWidth, outputFramebuffer.textureHeight);
      GpuSampler sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
      GpuTextureView sourceView = textureView;
      float downsamplePadding = blurOffset + 1.0F;
      float upsampleStepPadding = blurOffset * 0.5F + 1.0F;

      for (int mipIndex = 0; mipIndex < iterations; mipIndex++) {
         int[] downScissor = null;
         if (rect != null) {
            float paddingRadius = upsamplePadding(padding, downsamplePadding, iterations - 1);

            for (int remainingMip = iterations - 1; remainingMip > mipIndex; remainingMip--) {
               paddingRadius += upsampleStepPadding * (1 << remainingMip);
            }

            downScissor = this.scissorForRect(this.mipFramebuffers[mipIndex], outputFramebuffer, rect, paddingRadius);
         }

         MouseClientShapeBlurQuad.drawScissoredBlurPass(GuiShapePipelines.BLUR_DOWN, this.mipFramebuffers[mipIndex], sourceView, sampler, mipIndex == 0 ? firstOffset : blurOffset, downScissor);
         sourceView = this.mipFramebuffers[mipIndex].getColorAttachmentView();
      }

      for (int upsampleLevel = iterations - 2; upsampleLevel >= 0; upsampleLevel--) {
         int[] upScissor = rect == null ? null : this.scissorForRect(this.mipFramebuffers[upsampleLevel], outputFramebuffer, rect, upsamplePadding(padding, downsamplePadding, upsampleLevel));
         MouseClientShapeBlurQuad.drawScissoredBlurPass(
            GuiShapePipelines.BLUR_UP,
            this.mipFramebuffers[upsampleLevel],
            this.mipFramebuffers[upsampleLevel + 1].getColorAttachmentView(),
            sampler,
            blurOffset,
            upScissor
         );
      }

      int[] finalScissor = rect == null ? null : this.scissorForRect(outputFramebuffer, outputFramebuffer, rect, padding);
      MouseClientShapeBlurQuad.drawScissoredBlurPass(GuiShapePipelines.BLUR_UP, outputFramebuffer, this.mipFramebuffers[0].getColorAttachmentView(), sampler, blurOffset, finalScissor);
   }

   private static float upsamplePadding(float padding, float sampleOffset, int level) {
      return padding + sampleOffset * ((1 << level + 2) - 2);
   }

   private static float fullBlurPadding(float padding, int iterations, float blurOffset, float firstOffset) {
      float totalPadding = upsamplePadding(padding, blurOffset + 1.0F, iterations - 1);

      for (int mipIndex = iterations - 1; mipIndex > 0; mipIndex--) {
         totalPadding += (blurOffset * 0.5F + 1.0F) * (1 << mipIndex);
      }

      return totalPadding + firstOffset * 0.5F + 1.0F;
   }

   private int[] scissorForRect(Framebuffer targetFramebuffer, SimpleFramebuffer referenceFramebuffer, float[] rect, float padding) {
      int targetWidth = Math.max(1, targetFramebuffer.textureWidth);
      int targetHeight = Math.max(1, targetFramebuffer.textureHeight);
      float uPadding = padding / Math.max(1, referenceFramebuffer.textureWidth);
      float vPadding = padding / Math.max(1, referenceFramebuffer.textureHeight);
      int minX = Math.max(0, (int)Math.floor((rect[0] - uPadding) * targetWidth) - 1);
      int minY = Math.max(0, (int)Math.floor((rect[1] - vPadding) * targetHeight) - 1);
      int maxX = Math.min(targetWidth, (int)Math.ceil((rect[2] + uPadding) * targetWidth) + 1);
      int maxY = Math.min(targetHeight, (int)Math.ceil((rect[3] + vPadding) * targetHeight) + 1);
      int[] scissor = this.scratchScissor;
      scissor[0] = Math.min(minX, targetWidth - 1);
      scissor[1] = Math.min(minY, targetHeight - 1);
      scissor[2] = Math.max(1, maxX - scissor[0]);
      scissor[3] = Math.max(1, maxY - scissor[1]);
      return scissor;
   }

   private void ensureMipFramebuffers(int iterations, int width, int height) {
      if (this.mipFramebuffers.length < iterations) {
         this.mipFramebuffers = Arrays.copyOf(this.mipFramebuffers, iterations);
      }

      for (int mipIndex = 0; mipIndex < iterations; mipIndex++) {
         this.mipFramebuffers[mipIndex] = ensureFramebuffer(
            this.mipFramebuffers[mipIndex], "mouseclient_shape_blur_mip" + mipIndex, Math.max(1, width >> mipIndex + 1), Math.max(1, height >> mipIndex + 1)
         );
      }
   }

   public int requestRegion(int qualityIndex, float minU, float minV, float maxU, float maxV, float radius) {
      Window window = MinecraftClient.getInstance().getWindow();
      int halfWidth = Math.max(1, window.getFramebufferWidth() / 2);
      int halfHeight = Math.max(1, window.getFramebufferHeight() / 2);
      int regionIndex = this.regionCount++;

      while (this.regionFramebuffers.size() <= regionIndex) {
         this.regionFramebuffers.add(new SimpleFramebuffer("mouseclient_shape_blur_2d_res_" + this.regionFramebuffers.size(), halfWidth, halfHeight, false));
         this.regionViews.add(null);
         this.regionQualities.add(0);
         this.regionRects.add(new float[5]);
         this.regionStates.add(new GuiShapeBlurCache.CachedRegionState());
      }

      SimpleFramebuffer framebuffer = this.regionFramebuffers.get(regionIndex);
      if (framebuffer.textureWidth != halfWidth || framebuffer.textureHeight != halfHeight) {
         framebuffer.resize(halfWidth, halfHeight);
         this.regionStates.get(regionIndex).valid = false;
      }

      this.regionViews.set(regionIndex, framebuffer.getColorAttachmentView());
      this.regionQualities.set(regionIndex, clampQuality(qualityIndex));
      float[] rect = this.regionRects.get(regionIndex);
      rect[0] = minU;
      rect[1] = minV;
      rect[2] = maxU;
      rect[3] = maxV;
      rect[4] = radius;
      return regionIndex;
   }

   private TextureSetup regionTexture(int regionIndex) {
      if (regionIndex >= 0 && regionIndex < this.regionViews.size()) {
         GpuTextureView textureView = this.regionViews.get(regionIndex);
         return textureView != null && !textureView.isClosed() ? TextureSetup.of(textureView, RenderSystem.getSamplerCache().get(FilterMode.LINEAR)) : null;
      } else {
         return null;
      }
   }

   public int findRegionForTexture(GpuTextureView textureView) {
      if (textureView == null) {
         return -1;
      }

      for (int var2 = 0; var2 < this.regionViews.size(); var2++) {
         if (this.regionViews.get(var2) == textureView) {
            return var2;
         }
      }

      return -1;
   }

   public boolean isRegionDirty(int regionIndex) {
      return regionIndex >= 0 && regionIndex < this.regionFramebuffers.size() && this.needsRegionUpdate(regionIndex, System.nanoTime());
   }

   private boolean needsRegionUpdate(int regionIndex, long nowNanos) {
      GuiShapeBlurCache.CachedRegionState cached = this.regionStates.get(regionIndex);
      if (!cached.valid) {
         return true;
      } else {
         float[] rect = this.regionRects.get(regionIndex);
         if (cached.qualityIndex == this.regionQualities.get(regionIndex) && !boundsMovedSubstantially(cached.lastRect, rect)) {
            boolean boundsChanged = !Arrays.equals(cached.lastRect, rect);
            return boundsChanged && !cached.canReuseBounds(rect, this.regionFramebuffers.get(regionIndex)) ? true : nowNanos - cached.updatedAtNanos >= (boundsChanged ? 6944444L : 16666666L);
         } else {
            return true;
         }
      }
   }

   private static boolean boundsMovedSubstantially(float[] previousRect, float[] currentRect) {
      if (previousRect != null && currentRect != null) {
         float intersectionWidth = Math.max(0.0F, Math.min(previousRect[2], currentRect[2]) - Math.max(previousRect[0], currentRect[0]));
         float intersectionHeight = Math.max(0.0F, Math.min(previousRect[3], currentRect[3]) - Math.max(previousRect[1], currentRect[1]));
         float intersectionArea = intersectionWidth * intersectionHeight;
         float previousArea = Math.max(0.0F, previousRect[2] - previousRect[0]) * Math.max(0.0F, previousRect[3] - previousRect[1]);
         float currentArea = Math.max(0.0F, currentRect[2] - currentRect[0]) * Math.max(0.0F, currentRect[3] - currentRect[1]);
         float unionArea = previousArea + currentArea - intersectionArea;
         return unionArea > 1.0E-9F && intersectionArea / unionArea < 0.75F;
      } else {
         return previousRect != currentRect;
      }
   }

   public void updateRegion(int regionIndex, Framebuffer sourceFramebuffer) {
      if (regionIndex >= 0 && regionIndex < this.regionFramebuffers.size() && sourceFramebuffer != null && sourceFramebuffer.getColorAttachmentView() != null && !sourceFramebuffer.getColorAttachmentView().isClosed()) {
         SimpleFramebuffer targetFramebuffer = this.regionFramebuffers.get(regionIndex);
         long nowNanos = System.nanoTime();
         if (this.needsRegionUpdate(regionIndex, nowNanos)) {
            float[] rect = this.regionRects.get(regionIndex);
            int qualityIndex = this.regionQualities.get(regionIndex);
            boolean clamped = this.renderRegion(sourceFramebuffer, targetFramebuffer, rect, qualityIndex);
            GuiShapeBlurCache.CachedRegionState cached = this.regionStates.get(regionIndex);
            cached.valid = true;
            cached.updatedAtNanos = nowNanos;
            cached.qualityIndex = qualityIndex;
            System.arraycopy(rect, 0, cached.lastRect, 0, 5);
            cached.clamped = clamped;
         }
      }
   }

   private boolean renderRegion(Framebuffer sourceFramebuffer, SimpleFramebuffer outputFramebuffer, float[] rect, int qualityIndex) {
      GpuTextureView textureView = sourceFramebuffer.getColorAttachmentView();
      float firstOffset = QUALITY_OFFSETS[qualityIndex] * 2.0F;
      boolean clamped = false;

      try (MouseClientShapeBlurQuad.FramebufferResetScope framebufferScope = MouseClientShapeBlurQuad.framebufferResetScope()) {
         float[] clampRect = null;
         float padding = 9.0F;
         if (rect != null && rect[2] > rect[0] && rect[3] > rect[1]) {
            firstOffset = QUALITY_OFFSETS[qualityIndex];
            clampRect = this.scratchRect;
            System.arraycopy(rect, 0, clampRect, 0, 4);
            clamped = true;
            this.clampFramebuffer = ensureFramebuffer(this.clampFramebuffer, "mouseclient_shape_blur_clamp", outputFramebuffer.textureWidth, outputFramebuffer.textureHeight);
            int[] scissor = this.scissorForRect(this.clampFramebuffer, outputFramebuffer, clampRect, fullBlurPadding(padding, iterationsForQuality(qualityIndex), QUALITY_OFFSETS[qualityIndex], firstOffset));
            MouseClientShapeBlurQuad.drawScissoredUniformPass(
               GuiShapePipelines.CLAMP_COPY, this.clampFramebuffer, textureView, RenderSystem.getSamplerCache().get(FilterMode.LINEAR), this.uploadClampRect(rect), scissor
            );
            textureView = this.clampFramebuffer.getColorAttachmentView();
         }

         this.blurMipChain(textureView, iterationsForQuality(qualityIndex), QUALITY_OFFSETS[qualityIndex], firstOffset, outputFramebuffer, clampRect, padding);
      }

      return clamped;
   }

   private GpuBuffer uploadClampRect(float[] rect) {
      if (this.clampRectBuffer == null || this.clampRectBuffer.isClosed()) {
         this.clampRectBuffer = RenderSystem.getDevice().createBuffer(() -> "mouseclient_shape_blur_rect", 136, 32L);
      }

      MemoryStack stack = MemoryStack.stackPush();

      try {
         ByteBuffer paramsData = stack.calloc(32);
         paramsData.putFloat(0, rect[0]);
         paramsData.putFloat(4, rect[1]);
         paramsData.putFloat(8, rect[2]);
         paramsData.putFloat(12, rect[3]);
         paramsData.putFloat(16, rect[4]);
         paramsData.position(0);
         RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.clampRectBuffer.slice(0L, 32L), paramsData);
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

      return this.clampRectBuffer;
   }

   public TextureSetup textureForQuality(int qualityIndex, boolean includeGuiBlur, float minU, float minV, float maxU, float maxV, float radius) {
      int clampedQualityIndex = clampQuality(qualityIndex);
      if (includeGuiBlur) {
         int regionIndex = this.requestRegion(clampedQualityIndex, minU, minV, maxU, maxV, radius);
         TextureSetup textureSetup = this.regionTexture(regionIndex);
         if (textureSetup != null) {
            return textureSetup;
         }
      }

      this.requestedLevels[clampedQualityIndex] = true;
      if (!this.availableLevels[clampedQualityIndex]) {
         return null;
      }

      SimpleFramebuffer framebuffer = this.sharedFramebuffers[clampedQualityIndex];
      return framebuffer != null && framebuffer.getColorAttachmentView() != null && !framebuffer.getColorAttachmentView().isClosed()
         ? TextureSetup.of(framebuffer.getColorAttachmentView(), RenderSystem.getSamplerCache().get(FilterMode.LINEAR))
         : null;
   }

   private static int clampQuality(int qualityIndex) {
      return qualityIndex < 0 ? 0 : Math.min(qualityIndex, 2);
   }

   private static Framebuffer mainColorFramebuffer() {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client == null) {
         return null;
      } else {
         Framebuffer framebuffer = client.getFramebuffer();
         if (framebuffer != null && framebuffer.getColorAttachment() != null && framebuffer.getColorAttachment().getFormat() == TextureFormat.RGBA8) {
            return framebuffer.getColorAttachmentView() != null && !framebuffer.getColorAttachmentView().isClosed() ? framebuffer : null;
         } else {
            return null;
         }
      }
   }

   private static SimpleFramebuffer ensureFramebuffer(SimpleFramebuffer framebuffer, String name, int width, int height) {
      if (framebuffer == null) {
         return new SimpleFramebuffer(name, width, height, false);
      }

      if (framebuffer.textureWidth != width || framebuffer.textureHeight != height) {
         framebuffer.resize(width, height);
      }

      return framebuffer;
   }

   private static final class CachedRegionState {
      private boolean valid;
      private boolean clamped;
      private long updatedAtNanos;
      private int qualityIndex;
      private final float[] lastRect = new float[5];

      private boolean canReuseBounds(float[] rect, SimpleFramebuffer framebuffer) {
         if (!this.clamped) {
            return true;
         }

         float marginU = (6.0F) / Math.max(1, framebuffer.textureWidth);
         float marginV = (6.0F) / Math.max(1, framebuffer.textureHeight);
         return rect[0] >= this.lastRect[0] - marginU
            && rect[1] >= this.lastRect[1] - marginV
            && rect[2] <= this.lastRect[2] + marginU
            && rect[3] <= this.lastRect[3] + marginV;
      }
   }
}
