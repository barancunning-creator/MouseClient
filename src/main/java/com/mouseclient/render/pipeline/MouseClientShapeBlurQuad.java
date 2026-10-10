package com.mouseclient.render.pipeline;

import com.mouseclient.render.GuiVertexFormats;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.RenderSystem.ShapeIndexBuffer;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.util.BufferAllocator;

public final class MouseClientShapeBlurQuad {
   private static final Map<Float, GpuBuffer> quadBuffersByOffset = new HashMap<>();

   public static void drawUniformPass(RenderPipeline pipeline, Framebuffer framebuffer, GpuTextureView textureView, GpuSampler sampler, GpuBuffer paramsBuffer) {
      drawPass(pipeline, framebuffer, textureView, sampler, 0.0F, paramsBuffer, null);
   }

   private static void drawPass(RenderPipeline pipeline, Framebuffer framebuffer, GpuTextureView textureView, GpuSampler sampler, float blurOffset, GpuBuffer paramsBuffer, int[] scissorRect) {
      if (pipeline != null && framebuffer != null && textureView != null && sampler != null && framebuffer.getColorAttachmentView() != null) {
         GpuBuffer vertexBuffer = quadBuffer(blurOffset);
         ShapeIndexBuffer indices = RenderSystem.getSequentialBuffer(DrawMode.QUADS);
         GpuBuffer indexBuffer = indices.getIndexBuffer(6);

         try (RenderPass renderPass = RenderSystem.getDevice()
               .createCommandEncoder()
               .createRenderPass(() -> ("mouseclient_shape_blur_pass"), framebuffer.getColorAttachmentView(), scissorRect == null ? OptionalInt.of(0) : OptionalInt.empty())) {
            renderPass.setPipeline(pipeline);
            if (scissorRect != null) {
               renderPass.enableScissor(scissorRect[0], scissorRect[1], scissorRect[2], scissorRect[3]);
            }

            renderPass.bindTexture(("Sampler0"), textureView, sampler);
            if (paramsBuffer != null) {
               renderPass.setUniform(("MouseClientBlurRect"), paramsBuffer);
            }

            renderPass.setVertexBuffer(0, vertexBuffer);
            renderPass.setIndexBuffer(indexBuffer, indices.getIndexType());
            renderPass.drawIndexed(0, 0, 6, 1);
         }
      }
   }

   public static void drawScissoredUniformPass(RenderPipeline pipeline, Framebuffer framebuffer, GpuTextureView textureView, GpuSampler sampler, GpuBuffer paramsBuffer, int[] scissorRect) {
      drawPass(pipeline, framebuffer, textureView, sampler, 0.0F, paramsBuffer, scissorRect);
   }

   public static void drawBlurPass(RenderPipeline pipeline, Framebuffer framebuffer, GpuTextureView textureView, GpuSampler sampler, float blurOffset) {
      drawPass(pipeline, framebuffer, textureView, sampler, blurOffset, null, null);
   }

   public static void drawScissoredBlurPass(RenderPipeline pipeline, Framebuffer framebuffer, GpuTextureView textureView, GpuSampler sampler, float blurOffset, int[] scissorRect) {
      drawPass(pipeline, framebuffer, textureView, sampler, blurOffset, null, scissorRect);
   }

   private static GpuBuffer quadBuffer(float blurOffset) {
      GpuBuffer buffer = quadBuffersByOffset.get(blurOffset);
      if (buffer != null && !buffer.isClosed()) {
         return buffer;
      }

      try (BufferAllocator allocator = new BufferAllocator(4 * GuiVertexFormats.POST_PROCESS.getVertexSize())) {
         BufferBuilder builder = new BufferBuilder(allocator, DrawMode.QUADS, GuiVertexFormats.POST_PROCESS);
         builder.vertex((-1.0F), 1.0F, 0.0F).texture(0.0F, 1.0F).lineWidth(blurOffset);
         builder.vertex((-1.0F), (-1.0F), 0.0F).texture(0.0F, 0.0F).lineWidth(blurOffset);
         builder.vertex(1.0F, (-1.0F), 0.0F).texture(1.0F, 0.0F).lineWidth(blurOffset);
         builder.vertex(1.0F, 1.0F, 0.0F).texture(1.0F, 1.0F).lineWidth(blurOffset);

         try (BuiltBuffer quadData = builder.end()) {
            GpuBuffer createdBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_shape_blur_quad"), 32, quadData.getBuffer());
            quadBuffersByOffset.put(blurOffset, createdBuffer);
            return createdBuffer;
         }
      }
   }

   private MouseClientShapeBlurQuad() {
   }

   public static MouseClientShapeBlurQuad.FramebufferResetScope framebufferResetScope() {
      return new MouseClientShapeBlurQuad.FramebufferResetScope();
   }

   public static final class FramebufferResetScope implements AutoCloseable {

      @Override
      public void close() {
         GlStateManager._glBindFramebuffer((36160), 0);
         Framebuffer framebuffer = MinecraftClient.getInstance().getFramebuffer();
         if (framebuffer != null) {
            GlStateManager._viewport(0, 0, framebuffer.textureWidth, framebuffer.textureHeight);
         }

         GlStateManager._disableScissorTest();
      }
   }
}
