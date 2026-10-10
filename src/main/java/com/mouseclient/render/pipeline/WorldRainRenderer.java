package com.mouseclient.render.pipeline;

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
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Random;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

public final class WorldRainRenderer implements MinecraftAccess {
   private static WorldRainRenderer INSTANCE;

   private static final RenderPipeline PIPELINE = RenderPipeline.builder()
      .withLocation(ClientInitializer.id(("pipeline/world_rain")))
      .withVertexShader(ClientInitializer.id(("core/ambient_particles")))
      .withFragmentShader(ClientInitializer.id(("core/ambient_particles")))
      .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, DrawMode.QUADS)
      .withBlend(BlendFunction.TRANSLUCENT)
      .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
      .withDepthWrite(false)
      .withCull(false)
      .withSampler(("Sampler0"))
      .withUniform(("AmbientParticlesParams"), UniformType.UNIFORM_BUFFER)
      .build();
   
   private GpuBuffer vertexBuffer;
   private GpuBuffer uniformBuffer;
   private long lastDrawMillis;

   private WorldRainRenderer() {
   }

   public void releaseBuffers() {
      if (this.vertexBuffer != null && !this.vertexBuffer.isClosed()) {
         this.vertexBuffer.close();
      }

      this.vertexBuffer = null;
      if (this.uniformBuffer != null && !this.uniformBuffer.isClosed()) {
         this.uniformBuffer.close();
      }

      this.uniformBuffer = null;
   }

   public static WorldRainRenderer getInstance() {
      if (INSTANCE == null) {
         INSTANCE = new WorldRainRenderer();
      }

      return INSTANCE;
   }

   public void render(Matrix4f viewMatrix, Matrix4f projectionMatrix) {
      CustomWorld customWorld = CustomWorld.getInstance();
      if (customWorld.isEnabled() && customWorld.worldRain.getValue() && minecraftClient.world != null) {
         float rainFactor = customWorld.worldRainOnlyInRain.getValue()
            ? minecraftClient.world.getRainGradient(minecraftClient.getRenderTickCounter().getTickProgress(false))
            : 1.0F;
         float streakOpacity = customWorld.rainStreaks.getValue() * rainFactor;
         if (streakOpacity <= (0.002F)) {
            if (this.vertexBuffer != null && System.currentTimeMillis() - this.lastDrawMillis > (5000L)) {
               this.releaseBuffers();
            }
         } else {
            Framebuffer framebuffer = minecraftClient.getFramebuffer();
            if (framebuffer != null
               && framebuffer.getColorAttachmentView() != null
               && framebuffer.getDepthAttachmentView() != null
               && framebuffer.textureWidth > 0
               && framebuffer.textureHeight > 0) {
               RoofHeightMap.update();
               if (RoofHeightMap.textureView() != null) {
                  if (this.vertexBuffer == null || this.vertexBuffer.isClosed()) {
                     Random random = new Random((1592639215L));

                     try (BufferAllocator allocator = new BufferAllocator(32000 * VertexFormats.POSITION_TEXTURE_COLOR.getVertexSize())) {
                        BufferBuilder builder = new BufferBuilder(allocator, DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

                        for (int var10 = 0; var10 < 8000; var10++) {
                           float seedX = random.nextFloat() * (40.0F);
                           float seedY = random.nextFloat() * (30.0F);
                           float seedZ = random.nextFloat() * (40.0F);
                           int seedColor = 160 + random.nextInt(96) << 24 | random.nextInt(256) << 16 | random.nextInt(256) << 8 | random.nextInt(256);
                           builder.vertex(seedX, seedY, seedZ).texture(0.0F, 0.0F).color(seedColor);
                           builder.vertex(seedX, seedY, seedZ).texture(0.0F, 1.0F).color(seedColor);
                           builder.vertex(seedX, seedY, seedZ).texture(1.0F, 1.0F).color(seedColor);
                           builder.vertex(seedX, seedY, seedZ).texture(1.0F, 0.0F).color(seedColor);
                        }

                        try (BuiltBuffer mesh = builder.end()) {
                           this.vertexBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_world_rain_mesh"), 32, mesh.getBuffer());
                        }
                     }
                  }

                  if (this.uniformBuffer == null || this.uniformBuffer.isClosed()) {
                     this.uniformBuffer = RenderSystem.getDevice().createBuffer(() -> ("mouseclient_world_rain_params"), 136, (192L));
                  }

                  int particleCount = MathHelper.clamp(Math.round((4000.0F) * customWorld.rainDensity.getValue()), 1, 8000);
                  int rainColor = customWorld.customRainColor.getValue() ? customWorld.rainColor.getArgb() : CustomWorld.horizonColor();
                  float fallSpeed = (22.0F) * customWorld.rainSpeed.getValue();
                  boolean checkRoof = customWorld.checkRoof.getValue();
                  MemoryStack memory = MemoryStack.stackPush();

                  try {
                     ByteBuffer uniformBytes = memory.calloc(192);
                     new Matrix4f(projectionMatrix).mul(viewMatrix).get(0, uniformBytes);
                     uniformBytes.putFloat(64, (float)minecraftClient.gameRenderer.getCamera().getCameraPos().x);
                     uniformBytes.putFloat(68, (float)minecraftClient.gameRenderer.getCamera().getCameraPos().y);
                     uniformBytes.putFloat(72, (float)minecraftClient.gameRenderer.getCamera().getCameraPos().z);
                     uniformBytes.putFloat(76, (float)(System.currentTimeMillis() % (2000000L)) / (1000.0F));
                     uniformBytes.putFloat(80, viewMatrix.m00());
                     uniformBytes.putFloat(84, viewMatrix.m10());
                     uniformBytes.putFloat(88, viewMatrix.m20());
                     uniformBytes.putFloat(92, (0.09F));
                     uniformBytes.putFloat(96, viewMatrix.m01());
                     uniformBytes.putFloat(100, viewMatrix.m11());
                     uniformBytes.putFloat(104, viewMatrix.m21());
                     uniformBytes.putFloat(108, (0.4F));
                     uniformBytes.putFloat(112, (1.6F) * customWorld.rainSpeed.getValue());
                     uniformBytes.putFloat(116, -fallSpeed);
                     uniformBytes.putFloat(120, 1.0F * customWorld.rainSpeed.getValue());
                     uniformBytes.putFloat(124, 0.0F);
                     uniformBytes.putFloat(128, (40.0F));
                     uniformBytes.putFloat(132, (30.0F));
                     uniformBytes.putFloat(136, (40.0F));
                     uniformBytes.putFloat(140, customWorld.streakLength.getValue());
                     uniformBytes.putFloat(144, (rainColor >> 16 & 0xFF) / (255.0F));
                     uniformBytes.putFloat(148, (rainColor >> 8 & 0xFF) / (255.0F));
                     uniformBytes.putFloat(152, (rainColor & 0xFF) / (255.0F));
                     uniformBytes.putFloat(156, streakOpacity);
                     uniformBytes.putFloat(160, RoofHeightMap.originX);
                     uniformBytes.putFloat(164, RoofHeightMap.originZ);
                     uniformBytes.putFloat(168, RoofHeightMap.originY);
                     uniformBytes.putFloat(172, checkRoof ? 1.0F : 0.0F);
                     uniformBytes.putFloat(176, (64.0F));
                     RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.uniformBuffer.slice(0L, (192L)), uniformBytes);
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
                        .createRenderPass(
                           () -> ("mouseclient_world_rain"), framebuffer.getColorAttachmentView(), OptionalInt.empty(), framebuffer.getDepthAttachmentView(), OptionalDouble.empty()
                        )) {
                     renderPass.setPipeline(PIPELINE);
                     renderPass.bindTexture(("Sampler0"), RoofHeightMap.textureView(), RenderSystem.getSamplerCache().get(FilterMode.NEAREST));
                     renderPass.setUniform(("AmbientParticlesParams"), this.uniformBuffer);
                     renderPass.setVertexBuffer(0, this.vertexBuffer);
                     renderPass.setIndexBuffer(
                        RenderSystem.getSequentialBuffer(DrawMode.QUADS).getIndexBuffer(particleCount * 6),
                        RenderSystem.getSequentialBuffer(DrawMode.QUADS).getIndexType()
                     );
                     renderPass.drawIndexed(0, 0, particleCount * 6, 1);
                  }

                  this.lastDrawMillis = System.currentTimeMillis();
               }
            }
         }
      } else {
         this.releaseBuffers();
      }
   }
}
