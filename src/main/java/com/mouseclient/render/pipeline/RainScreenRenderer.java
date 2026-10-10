package com.mouseclient.render.pipeline;

import com.mouseclient.internal.SceneColorCopy;
import com.mouseclient.network.ClientInitializer;
import com.mouseclient.render.avatar.RoofHeightMap;
import com.mouseclient.util.entity.MinecraftAccess;
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
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.math.BlockPos;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

public final class RainScreenRenderer implements MinecraftAccess {
   private static RainScreenRenderer INSTANCE;
   private static final RenderPipeline PIPELINE = RenderPipeline.builder()
      .withLocation(ClientInitializer.id(("pipeline/rain_screen")))
      .withVertexShader(ClientInitializer.id(("core/rain_screen")))
      .withFragmentShader(ClientInitializer.id(("core/rain_screen")))
      .withVertexFormat(VertexFormats.POSITION, DrawMode.QUADS)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withDepthWrite(false)
      .withCull(false)
      .withSampler(("Sampler0"))
      .withSampler(("Sampler1"))
      .withSampler(("Sampler2"))
      .withUniform(("RainScreenParams"), UniformType.UNIFORM_BUFFER)
      .build();
   private GpuBuffer vertexBuffer;
   private GpuBuffer uniformBuffer;
   private float exposureFactor;

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

   public static RainScreenRenderer getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new RainScreenRenderer();
      }

      return INSTANCE;
   }

   public void render(Matrix4f viewMatrix, Matrix4f projectionMatrix) {
      CustomWorld customWorld = CustomWorld.getInstance();
      if (customWorld.isEnabled() && customWorld.worldRain.getValue() && minecraftClient.world != null) {
         float rainFactor = customWorld.worldRainOnlyInRain.getValue()
            ? minecraftClient.world.getRainGradient(minecraftClient.getRenderTickCounter().getTickProgress(false))
            : 1.0F;
         boolean cameraInRain = minecraftClient.world.hasRain(BlockPos.ofFloored(minecraftClient.gameRenderer.getCamera().getCameraPos()));
         this.exposureFactor = this.exposureFactor + ((!cameraInRain && customWorld.worldRainOnlyInRain.getValue() ? 0.0F : 1.0F) - this.exposureFactor) * (0.06F);
         float dropletOpacity = customWorld.screenDroplets.getValue() * rainFactor * this.exposureFactor;
         float splashOpacity = customWorld.groundSplashes.getValue() * rainFactor;
         if (dropletOpacity <= (0.002F) && splashOpacity <= (0.002F)) {
            SceneColorCopy.releaseIfIdle();
         } else {
            Framebuffer framebuffer = minecraftClient.getFramebuffer();
            if (framebuffer != null
               && framebuffer.getColorAttachment() != null
               && framebuffer.getColorAttachmentView() != null
               && framebuffer.getDepthAttachmentView() != null
               && framebuffer.textureWidth > 0
               && framebuffer.textureHeight > 0) {
               if (this.vertexBuffer == null || this.vertexBuffer.isClosed()) {
                  try (BufferAllocator allocator = new BufferAllocator(4 * VertexFormats.POSITION.getVertexSize())) {
                     BufferBuilder builder = new BufferBuilder(allocator, DrawMode.QUADS, VertexFormats.POSITION);
                     builder.vertex((-1.0F), (-1.0F), 0.0F);
                     builder.vertex((-1.0F), 1.0F, 0.0F);
                     builder.vertex(1.0F, 1.0F, 0.0F);
                     builder.vertex(1.0F, (-1.0F), 0.0F);

                     try (BuiltBuffer mesh = builder.end()) {
                        this.vertexBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_rain_screen_quad"), 32, mesh.getBuffer());
                     }
                  }
               }

               RoofHeightMap.update();
               if (RoofHeightMap.textureView() != null) {
                  GpuTextureView textureView = SceneColorCopy.copyColor(framebuffer);
                  if (textureView != null) {
                     boolean checkSplashRoof = customWorld.checkRoof.getValue() && splashOpacity > (0.002F);
                     if (this.uniformBuffer == null || this.uniformBuffer.isClosed()) {
                        this.uniformBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_rain_screen_params"), 136, (128L));
                     }

                     int rainColor = customWorld.customRainColor.getValue() ? customWorld.rainColor.getArgb() : CustomWorld.horizonColor();
                     MemoryStack memory = MemoryStack.stackPush();

                     try {
                        ByteBuffer uniformBytes = memory.calloc(128);
                        new Matrix4f(projectionMatrix).mul(viewMatrix).invert().get(0, uniformBytes);
                        uniformBytes.putFloat(64, (float)minecraftClient.gameRenderer.getCamera().getCameraPos().x);
                        uniformBytes.putFloat(68, (float)minecraftClient.gameRenderer.getCamera().getCameraPos().y);
                        uniformBytes.putFloat(72, (float)minecraftClient.gameRenderer.getCamera().getCameraPos().z);
                        uniformBytes.putFloat(76, (float)(System.currentTimeMillis() % (100000L)) / (1000.0F));
                        uniformBytes.putFloat(80, (rainColor >> 16 & 0xFF) / (255.0F));
                        uniformBytes.putFloat(84, (rainColor >> 8 & 0xFF) / (255.0F));
                        uniformBytes.putFloat(88, (rainColor & 0xFF) / (255.0F));
                        uniformBytes.putFloat(92, (float)framebuffer.textureWidth / framebuffer.textureHeight);
                        uniformBytes.putFloat(96, dropletOpacity);
                        uniformBytes.putFloat(100, splashOpacity);
                        uniformBytes.putFloat(104, (64.0F));
                        uniformBytes.putFloat(112, RoofHeightMap.originX);
                        uniformBytes.putFloat(116, RoofHeightMap.originZ);
                        uniformBytes.putFloat(120, RoofHeightMap.originY);
                        uniformBytes.putFloat(124, checkSplashRoof ? 1.0F : 0.0F);
                        RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.uniformBuffer.slice(0L, (128L)), uniformBytes);
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
                           .createRenderPass(() -> ("mouseclient_rain_screen"), framebuffer.getColorAttachmentView(), OptionalInt.empty(), null, OptionalDouble.empty())) {
                        renderPass.setPipeline(PIPELINE);
                        renderPass.bindTexture(("Sampler0"), textureView, RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
                        renderPass.bindTexture(("Sampler1"), framebuffer.getDepthAttachmentView(), RenderSystem.getSamplerCache().get(FilterMode.NEAREST));
                        renderPass.bindTexture(("Sampler2"), RoofHeightMap.textureView(), RenderSystem.getSamplerCache().get(FilterMode.NEAREST));
                        renderPass.setUniform(("RainScreenParams"), this.uniformBuffer);
                        renderPass.setVertexBuffer(0, this.vertexBuffer);
                        renderPass.setIndexBuffer(
                           RenderSystem.getSequentialBuffer(DrawMode.QUADS).getIndexBuffer(6), RenderSystem.getSequentialBuffer(DrawMode.QUADS).getIndexType()
                        );
                        renderPass.drawIndexed(0, 0, 6, 1);
                     }
                  }
               }
            }
         }
      } else {
         this.releaseBuffers();
      }
   }

   private RainScreenRenderer() {
   }
}
