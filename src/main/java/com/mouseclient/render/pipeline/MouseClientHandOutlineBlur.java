package com.mouseclient.render.pipeline;

import com.mouseclient.modules.impl.render.SwordAnimations;
import com.mouseclient.network.ClientInitializer;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import org.lwjgl.system.MemoryStack;

public final class MouseClientHandOutlineBlur {
   private static MouseClientHandOutlineBlur INSTANCE;
   private static final RenderPipeline OUTLINE_PIPELINE = RenderPipeline.builder()
      .withLocation(ClientInitializer.id(("pipeline/hand_outline")))
      .withVertexShader(ClientInitializer.id(("core/hand_outline")))
      .withFragmentShader(ClientInitializer.id(("core/hand_outline")))
      .withVertexFormat(VertexFormats.POSITION, DrawMode.QUADS)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withDepthWrite(false)
      .withCull(false)
      .withSampler(("Sampler0"))
      .withSampler(("Sampler1"))
      .withUniform(("HandOutlineParams"), UniformType.UNIFORM_BUFFER)
      .build();
   private static final RenderPipeline BLUR_PIPELINE = RenderPipeline.builder()
      .withLocation(ClientInitializer.id(("pipeline/hand_outline_blur")))
      .withVertexShader(ClientInitializer.id(("core/hand_outline_blur")))
      .withFragmentShader(ClientInitializer.id(("core/hand_outline_blur")))
      .withVertexFormat(VertexFormats.POSITION, DrawMode.QUADS)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withDepthWrite(false)
      .withCull(false)
      .withSampler(("Sampler0"))
      .withUniform(("HandOutlineParams"), UniformType.UNIFORM_BUFFER)
      .build();
   private SimpleFramebuffer unusedCaptureFramebuffer;
   private SimpleFramebuffer unusedMaskFramebuffer;
   private GpuTexture handDepthTexture;
   private GpuTextureView handDepthView;
   private SimpleFramebuffer blurFramebuffer;
   private GpuBuffer quadBuffer;
   private GpuBuffer paramsBuffer;
   private boolean captureRequested;
   private boolean depthCaptured;

   public void compositeOutline() {
      if (this.depthCaptured) {
         this.depthCaptured = false;
         this.captureRequested = false;
         if (SwordAnimations.getInstance().isShaderEnabled()) {
            Framebuffer framebuffer = MinecraftClient.getInstance().getFramebuffer();
            if (framebuffer != null
               && framebuffer.getColorAttachmentView() != null
               && this.handDepthTexture != null
               && this.handDepthView != null
               && !this.handDepthView.isClosed()) {
               if (this.handDepthTexture.getWidth(0) == framebuffer.textureWidth && this.handDepthTexture.getHeight(0) == framebuffer.textureHeight) {
                  if (this.quadBuffer == null || this.quadBuffer.isClosed()) {
                     try (BufferAllocator allocator = new BufferAllocator(4 * VertexFormats.POSITION.getVertexSize())) {
                        BufferBuilder builder = new BufferBuilder(allocator, DrawMode.QUADS, VertexFormats.POSITION);
                        builder.vertex((-1.0F), (-1.0F), 0.0F);
                        builder.vertex((-1.0F), 1.0F, 0.0F);
                        builder.vertex(1.0F, 1.0F, 0.0F);
                        builder.vertex(1.0F, (-1.0F), 0.0F);

                        try (BuiltBuffer quadData = builder.end()) {
                           this.quadBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_hand_outline_quad"), 32, quadData.getBuffer());
                        }
                     }
                  }

                  if (this.paramsBuffer == null || this.paramsBuffer.isClosed()) {
                     this.paramsBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_hand_outline_params"), 136, (48L));
                  }

                  MemoryStack stack = MemoryStack.stackPush();

                  try {
                     ByteBuffer paramsData = stack.calloc(48);
                     paramsData.putFloat(0, (SwordAnimations.getInstance().shaderColor() >> 16 & 0xFF) / (255.0F));
                     paramsData.putFloat(4, (SwordAnimations.getInstance().shaderColor() >> 8 & 0xFF) / (255.0F));
                     paramsData.putFloat(8, (SwordAnimations.getInstance().shaderColor() & 0xFF) / (255.0F));
                     paramsData.putFloat(12, SwordAnimations.getInstance().fill.getValue());
                     paramsData.putFloat(24, SwordAnimations.getInstance().outline.getValue());
                     paramsData.putFloat(28, SwordAnimations.getInstance().highlight.getValue());
                     paramsData.putFloat(32, 1.0F / framebuffer.textureWidth);
                     paramsData.putFloat(36, 1.0F / framebuffer.textureHeight);
                     RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.paramsBuffer.slice(0L, (48L)), paramsData);
                  } catch (Throwable compositeFailure) {
                     if (stack != null) {
                        try {
                           stack.close();
                        } catch (Throwable closeFailure) {
                           compositeFailure.addSuppressed(closeFailure);
                        }
                     }

                     throw compositeFailure;
                  }

                  if (stack != null) {
                     stack.close();
                  }

                  this.blurFramebuffer = this.ensureFramebuffer(this.blurFramebuffer, ("mouseclient_hand_outline_blur"), framebuffer.textureWidth, framebuffer.textureHeight);

                  try (RenderPass renderPass = RenderSystem.getDevice()
                        .createCommandEncoder()
                        .createRenderPass(() -> ("mouseclient_hand_outline_blur"), this.blurFramebuffer.getColorAttachmentView(), OptionalInt.empty(), null, OptionalDouble.empty())) {
                     renderPass.setPipeline(BLUR_PIPELINE);
                     renderPass.bindTexture(("Sampler0"), this.handDepthView, RenderSystem.getSamplerCache().get(FilterMode.NEAREST));
                     renderPass.setUniform(("HandOutlineParams"), this.paramsBuffer);
                     this.drawFullscreenQuad(renderPass);
                  }

                  try (RenderPass compositePass = RenderSystem.getDevice()
                        .createCommandEncoder()
                        .createRenderPass(() -> ("mouseclient_hand_outline"), framebuffer.getColorAttachmentView(), OptionalInt.empty(), null, OptionalDouble.empty())) {
                     compositePass.setPipeline(OUTLINE_PIPELINE);
                     compositePass.bindTexture(("Sampler0"), this.blurFramebuffer.getColorAttachmentView(), RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
                     compositePass.bindTexture(("Sampler1"), this.handDepthView, RenderSystem.getSamplerCache().get(FilterMode.NEAREST));
                     compositePass.setUniform(("HandOutlineParams"), this.paramsBuffer);
                     this.drawFullscreenQuad(compositePass);
                  }
               }
            }
         }
      }
   }

