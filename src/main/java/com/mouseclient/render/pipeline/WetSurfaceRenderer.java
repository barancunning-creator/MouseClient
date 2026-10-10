package com.mouseclient.render.pipeline;

import com.mouseclient.internal.SceneColorCopy;
import com.mouseclient.network.ClientInitializer;
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
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

public final class WetSurfaceRenderer implements MinecraftAccess {
   private static WetSurfaceRenderer INSTANCE;
   private static final RenderPipeline PIPELINE = RenderPipeline.builder()
      .withLocation(ClientInitializer.id(("pipeline/wet_world")))
      .withVertexShader(ClientInitializer.id(("core/wet_world")))
      .withFragmentShader(ClientInitializer.id(("core/wet_world")))
      .withVertexFormat(VertexFormats.POSITION, DrawMode.QUADS)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
      .withDepthWrite(false)
      .withCull(false)
      .withSampler(("Sampler0"))
      .withSampler(("Sampler1"))
      .withUniform(("WetWorldParams"), UniformType.UNIFORM_BUFFER)
      .build();
   private GpuBuffer vertexBuffer;
   private GpuBuffer uniformBuffer;

   private WetSurfaceRenderer() {
   }

   public static WetSurfaceRenderer getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new WetSurfaceRenderer();
      }

      return INSTANCE;
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

   public void render(Matrix4f viewMatrix, Matrix4f projectionMatrix) {
      CustomWorld customWorld = CustomWorld.getInstance();
      if (customWorld.isEnabled() && customWorld.wetSurfaces.getValue() && minecraftClient.world != null) {
         float rainFactor = customWorld.wetSurfacesOnlyInRain.getValue()
            ? minecraftClient.world.getRainGradient(minecraftClient.getRenderTickCounter().getTickProgress(false))
            : 1.0F;
         float reflectivity = customWorld.reflections.getValue() * rainFactor;
         float wetness = customWorld.wetness.getValue() * rainFactor;
         float gloss = customWorld.specularHighlight.getValue() * rainFactor;
         if (reflectivity <= (0.002F) && wetness <= (0.002F) && gloss <= (0.002F)) {
            SceneColorCopy.releaseIfIdle();
         } else {
            Framebuffer framebuffer = minecraftClient.getFramebuffer();
            if (framebuffer != null
               && framebuffer.getColorAttachment() != null
               && framebuffer.getColorAttachmentView() != null
               && framebuffer.getDepthAttachmentView() != null
               && framebuffer.textureWidth > 0
               && framebuffer.textureHeight > 0) {
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
                           this.vertexBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_wet_world_quad"), 32, mesh.getBuffer());
                        }
                     }
                  }

                  if (this.uniformBuffer == null || this.uniformBuffer.isClosed()) {
                     this.uniformBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_wet_world_params"), 136, (208L));
                  }

                  float dayPhase = (float)(minecraftClient.world.getTimeOfDay() % (24000L)) / (24000.0F) - (0.25F);
                  if (dayPhase < 0.0F) {
                     dayPhase++;
                  }

                  float celestialPhase = dayPhase + (1.0F - (float)((Math.cos(dayPhase * (3.141592653589793D)) + 1.0) / (2.0D)) - dayPhase) / (3.0F);
                  float sunAngle = celestialPhase * (6.2831855F);
                  int reflectionColor = customWorld.customReflectionColor.getValue() ? customWorld.reflectionColor.getArgb() : CustomWorld.horizonColor();
                  MemoryStack memory = MemoryStack.stackPush();

                  try {
                     ByteBuffer uniformBytes = memory.calloc(208);
                     new Matrix4f(projectionMatrix).mul(viewMatrix).get(0, uniformBytes);
                     new Matrix4f(projectionMatrix).mul(viewMatrix).invert().get(64, uniformBytes);
                     uniformBytes.putFloat(128, (float)minecraftClient.gameRenderer.getCamera().getCameraPos().x);
                     uniformBytes.putFloat(132, (float)minecraftClient.gameRenderer.getCamera().getCameraPos().y);
                     uniformBytes.putFloat(136, (float)minecraftClient.gameRenderer.getCamera().getCameraPos().z);
                     uniformBytes.putFloat(140, (float)(System.currentTimeMillis() % (100000L)) / (1000.0F));
                     uniformBytes.putFloat(144, (reflectionColor >> 16 & 0xFF) / (255.0F));
                     uniformBytes.putFloat(148, (reflectionColor >> 8 & 0xFF) / (255.0F));
                     uniformBytes.putFloat(152, (reflectionColor & 0xFF) / (255.0F));
                     uniformBytes.putFloat(156, reflectivity);
                     uniformBytes.putFloat(160, 0.0F);
                     uniformBytes.putFloat(164, (float)Math.cos(sunAngle));
                     uniformBytes.putFloat(168, (float)Math.sin(sunAngle));
                     uniformBytes.putFloat(172, gloss);
                     uniformBytes.putFloat(176, wetness);
                     uniformBytes.putFloat(180, customWorld.ripples.getValue());
                     uniformBytes.putFloat(184, customWorld.floorOnly.getValue() ? 1.0F : 0.0F);
                     uniformBytes.putFloat(188, customWorld.reflectSky.getValue());
                     uniformBytes.putFloat(192, (8.0F));
                     uniformBytes.putFloat(196, customWorld.hitThickness.getValue());
                     uniformBytes.putFloat(200, customWorld.reflectionDistance.getValue());
                     RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.uniformBuffer.slice(0L, (208L)), uniformBytes);
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
                        .createRenderPass(() -> ("mouseclient_wet_world"), framebuffer.getColorAttachmentView(), OptionalInt.empty(), null, OptionalDouble.empty())) {
                     renderPass.setPipeline(PIPELINE);
                     renderPass.bindTexture(("Sampler0"), textureView, RenderSystem.getSamplerCache().get(FilterMode.LINEAR));
                     renderPass.bindTexture(("Sampler1"), framebuffer.getDepthAttachmentView(), RenderSystem.getSamplerCache().get(FilterMode.NEAREST));
                     renderPass.setUniform(("WetWorldParams"), this.uniformBuffer);
                     renderPass.setVertexBuffer(0, this.vertexBuffer);
                     renderPass.setIndexBuffer(
                        RenderSystem.getSequentialBuffer(DrawMode.QUADS).getIndexBuffer(6), RenderSystem.getSequentialBuffer(DrawMode.QUADS).getIndexType()
                     );
                     renderPass.drawIndexed(0, 0, 6, 1);
                  }
               }
            }
         }
      } else {
         this.releaseBuffers();
      }
   }
}
