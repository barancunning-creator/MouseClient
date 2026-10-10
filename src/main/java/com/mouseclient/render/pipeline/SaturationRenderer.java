package com.mouseclient.render.pipeline;

import com.mouseclient.internal.SceneColorCopy;
import com.mouseclient.network.ClientInitializer;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import org.lwjgl.system.MemoryStack;

public final class SaturationRenderer {
   private static SaturationRenderer INSTANCE;
   private static final RenderPipeline PIPELINE = RenderPipeline.builder()
      .withLocation(ClientInitializer.id(("pipeline/saturation")))
      .withVertexShader(ClientInitializer.id(("core/saturation")))
      .withFragmentShader(ClientInitializer.id(("core/saturation")))
      .withVertexFormat(VertexFormats.POSITION, DrawMode.QUADS)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withDepthWrite(false)
      .withCull(false)
      .withSampler(("Sampler0"))
      .withUniform(("SaturationParams"), UniformType.UNIFORM_BUFFER)
      .build();
   private GpuBuffer vertexBuffer;
   private GpuBuffer uniformBuffer;

   public void render() {
      if (CustomWorld.getInstance().isEnabled() && CustomWorld.getInstance().saturation.getValue() != 1.0F) {
         Framebuffer framebuffer = MinecraftClient.getInstance().getFramebuffer();
         if (framebuffer != null && framebuffer.getColorAttachment() != null && framebuffer.getColorAttachmentView() != null && framebuffer.textureWidth > 0 && framebuffer.textureHeight > 0) {
            GpuTextureView textureView = SceneColorCopy.copyColor(framebuffer);
            if (textureView != null) {
               if (this.vertexBuffer == null || this.vertexBuffer.isClosed()) {
                  try (BufferAllocator allocator = new BufferAllocator(4 * VertexFormats.POSITION.getVertexSize())) {
                     BufferBuilder builder = new BufferBuilder(allocator, DrawMode.QUADS, VertexFormats.POSITION);
                     builder.vertex((-1.0F), (-1.0F), 0.0F);
                     builder.vertex((-1.0F), 1.0F, 0.0F);
                     builder.vertex(1.0F, 1.0F, 0.0F);
                     builder.vertex(1.0F, (-1.0F), 0.0F);

                     try (BuiltBuffer mesh = builder.end()) {
                        this.vertexBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_saturation_quad"), 32, mesh.getBuffer());
                     }
                  }
               }

               if (this.uniformBuffer == null || this.uniformBuffer.isClosed()) {
                  this.uniformBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_saturation_params"), 136, (16L));
               }

               MemoryStack memory = MemoryStack.stackPush();

               try {
                  ByteBuffer uniformBytes = memory.calloc(16);
                  uniformBytes.putFloat(0, CustomWorld.getInstance().saturation.getValue());
                  RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.uniformBuffer.slice(0L, (16L)), uniformBytes);
               } catch (Throwable drawFailure) {
                  if (memory != null) {
                     try {
                        memory.close();
                     } catch (Throwable closeFailure) {
                        drawFailure.addSuppressed(closeFailure);
                     }
                  }

                  throw drawFailure;
               }

               if (memory != null) {
                  memory.close();
               }

               try (RenderPass renderPass = RenderSystem.getDevice()
                     .createCommandEncoder()
                     .createRenderPass(() -> ("mouseclient_saturation"), framebuffer.getColorAttachmentView(), OptionalInt.empty(), null, OptionalDouble.empty())) {
                  renderPass.setPipeline(PIPELINE);
                  renderPass.bindTexture(("Sampler0"), textureView, RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
                  renderPass.setUniform(("SaturationParams"), this.uniformBuffer);
                  renderPass.setVertexBuffer(0, this.vertexBuffer);
                  renderPass.setIndexBuffer(
                     RenderSystem.getSequentialBuffer(DrawMode.QUADS).getIndexBuffer(6), RenderSystem.getSequentialBuffer(DrawMode.QUADS).getIndexType()
                  );
                  renderPass.drawIndexed(0, 0, 6, 1);
               }
            }
         }
      } else {
         this.releaseBuffers();
      }
   }

   public static SaturationRenderer getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new SaturationRenderer();
      }

      return INSTANCE;
   }

   private SaturationRenderer() {
   }

   public void releaseBuffers() {
      SceneColorCopy.releaseIfIdle();
      if (this.vertexBuffer != null && !this.vertexBuffer.isClosed()) {
         this.vertexBuffer.close();
      }

      this.vertexBuffer = null;
      if (this.uniformBuffer != null && !this.uniformBuffer.isClosed()) {
         this.uniformBuffer.close();
      }

      this.uniformBuffer = null;
   }
}