   public void captureHandDepth() {
      if (this.captureRequested && SwordAnimations.getInstance().isShaderEnabled()) {
         Framebuffer framebuffer = MinecraftClient.getInstance().getFramebuffer();
         if (framebuffer != null && framebuffer.getDepthAttachment() != null) {
            GpuTexture texture = framebuffer.getDepthAttachment();
            if (this.handDepthTexture != null
               && (
                  this.handDepthTexture.isClosed()
                     || this.handDepthTexture.getWidth(0) != framebuffer.textureWidth
                     || this.handDepthTexture.getHeight(0) != framebuffer.textureHeight
                     || this.handDepthTexture.getFormat() != texture.getFormat()
               )) {
               this.releaseDepthTexture();
            }

            if (this.handDepthTexture == null) {
               this.handDepthTexture = RenderSystem.getDevice().createTexture(() -> ("mouseclient_hand_depth"), 15, texture.getFormat(), framebuffer.textureWidth, framebuffer.textureHeight, 1, 1);
               this.handDepthView = RenderSystem.getDevice().createTextureView(this.handDepthTexture);
            }

            RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(texture, this.handDepthTexture, 0, 0, 0, 0, 0, framebuffer.textureWidth, framebuffer.textureHeight);
            this.depthCaptured = true;
         }
      }
   }

   private void releaseDepthTexture() {
      if (this.handDepthView != null) {
         if (!this.handDepthView.isClosed()) {
            this.handDepthView.close();
         }

         this.handDepthView = null;
      }

      if (this.handDepthTexture != null) {
         if (!this.handDepthTexture.isClosed()) {
            this.handDepthTexture.close();
         }

         this.handDepthTexture = null;
      }
   }

   public static MouseClientHandOutlineBlur getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new MouseClientHandOutlineBlur();
      }

      return INSTANCE;
   }

   public void close() {
      this.captureRequested = false;
      this.depthCaptured = false;
      if (this.unusedCaptureFramebuffer != null) {
         this.unusedCaptureFramebuffer.delete();
         this.unusedCaptureFramebuffer = null;
      }

      if (this.unusedMaskFramebuffer != null) {
         this.unusedMaskFramebuffer.delete();
         this.unusedMaskFramebuffer = null;
      }

      this.releaseDepthTexture();
      if (this.blurFramebuffer != null) {
         this.blurFramebuffer.delete();
         this.blurFramebuffer = null;
      }

      if (this.quadBuffer != null && !this.quadBuffer.isClosed()) {
         this.quadBuffer.close();
      }

      this.quadBuffer = null;
      if (this.paramsBuffer != null && !this.paramsBuffer.isClosed()) {
         this.paramsBuffer.close();
      }

      this.paramsBuffer = null;
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

   private void drawFullscreenQuad(RenderPass renderPass) {
      renderPass.setVertexBuffer(0, this.quadBuffer);
      renderPass.setIndexBuffer(RenderSystem.getSequentialBuffer(DrawMode.QUADS).getIndexBuffer(6), RenderSystem.getSequentialBuffer(DrawMode.QUADS).getIndexType());
      renderPass.drawIndexed(0, 0, 6, 1);
   }

   private MouseClientHandOutlineBlur() {
   }

   public void beginHandCapture() {
      if (!SwordAnimations.getInstance().isShaderEnabled()) {
         this.close();
      } else {
         Framebuffer framebuffer = MinecraftClient.getInstance().getFramebuffer();
         if (framebuffer != null && framebuffer.getDepthAttachment() != null && framebuffer.textureWidth > 0 && framebuffer.textureHeight > 0) {
            this.captureRequested = true;
         }
      }
   }
}
